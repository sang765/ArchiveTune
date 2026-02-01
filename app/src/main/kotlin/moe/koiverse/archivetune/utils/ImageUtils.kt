package moe.koiverse.archivetune.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import com.yalantis.ucrop.UCrop
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object ImageUtils {
    private const val MAX_IMAGE_SIZE = 2048
    private const val JPEG_QUALITY = 90

    /**
     * Creates an image picker launcher for selecting images.
     */
    fun createImagePickerLauncher(
        lifecycleOwner: LifecycleOwner,
        onImageSelected: (Uri?) -> Unit,
    ): ActivityResultLauncher<ActivityResultContracts.PickVisualMedia> {
        return lifecycleOwner.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            onImageSelected(uri)
        }
    }

    /**
     * Creates an image cropper launcher using uCrop library.
     * Crops to 1:1 aspect ratio.
     */
    fun createImageCropperLauncher(
        lifecycleOwner: LifecycleOwner,
        onCropComplete: (Uri?) -> Unit,
        onCropCancelled: () -> Unit,
    ): ActivityResultLauncher<Uri> {
        return lifecycleOwner.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
                val resultUri = UCrop.getOutput(result.data!!)
                onCropComplete(resultUri)
            } else if (result.resultCode == android.app.Activity.RESULT_CANCELED) {
                onCropCancelled()
            }
        }
    }

    /**
     * Creates an Android Intent for cropping images using uCrop.
     */
    fun createCropIntent(context: Context, sourceUri: Uri): android.content.Intent {
        val outputUri = Uri.fromFile(File(context.cacheDir, "cropped_${System.currentTimeMillis()}.jpg"))
        
        return UCrop.of(sourceUri, outputUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(1024, 1024)
            .getIntent(context)
    }

    /**
     * Saves an image to internal storage and returns the file path.
     */
    suspend fun saveImageToInternalStorage(
        context: Context,
        uri: Uri,
        playlistId: String,
    ): String = withContext(Dispatchers.IO) {
        val coversDir = File(context.filesDir, "playlist_covers")
        if (!coversDir.exists()) {
            coversDir.mkdirs()
        }

        val outputFile = File(coversDir, "$playlistId.jpg")

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    FileOutputStream(outputFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                    }
                    bitmap.recycle()
                } else {
                    throw Exception("Failed to decode bitmap from input stream")
                }
            }
            outputFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * Loads an image from a URI and converts it to a ByteArray for upload.
     */
    suspend fun loadImageAsByteArray(
        context: Context,
        uri: Uri,
    ): ByteArray = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    val outputStream = java.io.ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
                    bitmap.recycle()
                    outputStream.toByteArray()
                } else {
                    ByteArray(0)
                }
            } ?: ByteArray(0)
        } catch (e: Exception) {
            e.printStackTrace()
            ByteArray(0)
        }
    }

    /**
     * Deletes a playlist cover image from internal storage.
     */
    suspend fun deletePlaylistCover(
        context: Context,
        playlistId: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val coversDir = File(context.filesDir, "playlist_covers")
        val coverFile = File(coversDir, "$playlistId.jpg")
        if (coverFile.exists()) {
            coverFile.delete()
        } else {
            false
        }
    }

    /**
     * Gets the file path for a playlist cover if it exists.
     */
    fun getPlaylistCoverPath(
        context: Context,
        playlistId: String,
    ): String? {
        val coversDir = File(context.filesDir, "playlist_covers")
        val coverFile = File(coversDir, "$playlistId.jpg")
        return if (coverFile.exists()) coverFile.absolutePath else null
    }

    /**
     * Compresses an image to meet YouTube's file size requirements (max 2MB).
     */
    suspend fun compressImageForUpload(
        context: Context,
        uri: Uri,
        maxSizeBytes: Int = 2 * 1024 * 1024,
    ): ByteArray = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap == null) {
                    return@withContext ByteArray(0)
                }

                // Scale down if needed
                val scaledBitmap = scaleBitmap(bitmap, MAX_IMAGE_SIZE)
                val needsRecycleOriginal = scaledBitmap !== bitmap

                val outputStream = java.io.ByteArrayOutputStream()
                var quality = JPEG_QUALITY

                // Compress until we meet the size requirement
                do {
                    outputStream.reset()
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                    quality -= 10
                } while (outputStream.size() > maxSizeBytes && quality > 10)

                scaledBitmap.recycle()
                if (needsRecycleOriginal) {
                    bitmap.recycle()
                }

                outputStream.toByteArray()
            } ?: ByteArray(0)
        } catch (e: Exception) {
            e.printStackTrace()
            ByteArray(0)
        }
    }

    /**
     * Scales a bitmap to fit within maxSize while maintaining aspect ratio.
     */
    private fun scaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int

        if (width > height) {
            newWidth = maxSize
            newHeight = (maxSize / ratio).toInt()
        } else {
            newHeight = maxSize
            newWidth = (maxSize * ratio).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
