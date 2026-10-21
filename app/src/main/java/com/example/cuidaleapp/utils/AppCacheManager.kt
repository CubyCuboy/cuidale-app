package com.example.cuidaleapp.utils

import android.content.Context
import java.io.File

object AppCacheManager {

    fun clearMultimediaCaches(context: Context) {
        @androidx.media3.common.util.UnstableApi
        runCatching { com.example.cuidaleapp.ui.components.VideoPlayerCache.releaseAll() }
        runCatching { com.example.cuidaleapp.ui.components.MediaSkeletonCache.clearSeen() }
        runCatching {
            @androidx.media3.common.util.UnstableApi
            com.example.cuidaleapp.ui.components.MultimediaDiskCache.clear(context)
        }
        runCatching {
            val loader = coil3.SingletonImageLoader.get(context)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
    }

    /**
     * Limpia TODA la caché de la app excepto el tema (dark_mode).
     * Se usa al hacer logout.
     */
    fun clearAllExceptTheme(context: Context) {
        clearMultimediaCaches(context)

        val prefs = context.getSharedPreferences("cuidale_prefs", Context.MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode", false)
        prefs.edit().clear().apply()
        prefs.edit().putBoolean("dark_mode", isDark).apply()

        context.getSharedPreferences("verification_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun cacheSizeBytes(context: Context): Long {
        val multimedia = File(context.cacheDir, "multimedia_cache")
        val images = File(context.cacheDir, "image_cache")
        return folderSize(multimedia) + folderSize(images)
    }

    private fun folderSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        var total = 0L
        file.listFiles()?.forEach { total += folderSize(it) }
        return total
    }

    fun formatBytes(bytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        return when {
            bytes >= mb -> String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(java.util.Locale.getDefault(), "%.0f KB", bytes / kb)
            else -> "$bytes B"
        }
    }
}
