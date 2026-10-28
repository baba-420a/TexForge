package com.example

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp(Application::class)
class TexForgeApplication : Hilt_TexForgeApplication() {
    override fun onCreate() {
        super.onCreate()
    }
}
