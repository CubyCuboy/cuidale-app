package com.example.cuidaleapp.utils

import android.content.Context
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages deterministic local caching for profile images to ensure
 * immediate UI updates and cache consistency.
 */
object ProfileImageCacheManager {

    /**
     * Clears the Coil image cache for a specific key (usually the remote URL or custom cache key).
     */
    @OptIn(DelicateCoilApi::class)
    suspend fun evictFromCache(context: Context, cacheKey: String) {
        withContext(Dispatchers.IO) {
            val imageLoader = SingletonImageLoader.get(context)
            imageLoader.diskCache?.remove(cacheKey)
            imageLoader.memoryCache?.remove(coil3.memory.MemoryCache.Key(cacheKey))
        }
    }

    /**
     * Clears all cached profile images. Useful during logout.
     */
    @OptIn(DelicateCoilApi::class)
    suspend fun clearAllCache(context: Context) {
        withContext(Dispatchers.IO) {
            val imageLoader = SingletonImageLoader.get(context)
            imageLoader.diskCache?.clear()
            imageLoader.memoryCache?.clear()
        }
    }

    /**
     * Preloads an image into the cache.
     */
    suspend fun preloadImage(context: Context, url: String) {
        val request = ImageRequest.Builder(context)
            .data(url)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
        SingletonImageLoader.get(context).enqueue(request)
    }
}
