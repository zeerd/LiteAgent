package com.liteagent.textadventure.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton
import androidx.compose.runtime.Immutable

/**
 * 应用程序设置存储库，使用 SharedPreferences 持久化设置。
 */
@Singleton
class AppSettingsRepository @Inject constructor(
    context: Context
) {
    // 偏好设置文件
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("text_adventure_prefs", Context.MODE_PRIVATE)

    private val gson = Gson()
    private val settingsKey = "app_settings"

    /**
     * 从持久化存储中读取设置。
     */
    fun getSettings(): AppSettings {
        val json = sharedPreferences.getString(settingsKey, null)
        return if (json != null) {
            try {
                val type = object : TypeToken<AppSettings>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                AppSettings() // 解析失败返回默认值
            }
        } else {
            AppSettings() // 无记录返回默认值
        }
    }

    /**
     * 将设置保存到持久化存储。
     */
    fun saveSettings(settings: AppSettings) {
        val json = gson.toJson(settings)
        sharedPreferences.edit().putString(settingsKey, json).apply()
    }

    /**
     * 重置所有设置为默认值。
     */
    fun resetSettings() {
        sharedPreferences.edit().remove(settingsKey).apply()
    }
}

/**
 * 应用程序设置的数据类。
 */
@Immutable
data class AppSettings(
    val language: String = "zh", // 默认语言：中文
    val backend: String = "huggingface", // 后端引擎
    val accelerationMode: String = "CPU", // 加速模式：CPU/GPU
    val selectedModelPath: String? = null, // 本地模型文件路径
    val selectedModelName: String? = null, // 模型名称
    val temperature: Float = 0.7f, // 生成温度
    val topP: Float = 0.9f, // Top-P 采样
    val topK: Int = 40, // Top-K 采样
    val maxTokens: Int = 32768, // 最大生成 Token 数
    val systemPrompt: String? = null, // 系统提示词
    val lastSelectedFileName: String? = null, // 上次选择的文件名
    val lastSelectedFileUri: String? = null, // 上次选择的文件 URI
    val lastSelectedFileDirPath: String? = null // 上次选择的文件所在目录路径
) {
    /**
     * 根据后端引擎返回对应的模型接口端点。
     */
    val modelEndpoint: String
        get() = when (backend) {
            "huggingface" -> "https://api-inference.huggingface.co/models"
            "modelscope" -> "https://api-modelscope.alibaba.com/models"
            else -> "https://api-inference.huggingface.co/models"
        }
}
