package com.zap.tv.presentation.splash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.zap.tv.data.AppDependencies

class SplashViewModel(application: Application) : AndroidViewModel(application) {
    private val repo by lazy { AppDependencies.getChannelRepository(getApplication()) }
    suspend fun isSetupNeeded(): Boolean = !repo.isSetupComplete()
}
