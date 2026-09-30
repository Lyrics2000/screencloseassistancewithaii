package com.schoolproject.screenclose

import android.app.Application
import com.schoolproject.screenclose.di.AppContainer
import kotlinx.coroutines.launch

class ScreenCloseApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.mainScope.launch { container.auth.initialize() }
        container.logger.info("app_started", mapOf("version" to BuildConfig.VERSION_NAME))
    }
}
