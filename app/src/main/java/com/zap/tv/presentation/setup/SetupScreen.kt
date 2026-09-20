package com.zap.tv.presentation.setup

import android.app.Application
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.zap.tv.R
import com.zap.tv.ui.theme.*
import com.zap.tv.util.RemoteAction
import com.zap.tv.util.RemoteKeyHandler
import kotlinx.coroutines.delay
import org.json.JSONObject

@Composable
fun SetupScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    var vm by remember { mutableStateOf<SetupViewModel?>(null) }
    var vmError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application)
            vm = ViewModelProvider(context as ComponentActivity, factory).get(SetupViewModel::class.java)
        } catch (e: Exception) {
            vmError = ": \n"
        }
    }

    ZapTvTheme {
        if (vm == null) {
            Box(Modifier.fillMaxSize().background(ZapDarkBackground).padding(48.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (vmError != null) {
                        Text(
                            text = "Erro ao carregar configuração",
                            color = ZapLiveRed,
                            fontFamily = MontserratFontFamily,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(text = vmError ?: "", color = ZapTextPrimary, fontSize = 12.sp)
                    } else {
                        Text(
                            text = "ZAP-TV",
                            color = ZapGold,
                            fontFamily = MontserratFontFamily,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.height(14.dp))
                        CircularProgressIndicator(color = ZapGold)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Iniciando assistente de instalação...",
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
        val step by viewModel.step.collectAsState()
        val loading by viewModel.loading.collectAsState()
        val error by viewModel.error.collectAsState()
        val verifying by viewModel.verifying.collectAsState()
        val verifyProgress by viewModel.verifyProgress.collectAsState()
        val userName by viewModel.userName.collectAsState()
        val selectedUf by viewModel.uf.collectAsState()
        val selectedDeviceMode by viewModel.deviceMode.collectAsState()

        Box(
            Modifier
                .fillMaxSize()
                .background(ZapDarkBackground)
                .padding(horizontal = 48.dp, vertical = 28.dp)
        ) {
            Column(Modifier.fillMaxSize()) {
                // Header / Stepper (Breadcrumbs)
                SetupStepper(currentStep = if (verifying) 4 else step)

                Spacer(Modifier.height(20.dp))

                if (verifying) {
                    // Step 4: Sintonização & Inicialização
                    TuningProgressStep(
                        progress = verifyProgress,
                        userName = userName,
                        uf = selectedUf
                    )
                } else {
                    when (step) {
                        0 -> DeviceModeStep(
                            vm = viewModel,
                            selectedMode = selectedDeviceMode,
                            onNext = { viewModel.nextFromDeviceMode() }
                        )
                        1 -> StateStep(
                            vm = viewModel,
                            selectedUf = selectedUf,
                            onNext = { viewModel.nextFromState() }
                        )
                        2 -> UserNameStep(
                            vm = viewModel,
                            userName = userName,
                            onBack = { viewModel.prevStep() },
                            onNext = { viewModel.nextFromName() }
                        )
                        3 -> PinStep(
                            vm = viewModel,
                            loading = loading,
                            error = error,
                            onBack = { viewModel.prevStep() },
                            onSkip = { viewModel.skipPin(onComplete) },
                            onFinish = { viewModel.confirm(onComplete) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SetupStepper(currentStep: Int) {
    val steps = listOf("1. Aparelho", "2. Região", "3. Perfil", "4. Senha", "5. Fim")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF14141C))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.zap_tv_logo),
                contentDescription = "ZAP-TV",
                modifier = Modifier.size(42.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "•  Configuração Inicial",
                color = ZapTextSecondary,
                fontFamily = MontserratFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            steps.forEachIndexed { index, label ->
                val isActive = index == currentStep
                val isCompleted = index < currentStep

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                if (isCompleted) ZapGreenKey
                                else if (isActive) ZapGold
                                else Color(0xFF2C2C38)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isCompleted) "✓" else "",
                            color = if (isActive || isCompleted) Color.Black else ZapTextSecondary,
                            fontFamily = MontserratFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = label,
                        color = if (isActive) ZapGold else if (isCompleted) ZapTextPrimary else ZapTextMuted,
                        fontFamily = MontserratFontFamily,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceModeStep(
    vm: SetupViewModel,
    selectedMode: String,
    onNext: () -> Unit
) {
    val firstFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(200)
        try { firstFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Como você vai controlar a sua TV?",
            color = ZapTextPrimary,
            fontFamily = MontserratFontFamily,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Isso adapta a interface para o controle remoto que você possui.",
            color = ZapTextSecondary,
            fontFamily = MontserratFontFamily,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Android TV Card
            DeviceCard(
                title = "Android TV",
                description = "Smart TVs e controles completos\n(Com botões coloridos 🔴🟢🟡🔵)",
                isSelected = selectedMode == "ANDROID_TV",
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(firstFocusRequester),
                onClick = { vm.selectDeviceMode("ANDROID_TV"); onNext() }
            )

            // TV Box Card
            DeviceCard(
                title = "TV Box",
                description = "Aparelhos genéricos e controles básicos\n(Apenas Setas e OK)",
                isSelected = selectedMode == "TV_BOX",
                modifier = Modifier.weight(1f),
                onClick = { vm.selectDeviceMode("TV_BOX"); onNext() }
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Use ◀ ▶ para navegar e OK para selecionar",
                color = ZapTextMuted,
                fontFamily = MontserratFontFamily,
                fontSize = 12.sp
            )

            TvButton(
                text = "Continuar (OK) →",
                onClick = onNext,
                isPrimary = true
            )
        }
    }
}

@Composable
private fun DeviceCard(
    title: String,
    description: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isFocused) ZapGold
                else if (isSelected) Color(0xFF322C0A)
                else Color(0xFF181822)
            )
            .border(
                width = if (isFocused) 3.dp else if (isSelected) 2.dp else 1.dp,
                color = if (isFocused) Color.White else if (isSelected) ZapGold else Color(0xFF2A2A38),
                shape = RoundedCornerShape(16.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickableNoRipple(onClick)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isSelected) "✓ $title" else title,
                color = if (isFocused) Color.Black else if (isSelected) ZapGold else ZapTextPrimary,
                fontFamily = MontserratFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 28.sp
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = description,
                color = if (isFocused) Color(0xFF1E1E1E) else ZapTextSecondary,
                fontFamily = MontserratFontFamily,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
        }
    }
}


@Composable
private fun StateStep(
    vm: SetupViewModel,
    selectedUf: String,
    onNext: () -> Unit
) {
    val ctx = LocalContext.current
    val states = remember {
        try {
            val json = ctx.resources.openRawResource(R.raw.brazil_states).bufferedReader().readText()
            val arr = JSONObject(json).getJSONArray("states")
            (0 until arr.length()).map {
                arr.getJSONObject(it).getString("uf") to arr.getJSONObject(it).getString("name")
            }
        } catch (e: Exception) {
            listOf("NACIONAL" to "Todos os Canais")
        }
    }

    val firstItemFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(200)
        try { firstItemFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Em qual estado você está?",
            color = ZapTextPrimary,
            fontFamily = MontserratFontFamily,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Sintonizaremos a Globo, SBT, Record e Band locais da sua região nas primeiras posições.",
            color = ZapTextSecondary,
            fontFamily = MontserratFontFamily,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(states) { (uf, name) ->
                val isSelected = uf == selectedUf
                var isFocused by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isFocused) ZapGold
                            else if (isSelected) Color(0xFF322C0A)
                            else Color(0xFF181822)
                        )
                        .border(
                            width = if (isFocused) 2.dp else if (isSelected) 1.5.dp else 1.dp,
                            color = if (isFocused) Color.White else if (isSelected) ZapGold else Color(0xFF2A2A38),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .onFocusChanged { isFocused = it.isFocused }
                        .focusable()
                        .clickableNoRipple {
                            vm.selectUf(uf)
                            onNext()
                        }
                        .then(if (uf == "AC") Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isSelected) " ✓" else uf,
                            color = if (isFocused) Color.Black else if (isSelected) ZapGold else ZapTextPrimary,
                            fontFamily = MontserratFontFamily,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp
                        )
                        Text(
                            text = name,
                            color = if (isFocused) Color(0xFF1E1E1E) else ZapTextSecondary,
                            fontFamily = MontserratFontFamily,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Use as setas ▲▼◀▶ do controle remoto para navegar e OK para selecionar seu estado",
                color = ZapTextMuted,
                fontFamily = MontserratFontFamily,
                fontSize = 12.sp
            )

            TvButton(
                text = "Continuar (OK) →",
                onClick = onNext,
                isPrimary = true
            )
        }
    }
}

@Composable
private fun UserNameStep(
    vm: SetupViewModel,
    userName: String,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val inputFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(200)
        try { inputFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Como gostaria de ser chamado?",
            color = ZapTextPrimary,
            fontFamily = MontserratFontFamily,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Personalizaremos a interface do seu ZAP-TV com o seu nome.",
            color = ZapTextSecondary,
            fontFamily = MontserratFontFamily,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(32.dp))

        Column(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF161620))
                .border(1.5.dp, Color(0xFF2C2C3C), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Text(
                text = "SEU NOME OU APELIDO",
                color = ZapGold,
                fontFamily = MontserratFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = userName,
                onValueChange = { vm.setUserName(it) },
                placeholder = {
                    Text(
                        text = "Ex: Sala, Quarto, João...",
                        color = ZapTextMuted,
                        fontFamily = MontserratFontFamily
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onNext() }
                ),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = ZapTextPrimary
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ZapGold,
                    unfocusedBorderColor = Color(0xFF38384C),
                    focusedContainerColor = Color(0xFF1E1E2A),
                    unfocusedContainerColor = Color(0xFF121218)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(inputFocusRequester)
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Dica: Pressione OK no campo para abrir o teclado na tela da TV.",
                color = ZapTextMuted,
                fontFamily = MontserratFontFamily,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvButton(
                text = "← Voltar",
                onClick = onBack,
                isPrimary = false
            )

            TvButton(
                text = "Avançar (OK) →",
                onClick = onNext,
                isPrimary = true
            )
        }
    }
}

@Composable
private fun PinStep(
    vm: SetupViewModel,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit
) {
    val pin by vm.pin.collectAsState()
    val confirm by vm.confirmPin.collectAsState()
    val pin1FocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(200)
        try { pin1FocusRequester.requestFocus() } catch (_: Exception) {}
    }

    // Direct Remote Control number listener
    val keyHandler = Modifier.onKeyEvent { ev ->
        if (ev.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
        val action = RemoteKeyHandler.map(ev.nativeKeyEvent) ?: return@onKeyEvent false
        val digit = RemoteKeyHandler.digit(action)
        if (digit != null) {
            if (pin.length < 4) {
                vm.setPin(pin + digit)
            } else if (confirm.length < 4) {
                vm.setConfirmPin(confirm + digit)
            }
            return@onKeyEvent true
        }
        false
    }

    Column(modifier = Modifier.fillMaxSize().then(keyHandler)) {
        Text(
            text = "Senha para Canais Adultos (+18)",
            color = ZapTextPrimary,
            fontFamily = MontserratFontFamily,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Crie um PIN de 4 dígitos para proteger conteúdos adultos. Você também pode pular esta etapa.",
            color = ZapTextSecondary,
            fontFamily = MontserratFontFamily,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(28.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // PIN Box
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF161620))
                    .border(1.5.dp, Color(0xFF2C2C3C), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Text(
                    text = "DIGITE O PIN (4 DÍGITOS)",
                    color = ZapGold,
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { vm.setPin(it) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Next
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = MontserratFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp,
                        color = ZapGold,
                        letterSpacing = 8.sp,
                        textAlign = TextAlign.Center
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZapGold,
                        unfocusedBorderColor = Color(0xFF38384C),
                        focusedContainerColor = Color(0xFF1E1E2A),
                        unfocusedContainerColor = Color(0xFF121218)
                    ),
                    modifier = Modifier.fillMaxWidth().focusRequester(pin1FocusRequester)
                )
            }

            // Confirm PIN Box
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF161620))
                    .border(1.5.dp, Color(0xFF2C2C3C), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Text(
                    text = "CONFIRME O PIN",
                    color = ZapGold,
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { vm.setConfirmPin(it) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onFinish() }
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = MontserratFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp,
                        color = ZapGold,
                        letterSpacing = 8.sp,
                        textAlign = TextAlign.Center
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZapGold,
                        unfocusedBorderColor = Color(0xFF38384C),
                        focusedContainerColor = Color(0xFF1E1E2A),
                        unfocusedContainerColor = Color(0xFF121218)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = "⚠ ",
                color = ZapLiveRed,
                fontFamily = MontserratFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Dica: Digite diretamente os números 0-9 no seu controle remoto.",
            color = ZapTextMuted,
            fontFamily = MontserratFontFamily,
            fontSize = 12.sp
        )

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvButton(
                text = "← Voltar",
                onClick = onBack,
                isPrimary = false
            )

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvButton(
                    text = "Pular Senha",
                    onClick = onSkip,
                    isPrimary = false
                )

                TvButton(
                    text = if (loading) "Sintonizando..." else "Concluir e Sintonizar ✓",
                    onClick = onFinish,
                    isPrimary = true,
                    enabled = !loading
                )
            }
        }
    }
}

@Composable
private fun TuningProgressStep(
    progress: SetupViewModel.VerifyProgress?,
    userName: String,
    uf: String
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(600.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF14141C))
                .border(1.5.dp, Color(0xFF2A2A38), RoundedCornerShape(20.dp))
                .padding(36.dp)
        ) {
            Text(
                text = if (userName.isNotBlank()) "Bem-vindo, $userName!" else "Bem-vindo ao ZAP-TV!",
                color = ZapGold,
                fontFamily = MontserratFontFamily,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Sintonizando canais e verificando qualidade de sinal...\nIsso pode levar alguns minutos.",
                color = ZapTextSecondary,
                fontFamily = MontserratFontFamily,
                fontSize = 16.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            val checked = progress?.checked ?: 0
            val total = progress?.total ?: 1
            val alive = progress?.alive ?: 0
            val currentName = progress?.currentName ?: "Preparando..."
            val pct = if (total > 0) (checked.toFloat() / total.toFloat()) else 0f
            val pctInt = (pct * 100).toInt()

            Text(
                text = "Sintonizando: $currentName",
                color = Color.White,
                fontFamily = MontserratFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(12.dp))

            // Custom Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E1E2A))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(pct)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(ZapGold)
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Progresso: $pctInt%",
                    color = ZapTextSecondary,
                    fontFamily = MontserratFontFamily,
                    fontSize = 14.sp
                )
                Text(
                    text = "Canais funcionais encontrados: $alive",
                    color = ZapGold,
                    fontFamily = MontserratFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    isPrimary: Boolean,
    enabled: Boolean = true
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (!enabled) Color(0xFF2A2A36)
                else if (isFocused) if (isPrimary) Color.White else ZapGold
                else if (isPrimary) ZapGold
                else Color(0xFF1E1E2A)
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else if (isPrimary) ZapGold else Color(0xFF38384C),
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(enabled)
            .clickableNoRipple { if (enabled) onClick() }
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (!enabled) ZapTextMuted
            else if (isFocused) Color.Black
            else if (isPrimary) Color.Black
            else ZapTextPrimary,
            fontFamily = MontserratFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
)
