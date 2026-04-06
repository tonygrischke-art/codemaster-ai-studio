package com.codemaster.aistudio

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CodeMasterApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
