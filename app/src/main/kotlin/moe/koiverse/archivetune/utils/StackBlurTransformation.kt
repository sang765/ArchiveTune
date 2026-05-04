/*
 * ArchiveTune Project Original (2026)
 * Chartreux Westia (github.com/koiverse)
 * Licensed Under GPL-3.0 | see git history for contributors
 * Don't remove this copyright holder!
 */

package moe.koiverse.archivetune.utils

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.roundToInt

/**
 * A Coil [Transformation] that applies a stack blur effect.
 * Optimized for performance by scaling down the bitmap before blurring.
 */
class StackBlurTransformation(
    private val radius: Float,
    private val maxDimension: Int = 150
) : Transformation() {

    override val cacheKey: String = "${StackBlurTransformation::class.java.name}-$radius-$maxDimension"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val width = input.width
        val height = input.height

        val scale = if (width > maxDimension || height > maxDimension) {
            val scaleW = maxDimension.toFloat() / width
            val scaleH = maxDimension.toFloat() / height
            minOf(scaleW, scaleH)
        } else {
            1.0f
        }

        val intermediate = if (scale < 1.0f) {
            val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
            val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(input, targetWidth, targetHeight, true)
        } else {
            input
        }

        // Apply stack blur.
        // If intermediate is a new bitmap (intermediate !== input), we can modify it directly.
        // Otherwise, StackBlur.blur will create a copy to avoid modifying the original input.
        return StackBlur.blur(intermediate, radius.roundToInt(), canModifySource = intermediate !== input)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StackBlurTransformation) return false
        if (radius != other.radius) return false
        if (maxDimension != other.maxDimension) return false
        return true
    }

    override fun hashCode(): Int {
        var result = radius.hashCode()
        result = 31 * result + maxDimension
        return result
    }
}
