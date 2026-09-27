package com.aeonreader.data.network

import com.aeonreader.domain.ArticleSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aeon publishes one RSS document per section and every one of them is always
 * reachable at `https://aeon.co/<slug>/feed.rss`.
 *
 * The HTML `?page=N` listing used to be the way to walk back through the
 * archive, but it now ignores the `page` parameter and serves page 1 forever
 * (verified: `?page=1`, `?page=2`, `?page=3` and bare `/essays` are byte
 * identical). The section feeds are therefore the only reliable way to reach
 * more than the newest 20 essays.
 */
object AeonSections {

    const val ESSAYS_SLUG = "essays"

    /** Section slug paired with the label the UI shows. */
    val text: List<Pair<String, String>> = listOf(
        "philosophy" to "Philosophy",
        "psychology" to "Psychology",
        "science" to "Science",
        "society" to "Society",
        "culture" to "Culture"
    )

    fun feedUrl(slug: String): String = "https://aeon.co/$slug/feed.rss"

    fun sectionPageUrl(slug: String): String = "https://aeon.co/$slug"

    /**
     * Section pages list their subtopics as `<a href="/<section>/<slug>">`. Two of
     * those links are not feeds: "popular" is a ranking view and "feed.rss" is
     * the section feed itself.
     */
    private val NON_SUBTOPIC_SLUGS = setOf("popular", "feed.rss", "rss", "xml")

    /**
     * Pulls the subtopic paths out of a section page, e.g. `/philosophy/ethics`.
     * Each of these has its own feed at `<path>/feed.rss`, which is what makes a
     * real archive index possible: the five section feeds only ever carry the
     * newest 20 essays each, but the ~114 subtopic feeds together reach ~1200.
     */
    fun subtopicsOf(sectionSlug: String, html: String): List<String> {
        val prefix = "/$sectionSlug/"
        val found = LinkedHashSet<String>()
        SUBTOPIC_LINK.findAll(html).forEach { match ->
            val href = match.groupValues[1]
            if (!href.startsWith(prefix)) return@forEach
            val slug = href.removePrefix(prefix)
            if (slug.isEmpty() || slug.contains('/')) return@forEach
            if (slug.lowercase() in NON_SUBTOPIC_SLUGS) return@forEach
            found.add(href)
        }
        return found.toList()
    }

    private val SUBTOPIC_LINK = Regex("href=\"(/[a-z0-9\\-]+/[a-z0-9\\-]+)\"", RegexOption.IGNORE_CASE)

    /**
     * Resolves a UI category label to its URL slug. The section list is parsed
     * out of link *text* ("Philosophy"), while the feeds are keyed by slug
     * ("philosophy") — mixing the two 404s.
     */
    fun slugOf(category: String): String? {
        val needle = category.trim()
        return text.firstOrNull { (slug, display) ->
            display.equals(needle, ignoreCase = true) || slug.equals(needle, ignoreCase = true)
        }?.first
    }

    fun displayOf(category: String): String {
        val needle = category.trim()
        return text.firstOrNull { (slug, display) ->
            display.equals(needle, ignoreCase = true) || slug.equals(needle, ignoreCase = true)
        }?.second ?: category
    }
}

