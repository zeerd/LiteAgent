package com.liteagent.textadventure

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

/**
 * 应用程序类，配置 Hilt 依赖注入并加载必要的本地库。
 */
@HiltAndroidApp
class TextAdventureApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            // 加载 LiteRT-LM 所需的 JNI 本地库
            System.loadLibrary("litertlm_jni")
            Log.d("TextAdventureApp", "Successfully loaded litertlm_jni")
        } catch (e: UnsatisfiedLinkError) {
            // 如果库加载失败，记录错误
            Log.e("TextAdventureApp", "Failed to load litertlm_jni: ${e.message}")
        }
    }
}
