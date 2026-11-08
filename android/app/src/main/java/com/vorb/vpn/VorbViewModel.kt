package com.vorb.vpn

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vorb.vpn.data.ConfigStore
import com.vorb.vpn.data.ConfigSummary
import com.vorb.vpn.ui.theme.ThemeMode
import com.vorb.vpn.vpn.WarpRegistrar
import com.vorb.vpn.vpn.WireGuardManager
import com.wireguard.config.Config
import java.io.BufferedReader
import java.io.StringReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS = "vorb_settings"
private const val KEY_THEME = "theme_mode"
private const val KEY_DYNAMIC = "dynamic_color"

data class VorbUiState(
    val config: String? = null,
    val summary: ConfigSummary = ConfigSummary(),
    val connected: Boolean = false,
    val busy: Boolean = false,
    val generating: Boolean = false,
    val error: String? = null,
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
)

class VorbViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ConfigStore(application)
    private val vpn = WireGuardManager(application)
    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(VorbUiState())
    val state: StateFlow<VorbUiState> = _state.asStateFlow()

    private var statsJob: Job? = null

    init {
        val config = store.load()
        val savedTheme = prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        val themeMode = runCatching { ThemeMode.valueOf(savedTheme) }.getOrDefault(ThemeMode.SYSTEM)

        _state.value = VorbUiState(
            config = config,
            summary = config?.let(store::summarize) ?: ConfigSummary(),
            themeMode = themeMode,
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC, true),
        )

        // GoBackend keeps the tunnel alive across Activity recreation.
        viewModelScope.launch {
            val connected = withContext(Dispatchers.IO) { vpn.isConnected() }
            if (connected) {
                _state.update { it.copy(connected = true) }
                startStats()
            }
        }
    }

    fun importConfig(text: String): Boolean {
        if (_state.value.connected) {
            showError("Сначала отключите VPN, затем импортируйте новый конфиг.")
            return false
        }

        val cleaned = text.trim()
        if (cleaned.isEmpty()) {
            showError("Вставьте текст WireGuard-конфига.")
            return false
        }

        val validationError = validateConfig(cleaned)
        if (validationError != null) {
            showError(validationError)
            return false
        }

        return runCatching {
            store.save(cleaned)
            _state.update {
                it.copy(
                    config = cleaned,
                    summary = store.summarize(cleaned),
                    error = null,
                )
            }
            true
        }.getOrElse {
            showError("Не удалось сохранить конфиг: ${it.message ?: "ошибка файла"}.")
            false
        }
    }

    fun connect() {
        val current = _state.value
        if (current.busy) return

        val needsConfig = current.config.isNullOrBlank()
        _state.update { it.copy(busy = true, generating = needsConfig, error = null) }

        viewModelScope.launch {
            var config = _state.value.config

            if (config.isNullOrBlank()) {
                val generated = WarpRegistrar.generate().getOrElse { error ->
                    _state.update {
                        it.copy(
                            busy = false,
                            generating = false,
                            error = "Не удалось создать конфиг WARP: ${error.message ?: "неизвестная ошибка"}.",
                        )
                    }
                    return@launch
                }

                config = try {
                    store.save(generated)
                    generated
                } catch (error: Exception) {
                    _state.update {
                        it.copy(
                            busy = false,
                            generating = false,
                            error = "Не удалось сохранить конфиг: ${error.message ?: "ошибка файла"}.",
                        )
                    }
                    return@launch
                }

                _state.update {
                    it.copy(
                        config = generated,
                        summary = store.summarize(generated),
                        generating = false,
                    )
                }
            }

            val activeConfig = config ?: return@launch
            val result = vpn.connect(activeConfig)
            result.fold(
                onSuccess = {
                    _state.update {
                        it.copy(connected = true, busy = false, generating = false, error = null)
                    }
                    startStats()
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            connected = false,
                            busy = false,
                            generating = false,
                            error = error.userMessage(),
                        )
                    }
                },
            )
        }
    }

    /** Creates a fresh WARP identity, replacing the current config. */
    fun regenerateConfig() {
        val current = _state.value
        if (current.connected) {
            showError("Сначала отключите VPN, затем создайте новый конфиг.")
            return
        }
        if (current.busy) return

        _state.update { it.copy(busy = true, generating = true, error = null) }
        viewModelScope.launch {
            WarpRegistrar.generate().fold(
                onSuccess = { generated ->
                    try {
                        store.save(generated)
                        _state.update {
                            it.copy(
                                config = generated,
                                summary = store.summarize(generated),
                                busy = false,
                                generating = false,
                                error = null,
                            )
                        }
                    } catch (error: Exception) {
                        _state.update {
                            it.copy(
                                busy = false,
                                generating = false,
                                error = "Не удалось сохранить конфиг: ${error.message ?: "ошибка файла"}.",
                            )
                        }
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            busy = false,
                            generating = false,
                            error = "Не удалось создать конфиг WARP: ${error.message ?: "неизвестная ошибка"}.",
                        )
                    }
                },
            )
        }
    }

    fun disconnect() {
        if (_state.value.busy) return

        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = vpn.disconnect()
            result.fold(
                onSuccess = {
                    statsJob?.cancel()
                    statsJob = null
                    _state.update {
                        it.copy(
                            connected = false,
                            busy = false,
                            rxBytes = 0L,
                            txBytes = 0L,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(busy = false, error = error.userMessage())
                    }
                },
            )
        }
    }

    fun deleteConfig() {
        if (_state.value.connected) {
            showError("Сначала отключите VPN.")
            return
        }
        store.clear()
        _state.update {
            it.copy(
                config = null,
                summary = ConfigSummary(),
                rxBytes = 0L,
                txBytes = 0L,
                error = null,
            )
        }
    }

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _state.update { it.copy(themeMode = mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC, enabled).apply()
        _state.update { it.copy(dynamicColor = enabled) }
    }

    fun showError(message: String) {
        _state.update { it.copy(error = message) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun startStats() {
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            while (isActive) {
                val stats = vpn.statistics()
                if (stats != null) {
                    _state.update { it.copy(rxBytes = stats.first, txBytes = stats.second) }
                } else {
                    val stillConnected = withContext(Dispatchers.IO) { vpn.isConnected() }
                    if (!stillConnected) {
                        _state.update {
                            it.copy(connected = false, rxBytes = 0L, txBytes = 0L)
                        }
                        break
                    }
                }
                delay(1_000L)
            }
        }
    }

    private fun validateConfig(text: String): String? {
        if (!text.contains("[Interface]", ignoreCase = true)) {
            return "В конфиге нет секции [Interface]."
        }
        if (!text.contains("[Peer]", ignoreCase = true)) {
            return "В конфиге нет секции [Peer]."
        }
        return try {
            Config.parse(BufferedReader(StringReader(text)))
            null
        } catch (error: Exception) {
            "Проверьте формат конфига: ${error.message ?: "неизвестная ошибка"}."
        }
    }

    private fun Throwable.userMessage(): String {
        val raw = message?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            this is com.wireguard.config.BadConfigException -> "Некорректный конфиг WireGuard."
            raw != null -> raw
            else -> "Не удалось выполнить операцию WireGuard."
        }
    }
}
