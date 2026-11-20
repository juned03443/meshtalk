package com.campus.meshtalk.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.min

object ImageUtils {

    private const val MAX_DIMENSION = 512
    private const val JPEG_QUALITY = 80

    /**
     * Process image from content URI:
     * - Fix EXIF orientation
     * - Center-crop to square
     * - Downsample/scale to max 512x512
     * - Compress to JPEG 80%
     * - Save in context.filesDir/avatars/
     * - Delete old avatar file if present
     *
     * FIX: Read input stream into a byte array once, then process from memory.
     * This avoids the "stream already consumed" issue on Android 11+ with scoped
     * storage where content URI input streams may not support reset() or multiple reads.
     */
    suspend fun processAndSaveAvatar(
        context: Context,
        sourceUri: Uri,
        oldAvatarPath: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val contentResolver = context.contentResolver
            val avatarsDir = File(context.filesDir, "avatars")
            if (!avatarsDir.exists()) avatarsDir.mkdirs()

            // --- Step 1: Open input stream and read ALL data into a byte array ---
            val inputStream = contentResolver.openInputStream(sourceUri) ?: throw IllegalArgumentException("Cannot open image stream from URI: $sourceUri")

            try {
                // Read all bytes from the stream into memory
                // Use ByteArrayOutputStream for efficient reading
                val byteArrayOutputStream = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (true) {
                    bytesRead = inputStream.read(buffer)
                    if (bytesRead < 0) break
                    byteArrayOutputStream.write(buffer, 0, bytesRead)
                }
                val byteArray = byteArrayOutputStream.toByteArray()

                // --- Step 2: Read EXIF orientation from the byte array ---
                // ExifInterface can read from a byte array directly
                var orientation = ExifInterface.ORIENTATION_NORMAL
                try {
                    val exif = ExifInterface(java.io.ByteArrayInputStream(byteArray))
                    orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } catch (_: Exception) { }

                // --- Step 3: Decode bitmap from byte array with downsampling ---
                // First pass: get just the dimensions
                val options1 = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size, options1)

                if (options1.outWidth <= 0 || options1.outHeight <= 0) {
                    throw IllegalArgumentException("Invalid or corrupted image")
                }

                // Calculate sample size to avoid OOM on large images
                // Target: downsample so the smaller side is <= MAX_DIMENSION
                val minSide = min(options1.outWidth, options1.outHeight)
                var sampleSize = 1
                while (minSide / (sampleSize * 2) >= MAX_DIMENSION) {
                    sampleSize *= 2
                }
                // Ensure sampleSize is at least 1
                kotlin.math.max(1, sampleSize)
                //sampleSize = max(1, sampleSize)

                // Second pass: decode with sample size in a single operation
                val originalBitmap = BitmapFactory.decodeByteArray(
                    byteArray, 0, byteArray.size,
                    BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                ) ?: throw IllegalStateException("Failed to decode image bitmap")

                // --- Step 4: Apply EXIF rotation if needed ---
                val rotatedBitmap = rotateBitmap(originalBitmap, orientation)
                if (rotatedBitmap != originalBitmap) {
                    originalBitmap.recycle()
                }

                // --- Step 5: Center-crop to square ---
                val width = rotatedBitmap.width
                val height = rotatedBitmap.height
                val squareSide = min(width, height)
                val cropX = (width - squareSide) / 2
                val cropY = (height - squareSide) / 2

                val croppedBitmap = Bitmap.createBitmap(rotatedBitmap, cropX, cropY, squareSide, squareSide)
                if (croppedBitmap != rotatedBitmap) {
                    rotatedBitmap.recycle()
                }

                // --- Step 6: Scale down to MAX_DIMENSION if still larger ---
                val finalBitmap = if (squareSide > MAX_DIMENSION) {
                    val scaled = Bitmap.createScaledBitmap(croppedBitmap, MAX_DIMENSION, MAX_DIMENSION, true)
                    if (scaled != croppedBitmap) {
                        croppedBitmap.recycle()
                    }
                    scaled
                } else {
                    croppedBitmap
                }

                // Clean up old avatar if exists
                oldAvatarPath?.let { oldPath ->
                    deleteAvatarFile(oldPath)
                }

                // Save new avatar as JPEG
                val newFile = File(avatarsDir, "avatar_${System.currentTimeMillis()}.jpg")
                FileOutputStream(newFile).use { out ->
                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
                finalBitmap.recycle()

                newFile.absolutePath
            } finally {
                inputStream.close()
            }
        }
    }

    fun deleteAvatarFile(filePath: String?) {
        if (filePath.isNullOrBlank()) return
        try {
            val file = File(filePath)
            if (file.exists() && file.isFile) {
                file.delete()
            }
        } catch (_: Exception) { }
    }

    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
