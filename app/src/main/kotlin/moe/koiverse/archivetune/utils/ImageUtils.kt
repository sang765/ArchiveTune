package moe.koiverse.archivetune.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
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
        val launcher = ActivityResultContracts.PickVisualMedia { uri ->
            onImageSelected(uri)
        }
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Launcher is ready to use
            }
        }
        return launcher
    }

    /**
     * Creates an image cropper launcher using the system ACTION_CROP intent.
     * Crops to 1:1 aspect ratio.
     */
    fun createImageCropperLauncher(
        lifecycleOwner: LifecycleOwner,
        onCropComplete: (Uri?) -> Unit,
        onCropCancelled: () -> Unit,
    ): ActivityResultLauncher<Uri> {
        return ActivityResultContracts.GetContent().let { getContentLauncher ->
            ActivityResultContracts.StartActivityForResult().let { activityResultLauncher ->
                object : ActivityResultLauncher<Uri>() {
                    override fun launch(input: Uri) {
                        val cropIntent = createCropIntent(input)
                        activityResultLauncher.launch(cropIntent)
                    }

                    override fun getContract() = activityResultLauncher.contract
                }
            }
        }
    }

    /**
     * Creates an Android Intent for cropping images to 1:1 aspect ratio.
     */
    private fun createCropIntent(sourceUri: Uri): android.content.Intent {
        val cropIntent = android.content.Intent(android.content.Intent.ACTION_CROP).apply {
            setDataAndType(sourceUri, "image/*")
            putExtra(android.content.Intent.EXTRA_SCALE, true)
            putExtra(android.content.Intent.EXTRA_CROP, true)
            // Force 1:1 aspect ratio
            putExtra("aspectX", 1)
            putExtra("aspectY", 1)
            // Output size
            putExtra("outputX", MAX_IMAGE_SIZE)
            putExtra("outputY", MAX_IMAGE_SIZE)
            // Return data in intent
            putExtra("return-data", true)
        }
        return cropIntent
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
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // Compress and save
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            bitmap.recycle()

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
                val outputStream = java.io.ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
                bitmap.recycle()
                outputStream.toByteArray()
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
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // Scale down if needed
            val scaledBitmap = scaleBitmap(bitmap, MAX_IMAGE_SIZE)

            val outputStream = java.io.ByteArrayOutputStream()
            var quality = JPEG_QUALITY

            // Compress until we meet the size requirement
            do {
                outputStream.reset()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                quality -= 10
            } while (outputStream.size() > maxSizeBytes && quality > 10)

            scaledBitmap.recycle()
            bitmap.recycle()

            outputStream.toByteArray()
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
