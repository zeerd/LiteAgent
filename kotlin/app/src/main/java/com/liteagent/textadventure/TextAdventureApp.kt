package com.liteagent.textadventure

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TextAdventureApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            System.loadLibrary("litertlm_jni")
            Log.d("TextAdventureApp", "Successfully loaded litertlm_jni")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("TextAdventureApp", "Failed to load litertlm_jni: ${e.message}")
        }
    }
}
