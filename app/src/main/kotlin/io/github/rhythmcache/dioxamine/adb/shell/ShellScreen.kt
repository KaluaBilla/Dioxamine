package io.github.rhythmcache.dioxamine.adb.shell

import android.content.Context
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import io.github.rhythmcache.dioxamine.R
import io.github.rhythmcache.dioxamine.adb.AdbViewModel

/**
 * Main ADB Shell screen composable powered by Termux's pure-Java terminal emulator and view.
 *
 * Wires [ShellViewModel] to the currently-active device from [AdbViewModel].
 * Automatically starts a new interactive shell session when a device connects and
 * tears it down on disconnect or device change.
 */
@Composable
fun ShellScreen(adbViewModel: AdbViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var showInfoDialog by rememberSaveable {
        mutableStateOf(!prefs.getBoolean("adb_shell_dont_show_info", false))
    }

    if (showInfoDialog) {
        ShellInfoDialog(
            onDismiss = { showInfoDialog = false },
            onConfirm = { dontShowAgain ->
                if (dontShowAgain) {
                    prefs.edit().putBoolean("adb_shell_dont_show_info", true).apply()
                }
                showInfoDialog = false
            },
        )
    }

    val shellVm: ShellViewModel = viewModel()

    val activeClient = adbViewModel.activeClient()
    val activeDeviceId = adbViewModel.activeDeviceId

    val sessionState by shellVm.sessionState.collectAsState()
    val terminalSession by shellVm.terminalSession.collectAsState()
    val errorMessage by shellVm.errorMessage.collectAsState()

    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }
    var terminalViewRef by remember { mutableStateOf<TerminalView?>(null) }

    val typeface = remember {
        try {
            Typeface.createFromAsset(context.assets, "fonts/JetBrainsMono-Regular.ttf")
        } catch (_: Exception) {
            Typeface.MONOSPACE
        }
    }

    val viewClient = remember {
        object : TerminalViewClient {
            override fun readControlKey(): Boolean = ctrlActive
            override fun readAltKey(): Boolean = altActive

            override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
                if (ctrlActive) ctrlActive = false
                if (altActive) altActive = false
                return false
            }

            override fun onSingleTapUp(e: MotionEvent) {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                terminalViewRef?.let { tv ->
                    tv.requestFocus()
                    imm?.showSoftInput(tv, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
    }

    // Start / restart shell when the active device changes
    LaunchedEffect(activeDeviceId) {
        if (activeClient != null && !activeClient.isClosed) {
            val needsRestart = shellVm.currentDeviceId != activeDeviceId ||
                sessionState == ShellSessionState.CLOSED ||
                sessionState == ShellSessionState.ERROR
            if (needsRestart) {
                shellVm.startSession(activeDeviceId, activeClient)
            }
        } else {
            shellVm.stopSession()
        }
    }

    if (activeClient == null) {
        NoDeviceState()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // -- Terminal View (fills available space) -----------------
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    TerminalView(ctx).apply {
                        terminalViewRef = this
                        val fontPx = (13 * ctx.resources.displayMetrics.scaledDensity).toInt()
                        setTextSize(fontPx)
                        setTypeface(typeface)
                        setTerminalViewClient(viewClient)
                        terminalSession?.let { attachSession(it) }
                        isFocusable = true
                        isFocusableInTouchMode = true
                        requestFocus()
                    }
                },
                update = { view ->
                    terminalViewRef = view
                    view.setTerminalViewClient(viewClient)
                    if (terminalSession != null && view.currentSession != terminalSession) {
                        view.attachSession(terminalSession)
                    }
                }
            )
        }

        // -- Error banner ------------------------------------------
        if (sessionState == ShellSessionState.ERROR && errorMessage != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.shell_error_message, errorMessage ?: ""),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        // -- Toolbar (Esc, Tab, Ctrl, Alt, Arrows, Keyboard, Clear, Restart)
        ShellToolbar(
            sessionState     = sessionState,
            ctrlActive       = ctrlActive,
            altActive        = altActive,
            onToggleCtrl     = { ctrlActive = !ctrlActive },
            onToggleAlt      = { altActive = !altActive },
            onEsc            = { shellVm.sendEscape() },
            onTab            = { shellVm.sendTab() },
            onArrowUp        = { shellVm.sendArrowUp() },
            onArrowDown      = { shellVm.sendArrowDown() },
            onArrowLeft      = { shellVm.sendArrowLeft() },
            onArrowRight     = { shellVm.sendArrowRight() },
            onToggleKeyboard = {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                terminalViewRef?.let { tv ->
                    tv.requestFocus()
                    imm?.showSoftInput(tv, InputMethodManager.SHOW_IMPLICIT)
                }
            },
            onClear          = { shellVm.clearTerminal() },
            onRestart        = {
                val client = adbViewModel.activeClient()
                if (client != null && !client.isClosed) {
                    shellVm.startSession(activeDeviceId, client)
                }
            },
        )
    }
}

// -- Empty state when no device is connected -------------------------

@Composable
private fun NoDeviceState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Terminal,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "No Device Connected",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Connect a device to start a shell session",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
