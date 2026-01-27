package moe.koiverse.archivetune.ui.component

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.core.text.HtmlCompat

@Immutable
data class RichTextLink(
    val start: Int,
    val end: Int,
    val url: String,
)

@Composable
fun RichTextContent(
    content: String?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    linkStyle: SpanStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
    ),
) {
    if (content.isNullOrBlank()) {
        BasicText(
            text = "—",
            modifier = modifier,
            style = style.copy(color = LocalContentColor.current.copy(alpha = 0.7f)),
        )
        return
    }

    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    val parsed = remember(content) {
        runCatching { toAnnotatedString(content) }.getOrNull()
    }

    if (parsed == null) {
        BasicText(text = content, modifier = modifier, style = style)
        return
    }

    val annotatedText = remember(parsed, style, linkStyle) {
        buildAnnotatedString {
            append(parsed.text)

            if (parsed.links.isNotEmpty()) {
                parsed.links.forEach { link ->
                    if (link.start in 0..link.end && link.end <= length) {
                        addStringAnnotation(
                            tag = "URL",
                            annotation = link.url,
                            start = link.start,
                            end = link.end,
                        )
                        addStyle(linkStyle, link.start, link.end)
                    }
                }
            }
        }
    }

    ClickableText(
        text = annotatedText,
        modifier = modifier,
        style = style.copy(color = LocalContentColor.current),
        onClick = { offset ->
            val url = annotatedText
                .getStringAnnotations("URL", start = offset, end = offset)
                .firstOrNull()
                ?.item
                ?: return@ClickableText

            runCatching { uriHandler.openUri(url) }
                .recoverCatching {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse(url)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
        },
    )
}

private data class ParsedRichText(
    val text: String,
    val links: List<RichTextLink>,
)

private fun toAnnotatedString(raw: String): ParsedRichText {
    val markdownLike = raw.trim()
    val html = markdownToBasicHtml(markdownLike)

    val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)

    val plainText = spanned.toString()
    val links = spanned.getLinks()

    return ParsedRichText(
        text = plainText,
        links = links.mapNotNull { link ->
            val start = plainText.indexOf(link.text)
            if (start == -1) return@mapNotNull null
            RichTextLink(start = start, end = start + link.text.length, url = link.url)
        },
    )
}

private data class DetectedLink(val text: String, val url: String)

private fun markdownToBasicHtml(raw: String): String {
    val lines = raw.replace("\r\n", "\n").split("\n")
    val sb = StringBuilder()

    var inCodeBlock = false
    var inUl = false
    var inOl = false

    fun closeLists() {
        if (inUl) {
            sb.append("</ul>")
            inUl = false
        }
        if (inOl) {
            sb.append("</ol>")
            inOl = false
        }
    }

    for (line in lines) {
        val trimmed = line.trimEnd()

        if (trimmed.startsWith("```")) {
            if (!inCodeBlock) {
                closeLists()
                inCodeBlock = true
                sb.append("<pre><code>")
            } else {
                inCodeBlock = false
                sb.append("</code></pre>")
            }
            continue
        }

        if (inCodeBlock) {
            sb.append(escapeHtml(trimmed)).append("\n")
            continue
        }

        when {
            trimmed.startsWith("### ") -> {
                closeLists()
                sb.append("<h3>").append(inlineMarkdownToHtml(trimmed.removePrefix("### "))).append("</h3>")
            }

            trimmed.startsWith("## ") -> {
                closeLists()
                sb.append("<h2>").append(inlineMarkdownToHtml(trimmed.removePrefix("## "))).append("</h2>")
            }

            trimmed.startsWith("# ") -> {
                closeLists()
                sb.append("<h1>").append(inlineMarkdownToHtml(trimmed.removePrefix("# "))).append("</h1>")
            }

            trimmed.matches(Regex("^[-*]\\s+.+")) -> {
                if (!inUl) {
                    closeLists()
                    inUl = true
                    sb.append("<ul>")
                }
                val item = trimmed.replaceFirst(Regex("^[-*]\\s+"), "")
                sb.append("<li>").append(inlineMarkdownToHtml(item)).append("</li>")
            }

            trimmed.matches(Regex("^\\d+\\.\\s+.+")) -> {
                if (!inOl) {
                    closeLists()
                    inOl = true
                    sb.append("<ol>")
                }
                val item = trimmed.replaceFirst(Regex("^\\d+\\.\\s+"), "")
                sb.append("<li>").append(inlineMarkdownToHtml(item)).append("</li>")
            }

            trimmed.isBlank() -> {
                closeLists()
                sb.append("<br/>")
            }

            else -> {
                closeLists()
                sb.append("<p>").append(inlineMarkdownToHtml(trimmed)).append("</p>")
            }
        }
    }

    if (inCodeBlock) {
        sb.append("</code></pre>")
    }
    closeLists()

    return sb.toString()
}

private fun inlineMarkdownToHtml(text: String): String {
    var s = escapeHtml(text)

    s = s.replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
    s = s.replace(Regex("(?<!\\*)\\*(?!\\s)(.+?)(?<!\\s)\\*(?!\\*)"), "<i>$1</i>")
    s = s.replace(Regex("`([^`]+)`"), "<code>$1</code>")

    s = s.replace(Regex("\\[([^\\]]+)]\\(([^)]+)\\)")) { m ->
        val label = m.groupValues[1]
        val url = m.groupValues[2]
        "<a href=\"$url\">$label</a>"
    }

    return s
}

private fun escapeHtml(text: String): String =
    text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

private fun android.text.Spanned.getLinks(): List<DetectedLink> {
    val spans = getSpans(0, length, android.text.style.URLSpan::class.java)
    return spans.map { span ->
        val start = getSpanStart(span)
        val end = getSpanEnd(span)
        DetectedLink(text = subSequence(start, end).toString(), url = span.url)
    }
