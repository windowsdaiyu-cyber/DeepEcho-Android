package com.deepecho.mobile.data

/**
 * High-quality artwork URL helpers shared by all Compose artwork surfaces.
 *
 * This never performs network work itself. It only promotes known image CDN URLs to a larger
 * source and returns conservative fallbacks. Local/content/file artwork is left untouched.
 */
object ArtworkQuality {
    private val youtubeThumb = Regex(
        """https?://(?:i|img)\.ytimg\.com/(?:vi|vi_webp)/([^/]+)/[^?]+(?:\?.*)?""",
        RegexOption.IGNORE_CASE
    )
    private val googleSize = Regex("""=s\d+""", RegexOption.IGNORE_CASE)
    private val googleWidthHeight = Regex("""=w\d+-h\d+""", RegexOption.IGNORE_CASE)

    fun candidates(url: String?, dataSaver: Boolean = false): List<String> {
        val source = url?.trim().orEmpty()
        if (source.isBlank()) return emptyList()
        if (dataSaver) return listOf(source)

        val output = ArrayList<String>(4)
        val yt = youtubeThumb.find(source)
        if (yt != null) {
            val id = yt.groupValues.getOrNull(1).orEmpty()
            if (id.isNotBlank()) {
                output += "https://i.ytimg.com/vi/$id/maxresdefault.jpg"
                output += "https://i.ytimg.com/vi/$id/sddefault.jpg"
            }
        }

        val lower = source.lowercase()
        if (lower.contains("googleusercontent.com") || lower.contains("ggpht.com")) {
            val promoted = when {
                googleWidthHeight.containsMatchIn(source) ->
                    source.replace(googleWidthHeight, "=w1200-h1200")
                googleSize.containsMatchIn(source) ->
                    source.replace(googleSize, "=s1200")
                else -> source
            }
            output += promoted
        }

        output += source
        return output.distinct()
    }

    fun decodeSizePx(dataSaver: Boolean): Int = if (dataSaver) 720 else 1440
}
