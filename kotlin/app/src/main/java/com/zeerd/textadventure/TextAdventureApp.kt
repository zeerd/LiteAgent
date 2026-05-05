package com.zeerd.textadventure

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

/**
 * 应用程序类，配置 Hilt 依赖注入并加载必要的本地库。
 */
@HiltAndroidApp
class TextAdventureApp : Application() {
    override fun onCreate() {
        Log.v("TextAdventureApp", ">>> onCreate() IN")
        try {
            super.onCreate()
            Log.v("TextAdventureApp", "super.onCreate() called")
            // 加载 LiteRT-LM 所需的 JNI 本地库
            Log.v("TextAdventureApp", "Calling System.loadLibrary(\"litertlm_jni\")")
            System.loadLibrary("litertlm_jni")
            Log.v("TextAdventureApp", "Successfully loaded litertlm_jni")
            Log.v("TextAdventureApp", "<<< onCreate() OUT - success")
        } catch (e: UnsatisfiedLinkError) {
            // 如果库加载失败，记录错误
            Log.e("TextAdventureApp", "Failed to load litertlm_jni: ${e.message}")
            Log.v("TextAdventureApp", "<<< onCreate() OUT - error")
            throw e
        }
    }
}