@Singleton
class AeonScraperImpl @Inject constructor(
    private val client: OkHttpClient,
    private val parser: AeonParser
) : AeonScraper {

    private val httpClient = client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // Aeon runs on Vercel, which answers HTTP/2 with 429s. Article requests
    // are pinned to HTTP/1.1 for that reason.
    private val articleClient = client.newBuilder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun buildRequest(url: String, accept: String? = null): Request {
        val builder = Request.Builder().url(url).get()
        if (accept != null) builder.header("Accept", accept)
        return builder.build()
    }

    private suspend fun executeRequest(
        client: OkHttpClient,
        url: String,
        accept: String? = null
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val request = buildRequest(url, accept)
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Result.failure(Exception("HTTP ${response.code} for $url"))
                    } else {
                        val body = response.body?.string()
                        if (body != null) Result.success(body) else Result.failure(Exception("Empty response body"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private val feedAccept = "application/rss+xml,application/xml;q=0.9,text/xml;q=0.9,*/*;q=0.8"

    private suspend fun fetchFeedDocument(url: String): Result<List<ArticleSummary>> =
        executeRequest(httpClient, url, feedAccept).mapCatching { body ->
            parser.parseFeedPage(body).getOrThrow()
        }

    private suspend fun fetchSectionFeed(slug: String, display: String): Result<List<ArticleSummary>> =
        fetchFeedDocument(AeonSections.feedUrl(slug)).map { items ->
            items.map { it.copy(category = it.category ?: display) }
        }

    /**
     * Page 1 is the combined front page: the essays feed for ordering, plus the
     * section feeds purely to learn each article's topic so the feed cards can
     * show a category. Section-only articles are appended after the essays feed.
     */
    private suspend fun fetchCombinedFeed(): Result<List<ArticleSummary>> = withContext(Dispatchers.IO) {
        val (essaysResult, sectionResults) = coroutineScope {
            val essays = async { fetchFeedDocument(AeonSections.feedUrl(AeonSections.ESSAYS_SLUG)) }
            val sections = AeonSections.text.map { (slug, display) ->
                async { display to fetchSectionFeed(slug, display) }
            }
            essays.await() to sections.awaitAll()
        }

        val categoryByUrl = HashMap<String, String>()
        for ((display, result) in sectionResults) {
            for (item in result.getOrDefault(emptyList())) {
                categoryByUrl.putIfAbsent(item.url, display)
            }
        }

        val ordered = ArrayList<ArticleSummary>(64)
        val seen = HashSet<String>(64)
        fun add(item: ArticleSummary) {
            if (!seen.add(item.url)) return
            ordered.add(item.copy(category = item.category ?: categoryByUrl[item.url]))
        }
        essaysResult.getOrDefault(emptyList()).forEach(::add)
        for ((_, result) in sectionResults) result.getOrDefault(emptyList()).forEach(::add)

        essaysResult.exceptionOrNull()?.let { error ->
            // Section feeds alone are still a usable feed, so only give up if
            // nothing at all came back.
            if (ordered.isEmpty()) return@withContext Result.failure(error)
        }
        if (ordered.isEmpty()) {
            Result.failure(Exception("Aeon returned no feed items"))
        } else {
            Result.success(ordered)
        }
    }

    override suspend fun fetchFeed(page: Int): Result<List<ArticleSummary>> {
        return when {
            page <= 1 -> fetchCombinedFeed()
            // Remaining pages walk the section feeds for older articles.
            page - 2 < AeonSections.text.size -> {
                val (slug, display) = AeonSections.text[page - 2]
                fetchSectionFeed(slug, display)
            }
            else -> Result.success(emptyList())
        }
    }

    override suspend fun fetchCategoryFeed(category: String, page: Int): Result<List<ArticleSummary>> {
        // Section feeds are a single page deep; there is no deeper archive to page into.
        if (page > 1) return Result.success(emptyList())
        val slug = AeonSections.slugOf(category)
            ?: return Result.failure(Exception("Unknown category: $category"))
        return fetchSectionFeed(slug, AeonSections.displayOf(category))
    }

    override suspend fun fetchArticle(url: String): Result<String> {
        return executeRequest(articleClient, url)
    }

    override suspend fun fetchCategories(): Result<List<String>> {
        // Labels and slugs have to stay in step or every category tab 404s, so
        // they come from the single section registry rather than scraped text.
        return Result.success(AeonSections.text.map { it.second })
    }

    /**
     * Searches Aeon's own feeds. The previous Mojeek backend now answers every
     * request with a CAPTCHA page, and Bing/Google render results in JS, so
     * neither is usable from a serverless app. The section feeds are the only
     * index Aeon exposes that can be read without a browser.
     *
     * This only covers the newest 20 essays per section. [fetchArchiveIndex]
     * reaches ~20x further; the repository uses that as the primary index and
     * falls back to this.
     */
    override suspend fun search(query: String, page: Int): Result<List<ArticleSummary>> =
        withContext(Dispatchers.IO) {
            val terms = query.trim().lowercase().split(WHITESPACE).filter { it.isNotEmpty() }
            if (terms.isEmpty()) return@withContext Result.success(emptyList())

            val sectionResults = coroutineScope {
                val sections = AeonSections.text.map { (slug, display) ->
                    async { display to fetchSectionFeed(slug, display) }
                }
                sections.awaitAll()
            }
            val reached = sectionResults.count { it.second.isSuccess }
            if (reached == 0) {
                return@withContext Result.failure(Exception("Could not reach the Aeon feeds"))
            }

            val categoryByUrl = HashMap<String, String>()
            for ((display, result) in sectionResults) {
                for (item in result.getOrDefault(emptyList())) {
                    categoryByUrl.putIfAbsent(item.url, display)
                }
            }

            val corpus = LinkedHashMap<String, ArticleSummary>()
            for ((_, result) in sectionResults) {
                for (item in result.getOrDefault(emptyList())) {
                    corpus.putIfAbsent(item.url, item.copy(category = item.category ?: categoryByUrl[item.url]))
                }
            }

            val matches = corpus.values
                .filter { matchesQuery(it, terms) }
                .sortedByDescending { score(it, terms) }

            Result.success(matches)
        }

    /**
     * Walks every section page to discover its subtopics, then reads every
     * subtopic feed.
     *
     * This is the only way to search beyond the newest 20 essays per section.
     * Aeon serves no archive listing, no sitemap and no search API (its
     * `/search` is client-side rendered and disallowed in robots.txt), and every
     * third-party engine reachable without an API key either CAPTCHAs or
     * rate-limits to a single query per IP. The subtopic feeds are the one index
     * Aeon publishes that is both complete enough to be useful and readable
     * without a browser.
     */
    override suspend fun fetchArchiveIndex(
        onProgress: ((done: Int, total: Int) -> Unit)?
    ): Result<List<ArticleSummary>> = withContext(Dispatchers.IO) {
        // Discover the subtopic paths (5 requests, cheap and cached with the index).
        val discovered = coroutineScope {
            AeonSections.text.map { (slug, display) ->
                async {
                    executeRequest(httpClient, AeonSections.sectionPageUrl(slug))
                        .map { body -> display to AeonSections.subtopicsOf(slug, body) }
                        .getOrDefault(display to emptyList())
                }
            }.awaitAll()
        }

        val sectionByPath = HashMap<String, String>()
        val feedPaths = LinkedHashSet<String>()
        for ((display, paths) in discovered) {
            for (path in paths) {
                sectionByPath.putIfAbsent(path, display)
                feedPaths.add(path)
            }
        }

        // The section feeds themselves are part of the corpus too, and are the
        // fallback if a section page has no discoverable subtopics.
        for ((slug, display) in AeonSections.text) {
            val path = "/$slug"
            sectionByPath.putIfAbsent(path, display)
            feedPaths.add(path)
        }

        if (feedPaths.isEmpty()) {
            return@withContext Result.failure(Exception("No Aeon feeds could be discovered"))
        }

        val paths = feedPaths.toList()
        val total = paths.size
        val corpus = LinkedHashMap<String, ArticleSummary>()
        val reached = java.util.concurrent.atomic.AtomicInteger(0)

        coroutineScope {
            // Semaphore rather than launching all ~120 at once: enough concurrency
            // to finish in seconds, not enough to look like a scraper hammering
            // the site.
            val gate = Semaphore(ARCHIVE_CONCURRENCY)
            val jobs = paths.mapNotNull { path ->
                // Every path came out of sectionByPath, but resolving it here
                // keeps the job's result non-null so it can be destructured.
                val display = sectionByPath[path] ?: return@mapNotNull null
                async(Dispatchers.IO) {
                    gate.withPermit {
                        val result = fetchFeedDocument("$BASE_URL$path/feed.rss")
                        onProgress?.invoke(reached.incrementAndGet(), total)
                        display to result
                    }
                }
            }
            for ((display, result) in jobs.awaitAll()) {
                if (!result.isSuccess) continue
                for (item in result.getOrThrow()) {
                    corpus.putIfAbsent(item.url, item.copy(category = item.category ?: display))
                }
            }
        }

        if (corpus.isEmpty()) {
            Result.failure(Exception("Aeon returned no indexable articles"))
        } else {
            Result.success(corpus.values.toList())
        }
    }

    private companion object {
        const val BASE_URL = "https://aeon.co"
        const val ARCHIVE_CONCURRENCY = 8
        val WHITESPACE = Regex("\\s+")
    }

    private fun matchesQuery(summary: ArticleSummary, terms: List<String>): Boolean {
        val title = summary.title.lowercase()
        val body = "${summary.description.orEmpty()} ${summary.author.orEmpty()}".lowercase()
        return terms.all { title.contains(it) || body.contains(it) }
    }

    private fun score(summary: ArticleSummary, terms: List<String>): Int {
        val title = summary.title.lowercase()
        val body = "${summary.description.orEmpty()} ${summary.author.orEmpty()}".lowercase()
        var total = 0
        for (term in terms) {
            if (title.contains(term)) total += 3
            if (body.contains(term)) total += 1
        }
        if (title.startsWith(terms.first())) total += 2
        return total
    }

    override suspend fun searchViaServer(query: String): Result<List<ArticleSummary>> {
        // The app is serverless by design; there is no companion service to call.
        return Result.failure(
            UnsupportedOperationException("This build has no companion server; use search() instead")
        )
    }
}
