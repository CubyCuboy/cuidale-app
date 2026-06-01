package com.example.cuidaleapp.utils

import android.content.Context
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


object ProfileImageCacheManager {

    
    @OptIn(DelicateCoilApi::class)
    suspend fun evictFromCache(context: Context, cacheKey: String) {
        withContext(Dispatchers.IO) {
            val imageLoader = SingletonImageLoader.get(context)
            imageLoader.diskCache?.remove(cacheKey)
            imageLoader.memoryCache?.remove(coil3.memory.MemoryCache.Key(cacheKey))
        }
    }

    
    @OptIn(DelicateCoilApi::class)
    suspend fun clearAllCache(context: Context) {
        withContext(Dispatchers.IO) {
            val imageLoader = SingletonImageLoader.get(context)
            imageLoader.diskCache?.clear()
            imageLoader.memoryCache?.clear()
        }
    }

    
    suspend fun preloadImage(context: Context, url: String) {
        val request = ImageRequest.Builder(context)
            .data(url)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
        SingletonImageLoader.get(context).enqueue(request)
    }
}

