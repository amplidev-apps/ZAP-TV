package com.zap.tv.presentation.player

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zap.tv.BuildConfig
import com.zap.tv.data.AppDependencies
import com.zap.tv.data.local.entity.ChannelNumberEntity
import com.zap.tv.data.reidoscanais.ChannelEpg
import com.zap.tv.presentation.overlay.AspectRatioMode
import com.zap.tv.util.AffiliateRegistry
import com.zap.tv.util.ChannelNumbering
import com.zap.tv.util.PlaybackAttempt
import com.zap.tv.util.PlaybackMechanism
import com.zap.tv.util.PlaybackPlan
import com.zap.tv.util.PlaybackResolver
import com.zap.tv.util.PlaybackState
import com.zap.tv.util.SourcePreferenceRegistry
import com.zap.tv.util.SourceRegionKind
import com.zap.tv.util.VideoFallbackDecision
import com.zap.tv.util.VideoFallbackPolicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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
    val allStreams: List<com.zap.tv.data.api.ApiStreamDto> = emptyList(),
    val allChannels: List<ChannelNumberEntity> = emptyList(),
    val favorites: Set<Int> = emptySet(),
    val aspectRatio: AspectRatioMode = AspectRatioMode.FILL,
    val isReconnecting: Boolean = false,
    // Canal sintonizado sem nenhuma fonte reproduzível disponível.
    val noSignal: Boolean = false,
    // Estado técnico de reprodução (Etapa 10 §07): permite distinguir àudio-só,
    // conexão, buffering e reconexão de uma indisponibilidade real.
    val playbackState: PlaybackState = PlaybackState.IDLE,
    // Player híbrido (Etapa 09): mecanismo ativo.
    val mechanism: PlaybackMechanism = PlaybackMechanism.UNAVAILABLE,
    val webUrl: String? = null,
    // Regionalização real (Etapa 11): plano ordenado de tentativas e FONTE em reprodução.
    val attempts: List<PlaybackAttempt> = emptyList(),
    val attemptIndex: Int = 0,
    val identityLabel: String? = null,
    val sourceRegion: String? = null,
    val sourceLabel: String? = null,
    val sourceKind: SourceRegionKind? = null,
    val sourceConfidence: AffiliateRegistry.Confidence? = null,
    // Fonte A (Rei dos Canais): programação atual/próxima; null = sem EPG disponível
    val epg: ChannelEpg? = null
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val channelRepo by lazy { AppDependencies.getChannelRepository(getApplication()) }
    private val favRepo by lazy { AppDependencies.getFavoriteRepository(getApplication()) }
    private val parentalRepo by lazy { AppDependencies.getParentalRepository(getApplication()) }
    private val rdcRepo by lazy { AppDependencies.getReiDosCanaisRepository() }
    private val _ui = MutableStateFlow(PlayerUiState())
    val ui: StateFlow<PlayerUiState> = _ui
    private var digitJob: Job? = null
    private var bannerJob: Job? = null
    private var previousChannelNumber: Int? = null
    // Geração de sintonia: descarta carregamentos antigos que tentarem sobrescrever
    // o estado do canal atual (Etapa 07 §06.8).
    private var tuneGeneration: Int = 0
    // Etapa 13 §07/§11: instante do início da sintonia e marca para registrar o
    // tempo até o primeiro quadro apenas uma vez por canal (apenas em DEBUG).
    private var tuneStartedAtMs: Long = 0L
    private var firstFrameLogged: Boolean = false

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
                    noSignal = false,
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
        val generation = ++tuneGeneration
        tuneStartedAtMs = android.os.SystemClock.elapsedRealtime()
        firstFrameLogged = false
        _ui.value = _ui.value.copy(
            current = ch,
            isLoading = true,
            error = null,
            noSignal = false,
            showInfoBanner = true,
            showParental = false,
            showCategoriesDrawer = false,
            showQuickOptions = false,
            showProgramGuide = false,
            showTvBoxHub = false,
            allStreams = emptyList(),
            streamUrl = null,
            webUrl = null,
            mechanism = PlaybackMechanism.UNAVAILABLE,
            playbackState = PlaybackState.IDLE,
            attempts = emptyList(),
            attemptIndex = 0,
            identityLabel = ch.channelName,
            sourceRegion = null,
            sourceLabel = null,
            sourceKind = null,
            sourceConfidence = null,
            epg = null
        )
        if (saveLast) channelRepo.saveLastChannel(ch.number)
        // Fonte A (Rei dos Canais): EPG opcional — falha/ausência nunca afeta a sintonia.
        viewModelScope.launch {
            val epg = try {
                rdcRepo.getEpgForChannel(ch.channelId, ch.channelName)
            } catch (_: Exception) {
                null
            }
            // Aplica apenas se o usuário ainda estiver no mesmo canal/geração.
            if (generation == tuneGeneration && _ui.value.current?.number == ch.number) {
                _ui.value = _ui.value.copy(epg = epg)
            }
        }
        // Etapa 14 §03: resolve streams e embeds EM PARALELO (antes eram sequenciais,
        // somando o tempo de rede das duas fontes antes de qualquer tentativa de vídeo).
        val (streams, embeds) = kotlinx.coroutines.coroutineScope {
            val streamsDeferred = async { channelRepo.getStreamsForChannel(ch.channelId) }
            val embedsDeferred = async {
                try {
                    rdcRepo.getEmbedsForChannel(ch.channelId, ch.channelName)
                } catch (_: Exception) {
                    emptyList()
                }
            }
            streamsDeferred.await() to embedsDeferred.await()
        }
        // Descarta resultado obsoleto de uma sintonia anterior.
        if (generation != tuneGeneration) return
        val uf = channelRepo.getSelectedUf()
        val fixedNetwork = if (ch.number in 1..9) ChannelNumbering.networkForFixedPosition(ch.number) else null
        val plan = PlaybackResolver.resolveForChannel(
            fixedNetwork = fixedNetwork,
            uf = uf,
            entityChannelId = ch.channelId,
            identityLabel = ch.channelName,
            directStreams = streams,
            embeds = embeds
        )
        val fav = favRepo.isFavorite(ch.number)
        val startState = _ui.value.copy(isFavorite = fav)
        _ui.value = stateForAttempt(startState, plan.attempts, 0)
        if (plan.attempts.isNotEmpty()) autoHideBanner()
        logPlan(uf, fixedNetwork, plan)
        logTuningNote(ch)
    }

    /**
     * Projeta o estado do canal para uma tentativa específica do plano (Etapa 11 §04).
     * Atualiza SEMPRE a indicação da FONTE REAL, para nunca exibir a região errada
     * como se fosse a afiliada da UF.
     */
    private fun stateForAttempt(
        st: PlayerUiState,
        attempts: List<PlaybackAttempt>,
        index: Int
    ): PlayerUiState {
        val base = st.copy(
            attempts = attempts,
            attemptIndex = index,
            error = null,
            noSignal = false,
            isReconnecting = false,
            allStreams = emptyList(),
            streamUrl = null,
            webUrl = null
        )
        val a = attempts.getOrNull(index)
            ?: return base.copy(
                mechanism = PlaybackMechanism.UNAVAILABLE,
                isLoading = false,
                noSignal = true,
                playbackState = PlaybackState.UNAVAILABLE
            )
        val src = a.source
        val regionalized = src.regionKind != SourceRegionKind.UNREGIONALIZED
        val withSource = base.copy(
            identityLabel = src.identityLabel,
            sourceRegion = src.region,
            // Só exibe a FONTE quando há regionalização (evita ruído em canais comuns).
            sourceLabel = if (regionalized) src.label else null,
            sourceKind = src.regionKind,
            sourceConfidence = src.confidence
        )
        return when (a.mechanism) {
            PlaybackMechanism.DIRECT_STREAM -> withSource.copy(
                mechanism = PlaybackMechanism.DIRECT_STREAM,
                allStreams = a.streams,
                streamUrl = a.streams.firstOrNull()?.url,
                webUrl = null,
                isLoading = false,
                playbackState = PlaybackState.CONNECTING
            )
            PlaybackMechanism.WEB_EMBED -> withSource.copy(
                mechanism = PlaybackMechanism.WEB_EMBED,
                allStreams = emptyList(),
                streamUrl = null,
                webUrl = a.embed?.embed_url,
                isLoading = true,
                playbackState = PlaybackState.UNKNOWN_PLAYBACK_STATE
            )
            PlaybackMechanism.UNAVAILABLE -> withSource.copy(
                mechanism = PlaybackMechanism.UNAVAILABLE,
                isLoading = false,
                noSignal = true,
                playbackState = PlaybackState.UNAVAILABLE
            )
        }
    }

    /**
     * Passa para a próxima fonte do plano. Só é chamado após o player confirmar a
     * falha (após suas repetições transitórias internas) — nunca em carregamento
     * inicial (Etapa 11 §05). Esgotado o plano, declara indisponibilidade real.
     */
    private fun advanceAttempt(reason: String) {
        val st = _ui.value
        if (st.mechanism == PlaybackMechanism.UNAVAILABLE) return
        val next = st.attemptIndex + 1
        if (next >= st.attempts.size) {
            logFallback(st, reason, exhausted = true)
            _ui.value = st.copy(
                mechanism = PlaybackMechanism.UNAVAILABLE,
                isLoading = false,
                noSignal = true,
                isReconnecting = false,
                allStreams = emptyList(),
                streamUrl = null,
                webUrl = null,
                playbackState = PlaybackState.UNAVAILABLE,
                error = null
            )
            return
        }
        logFallback(st, reason, exhausted = false)
        _ui.value = stateForAttempt(st, st.attempts, next)
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
        // Enquanto há tentativa legítima (reconexão), "Sem sinal" não se aplica.
        _ui.value = _ui.value.copy(isReconnecting = v, noSignal = if (v) false else _ui.value.noSignal)
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

    /** Falha confirmada de uma fonte direta (ExoPlayer): avança para a próxima fonte. */
    fun nextStreamOnError() = advanceAttempt("exo_error")

    /**
     * Etapa 15 §03/§04: falha de VÍDEO comprovada na fonte direta (trilha declarada,
     * nenhum quadro renderizado). Áudio sem imagem não é reprodução audiovisual validada.
     * Tenta a próxima fonte autorizada; esgotado o plano, o canal PERMANECE acessível no
     * estado "sem imagem" (nunca é removido nem vira "sem sinal").
     */
    fun onVideoUnavailable() {
        val st = _ui.value
        if (st.mechanism != PlaybackMechanism.DIRECT_STREAM) return
        when (val d = VideoFallbackPolicy.decide(st.attemptIndex, st.attempts)) {
            is VideoFallbackDecision.Next -> {
                logFallback(st, "video_sem_quadro", exhausted = false)
                _ui.value = stateForAttempt(st, st.attempts, d.index)
            }
            VideoFallbackDecision.KeepAudioOnly -> {
                logFallback(st, "video_sem_quadro", exhausted = true)
                _ui.value = st.copy(
                    mechanism = PlaybackMechanism.DIRECT_STREAM,
                    isLoading = false,
                    isReconnecting = false,
                    noSignal = false,
                    playbackState = PlaybackState.AUDIO_ONLY
                )
            }
            VideoFallbackDecision.None -> Unit
        }
    }

    /** Atualiza o estado técnico de reprodução reportado pelo ExoPlayerManager (Etapa 10 §07). */
    fun onPlaybackState(state: PlaybackState) {
        if (_ui.value.mechanism != PlaybackMechanism.DIRECT_STREAM) return
        if (state == PlaybackState.PLAYING) {
            _ui.value = _ui.value.copy(playbackState = state, isLoading = false, isReconnecting = false, noSignal = false)
            logFirstFrame("exo")
        } else if (state == PlaybackState.AUDIO_ONLY) {
            // Etapa 15 §03: áudio sem imagem NÃO confirma reprodução audiovisual.
            _ui.value = _ui.value.copy(playbackState = state, isLoading = false, isReconnecting = false, noSignal = false)
            logNotConfirmed("exo")
        } else {
            _ui.value = _ui.value.copy(playbackState = state)
        }
    }

    /** Confirmação de carregamento do player web (timeout/erro tratados na WebView). */
    fun setWebLoading(loading: Boolean) {
        if (_ui.value.mechanism != PlaybackMechanism.WEB_EMBED) return
        _ui.value = _ui.value.copy(isLoading = loading)
        // Etapa 15 §02: documento carregado NÃO é confirmação audiovisual. A WebView não
        // expõe evento confiável de primeiro quadro sem contornar restrições — registramos
        // apenas como "reprodução não verificável", nunca como confirmada.
        if (!loading) logNotConfirmed("web")
    }

    /** Falha confirmada de um embed web: avança para a próxima fonte, sem tentativas infinitas. */
    fun onWebError() = advanceAttempt("web_error")

    fun dismissError() {
        _ui.value = _ui.value.copy(error = null)
    }

    // ------------------------------------------------------------------
    // Diagnóstico (Etapa 11 §08) — apenas em DEBUG, sem URLs/credenciais.
    // ------------------------------------------------------------------
    private fun logPlan(uf: String, network: String?, plan: PlaybackPlan) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            "RegionalDiag",
            "uf=$uf rede=${network ?: "-"} identidade=${_ui.value.identityLabel ?: "-"} tentativas=${plan.attempts.size}"
        )
        plan.attempts.forEachIndexed { i, a ->
            val host = a.embed?.embed_url?.let { hostOf(it) }
                ?: a.streams.firstOrNull()?.url?.let { hostOf(it) } ?: "-"
            Log.d(
                "RegionalDiag",
                "  [$i] ${a.mechanism} tipo=${a.source.regionKind} regiao=${a.source.region ?: "-"} " +
                    "rotulo=\"${a.source.label}\" conf=${a.source.confidence} host=$host"
            )
        }
    }

    /**
     * Registra a observação de lentidão/travamento do usuário (Etapa 13 §07), quando
     * existir, no início da sintonia — apenas em DEBUG e SEM URLs/credenciais.
     */
    private fun logTuningNote(ch: ChannelNumberEntity) {
        if (!BuildConfig.DEBUG) return
        SourcePreferenceRegistry.noteFor(ch.channelId, ch.channelName)?.let {
            Log.d("TuningDiag", "canal=${ch.number} identidade=\"${ch.channelName}\" observacao=\"$it\"")
        }
    }

    /** Tempo até o primeiro quadro (Etapa 13 §11) — uma vez por sintonia, só em DEBUG. */
    private fun logFirstFrame(mechanism: String) {
        if (!BuildConfig.DEBUG || firstFrameLogged) return
        firstFrameLogged = true
        val ms = android.os.SystemClock.elapsedRealtime() - tuneStartedAtMs
        val num = _ui.value.current?.number
        Log.d("TuningDiag", "ttf=${ms}ms canal=$num mecanismo=$mechanism estado=${_ui.value.playbackState}")
        Log.d("AvDiag", "reproducao confirmada mecanismo=$mechanism primeiro_quadro=sim")
    }

    /**
     * Etapa 15 §06: registra explicitamente que a reprodução NÃO foi confirmada
     * audiovisualmente (áudio sem quadro ou WebView carregada sem evento de vídeo).
     * Nunca declaramos sucesso sem um evento real de renderização.
     */
    private fun logNotConfirmed(mechanism: String) {
        if (!BuildConfig.DEBUG) return
        val num = _ui.value.current?.number
        Log.d("AvDiag", "reproducao NAO verificavel canal=$num mecanismo=$mechanism estado=${_ui.value.playbackState}")
    }

    private fun logFallback(st: PlayerUiState, reason: String, exhausted: Boolean) {
        if (!BuildConfig.DEBUG) return
        val next = st.attempts.getOrNull(st.attemptIndex + 1)
        Log.d(
            "RegionalDiag",
            "fallback motivo=$reason de=\"${st.sourceLabel}\" (${st.sourceKind}/${st.sourceRegion}) -> " +
                (if (exhausted) "INDISPONIVEL" else "proxima=\"${next?.source?.label}\"")
        )
        Log.d("AvDiag", "fallback iniciado motivo=$reason")
        if (!exhausted && next != null) {
            Log.d("AvDiag", "fonte alternativa selecionada mecanismo=${next.mechanism} regiao=${next.source.region ?: "-"}")
        }
    }

    private fun hostOf(url: String): String =
        try { java.net.URI(url).host ?: "-" } catch (_: Exception) { "-" }
}
