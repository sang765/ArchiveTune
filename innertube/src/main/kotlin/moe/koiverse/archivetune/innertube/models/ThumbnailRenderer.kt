package moe.koiverse.archivetune.innertube.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ThumbnailRenderer(
    @JsonNames("croppedSquareThumbnailRenderer")
    val musicThumbnailRenderer: MusicThumbnailRenderer?,
    val musicAnimatedThumbnailRenderer: MusicAnimatedThumbnailRenderer?,
    val croppedSquareThumbnailRenderer: MusicThumbnailRenderer?,
) {
    @Serializable
    data class MusicThumbnailRenderer(
        val thumbnail: Thumbnails,
        val thumbnailCrop: String?,
        val thumbnailScale: String?,
    ) {

        fun getThumbnailUrl(): String? {
            val url = thumbnail.thumbnails.lastOrNull()?.url ?: return null
            return getHighestQualityYouTubeThumbnail(url)
        }

        fun getThumbnailUrls(): List<String> {
            val url = thumbnail.thumbnails.lastOrNull()?.url ?: return emptyList()
            return getHighestQualityYouTubeThumbnailUrls(url)
        }

        private fun getHighestQualityYouTubeThumbnailUrls(url: String): List<String> {
            if (!url.contains("i.ytimg.com")) {
                return listOf(url)
            }

            val basePath = if (url.contains("/vi_webp/")) {
                url.substringBeforeLast("/vi_webp/")
            } else {
                url.substringBeforeLast("/vi/")
            }

            val isWebP = url.contains("/vi_webp/")
            val videoId = when {
                url.contains("/vi/") -> url.substringAfter("/vi/").substringBefore("/")
                url.contains("/vi_webp/") -> url.substringAfter("/vi_webp/").substringBefore("/")
                else -> return listOf(url)
            }

            val pathPrefix = if (isWebP) "/vi_webp/$videoId" else "/vi/$videoId"

            val candidates = mutableListOf<String>()
            val extensions = if (isWebP) listOf("webp") else listOf("jpg")
            val sizes = listOf("maxresdefault", "sddefault", "hqdefault", "mqdefault", "default")

            for (size in sizes) {
                for (ext in extensions) {
                    candidates.add("$basePath$pathPrefix/$size.$ext")
                }
            }

            return candidates
        }

        private fun getHighestQualityYouTubeThumbnail(url: String): String {
            return getHighestQualityYouTubeThumbnailUrls(url).firstOrNull() ?: url
        }
    }

    @Serializable
    data class MusicAnimatedThumbnailRenderer(
        val animatedThumbnail: Thumbnails,
        val backupRenderer: MusicThumbnailRenderer,
    )
}
