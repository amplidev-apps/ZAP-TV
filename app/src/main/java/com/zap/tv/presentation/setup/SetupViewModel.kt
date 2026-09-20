package com.zap.tv.presentation.setup

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zap.tv.data.AppDependencies
import com.zap.tv.data.local.dao.*
import com.zap.tv.util.ParentalUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SetupViewModel(application: Application) : AndroidViewModel(application) {
    private val repo by lazy { AppDependencies.getChannelRepository(getApplication()) }
    private val _deviceMode = MutableStateFlow("ANDROID_TV") // "ANDROID_TV" ou "TV_BOX"
    val deviceMode: StateFlow<String> = _deviceMode

    private val _uf = MutableStateFlow("NACIONAL")
    val uf: StateFlow<String> = _uf
    private val _pin = MutableStateFlow("")
    val pin: StateFlow<String> = _pin
    private val _confirmPin = MutableStateFlow("")
    val confirmPin: StateFlow<String> = _confirmPin
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _step = MutableStateFlow(0) // 0 device, 1 state, 2 user name, 3 pin
    val step: StateFlow<Int> = _step

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName

    fun selectDeviceMode(mode: String) { _deviceMode.value = mode; _error.value = null }
    fun selectUf(v: String) { _uf.value = v; _error.value = null }
    fun setPin(v: String) { if (v.length <= 4 && v.all { it.isDigit() }) { _pin.value = v; _error.value = null } }
    fun setConfirmPin(v: String) { if (v.length <= 4 && v.all { it.isDigit() }) { _confirmPin.value = v; _error.value = null } }
    fun setUserName(name: String) { _userName.value = name; _error.value = null }

    fun nextFromDeviceMode() {
        saveDeviceMode(_deviceMode.value)
        _step.value = 1
    }

    fun nextFromState() {
        if (_uf.value.isNotEmpty()) {
            _step.value = 2
        }
    }

    fun nextFromName() {
        val name = _userName.value.trim().ifEmpty { "Usuário" }
        saveUserName(name)
        _step.value = 3
    }

    fun prevStep() {
        if (_step.value > 0) {
            _step.value -= 1
            _error.value = null
        }
    }

    private val _prefs: SharedPreferences by lazy { 
        getApplication<Application>().getSharedPreferences("zap_tv_prefs", android.content.Context.MODE_PRIVATE) 
    }

    fun saveDeviceMode(mode: String) {
        _prefs.edit().putString("device_mode", mode).apply()
        _deviceMode.value = mode
    }

    fun getDeviceMode(): String {
        return _prefs.getString("device_mode", "ANDROID_TV") ?: "ANDROID_TV"
    }

    fun saveUserName(name: String) {
        _prefs.edit().putString("user_name", name).apply()
        _userName.value = name
    }

    fun getUserName(): String {
        return _prefs.getString("user_name", "") ?: ""
    }

    data class VerifyProgress(val currentName: String, val checked: Int, val total: Int, val alive: Int)

    private val _verifying = MutableStateFlow(false)
    val verifying: StateFlow<Boolean> = _verifying
    private val _verifyProgress = MutableStateFlow<VerifyProgress?>(null)
    val verifyProgress: StateFlow<VerifyProgress?> = _verifyProgress

    fun skipPin(onDone: () -> Unit) {
        viewModelScope.launch {
            _loading.value = true
            _verifying.value = true
            _verifyProgress.value = VerifyProgress("Iniciando sintonia...", 0, 1, 0)
            try {
                repo.completeSetupWithVerification(_uf.value, null) { name, checked, total, alive ->
                    _verifyProgress.value = VerifyProgress(name, checked, total, alive)
                }
                onDone()
            } catch (e: Exception) {
                _error.value = "Falha ao verificar canais: ${e.message}"
            } finally {
                _loading.value = false
                _verifying.value = false
            }
        }
    }

    fun confirm(onDone: () -> Unit) {
        val p = _pin.value
        val c = _confirmPin.value
        if (p.length != 4) { _error.value = "PIN deve ter 4 dígitos"; return }
        if (p != c) { _error.value = "PINs não conferem"; return }
        if (ParentalUtils.isWeak(p)) { _error.value = "PIN muito simples (evite 0000, 1234)"; return }

        viewModelScope.launch {
            _loading.value = true
            _verifying.value = true
            _verifyProgress.value = VerifyProgress("Iniciando sintonia...", 0, 1, 0)
            try {
                repo.completeSetupWithVerification(_uf.value, p) { name, checked, total, alive ->
                    _verifyProgress.value = VerifyProgress(name, checked, total, alive)
                }
                onDone()
            } catch (e: Exception) {
                _error.value = "Falha ao verificar canais: ${e.message}"
            } finally {
                _loading.value = false
                _verifying.value = false
            }
        }
    }
}
