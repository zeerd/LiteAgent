package com.liteagent.textadventure.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton
import androidx.compose.runtime.Immutable

@Singleton
class AppSettingsRepository @Inject constructor(
    context: Context
) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("text_adventure_prefs", Context.MODE_PRIVATE)

    private val gson = Gson()
    private val settingsKey = "app_settings"

    fun getSettings(): AppSettings {
        val json = sharedPreferences.getString(settingsKey, null)
        return if (json != null) {
            try {
                val type = object : TypeToken<AppSettings>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                AppSettings()
            }
        } else {
            AppSettings()
        }
    }

    fun saveSettings(settings: AppSettings) {
        val json = gson.toJson(settings)
        sharedPreferences.edit().putString(settingsKey, json).apply()
    }

    fun resetSettings() {
        sharedPreferences.edit().remove(settingsKey).apply()
    }
}

// AppSettings data class moved from model package

@Immutable
data class AppSettings(
    val backend: String = "huggingface",
    val accelerationMode: String = "CPU",
    val selectedModelPath: String? = null,
    val selectedModelName: String? = null,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val maxTokens: Int = 32768,
    val systemPrompt: String = "You are a Text Adventure game master. Create engaging, interactive stories where the user makes choices that affect the narrative.\nThe story should present challenges, opportunities, and choices for the player.\nRespond in-character and offer 2-4 options for the player to choose from.\nKeep the story flowing with interesting plot developments."
) {
    val modelEndpoint: String
        get() = when (backend) {
            "huggingface" -> "https://api-inference.huggingface.co/models"
            "modelscope" -> "https://api-modelscope.alibaba.com/models"
            else -> "https://api-inference.huggingface.co/models"
        }
}
