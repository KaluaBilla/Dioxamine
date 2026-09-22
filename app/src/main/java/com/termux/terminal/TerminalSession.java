package com.termux.terminal;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * A terminal session, consisting of an I/O stream coupled to a terminal emulator.
 * <p>
 * This pure-Java implementation handles ANSI/VT100 escape sequences and renders to a terminal
 * screen without requiring native JNI/forkpty bindings. Output from the terminal (user key events
 * and terminal query responses) is dispatched via {@link SessionOutputListener}.
 * Incoming data from the shell stream is passed into {@link #append(byte[], int, int)}.
 * All terminal emulation and callback methods are performed on the main thread.
 */
public final class TerminalSession extends TerminalOutput {

    public interface SessionOutputListener {
        void onSessionWrite(@NonNull TerminalSession session, @NonNull byte[] data, int offset, int count);
        void onSessionResize(@NonNull TerminalSession session, int columns, int rows);
    }

    public interface SessionUpdateListener {
        void onSessionUpdate(@NonNull TerminalSession session);
    }

    private SessionUpdateListener mSessionUpdateListener;

    public void setSessionUpdateListener(SessionUpdateListener listener) {
        this.mSessionUpdateListener = listener;
    }

    private Integer mCustomForeground;
    private Integer mCustomBackground;
    private Integer mCustomCursor;

    public void setColors(int foreground, int background, int cursor) {
        if (mCustomForeground != null && mCustomForeground == foreground &&
            mCustomBackground != null && mCustomBackground == background &&
            mCustomCursor != null && mCustomCursor == cursor) {
            return;
        }
        mCustomForeground = foreground;
        mCustomBackground = background;
        mCustomCursor = cursor;
        if (mEmulator != null && mEmulator.mColors != null) {
            mEmulator.mColors.setColors(foreground, background, cursor);
        }
        notifyScreenUpdate();
    }

    private static final int MSG_NEW_INPUT = 1;
    private static final int MSG_PROCESS_EXITED = 4;

    public final String mHandle = UUID.randomUUID().toString();

    TerminalEmulator mEmulator;

    /**
     * A queue written to from a separate thread when data arrives, and read by main thread
     * to process by terminal emulator.
     */
    final ByteQueue mProcessToTerminalIOQueue = new ByteQueue(64 * 1024);

    /**
     * Queue for outgoing data when no direct SessionOutputListener is attached.
     */
    final ByteQueue mTerminalToProcessIOQueue = new ByteQueue(4096);

    /** Buffer to translate code points into utf8 before writing */
    private final byte[] mUtf8InputBuffer = new byte[5];

    /** Callback which gets notified when a session finishes or changes title. */
    TerminalSessionClient mClient;

    private SessionOutputListener mSessionOutputListener;

    /** The pid of the shell process. 0 if not started, 1 if active, and -1 if finished running. */
    int mShellPid;

    /** The exit status of the shell process. Only valid if {@link #mShellPid} is -1. */
    int mShellExitStatus;

    /** Set by the application for user identification of session, not by terminal. */
    public String mSessionName;

    final Handler mMainThreadHandler = new MainThreadHandler();

    private final String mShellPath;
    private final String mCwd;
    private final String[] mArgs;
    private final String[] mEnv;
    private final Integer mTranscriptRows;

    private static final String LOG_TAG = "TerminalSession";

    public TerminalSession(String shellPath, String cwd, String[] args, String[] env, Integer transcriptRows, TerminalSessionClient client) {
        this.mShellPath = shellPath;
        this.mCwd = cwd;
        this.mArgs = args;
        this.mEnv = env;
        this.mTranscriptRows = transcriptRows;
        this.mClient = client;
    }

    public TerminalSession(Integer transcriptRows, TerminalSessionClient client) {
        this(null, null, null, null, transcriptRows, client);
    }

    public void setSessionOutputListener(@Nullable SessionOutputListener listener) {
        this.mSessionOutputListener = listener;
    }

    public @Nullable SessionOutputListener getSessionOutputListener() {
        return mSessionOutputListener;
    }

    /**
     * @param client The {@link TerminalSessionClient} interface implementation to allow
     *               for communication between {@link TerminalSession} and its client.
     */
    public void updateTerminalSessionClient(TerminalSessionClient client) {
        mClient = client;

        if (mEmulator != null)
            mEmulator.updateTerminalSessionClient(client);
    }

    /** Inform the attached pty / remote shell of the new size and reflow or initialize the emulator. */
    public void updateSize(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
        if (mEmulator == null) {
            initializeEmulator(columns, rows, cellWidthPixels, cellHeightPixels);
        } else {
            mEmulator.resize(columns, rows, cellWidthPixels, cellHeightPixels);
            if (mSessionOutputListener != null) {
                mSessionOutputListener.onSessionResize(this, columns, rows);
            }
        }
    }

    /** The terminal title as set through escape sequences or null if none set. */
    public String getTitle() {
        return (mEmulator == null) ? null : mEmulator.getTitle();
    }

    /**
     * Set the terminal emulator's window size and start terminal emulation.
     *
     * @param columns The number of columns in the terminal window.
     * @param rows    The number of rows in the terminal window.
     */
    public void initializeEmulator(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
        mEmulator = new TerminalEmulator(this, columns, rows, cellWidthPixels, cellHeightPixels, mTranscriptRows, mClient);
        if (mCustomForeground != null && mCustomBackground != null && mCustomCursor != null) {
            mEmulator.mColors.setColors(mCustomForeground, mCustomBackground, mCustomCursor);
        }
        mShellPid = 1;
        if (mClient != null) {
            mClient.setTerminalShellPid(this, mShellPid);
        }
        if (mSessionOutputListener != null) {
            mSessionOutputListener.onSessionResize(this, columns, rows);
        }
    }

    /** Feeds bytes received from the remote process/device into the terminal emulator. */
    public void append(byte[] buffer, int offset, int count) {
        if (count <= 0) return;
        if (!mProcessToTerminalIOQueue.write(buffer, offset, count)) return;
        mMainThreadHandler.sendEmptyMessage(MSG_NEW_INPUT);
    }

    public void append(byte[] buffer, int count) {
        append(buffer, 0, count);
    }

    public void append(byte[] buffer) {
        if (buffer != null) {
            append(buffer, 0, buffer.length);
        }
    }

    /** Write data to the shell process or output listener. */
    @Override
    public void write(byte[] data, int offset, int count) {
        if (mSessionOutputListener != null) {
            mSessionOutputListener.onSessionWrite(this, data, offset, count);
        } else if (mShellPid > 0) {
            mTerminalToProcessIOQueue.write(data, offset, count);
        }
    }

    /** Write the Unicode code point to the terminal encoded in UTF-8. */
    public void writeCodePoint(boolean prependEscape, int codePoint) {
        if (codePoint > 1114111 || (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
            throw new IllegalArgumentException("Invalid code point: " + codePoint);
        }

        int bufferPosition = 0;
        if (prependEscape) mUtf8InputBuffer[bufferPosition++] = 27;

        if (codePoint <= /* 7 bits */0b1111111) {
            mUtf8InputBuffer[bufferPosition++] = (byte) codePoint;
        } else if (codePoint <= /* 11 bits */0b11111111111) {
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b11000000 | (codePoint >> 6));
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        } else if (codePoint <= /* 16 bits */0b1111111111111111) {
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b11100000 | (codePoint >> 12));
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 6) & 0b111111));
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        } else {
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b11110000 | (codePoint >> 18));
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 12) & 0b111111));
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 6) & 0b111111));
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        }
        write(mUtf8InputBuffer, 0, bufferPosition);
    }

    public TerminalEmulator getEmulator() {
        return mEmulator;
    }

    /** Notify the {@link #mClient} that the screen has changed. */
    protected void notifyScreenUpdate() {
        if (mClient != null) {
            mClient.onTextChanged(this);
        }
        if (mSessionUpdateListener != null) {
            mSessionUpdateListener.onSessionUpdate(this);
        }
    }

    /** Reset state for terminal emulator state. */
    public void reset() {
        if (mEmulator != null) {
            mEmulator.reset();
        }
        notifyScreenUpdate();
    }

    /** Finish this terminal session. */
    public void finishIfRunning() {
        if (isRunning()) {
            finish(0);
        }
    }

    public void finish(int exitCode) {
        mMainThreadHandler.sendMessage(mMainThreadHandler.obtainMessage(MSG_PROCESS_EXITED, exitCode));
    }

    /** Cleanup resources when the process exits. */
    void cleanupResources(int exitStatus) {
        synchronized (this) {
            mShellPid = -1;
            mShellExitStatus = exitStatus;
        }

        mTerminalToProcessIOQueue.close();
        mProcessToTerminalIOQueue.close();
    }

    @Override
    public void titleChanged(String oldTitle, String newTitle) {
        if (mClient != null) {
            mClient.onTitleChanged(this);
        }
    }

    public synchronized boolean isRunning() {
        return mShellPid != -1;
    }

    /** Only valid if not {@link #isRunning()}. */
    public synchronized int getExitStatus() {
        return mShellExitStatus;
    }

    @Override
    public void onCopyTextToClipboard(String text) {
        if (mClient != null) {
            mClient.onCopyTextToClipboard(this, text);
        }
    }

    @Override
    public void onPasteTextFromClipboard() {
        if (mClient != null) {
            mClient.onPasteTextFromClipboard(this);
        }
    }

    @Override
    public void onBell() {
        if (mClient != null) {
            mClient.onBell(this);
        }
    }

    @Override
    public void onColorsChanged() {
        if (mClient != null) {
            mClient.onColorsChanged(this);
        }
    }

    public int getPid() {
        return mShellPid;
    }

    public String getCwd() {
        return mCwd;
    }

    @SuppressLint("HandlerLeak")
    class MainThreadHandler extends Handler {

        final byte[] mReceiveBuffer = new byte[64 * 1024];

        @Override
        public void handleMessage(Message msg) {
            int bytesRead;
            while ((bytesRead = mProcessToTerminalIOQueue.read(mReceiveBuffer, false)) > 0) {
                if (mEmulator != null) {
                    mEmulator.append(mReceiveBuffer, bytesRead);
                    notifyScreenUpdate();
                }
            }

            if (msg.what == MSG_PROCESS_EXITED) {
                int exitCode = (Integer) msg.obj;
                cleanupResources(exitCode);

                String exitDescription = "\r\n[Process completed";
                if (exitCode > 0) {
                    exitDescription += " (code " + exitCode + ")";
                } else if (exitCode < 0) {
                    exitDescription += " (signal " + (-exitCode) + ")";
                }
                exitDescription += " - press Enter]";

                byte[] bytesToWrite = exitDescription.getBytes(StandardCharsets.UTF_8);
                if (mEmulator != null) {
                    mEmulator.append(bytesToWrite, bytesToWrite.length);
                    notifyScreenUpdate();
                }

                if (mClient != null) {
                    mClient.onSessionFinished(TerminalSession.this);
                }
            }
        }

    }

}
