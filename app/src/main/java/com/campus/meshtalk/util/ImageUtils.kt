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
     */
    suspend fun processAndSaveAvatar(
        context: Context,
        sourceUri: Uri,
        oldAvatarPath: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val contentResolver = context.contentResolver

            // 1. Read EXIF orientation
            var orientation = ExifInterface.ORIENTATION_NORMAL
            try {
                contentResolver.openInputStream(sourceUri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                }
            } catch (_: Exception) { }

            // 2. Decode bitmap bounds
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: throw IllegalArgumentException("Cannot open image stream")

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                throw IllegalArgumentException("Invalid or corrupted image")
            }

            // 3. Calculate sample size to avoid OOM on large images
            val minSide = min(options.outWidth, options.outHeight)
            var sampleSize = 1
            while (minSide / (sampleSize * 2) >= MAX_DIMENSION) {
                sampleSize *= 2
            }

            // 4. Decode actual scaled bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val originalBitmap = contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: throw IllegalStateException("Failed to decode image bitmap")

            // 5. Apply EXIF rotation if needed
            val rotatedBitmap = rotateBitmap(originalBitmap, orientation)
            if (rotatedBitmap != originalBitmap) {
                originalBitmap.recycle()
            }

            // 6. Center-crop to square
            val width = rotatedBitmap.width
            val height = rotatedBitmap.height
            val squareSide = min(width, height)
            val cropX = (width - squareSide) / 2
            val cropY = (height - squareSide) / 2

            val croppedBitmap = Bitmap.createBitmap(rotatedBitmap, cropX, cropY, squareSide, squareSide)
            if (croppedBitmap != rotatedBitmap) {
                rotatedBitmap.recycle()
            }

            // 7. Scale down to MAX_DIMENSION if still larger
            val finalBitmap = if (squareSide > MAX_DIMENSION) {
                val scaled = Bitmap.createScaledBitmap(croppedBitmap, MAX_DIMENSION, MAX_DIMENSION, true)
                if (scaled != croppedBitmap) {
                    croppedBitmap.recycle()
                }
                scaled
            } else {
                croppedBitmap
            }

            // 8. Prepare target avatars directory
            val avatarsDir = File(context.filesDir, "avatars").apply {
                if (!exists()) mkdirs()
            }

            // Clean up old avatar if exists
            oldAvatarPath?.let { oldPath ->
                deleteAvatarFile(oldPath)
            }

            // Save new avatar
            val newFile = File(avatarsDir, "avatar_${System.currentTimeMillis()}.jpg")
            FileOutputStream(newFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            finalBitmap.recycle()

            newFile.absolutePath
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
