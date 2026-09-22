package io.github.rhythmcache.dioxamine.adb.shell

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import io.github.rhythmcache.adb.AdbClient
import io.github.rhythmcache.adb.AdbInteractiveSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lifecycle states for a shell session.
 */
enum class ShellSessionState {
    IDLE, STARTING, ACTIVE, CLOSED, ERROR
}

/**
 * Manages an interactive ADB shell session backed by Termux's pure-Java [TerminalSession]
 * and adb-kt's [AdbInteractiveSession].
 */
class ShellSession(
    private val context: Context,
) {
    private var adbSession: AdbInteractiveSession? = null
    private var readJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _terminalSession = MutableStateFlow<TerminalSession?>(null)
    val terminalSession: StateFlow<TerminalSession?> = _terminalSession

    private val _state = MutableStateFlow(ShellSessionState.IDLE)
    val state: StateFlow<ShellSessionState> = _state

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _title = MutableStateFlow<String?>(null)
    val title: StateFlow<String?> = _title

    fun start(client: AdbClient) {
        if (_state.value == ShellSessionState.ACTIVE ||
            _state.value == ShellSessionState.STARTING
        ) return

        _state.value = ShellSessionState.STARTING
        _errorMessage.value = null

        scope.launch {
            try {
                val adb = withContext(Dispatchers.IO) {
                    client.openInteractiveShell(terminalType = "xterm-256color")
                }
                adbSession = adb

                val termClient = object : TerminalSessionClient {
                    override fun onTitleChanged(changedSession: TerminalSession) {
                        _title.value = changedSession.title
                    }

                    override fun onSessionFinished(finishedSession: TerminalSession) {
                        _state.value = ShellSessionState.CLOSED
                    }

                    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("Terminal", text))
                    }

                    override fun onPasteTextFromClipboard(session: TerminalSession?) {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = cm?.primaryClip?.getItemAt(0)?.text?.toString()
                        if (!clip.isNullOrEmpty()) {
                            session?.write(clip)
                        }
                    }
                }

                val term = TerminalSession(
                    /* transcriptRows = */ 2000,
                    /* client = */ termClient
                )
                _terminalSession.value = term

                term.setSessionOutputListener(object : TerminalSession.SessionOutputListener {
                    override fun onSessionWrite(session: TerminalSession, data: ByteArray, offset: Int, count: Int) {
                        val chunk = data.copyOfRange(offset, offset + count)
                        scope.launch(Dispatchers.IO) {
                            try {
                                adb.write(chunk)
                            } catch (_: Exception) {
                            }
                        }
                    }

                    override fun onSessionResize(session: TerminalSession, columns: Int, rows: Int) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                adb.resize(cols = columns, rows = rows)
                            } catch (_: Exception) {
                            }
                        }
                    }
                })

                _state.value = ShellSessionState.ACTIVE

                readJob = scope.launch(Dispatchers.IO) {
                    try {
                        adb.outputFlow.collect { bytes ->
                            term.append(bytes)
                        }
                        withContext(Dispatchers.Main) {
                            term.finish(0)
                            _state.value = ShellSessionState.CLOSED
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            _errorMessage.value = e.message ?: "Shell disconnected"
                            _state.value = ShellSessionState.ERROR
                            term.finish(1)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to open interactive shell"
                _state.value = ShellSessionState.ERROR
            }
        }
    }

    fun write(bytes: ByteArray) {
        _terminalSession.value?.write(bytes, 0, bytes.size)
    }

    fun write(text: String) {
        _terminalSession.value?.write(text)
    }

    fun reset() {
        _terminalSession.value?.reset()
    }

    fun close() {
        readJob?.cancel()
        readJob = null
        _terminalSession.value?.finishIfRunning()
        runCatching { adbSession?.close() }
        adbSession = null
        if (_state.value != ShellSessionState.ERROR) {
            _state.value = ShellSessionState.CLOSED
        }
    }

    fun destroy() {
        close()
        scope.cancel()
    }
}
