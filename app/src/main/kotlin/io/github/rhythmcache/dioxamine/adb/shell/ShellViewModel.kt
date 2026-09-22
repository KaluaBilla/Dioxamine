package io.github.rhythmcache.dioxamine.adb.shell

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.termux.terminal.TerminalSession
import io.github.rhythmcache.adb.AdbClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the ADB interactive terminal shell.
 *
 * Manages the [ShellSession] lifecycle and exposes the active [TerminalSession]
 * to the Compose UI layer.
 */
class ShellViewModel(application: Application) : AndroidViewModel(application) {

    private var session: ShellSession? = null

    private val _terminalSession = MutableStateFlow<TerminalSession?>(null)
    val terminalSession: StateFlow<TerminalSession?> = _terminalSession

    private val _sessionState = MutableStateFlow(ShellSessionState.IDLE)
    val sessionState: StateFlow<ShellSessionState> = _sessionState

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _title = MutableStateFlow<String?>(null)
    val title: StateFlow<String?> = _title

    var currentDeviceId: String? = null
        private set

    /**
     * Start (or restart) an interactive shell on [client].
     * Closes any existing session first.
     */
    fun startSession(deviceId: String?, client: AdbClient) {
        stopSession()
        currentDeviceId = deviceId

        val newSession = ShellSession(getApplication())
        session = newSession

        viewModelScope.launch {
            newSession.terminalSession.collect { _terminalSession.value = it }
        }
        viewModelScope.launch {
            newSession.state.collect { _sessionState.value = it }
        }
        viewModelScope.launch {
            newSession.errorMessage.collect { _errorMessage.value = it }
        }
        viewModelScope.launch {
            newSession.title.collect { _title.value = it }
        }

        newSession.start(client)
    }

    /** Close the current session (if any). */
    fun stopSession() {
        session?.close()
        session = null
        _terminalSession.value = null
    }

    // -- Terminal Input Helpers --------------------------------------

    fun sendRaw(bytes: ByteArray) {
        session?.write(bytes)
    }

    fun sendText(text: String) {
        session?.write(text)
    }

    fun sendCommand(command: String) {
        session?.write("$command\r")
    }

    fun sendInterrupt() = sendRaw(byteArrayOf(0x03)) // Ctrl+C
    fun sendEof()       = sendRaw(byteArrayOf(0x04)) // Ctrl+D
    fun sendSuspend()   = sendRaw(byteArrayOf(0x1A)) // Ctrl+Z
    fun sendTab()       = sendRaw(byteArrayOf(0x09)) // Tab
    fun sendEscape()    = sendRaw(byteArrayOf(0x1B)) // Esc

    fun sendArrowUp()    = sendRaw("\u001b[A".toByteArray())
    fun sendArrowDown()  = sendRaw("\u001b[B".toByteArray())
    fun sendArrowRight() = sendRaw("\u001b[C".toByteArray())
    fun sendArrowLeft()  = sendRaw("\u001b[D".toByteArray())

    fun clearTerminal() {
        session?.reset()
    }

    override fun onCleared() {
        session?.destroy()
        super.onCleared()
    }
}
