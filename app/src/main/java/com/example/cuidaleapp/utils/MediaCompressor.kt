package com.example.cuidaleapp.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream

object MediaCompressor {

    const val MAX_MEDIA_BYTES = 25 * 1024 * 1024 // 25 MB

    // Lado máximo de decodificación: evita asignar bitmaps a resolución completa
    // (los 25 MB de un JPEG pueden expandirse a cientos de MB en memoria).
    private const val MAX_DECODE_SIDE = 2560

    /**
     * Calcula un inSampleSize que acota el lado mayor de la imagen a [MAX_DECODE_SIDE]
     * sin tocar la imagen si ya es más pequeña.
     */
    private fun sampleSizeFor(bytes: ByteArray): Int {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        var longSide = maxOf(opts.outWidth, opts.outHeight)
        var sample = 1
        while (longSide / 2 >= MAX_DECODE_SIDE) {
            longSide /= 2
            sample *= 2
        }
        return sample
    }

    fun getSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun isVideo(context: Context, uri: Uri): Boolean {
        return context.contentResolver.getType(uri)?.startsWith("video/") == true
    }

    fun isImage(context: Context, uri: Uri): Boolean {
        return context.contentResolver.getType(uri)?.startsWith("image/") == true
    }

    /**
     * Comprime una imagen intentando mantener calidad: reduce la calidad JPEG de forma
     * iterativa hasta que el resultado quepa en [MAX_MEDIA_BYTES]. Si la imagen ya cabe,
     * devuelve los bytes originales.
     */
    fun compressImage(context: Context, uri: Uri): ByteArray? {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val originalBytes = inputStream?.readBytes() ?: return null

        if (originalBytes.size <= MAX_MEDIA_BYTES) {
            return originalBytes
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(originalBytes)
        }
        val bitmap = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, decodeOpts) ?: return originalBytes

        var quality = 92
        val outputStream = ByteArrayOutputStream()
        do {
            outputStream.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            quality -= 5
        } while (outputStream.size() > MAX_MEDIA_BYTES && quality > 20)

        if (outputStream.size() > MAX_MEDIA_BYTES) {
            // Último recurso: reducir dimensiones a la mitad
            val scaled = Bitmap.createScaledBitmap(bitmap, bitmap.width / 2, bitmap.height / 2, true)
            outputStream.reset()
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        }

        return outputStream.toByteArray()
    }
}
