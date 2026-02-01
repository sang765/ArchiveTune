package moe.koiverse.archivetune.ui.utils

/**
 * Get a prioritized list of YouTube thumbnail URLs with fallbacks.
 * Returns URLs in quality order: maxresdefault, sddefault, hqdefault, mqdefault, default.
 * This allows Coil/Glide to try fallbacks if higher quality versions don't exist.
 * 
 * Example input: "https://i.ytimg.com/vi/abc123/mqdefault.jpg"
 * Example output: ["https://i.ytimg.com/vi/abc123/maxresdefault.jpg",
 *                  "https://i.ytimg.com/vi/abc123/sddefault.jpg",
 *                  "https://i.ytimg.com/vi/abc123/hqdefault.jpg",
 *                  "https://i.ytimg.com/vi/abc123/mqdefault.jpg",
 *                  "https://i.ytimg.com/vi/abc123/default.jpg"]
 */
fun String.getHighestQualityYouTubeThumbnailUrls(): List<String> {
    if (!this.contains("i.ytimg.com")) {
        return listOf(this)
    }

    // Extract the base path (protocol + host + path prefix up to /vi/ or similar)
    val basePath = if (this.contains("/vi_webp/")) {
        this.substringBeforeLast("/vi_webp/")
    } else {
        this.substringBeforeLast("/vi/")
    }

    // Check if it's a vi or vi_webp path
    val isWebP = this.contains("/vi_webp/")
    val videoId = if (this.contains("/vi/")) {
        this.substringAfter("/vi/").substringBefore("/")
    } else if (this.contains("/vi_webp/")) {
        this.substringAfter("/vi_webp/").substringBefore("/")
    } else {
        // Non-vi path, just return original
        return listOf(this)
    }

    val pathPrefix = if (isWebP) "/vi_webp/$videoId" else "/vi/$videoId"

    // Generate fallback URLs in quality order
    val candidates = mutableListOf<String>()

    // Priority order: maxresdefault, sddefault, hqdefault, mqdefault, default
    val extensions = if (isWebP) listOf("webp") else listOf("jpg")
    val sizes = listOf("maxresdefault", "sddefault", "hqdefault", "mqdefault", "default")

    for (size in sizes) {
        for (ext in extensions) {
            candidates.add("$basePath$pathPrefix/$size.$ext")
        }
    }

    return candidates
}

/**
 * Legacy single-URL getter that returns the highest quality URL (may 404).
 * @deprecated Use getHighestQualityYouTubeThumbnailUrls() for fallback support
 */
fun String.getHighestQualityYouTubeThumbnail(): String {
    return getHighestQualityYouTubeThumbnailUrls().firstOrNull() ?: this
}

fun String.resize(
    width: Int? = null,
    height: Int? = null,
): String {
    if (width == null && height == null) return this
    "https://lh3\\.googleusercontent\\.com/.*=w(\\d+)-h(\\d+).*".toRegex()
        .matchEntire(this)?.groupValues?.let { group ->
        val (W, H) = group.drop(1).map { it.toInt() }
        var w = width
        var h = height
        if (w != null && h == null) h = (w / W) * H
        if (w == null && h != null) w = (h / H) * W
        return "${split("=w")[0]}=w$w-h$h-p-l90-rj"
    }
    if (this matches "https://yt3\\.ggpht\\.com/.*=s(\\d+)".toRegex()) {
        return "$this-s${width ?: height}"
    }
    return this
}
