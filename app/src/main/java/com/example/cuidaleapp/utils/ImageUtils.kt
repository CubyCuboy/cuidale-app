package com.example.cuidaleapp.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream

object ImageUtils {
    private const val MAX_SIZE_BYTES = 5 * 1024 * 1024
    private val ALLOWED_FORMATS = listOf("image/jpeg", "image/png", "image/webp", "image/jpg")

    fun validateImage(context: Context, uri: Uri): Result<Unit> {
        val type = context.contentResolver.getType(uri)
        if (type != null && type !in ALLOWED_FORMATS) {
            return Result.failure(Exception("Formato no permitido: $type. Solo se aceptan JPG, PNG y WEBP."))
        }
        
        val size = getImageSize(context, uri)
        if (size > MAX_SIZE_BYTES * 2) {
            return Result.failure(Exception("La imagen es demasiado grande. Máximo permitido: 10MB."))
        }
        
        return Result.success(Unit)
    }

    fun getCompressedImage(context: Context, uri: Uri): ByteArray? {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val originalBytes = inputStream?.readBytes() ?: return null
        
        if (originalBytes.size <= MAX_SIZE_BYTES) {
            return originalBytes
        }

        val bitmap = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size)
        var quality = 90
        val outputStream = ByteArrayOutputStream()
        
        do {
            outputStream.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            quality -= 10
        } while (outputStream.size() > MAX_SIZE_BYTES && quality > 0)

        return outputStream.toByteArray()
    }
    
    fun getImageSize(context: Context, uri: Uri): Long {
        return context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { 
            it.length 
        } ?: 0L
    }
}

