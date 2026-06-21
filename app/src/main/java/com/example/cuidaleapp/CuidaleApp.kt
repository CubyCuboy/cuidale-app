package com.example.cuidaleapp

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.example.cuidaleapp.utils.ConnectivityObserver
import okio.Path.Companion.toPath

class CuidaleApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        ConnectivityObserver.initialize(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").path.toPath())
                    .maxSizeBytes(1024 * 1024 * 100)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
