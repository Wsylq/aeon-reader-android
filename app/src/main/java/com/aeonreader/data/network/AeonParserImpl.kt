package com.aeonreader.data.network

import com.aeonreader.domain.Article
import com.aeonreader.domain.ArticleSummary
import com.aeonreader.domain.ContentBlock
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.jsoup.select.Elements
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

@Singleton
class AeonParserImpl @Inject constructor() : AeonParser {

    override fun parseFeedPage(html: String): Result<List<ArticleSummary>> {
        return try {
            val isRss = html.trimStart().startsWith("<?xml") || html.contains("<rss")
            val doc = if (isRss) Jsoup.parse(html, "", Parser.xmlParser()) else Jsoup.parse(html)
            val items = doc.select("item")

            if (items.isNotEmpty()) {
                return parseRssItems(items)
            }

            val cards = doc.select("a[href^=\"/essays/\"]")

            val articles = cards.mapNotNull { card ->
                val href = card.attr("href")
                val url = "https://aeon.co$href"

                val titleEl = card.selectFirst("p.font-bold.font-serif, h2, h3")
                val title = titleEl?.text()?.ifBlank { null } ?: return@mapNotNull null

                val descEl = card.selectFirst("p.mb-0.text-base.leading-normal")
                val description = descEl?.text()?.ifBlank { null }

                val authorEl = card.selectFirst("p.font-sans.text-base, p.text-sm, [class*=author]")
                val author = authorEl?.text()?.ifBlank { null }

                // fallback: use the largest non-title, non-author text paragraph in the card
                val fallbackDesc = if (description == null) {
                    card.select("p").filter { p ->
                        val t = p.text().trim()
                        t.length > 20 && t != title && (author == null || t != author)
                    }.maxByOrNull { it.text().length }?.text()?.trim()?.ifBlank { null }
                } else null

                val imgEl = card.selectFirst("img[src]")
                val heroImage = imgEl?.let { extractSrc(it) }

                val textContent = card.text()
                val wordCount = textContent.split("\\s+".toRegex()).size
                val readingTime = ceil(wordCount / 200.0).toInt().coerceAtLeast(1)

                ArticleSummary(
                    url = url,
                    title = title,
                    description = description ?: fallbackDesc,
                    author = author,
                    category = null,
                    heroImageUrl = heroImage,
                    estimatedReadingTimeMinutes = readingTime,
                    cachedAt = null
                )
            }

            return if (articles.isEmpty()) {
                Result.failure(Exception("No articles found in feed page"))
            } else {
                Result.success(articles)
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse feed page: ${e.message}", e))
        }
    }

    private fun parseRssItems(items: Elements): Result<List<ArticleSummary>> {
        val articles = items.mapNotNull { item ->
            val title = item.selectFirst("title")?.text()?.ifBlank { null } ?: return@mapNotNull null
            val link = item.selectFirst("link")?.text()?.ifBlank { null } ?: return@mapNotNull null
            val url = link

            val creatorEl = run {
                val byTag = item.selectFirst("dc\\:creator")
                if (byTag != null) byTag else item.selectFirst("creator")
            }
            val author = creatorEl?.text()?.ifBlank { null }

            val pubDateEl = item.selectFirst("pubDate")
            pubDateEl?.let { parseRssDate(it.text()) }

            val descEl = item.selectFirst("description")
            val descriptionHtml = descEl?.html() ?: ""
            val descDoc = Jsoup.parse(descriptionHtml)
            val imgEl = descDoc.selectFirst("img[src]")
            val heroImage = imgEl?.let { extractSrc(it) }

            // RSS description structure: <p><img/></p><p>description text</p><p><em>- author</em></p><p><a>Read on Aeon</a></p>
            val descParas = descDoc.select("p").filter { p ->
                val t = p.text().trim()
                t.length > 15 && p.select("img, a").isEmpty()
            }
            val snippet = descParas.firstOrNull()?.text()?.ifBlank { null }
            val textContent = "$title ${snippet ?: ""}"
            val wordCount = textContent.split("\\s+".toRegex()).size
            val readingTime = ceil(wordCount / 200.0).toInt().coerceAtLeast(1)

            ArticleSummary(
                url = url,
                title = title,
                description = snippet,
                author = author,
                category = null,
                heroImageUrl = heroImage,
                estimatedReadingTimeMinutes = readingTime,
                cachedAt = null
            )
        }

        return if (articles.isEmpty()) {
            Result.failure(Exception("No articles parsed from RSS feed"))
        } else {
            Result.success(articles)
        }
    }

    override fun parseArticle(html: String): Result<Article> {
        return try {
            val doc = Jsoup.parse(html)

            doc.select("script, style, noscript, header, footer, [id=top-portal], [id=bottom-portal]").remove()

            val titleEl = doc.selectFirst("h1") ?: return Result.failure(Exception("No h1 found"))
            val title = titleEl.text().ifBlank { return Result.failure(Exception("Empty title")) }

            val descEl = doc.selectFirst("h2.mb-6, h2[class*=max-w-120], h2[class*=text-pretty]")
            val description = descEl?.text()?.ifBlank { null }

            val bodyText = doc.text()

            val author = extractAuthor(bodyText)

            val publicationDate = extractDate(bodyText)

            val category = extractCategory(bodyText)

            val heroImg = doc.selectFirst("img[src*=images.aeonmedia]")
            val heroImage = heroImg?.let { extractSrc(it) }

            val bodyBlocks = extractBodyBlocks(doc)

            val wordCount = bodyBlocks.sumOf { block ->
                when (block) {
                    is ContentBlock.Paragraph -> block.text.split("\\s+".toRegex()).size
                    is ContentBlock.Subheading -> block.text.split("\\s+".toRegex()).size
                    is ContentBlock.BlockQuote -> block.text.split("\\s+".toRegex()).size
                    is ContentBlock.PullQuote -> block.text.split("\\s+".toRegex()).size
                    is ContentBlock.InlineImage -> 0
                }
            }

            Result.success(
                Article(
                    url = "",
                    title = title,
                    description = description,
                    author = author,
                    authorBio = null,
                    publicationDate = publicationDate,
                    category = category,
                    heroImageUrl = heroImage,
                    bodyBlocks = bodyBlocks,
                    wordCount = wordCount
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse article: ${e.message}", e))
        }
    }

    private fun extractAuthor(text: String): String? {
        val match = Regex("by\\s+(.+?)(?:\\s+[+×]|\\s+BIOS|\\s+Edited|$)").find(text)
        val name = match?.groupValues?.getOrNull(1)?.trim()
        if (name != null && name.length < 80) return name
        val simple = Regex("(?i)by\\s+([A-Z][a-z]+\\s+[A-Z][a-z]+)").find(text)
        return simple?.groupValues?.getOrNull(1)
    }

    private fun extractDate(text: String): LocalDate? {
        val patterns = listOf(
            Regex("""(\d{1,2}\s+(?:January|February|March|April|May|June|July|August|September|October|November|December)\s+\d{4})"""),
            Regex("""(\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+\d{4})"""),
            Regex("""(\d{4}-\d{2}-\d{2})""")
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val parsed = parseDateFromText(match.groupValues[1])
                if (parsed != null) return parsed
            }
        }
        return null
    }

    private fun extractCategory(text: String): String? {
        val known = listOf(
            "Philosophy", "Science", "Psychology", "Society", "Culture",
            "Technology", "History", "Politics", "Arts", "Health"
        )
        for (cat in known) {
            if (text.contains(cat, ignoreCase = true)) return cat
        }
        return null
    }

    private fun extractBodyBlocks(doc: Element): List<ContentBlock> {
        val main = doc.selectFirst("main")
        val content = if (main != null) {
            main.selectFirst("#article-content, .article-content, [id*=article-content]")
                ?: main.children().filter { it.tagName() == "div" }.maxByOrNull { it.text().length }
                ?: main
        } else {
            doc.selectFirst("article") ?: doc
        }

        val nonContentSelectors = listOf(
            "nav", "button", "form", "input", "select", "textarea",
            "[class*=social]", "[class*=share]",
            "[class*=related]", "[class*=recommended]"
        )

        fun isInsideNonContent(el: Element): Boolean {
            var p: Element? = el
            while (p != null && p !== doc) {
                for (sel in nonContentSelectors) {
                    if (p.`is`(sel)) return true
                }
                p = p.parent()
            }
            return false
        }

        val seen = mutableSetOf<Element>()
        val blocks = mutableListOf<ContentBlock>()

        val elements = content.select("p, h2, h3, h4, blockquote, figure")
        for (el in elements) {
            if (el in seen) continue
            if (isInsideNonContent(el)) continue

            val tag = el.tagName()
            val text = el.text().trim()
            val classAttr = el.className()

            when (tag) {
                "p" -> {
                    if (el.select("a[href*=syndicate], a[href*=save]").isNotEmpty()) continue
                    if (el.select("a[href^='#annotation']").isNotEmpty()) continue
                    if (text.length < 20 && el.select("img").isEmpty()) continue
                    if (classAttr.contains("pullquote")) {
                        if (text.isNotEmpty()) {
                            blocks.add(ContentBlock.PullQuote(text))
                        }
                    } else if (el.select("img").isNotEmpty()) {
                        val img = el.selectFirst("img[src]") ?: continue
                        seen.add(img)
                        blocks.add(ContentBlock.InlineImage(extractSrc(img), null))
                    } else if (text.length > 15 && !text.startsWith("by ") && !text.startsWith("By ")) {
                        blocks.add(ContentBlock.Paragraph(text))
                    }
                }
                "h2", "h3", "h4" -> {
                    if (text.isNotEmpty() && text.length < 200 &&
                        !text.contains("Save") && !text.contains("Share")) {
                        blocks.add(ContentBlock.Subheading(text))
                    }
                }
                "blockquote" -> {
                    if (text.isNotEmpty()) {
                        blocks.add(ContentBlock.BlockQuote(text))
                    }
                }
                "figure" -> {
                    val img = el.selectFirst("img[src]") ?: continue
                    val caption = el.selectFirst("figcaption, p.caption, span.caption")
                    seen.add(img)
                    blocks.add(
                        ContentBlock.InlineImage(
                            extractSrc(img),
                            caption?.text()?.ifBlank { null }
                        )
                    )
                }
            }
        }
        return blocks
    }

    override fun parseSearchResults(html: String): Result<List<ArticleSummary>> {
        return try {
            val doc = Jsoup.parse(html)
            val seen = mutableSetOf<String>()
            val articles = doc.select("a[rel=nofollow][href*=/aeon.co/essays/]")
                .mapNotNull { link ->
                    val href = link.attr("href")
                    val title = link.text().ifBlank { return@mapNotNull null }
                    val url = href
                    if (url in seen) return@mapNotNull null
                    seen.add(url)

                    val parent = link.parent()
                    val snippet = parent?.nextElementSibling()?.text()?.ifBlank { null }

                    val textContent = "$title ${snippet ?: ""}"
                    val wordCount = textContent.split("\\s+".toRegex()).size
                    val readingTime = ceil(wordCount / 200.0).toInt().coerceAtLeast(1)

                    ArticleSummary(
                        url = url,
                        title = title,
                        description = snippet,
                        author = null,
                        category = null,
                        heroImageUrl = null,
                        estimatedReadingTimeMinutes = readingTime,
                        cachedAt = null
                    )
                }

            return if (articles.isEmpty()) {
                Result.failure(Exception("No search results found"))
            } else {
                Result.success(articles)
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse search results: ${e.message}", e))
        }
    }

    override fun parseServerSearchResults(json: String): Result<List<ArticleSummary>> {
        return try {
            val root = org.json.JSONArray(json)
            val results = mutableListOf<ArticleSummary>()
            for (i in 0 until root.length()) {
                val obj = root.getJSONObject(i)
                val url = obj.optString("url", "")
                if (url.isBlank() || !url.startsWith("https://aeon.co/")) continue
                val title = obj.optString("title", "")
                if (title.isBlank()) continue
                val summary = obj.optString("summary", "") ?: ""
                val textContent = "$title $summary"
                val wordCount = textContent.split("\\s+".toRegex()).size
                val readingTime = ceil(wordCount / 200.0).toInt().coerceAtLeast(1)
                    val imageUrl = obj.optString("image_url", "")?.ifBlank { null }
                    results.add(
                        ArticleSummary(
                            url = url,
                            title = title,
                            description = summary.ifBlank { null },
                            author = null,
                            category = null,
                            heroImageUrl = imageUrl,
                            estimatedReadingTimeMinutes = readingTime,
                            cachedAt = null
                        )
                    )
            }
            if (results.isEmpty()) {
                Result.failure(Exception("No results from server"))
            } else {
                Result.success(results)
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse server search results: ${e.message}", e))
        }
    }

    override fun parseMojeekResults(html: String): Result<List<ArticleSummary>> {
        return try {
            val doc = Jsoup.parse(html)
            val seen = mutableSetOf<String>()
            val results = doc.select("li[class^=r]").mapNotNull { li ->
                val link = li.selectFirst("a.title")
                val url = link?.attr("href") ?: return@mapNotNull null
                val title = link.text().ifBlank { return@mapNotNull null }
                if (!url.startsWith("https://aeon.co/essays/")) return@mapNotNull null
                if (url in seen) return@mapNotNull null
                seen.add(url)
                val snippet = li.selectFirst("p.s")?.text()?.ifBlank { null }
                val textContent = "$title ${snippet ?: ""}"
                val wordCount = textContent.split("\\s+".toRegex()).size
                val readingTime = ceil(wordCount / 200.0).toInt().coerceAtLeast(1)
                ArticleSummary(
                    url = url,
                    title = title,
                    description = snippet,
                    author = null,
                    category = null,
                    heroImageUrl = null,
                    estimatedReadingTimeMinutes = readingTime,
                    cachedAt = null
                )
            }
            if (results.isEmpty()) {
                Result.failure(Exception("No results found"))
            } else {
                Result.success(results)
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse Mojeek results: ${e.message}", e))
        }
    }

    override fun parseCategories(html: String): Result<List<String>> {
        return try {
            val doc = Jsoup.parse(html)
            val categories = doc.select("a[href^=\"/philosophy\"], a[href^=\"/science\"], a[href^=\"/psychology\"], a[href^=\"/society\"], a[href^=\"/culture\"]")
                .mapNotNull { it.text().ifBlank { null } }
                .distinct()
                .filter { it.isNotBlank() && it.length < 30 }

            return if (categories.isEmpty()) {
                Result.success(listOf("Philosophy", "Science", "Psychology", "Society", "Culture"))
            } else {
                Result.success(categories)
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse categories: ${e.message}", e))
        }
    }

    override fun serialize(article: Article): String {
        val bodyJson = article.bodyBlocks.joinToString("|") { block ->
            when (block) {
                is ContentBlock.Paragraph -> "p:${block.text}"
                is ContentBlock.Subheading -> "h:${block.text}"
                is ContentBlock.BlockQuote -> "bq:${block.text}"
                is ContentBlock.PullQuote -> "pq:${block.text}"
                is ContentBlock.InlineImage -> "img:${block.url}|${block.caption ?: ""}"
            }
        }
        return buildString {
            appendLine("@cache_version=3")
            appendLine(escape(article.url))
            appendLine(escape(article.title))
            appendLine(escape(article.description ?: ""))
            appendLine(escape(article.author ?: ""))
            appendLine(escape(article.authorBio ?: ""))
            appendLine(escape(article.publicationDate?.toString() ?: ""))
            appendLine(escape(article.category ?: ""))
            appendLine(escape(article.heroImageUrl ?: ""))
            appendLine(article.wordCount.toString())
            append(bodyJson)
        }
    }

    override fun deserialize(json: String): Result<Article> {
        return try {
            val lines = json.split("\n")
            if (lines.size < 10) {
                return Result.failure(Exception("Invalid serialized format"))
            }
            val versionLine = lines[0]
            if (!versionLine.startsWith("@cache_version=")) {
                return Result.failure(Exception("Unknown cache format"))
            }
            val url = unescape(lines[1])
            val title = unescape(lines[2])
            val description = unescape(lines[3]).ifEmpty { null }
            val author = unescape(lines[4]).ifEmpty { null }
            val authorBio = unescape(lines[5]).ifEmpty { null }
            val pubDateStr = unescape(lines[6])
            val publicationDate = if (pubDateStr.isNotEmpty()) LocalDate.parse(pubDateStr) else null
            val category = unescape(lines[7]).ifEmpty { null }
            val heroImageUrl = unescape(lines[8]).ifEmpty { null }
            val wordCount = lines[9].toIntOrNull() ?: 0
            val bodyJson = lines.drop(10).joinToString("\n")

            val bodyBlocks = if (bodyJson.isNotEmpty()) {
                bodyJson.split("|").mapNotNull { block ->
                    val type = block.substringBefore(":")
                    val content = block.substringAfter(":")
                    when (type) {
                        "p" -> ContentBlock.Paragraph(content)
                        "h" -> ContentBlock.Subheading(content)
                        "bq" -> ContentBlock.BlockQuote(content)
                        "pq" -> ContentBlock.PullQuote(content)
                        "img" -> {
                            val parts = content.split("|", limit = 2)
                            ContentBlock.InlineImage(parts[0], parts.getOrNull(1)?.ifEmpty { null })
                        }
                        else -> null
                    }
                }
            } else {
                emptyList()
            }

            Result.success(
                Article(
                    url = url,
                    title = title,
                    description = description,
                    author = author,
                    authorBio = authorBio,
                    publicationDate = publicationDate,
                    category = category,
                    heroImageUrl = heroImageUrl,
                    bodyBlocks = bodyBlocks,
                    wordCount = wordCount
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception("Failed to deserialize article: ${e.message}", e))
        }
    }

    private fun extractSrc(img: Element): String {
        val src = img.attr("src")
        if (src.isNotBlank() && src.startsWith("http")) return src
        val srcset = img.attr("srcset")
        if (srcset.isNotBlank()) {
            val firstUrl = srcset.split(",").firstOrNull()?.trim()?.split(" ")?.firstOrNull()
            if (firstUrl != null && firstUrl.startsWith("http")) return firstUrl
        }
        val dataSrc = img.attr("data-src")
        if (dataSrc.isNotBlank() && dataSrc.startsWith("http")) return dataSrc
        return if (src.startsWith("/")) "https://aeon.co$src" else src
    }

    private fun parseRssDate(text: String): LocalDate? {
        val cleaned = text.trim()
        val formats = listOf(
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z"),
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss z")
        )
        for (format in formats) {
            try {
                val zdt = ZonedDateTime.parse(cleaned, format)
                return zdt.toLocalDate()
            } catch (_: Exception) { }
        }
        return parseDateFromText(cleaned)
    }

    private fun parseDateFromText(text: String): LocalDate? {
        val cleaned = text.trim()
        val formats = listOf(
            DateTimeFormatter.ofPattern("d MMMM yyyy"),
            DateTimeFormatter.ofPattern("d MMM yyyy"),
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MMMM d, yyyy"),
            DateTimeFormatter.ofPattern("MMM d, yyyy")
        )
        for (format in formats) {
            try {
                return LocalDate.parse(cleaned, format)
            } catch (_: DateTimeParseException) { }
        }
        return null
    }

    private fun escape(s: String): String {
        return s.replace("\\", "\\\\").replace("\n", "\\n")
    }

    private fun unescape(s: String): String {
        return s.replace("\\n", "\n").replace("\\\\", "\\")
    }
}
