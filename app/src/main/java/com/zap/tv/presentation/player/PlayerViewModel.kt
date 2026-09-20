package com.zap.tv.presentation.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zap.tv.data.AppDependencies
import com.zap.tv.data.local.entity.ChannelNumberEntity
import com.zap.tv.presentation.overlay.AspectRatioMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PlayerUiState(
    val current: ChannelNumberEntity? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val inputDigits: String = "",
    val showInfoBanner: Boolean = false,
    val showCategoriesDrawer: Boolean = false,
    val showQuickOptions: Boolean = false,
    val showProgramGuide: Boolean = false,
    val showParental: Boolean = false,
    val showTvBoxHub: Boolean = false,
    val deviceMode: String = "ANDROID_TV",
    val parentalError: String? = null,
    val isFavorite: Boolean = false,
    val streamUrl: String? = null,
    val streamIndex: Int = 0,
    val allStreams: List<com.zap.tv.data.api.ApiStreamDto> = emptyList(),
    val allChannels: List<ChannelNumberEntity> = emptyList(),
    val favorites: Set<Int> = emptySet(),
    val aspectRatio: AspectRatioMode = AspectRatioMode.FILL,
    val isReconnecting: Boolean = false
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val channelRepo by lazy { AppDependencies.getChannelRepository(getApplication()) }
    private val favRepo by lazy { AppDependencies.getFavoriteRepository(getApplication()) }
    private val parentalRepo by lazy { AppDependencies.getParentalRepository(getApplication()) }
    private val _ui = MutableStateFlow(PlayerUiState())
    val ui: StateFlow<PlayerUiState> = _ui
    private var digitJob: Job? = null
    private var bannerJob: Job? = null
    private var previousChannelNumber: Int? = null

    init {
        val prefs = application.getSharedPreferences("zap_tv_prefs", android.content.Context.MODE_PRIVATE)
        val mode = prefs.getString("device_mode", "ANDROID_TV") ?: "ANDROID_TV"
        _ui.value = _ui.value.copy(deviceMode = mode)

        viewModelScope.launch {
            loadChannelsAndFavorites()
            loadInitial()
        }
    }

    private suspend fun loadChannelsAndFavorites() {
        val channels = channelRepo.getNumberedChannels()
        val favs = favRepo.getAll().map { it.channelNumber }.toSet()
        _ui.value = _ui.value.copy(allChannels = channels, favorites = favs)
    }

    private suspend fun loadInitial() {
        val last = channelRepo.getLastChannelNumber()
        val ch = last?.let { channelRepo.getChannelByNumber(it) }
            ?: channelRepo.getChannelByNumber(1)
            ?: channelRepo.getNumberedChannels().firstOrNull()

        if (ch == null) {
            _ui.value = _ui.value.copy(isLoading = false, error = "Nenhum canal. Verifique internet.")
            return
        }
        tune(ch.number, saveLast = false)
    }

    fun tune(number: Int, saveLast: Boolean = true) {
        viewModelScope.launch {
            val ch = channelRepo.getChannelByNumber(number) ?: run {
                _ui.value = _ui.value.copy(error = "Canal $number não existe")
                delay(1500)
                _ui.value = _ui.value.copy(error = null)
                return@launch
            }

            // Guarda canal anterior para a função Recall
            val curNum = _ui.value.current?.number
            if (curNum != null && curNum != ch.number) {
                previousChannelNumber = curNum
            }

            // Parental check
            if (ch.isNsfw && parentalRepo.hasPin()) {
                _ui.value = _ui.value.copy(
                    current = ch,
                    showParental = true,
                    parentalError = null,
                    isLoading = false,
                    showInfoBanner = false,
                    showCategoriesDrawer = false,
                    showQuickOptions = false,
                    showProgramGuide = false,
                    showTvBoxHub = false
                )
                return@launch
            }
            playChannel(ch, saveLast)
        }
    }

    private suspend fun playChannel(ch: ChannelNumberEntity, saveLast: Boolean) {
        _ui.value = _ui.value.copy(
            current = ch,
            isLoading = true,
            error = null,
            showInfoBanner = true,
            showParental = false,
            showCategoriesDrawer = false,
            showQuickOptions = false,
            showProgramGuide = false,
            showTvBoxHub = false
        )
        if (saveLast) channelRepo.saveLastChannel(ch.number)
        val streams = channelRepo.getStreamsForChannel(ch.channelId)
        val fav = favRepo.isFavorite(ch.number)
        if (streams.isEmpty()) {
            _ui.value = _ui.value.copy(
                isLoading = false,
                error = "Sem sinal para ${ch.channelName}",
                isFavorite = fav,
                allStreams = emptyList()
            )
            return
        }
        _ui.value = _ui.value.copy(
            allStreams = streams,
            streamIndex = 0,
            streamUrl = streams[0].url,
            isFavorite = fav,
            isLoading = false
        )
        autoHideBanner()
    }

    fun onParentalSubmit(pin: String) {
        viewModelScope.launch {
            val ok = parentalRepo.verify(pin)
            if (ok) {
                val ch = _ui.value.current ?: return@launch
                _ui.value = _ui.value.copy(showParental = false, parentalError = null)
                playChannel(ch, true)
            } else {
                if (parentalRepo.isCurrentlyLocked()) {
                    _ui.value = _ui.value.copy(parentalError = "Bloqueado 5 min. Tente depois.")
                } else {
                    _ui.value = _ui.value.copy(parentalError = "PIN incorreto")
                }
            }
        }
    }

    fun cancelParental() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(showParental = false, parentalError = null)
            val last = channelRepo.getLastChannelNumber()
            last?.let { tune(it, false) }
        }
    }

    fun channelUp() {
        val cur = _ui.value.current?.number ?: 1
        viewModelScope.launch {
            channelRepo.getNext(cur)?.let { tune(it.number) }
        }
    }

    fun channelDown() {
        val cur = _ui.value.current?.number ?: 1
        viewModelScope.launch {
            channelRepo.getPrev(cur)?.let { tune(it.number) }
        }
    }

    fun recallLastChannel() {
        previousChannelNumber?.let { lastNum ->
            tune(lastNum)
        }
    }

    fun onDigit(d: Int) {
        val cur = _ui.value.inputDigits
        if (cur.length >= 5) return
        val next = cur + d
        _ui.value = _ui.value.copy(inputDigits = next)
        digitJob?.cancel()
        digitJob = viewModelScope.launch {
            delay(900)
            val n = _ui.value.inputDigits.toIntOrNull()
            _ui.value = _ui.value.copy(inputDigits = "")
            if (n != null) tune(n)
        }
    }

    fun toggleInfoBanner() {
        val show = !_ui.value.showInfoBanner
        _ui.value = _ui.value.copy(showInfoBanner = show)
        if (show) autoHideBanner() else bannerJob?.cancel()
    }

    fun showBanner() {
        _ui.value = _ui.value.copy(showInfoBanner = true)
        autoHideBanner()
    }

    private fun autoHideBanner() {
        bannerJob?.cancel()
        bannerJob = viewModelScope.launch {
            delay(6000)
            _ui.value = _ui.value.copy(showInfoBanner = false)
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val ch = _ui.value.current ?: return@launch
            val isNow = favRepo.toggle(ch.number)
            val updatedFavs = if (isNow) _ui.value.favorites + ch.number else _ui.value.favorites - ch.number
            _ui.value = _ui.value.copy(isFavorite = isNow, favorites = updatedFavs, showInfoBanner = true)
            autoHideBanner()
        }
    }

    fun toggleCategoriesDrawer() {
        val show = !_ui.value.showCategoriesDrawer
        _ui.value = _ui.value.copy(
            showCategoriesDrawer = show,
            showQuickOptions = false,
            showProgramGuide = false,
            showInfoBanner = false,
            showTvBoxHub = false
        )
    }

    fun toggleQuickOptions() {
        val show = !_ui.value.showQuickOptions
        _ui.value = _ui.value.copy(
            showQuickOptions = show,
            showCategoriesDrawer = false,
            showProgramGuide = false,
            showInfoBanner = false,
            showTvBoxHub = false
        )
    }

    fun toggleProgramGuide() {
        val show = !_ui.value.showProgramGuide
        _ui.value = _ui.value.copy(
            showProgramGuide = show,
            showCategoriesDrawer = false,
            showQuickOptions = false,
            showInfoBanner = false,
            showTvBoxHub = false
        )
    }

    fun toggleTvBoxHub() {
        val show = !_ui.value.showTvBoxHub
        _ui.value = _ui.value.copy(
            showTvBoxHub = show,
            showCategoriesDrawer = false,
            showQuickOptions = false,
            showProgramGuide = false,
            showInfoBanner = false
        )
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        _ui.value = _ui.value.copy(aspectRatio = mode)
    }

    fun setReconnecting(v: Boolean) {
        _ui.value = _ui.value.copy(isReconnecting = v)
    }

    fun closeAllOverlays(): Boolean {
        val st = _ui.value
        return if (st.showParental) {
            cancelParental()
            true
        } else if (st.showTvBoxHub) {
            _ui.value = st.copy(showTvBoxHub = false)
            true
        } else if (st.showProgramGuide) {
            _ui.value = st.copy(showProgramGuide = false)
            true
        } else if (st.showCategoriesDrawer) {
            _ui.value = st.copy(showCategoriesDrawer = false)
            true
        } else if (st.showQuickOptions) {
            _ui.value = st.copy(showQuickOptions = false)
            true
        } else if (st.showInfoBanner) {
            _ui.value = st.copy(showInfoBanner = false)
            true
        } else {
            false
        }
    }

    fun nextStreamOnError() {
        val currentState = _ui.value
        val allStreams = currentState.allStreams
        val currentIndex = currentState.streamIndex
        
        if (allStreams.isNotEmpty() && currentIndex + 1 < allStreams.size) {
            val nextIndex = currentIndex + 1
            _ui.value = currentState.copy(
                streamIndex = nextIndex,
                streamUrl = allStreams[nextIndex].url,
                isLoading = true,
                error = null
            )
        } else {
            val chName = currentState.current?.channelName ?: "Canal"
            _ui.value = currentState.copy(error = "Sem sinal para $chName", isLoading = false)
        }
    }

    fun dismissError() {
        _ui.value = _ui.value.copy(error = null)
    }
}