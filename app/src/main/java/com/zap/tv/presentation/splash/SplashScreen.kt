package com.zap.tv.presentation.splash

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zap.tv.R
import com.zap.tv.data.AppDependencies
import kotlinx.coroutines.withTimeout

private const val PREFS_NAME = "zap_tv_prefs"
private const val KEY_USER_NAME = "user_name"

@Composable
fun SplashScreen(
    onSetupNeeded: () -> Unit,
    onReady: () -> Unit
) {
    val context = LocalContext.current
    val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    var state by remember { mutableStateOf("Carregando...") }
    var error by remember { mutableStateOf<String?>(null) }
    var userName by remember { mutableStateOf<String?>(null) }
    // Recupera nome gravado na splash anterior
    LaunchedEffect(Unit) {
        userName = prefs.getString(KEY_USER_NAME, null)
    }

    LaunchedEffect(Unit) {
        try {
            state = "Verificando configuração..."
            kotlinx.coroutines.delay(300)
            val repo = AppDependencies.getChannelRepository(context)
            val needSetup = withTimeout(4000) { !repo.isSetupComplete() }
            if (needSetup) {
                // Se precisa de setup, limpa nome caso seja nova instalação
                if (userName == null) {
                    // nova instalação - segue para setup
                } else {
                    // usuário retornando - mantém nome
                }
                onSetupNeeded()
            } else {
                state = "Sintonizando..."
                kotlinx.coroutines.delay(300)
                onReady()
            }
        } catch (e: Exception) {
            // Se falhar verificação, assume que precisa setup (primeira vez) mas tenta ir para player se já tem canais
            try {
                val repo = AppDependencies.getChannelRepository(context)
                val hasChannels = repo.countAvailable() > 0 || repo.count() > 0
                if (hasChannels) onReady() else onSetupNeeded()
            } catch (_: Exception) {
                onSetupNeeded()
            }
        }
    }
    // timeout segurança
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(7000)
        if (state == "Carregando..." || state == "Verificando configuração...") {
            onSetupNeeded()
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.logo_zap_splash),
                contentDescription = "ZAP-TV",
                modifier = Modifier
                    .size(width = 360.dp, height = 200.dp)
                    .padding(bottom = 16.dp),
                contentScale = ContentScale.Fit
            )
            CircularProgressIndicator(color = Color(0xFFFFD600))
            Spacer(Modifier.height(16.dp))
            Text(state, color = Color.White, fontSize = 18.sp)
            // Linha de boas-vindas com nome do usuário
            if (userName != null) {
                Spacer(Modifier.height(8.dp))
                Text("Bem vindo de volta, $userName!", color = Color(0xFFFFD600), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
