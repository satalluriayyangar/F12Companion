package com.f12companion.watchface

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object WatchFaceManager {

    fun convertToBin(
        context: Context,
        imagePath: String?,
        width: Int,
        height: Int,
        timePos: Int,
        timeUp: Int,
        timeDown: Int,
        color: Int
    ): ByteArray {
        return if (imagePath == null || imagePath.isEmpty()) {
            createDefaultBin(timePos, timeUp, timeDown, color)
        } else {
            createCustomBin(context, imagePath, width, height, timePos, timeUp, timeDown, color)
        }
    }

    private fun createDefaultBin(
        timePos: Int,
        timeUp: Int,
        timeDown: Int,
        color: Int
    ): ByteArray {
        val bgr = bgr565Value(color)
        val bin = ByteArray(6)
        bin[0] = (timePos and 0xFF).toByte()
        bin[1] = (timeUp and 0xFF).toByte()
        bin[2] = (timeDown and 0xFF).toByte()
        bin[3] = ((bgr shr 8) and 0xFF).toByte()
        bin[4] = (bgr and 0xFF).toByte()
        bin[5] = 1
        return bin
    }

    private fun createCustomBin(
        context: Context,
        imagePath: String,
        width: Int,
        height: Int,
        timePos: Int,
        timeUp: Int,
        timeDown: Int,
        color: Int
    ): ByteArray {
        val bitmap = decodeSampledBitmap(imagePath, width, height)
            ?: throw IllegalArgumentException("Cannot decode image: $imagePath")

        val scaled = if (bitmap.width != width || bitmap.height != height) {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        } else {
            bitmap
        }

        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)

        val pixelDataSize = width * height * 2
        val bin = ByteArray(10 + pixelDataSize)

        bin[0] = (timePos and 0xFF).toByte()
        bin[1] = (timeUp and 0xFF).toByte()
        bin[2] = (timeDown and 0xFF).toByte()

        val bgr = bgr565Value(color)
        bin[3] = ((bgr shr 8) and 0xFF).toByte()
        bin[4] = (bgr and 0xFF).toByte()
        bin[5] = 0

        bin[6] = ((width shr 8) and 0xFF).toByte()
        bin[7] = (width and 0xFF).toByte()
        bin[8] = ((height shr 8) and 0xFF).toByte()
        bin[9] = (height and 0xFF).toByte()

        var offset = 10
        for (i in pixels.indices) {
            val rgb565 = rgb888ToRgb565(pixels[i])
            bin[offset++] = ((rgb565 shr 8) and 0xFF).toByte()
            bin[offset++] = (rgb565 and 0xFF).toByte()
        }

        return bin
    }

    private fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.RGB_565
        return BitmapFactory.decodeFile(path, options)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqW: Int, reqH: Int): Int {
        var inSampleSize = 1
        if (options.outHeight > reqH || options.outWidth > reqW) {
            val halfH = options.outHeight / 2
            val halfW = options.outWidth / 2
            while (halfH / inSampleSize >= reqH && halfW / inSampleSize >= reqW) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun rgb888ToRgb565(rgb8888: Int): Int {
        return ((rgb8888 shr 19) and 0x1F) or
                (((rgb8888 shr 3) and 0x1F) shl 11) or
                (((rgb8888 shr 10) and 0x3F) shl 5)
    }

    private fun bgr565Value(color: Int): Int {
        return (((color and 0xFF) and 0xF8) shl 8) or
                ((((color shr 8) and 0xFF) and 0xFC) shl 3) or
                (((color shr 16) and 0xFF) shr 3)
    }

    fun saveBinToFile(context: Context, binData: ByteArray, fileName: String = "watchface_${System.currentTimeMillis()}.bin"): File {
        val dir = File(context.cacheDir, "watchface")
        dir.mkdirs()
        val file = File(dir, fileName)
        FileOutputStream(file).use { it.write(binData) }
        return file
    }
}
