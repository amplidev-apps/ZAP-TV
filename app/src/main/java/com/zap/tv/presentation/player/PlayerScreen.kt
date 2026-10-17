package com.zap.tv.presentation.player

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModelProvider
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.zap.tv.presentation.overlay.*
import com.zap.tv.ui.theme.*
import com.zap.tv.util.PlaybackMechanism
import com.zap.tv.util.PlaybackState
import com.zap.tv.util.RemoteAction
import com.zap.tv.util.RemoteKeyHandler

@Composable
fun PlayerScreen() {
    val context = LocalContext.current
    var vm by remember { mutableStateOf<PlayerViewModel?>(null) }
    var vmError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application)
            vm = ViewModelProvider(context as ComponentActivity, factory).get(PlayerViewModel::class.java)
        } catch (e: Exception) {
            vmError = "${e::class.java.simpleName}: ${e.message}\n${e.stackTrace.take(8).joinToString("\n")}"
        }
    }

    ZapTvTheme {
        if (vm == null) {
            Box(Modifier.fillMaxSize().background(ZapDarkBackground), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    if (vmError != null) {
                        Text(
                            text = "Erro Player ViewModel",
                            color = ZapLiveRed,
                            fontFamily = MontserratFontFamily,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = vmError ?: "",
                            color = ZapTextPrimary,
                            fontFamily = MontserratFontFamily,
                            fontSize = 11.sp
                        )
                    } else {
                        Text(
                            text = "ZAP-TV",
                            color = ZapGold,
                            fontFamily = MontserratFontFamily,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.height(14.dp))
                        CircularProgressIndicator(color = ZapGold, strokeWidth = 3.5.dp)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Inicializando receptor...",
                            color = ZapTextSecondary,
                            fontFamily = MontserratFontFamily,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            return@ZapTvTheme
        }

        val viewModel = vm!!
        val ui by viewModel.ui.collectAsState()
        var exoManager by remember { mutableStateOf<ExoPlayerManager?>(null) }
        var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

        DisposableEffect(Unit) {
            val m = ExoPlayerManager(context)
            exoManager = m
            onDispose { m.release() }
        }

        LaunchedEffect(exoManager) {
            exoManager?.onReconnecting = { viewModel.setReconnecting(it) }
            exoManager?.onStateChanged = { viewModel.onPlaybackState(it) }
            // Etapa 15 §03/§04: falha de vídeo confirmada -> tentar outra fonte autorizada.
            exoManager?.onVideoUnavailable = { viewModel.onVideoUnavailable() }
        }

        // Conecta o player ao PlayerView assim que o ExoPlayerManager estiver pronto
        LaunchedEffect(exoManager, playerViewRef) {
            val mgr = exoManager ?: return@LaunchedEffect
            val pv = playerViewRef ?: return@LaunchedEffect
            pv.player = mgr.build()
        }

        // Carrega streams quando o canal muda. Ao trocar para um canal sem fonte
        // reproduzível, limpa o player para que o frame anterior não permaneça (Etapa 07 §06).
        // Etapa 09: somente o mecanismo DIRECT_STREAM usa o ExoPlayer; nos demais a
        // mídia anterior é encerrada (fallback para WebView ou indisponível).
        LaunchedEffect(exoManager, ui.current?.number, ui.mechanism, ui.allStreams) {
            val mgr = exoManager ?: return@LaunchedEffect
            val streams = ui.allStreams
            if (ui.mechanism != PlaybackMechanism.DIRECT_STREAM || streams.isEmpty()) {
                mgr.clear()
                return@LaunchedEffect
            }
            val firstStream = streams[0]
            mgr.playMulti(
                streams = streams,
                referrer = firstStream.referrer,
                userAgent = firstStream.user_agent,
                onError = { viewModel.nextStreamOnError() }
            )
        }

        // Atualiza modo de proporção da tela dinamicamente
        LaunchedEffect(ui.aspectRatio) {
            playerViewRef?.resizeMode = when (ui.aspectRatio) {
                AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                AspectRatioMode.FOUR_THREE -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
            }
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val rootFocusRequester = remember { FocusRequester() }

        // Garante que o root Box capture foco logo ao iniciar (necessário para onKeyEvent funcionar)
        LaunchedEffect(Unit) {
            try { rootFocusRequester.requestFocus() } catch (_: Exception) {}
        }

        // Gerencia foco entre PlayerView (nativa) e Overlays (Compose)
        // Quando qualquer overlay abre: remove o foco da PlayerView nativa para que
        // o sistema de foco do Android TV propague os eventos D-Pad aos itens Compose
        val anyOverlayOpen = ui.showCategoriesDrawer || ui.showQuickOptions ||
            ui.showProgramGuide || ui.showParental
        LaunchedEffect(anyOverlayOpen) {
            if (anyOverlayOpen) {
                playerViewRef?.isFocusable = false
                playerViewRef?.isFocusableInTouchMode = false
                playerViewRef?.clearFocus()
            } else {
                playerViewRef?.isFocusable = true
                playerViewRef?.isFocusableInTouchMode = true
                try { rootFocusRequester.requestFocus() } catch (_: Exception) {}
            }
        }

        var okDownTime by remember { mutableLongStateOf(0L) }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(rootFocusRequester)
                .onKeyEvent { ev ->
                    val action = ev.nativeKeyEvent.action
                    if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) return@onKeyEvent false
                    val remoteAction = RemoteKeyHandler.map(ev.nativeKeyEvent) ?: return@onKeyEvent false

                    // Qualquer overlay aberto "congela" as teclas de canal e volume nativas
                    val anyOverlayOpen = ui.showCategoriesDrawer || ui.showTvBoxHub ||
                        ui.showQuickOptions || ui.showProgramGuide || ui.showParental

                    if (anyOverlayOpen) {
                        if (action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                        // Deixa que o Compose e o D-Pad naveguem nativamente dentro dos overlays
                        return@onKeyEvent when (remoteAction) {
                            RemoteAction.RED -> { if (ui.deviceMode == "ANDROID_TV") viewModel.toggleFavorite(); true }
                            RemoteAction.GREEN -> { if (ui.deviceMode == "ANDROID_TV") viewModel.toggleCategoriesDrawer(); true }
                            RemoteAction.YELLOW -> { if (ui.deviceMode == "ANDROID_TV") viewModel.toggleQuickOptions(); true }
                            RemoteAction.BLUE, RemoteAction.GUIDE -> { if (ui.deviceMode == "ANDROID_TV") viewModel.toggleProgramGuide(); true }
                            RemoteAction.BACK -> { viewModel.closeAllOverlays() }
                            else -> false // Retorna false para que o Compose trate como eventos D-Pad normais nos menus
                        }
                    }

                    // Detecção de Long Press na tecla OK para o modo TV Box
                    if (remoteAction == RemoteAction.OK) {
                        if (action == KeyEvent.ACTION_DOWN) {
                            if (ev.nativeKeyEvent.repeatCount == 0) {
                                okDownTime = System.currentTimeMillis()
                            } else if (ui.deviceMode == "TV_BOX" && System.currentTimeMillis() - okDownTime >= 600) {
                                // Dispara o Mini Hub durante o Long Press
                                viewModel.toggleTvBoxHub()
                                okDownTime = Long.MAX_VALUE // Evita disparar novamente no ACTION_UP
                            }
                            return@onKeyEvent true // Consome o evento
                        } else if (action == KeyEvent.ACTION_UP) {
                            val holdTime = System.currentTimeMillis() - okDownTime
                            if (holdTime < 600) {
                                // Clique curto (abre o banner normal)
                                viewModel.toggleInfoBanner()
                            }
                            okDownTime = 0L
                            return@onKeyEvent true
                        }
                    }

                    // Para as demais teclas, processa apenas o ACTION_DOWN
                    if (action != KeyEvent.ACTION_DOWN) return@onKeyEvent false

                    when (remoteAction) {
                        RemoteAction.DIGIT_0 -> { viewModel.onDigit(0); true }
                        RemoteAction.DIGIT_1 -> { viewModel.onDigit(1); true }
                        RemoteAction.DIGIT_2 -> { viewModel.onDigit(2); true }
                        RemoteAction.DIGIT_3 -> { viewModel.onDigit(3); true }
                        RemoteAction.DIGIT_4 -> { viewModel.onDigit(4); true }
                        RemoteAction.DIGIT_5 -> { viewModel.onDigit(5); true }
                        RemoteAction.DIGIT_6 -> { viewModel.onDigit(6); true }
                        RemoteAction.DIGIT_7 -> { viewModel.onDigit(7); true }
                        RemoteAction.DIGIT_8 -> { viewModel.onDigit(8); true }
                        RemoteAction.DIGIT_9 -> { viewModel.onDigit(9); true }

                        RemoteAction.CHANNEL_UP -> { viewModel.channelUp(); true }
                        RemoteAction.CHANNEL_DOWN -> { viewModel.channelDown(); true }
                        RemoteAction.VOLUME_UP -> {
                            audioManager.adjustStreamVolume(
                                AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI
                            )
                            true
                        }
                        RemoteAction.VOLUME_DOWN -> {
                            audioManager.adjustStreamVolume(
                                AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI
                            )
                            true
                        }

                        RemoteAction.RED -> { if (ui.deviceMode == "ANDROID_TV") viewModel.toggleFavorite(); true }
                        RemoteAction.GREEN -> { if (ui.deviceMode == "ANDROID_TV") viewModel.toggleCategoriesDrawer(); true }
                        RemoteAction.YELLOW -> { if (ui.deviceMode == "ANDROID_TV") viewModel.toggleQuickOptions(); true }
                        RemoteAction.BLUE, RemoteAction.GUIDE -> { if (ui.deviceMode == "ANDROID_TV") viewModel.toggleProgramGuide(); true }
                        RemoteAction.INFO -> { viewModel.showBanner(); true }
                        RemoteAction.LAST_CHANNEL -> { viewModel.recallLastChannel(); true }

                        RemoteAction.BACK -> {
                            viewModel.closeAllOverlays()
                        }
                        else -> false
                    }
                }
                .focusable()
        ) {
            // Video Surface — factory cria a View; update reconecta o player quando ele estiver pronto
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        useController = false
                        // Player NÃO é definido aqui — neste momento exoManager ainda é null.
                        // A conexão é feita via LaunchedEffect(exoManager, playerViewRef) acima.
                        setKeepContentOnPlayerReset(false)
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                        setOnTouchListener(null)
                        playerViewRef = this
                    }
                },
                update = { pv ->
                    // Reconecta o player sempre que o exoManager mudar
                    val mgr = exoManager
                    if (mgr != null && pv.player == null) {
                        pv.player = mgr.build()
                    }
                    // Gerencia focusabilidade: quando overlay está aberto, a PlayerView
                    // deve liberar o foco para que o D-Pad alcance os menus Compose
                    pv.isFocusable = !anyOverlayOpen
                    pv.isFocusableInTouchMode = !anyOverlayOpen
                    if (anyOverlayOpen) pv.clearFocus()
                    playerViewRef = pv
                },
                modifier = Modifier.fillMaxSize()
            )

            // Player web universal (Etapa 09 §04/§05): reutilizável, ativo apenas no
            // mecanismo WEB_EMBED. Recebe dinamicamente o embed_url do canal atual.
            if (ui.mechanism == PlaybackMechanism.WEB_EMBED && ui.webUrl != null) {
                WebPlayer(
                    url = ui.webUrl!!,
                    onLoadingChanged = { viewModel.setWebLoading(it) },
                    onError = { viewModel.onWebError() },
                    onFullscreenChanged = { },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Tuning / Loading Overlay
            if (ui.isLoading) {
                Box(
                    Modifier.fillMaxSize().background(Color(0x8A000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = ZapGold,
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "Sintonizando ${ui.current?.number ?: ""} • ${ui.current?.channelName ?: ""}",
                            color = ZapTextPrimary,
                            fontFamily = MontserratFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }
                }
            }

            // Sem sinal — estado explícito, não modal (não exige ação do usuário)
            if (ui.noSignal && !ui.isLoading) {
                Box(
                    Modifier.fillMaxSize().background(Color(0xE6000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "SEM SINAL",
                            color = ZapLiveRed,
                            fontFamily = MontserratFontFamily,
                            fontWeight = FontWeight.Black,
                            fontSize = 34.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "${ui.current?.number ?: ""} • ${ui.current?.channelName ?: ""}",
                            color = ZapTextSecondary,
                            fontFamily = MontserratFontFamily,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // Áudio sem imagem (Etapa 10 §06): há sinal, mas nenhuma trilha de vídeo
            // foi selecionada/renderizada — informa sem tratar como "sem sinal".
            if (ui.mechanism == PlaybackMechanism.DIRECT_STREAM &&
                ui.playbackState == PlaybackState.AUDIO_ONLY && !ui.noSignal
            ) {
                Box(
                    Modifier.fillMaxSize().background(Color(0xB3000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "SEM IMAGEM",
                            color = ZapTextPrimary,
                            fontFamily = MontserratFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Reproduzindo apenas o áudio",
                            color = ZapTextSecondary,
                            fontFamily = MontserratFontFamily,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            // Error Dialog
            ui.error?.let { msg ->
                Box(
                    Modifier.fillMaxSize().background(Color(0xCC000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .background(ZapSurfaceCard, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            .padding(28.dp)
                    ) {
                        Text(
                            text = "⚠  $msg",
                            color = ZapTextPrimary,
                            fontFamily = MontserratFontFamily,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(16.dp))
                        androidx.compose.material3.Button(
                            onClick = { viewModel.dismissError() },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = ZapGold,
                                contentColor = Color.Black
                            )
                        ) {
                            Text(
                                text = "OK",
                                fontFamily = MontserratFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Direct 3-digit Channel Typing Overlay
            ChannelNumberOverlay(ui.inputDigits)

            // Bottom Info Banner (Now & Next)
            if (ui.current != null && !ui.showParental && !ui.showProgramGuide && !ui.showTvBoxHub) {
                ChannelInfoBanner(
                    visible = ui.showInfoBanner,
                    channel = ui.current!!,
                    isFavorite = ui.isFavorite,
                    deviceMode = ui.deviceMode,
                    isReconnecting = ui.isReconnecting,
                    epg = ui.epg,
                    sourceLabel = ui.sourceLabel,
                    sourceRegion = ui.sourceRegion,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            // 🟢 Categories Drawer (Left side)
            CategoriesDrawer(
                visible = ui.showCategoriesDrawer && !ui.showParental,
                channels = ui.allChannels,
                favorites = ui.favorites,
                currentChannelNumber = ui.current?.number ?: 0,
                onSelectChannel = { viewModel.tune(it) },
                onClose = { viewModel.toggleCategoriesDrawer() }
            )

            // 📦 TV Box Hub (Left side, only TV Box)
            TvBoxHubOverlay(
                visible = ui.showTvBoxHub && !ui.showParental,
                channels = ui.allChannels,
                favorites = ui.favorites,
                currentChannelNumber = ui.current?.number ?: 0,
                currentAspectRatio = ui.aspectRatio,
                onSelectChannel = { viewModel.tune(it) },
                onSetAspectRatio = { viewModel.setAspectRatio(it) },
                onClose = { viewModel.toggleTvBoxHub() }
            )

            // 🟡 Quick Options Menu (Audio, Subtitles, Aspect Ratio)
            QuickOptionsMenu(
                visible = ui.showQuickOptions && !ui.showParental,
                currentAspectRatio = ui.aspectRatio,
                onSelectAspectRatio = { viewModel.setAspectRatio(it) },
                onClose = { viewModel.toggleQuickOptions() }
            )

            // 🔵 Full Program Guide (Matrix / EPG)
            ProgramGuideOverlay(
                visible = ui.showProgramGuide && !ui.showParental,
                channels = ui.allChannels,
                currentChannelNumber = ui.current?.number ?: 0,
                onSelectChannel = { viewModel.tune(it) },
                onClose = { viewModel.toggleProgramGuide() }
            )

            // 🔒 Parental Lock Dialog
            if (ui.showParental) {
                ParentalLockOverlay(
                    error = ui.parentalError,
                    onSubmit = { viewModel.onParentalSubmit(it) },
                    onCancel = { viewModel.cancelParental() }
                )
            }
        }
    }
}