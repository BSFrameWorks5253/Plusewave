package com.music.innertube.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

fun upgradeThumbnailUrl(url: String): String {
    if (url.contains("googleusercontent.com") || url.contains("ggpht.com")) {
        if (url.contains("=w")) {
            val baseUrl = url.substringBefore("=w")
            return "$baseUrl=w800-h800-l90-rj"
        }
        if (url.contains("=s")) {
            val baseUrl = url.substringBefore("=s")
            return "$baseUrl=s800"
        }
    }
    return url
}

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
        fun getThumbnailUrl() = thumbnail.thumbnails.lastOrNull()?.url?.let(::upgradeThumbnailUrl)
    }

    @Serializable
    data class MusicAnimatedThumbnailRenderer(
        val animatedThumbnail: Thumbnails,
        val backupRenderer: MusicThumbnailRenderer,
    )
}
