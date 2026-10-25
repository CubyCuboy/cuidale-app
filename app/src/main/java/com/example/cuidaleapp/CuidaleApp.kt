package com.example.cuidaleapp

import android.app.Activity
import android.app.Application
import android.os.Bundle
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.example.cuidaleapp.utils.AppNavigationState
import com.example.cuidaleapp.utils.ChatCacheStore
import com.example.cuidaleapp.utils.ConnectivityObserver
import okio.Path.Companion.toPath

class CuidaleApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        ConnectivityObserver.initialize(this)
        ChatCacheStore.init(this)
        registerActivityLifecycleCallbacks(VisibilityTracker())
    }

    private class VisibilityTracker : ActivityLifecycleCallbacks {
        private var activeCount = 0

        override fun onActivityStarted(activity: Activity) {
            activeCount++
            AppNavigationState.setAppVisible(true)
        }

        override fun onActivityStopped(activity: Activity) {
            activeCount--
            if (activeCount <= 0) {
                activeCount = 0
                AppNavigationState.setAppVisible(false)
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
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
