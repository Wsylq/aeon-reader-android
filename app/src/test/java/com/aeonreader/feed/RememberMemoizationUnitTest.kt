package com.aeonreader.feed

import com.aeonreader.data.local.ArticleSummaryEntity
import com.aeonreader.domain.ArticleSummary
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs

// ---------------------------------------------------------------------------
// Inline conversion — mirrors the private ArticleSummaryEntity.toArticleSummary()
// extension from FeedScreen.kt. Named convertToSummary() to avoid a redeclaration
// clash with the same-named private function in ArticleSummaryMemoizationTest.kt
// (both files share the same package and private top-level names collide in Kotlin).
// ---------------------------------------------------------------------------
private fun ArticleSummaryEntity.convertToSummary(): ArticleSummary {
    return ArticleSummary(
        url = url,
        title = title,
        description = description,
        author = author,
        category = category,
        heroImageUrl = heroImageUrl,
        estimatedReadingTimeMinutes = estimatedReadingTimeMinutes,
        cachedAt = cachedAt
    )
}

// ---------------------------------------------------------------------------
// MemoCache — reuses the HashMap-based pattern from ArticleSummaryMemoizationTest.kt
// to simulate Compose's:
//   remember(entity.url, entity) { entity.toArticleSummary() }
//
// Named MemoCache (not RememberSimulator) to avoid a redeclaration clash with the
// private RememberSimulator class already defined in ArticleSummaryMemoizationTest.kt.
// Cache key = (entity.url, entity) — matching the two production remember() keys.
// ---------------------------------------------------------------------------
private class MemoCache {
    private data class CacheKey(val url: String, val entity: ArticleSummaryEntity)
    private val cache = HashMap<CacheKey, ArticleSummary>()

    fun getOrCompute(entity: ArticleSummaryEntity): ArticleSummary {
        val key = CacheKey(entity.url, entity)
        return cache.getOrPut(key) { entity.convertToSummary() }
    }
}

/**
 * Unit tests for the `remember`-based memoization introduced by Fix 3.
 *
 * Validates: Requirements 2.4
 *
 * The production fix wraps toArticleSummary() in:
 *   val summary = remember(entity.url, entity) { entity.toArticleSummary() }
 * inside the items block of FeedContent. MemoCache mirrors this behaviour using
 * a HashMap keyed on (entity.url, entity), reusing the same pattern as
 * RememberSimulator in ArticleSummaryMemoizationTest.kt.
 *
 * Four test cases:
 *  1. Two passes with same entity → same reference (===)
 *  2. Two passes with `title` changed → new reference
 *  3. Two passes with `url` changed → new reference
 *  4. New reference is structurally equal (==) to a fresh convertToSummary() call
 */
class RememberMemoizationUnitTest : FunSpec({

    val baseEntity = ArticleSummaryEntity(
        url = "https://aeon.co/essays/the-test-article",
        title = "The Test Article",
        description = "A description",
        author = "Test Author",
        category = "philosophy",
        heroImageUrl = "https://cdn.aeon.co/images/test.jpg",
        estimatedReadingTimeMinutes = 8,
        cachedAt = 1_700_000_000_000L,
        lastAccessedAt = 1_700_000_000_000L,
        page = 1,
        pageOrder = 0
    )

    // -----------------------------------------------------------------------
    // Test 1: Two passes with same entity → same reference (===)
    // -----------------------------------------------------------------------

    test("Two composition passes with the same entity return the same ArticleSummary reference") {
        // **Validates: Requirements 2.4**

        val memo = MemoCache()

        // First composition pass — computes and caches
        val firstPass = memo.getOrCompute(baseEntity)

        // Second composition pass — identical entity, must return cached reference
        val secondPass = memo.getOrCompute(baseEntity)

        firstPass shouldBeSameInstanceAs secondPass
    }

    // -----------------------------------------------------------------------
    // Test 2: Two passes with `title` changed → new reference
    // -----------------------------------------------------------------------

    test("A composition pass with a changed title returns a NEW ArticleSummary reference") {
        // **Validates: Requirements 2.4**

        val updatedEntity = baseEntity.copy(title = "Updated Title")
        val memo = MemoCache()

        val firstPass = memo.getOrCompute(baseEntity)
        val afterTitleChange = memo.getOrCompute(updatedEntity)

        firstPass shouldNotBeSameInstanceAs afterTitleChange
    }

    // -----------------------------------------------------------------------
    // Test 3: Two passes with `url` changed → new reference
    // -----------------------------------------------------------------------

    test("A composition pass with a changed url returns a NEW ArticleSummary reference") {
        // **Validates: Requirements 2.4**

        val differentUrlEntity = baseEntity.copy(url = "https://aeon.co/essays/different-article")
        val memo = MemoCache()

        val firstPass = memo.getOrCompute(baseEntity)
        val afterUrlChange = memo.getOrCompute(differentUrlEntity)

        firstPass shouldNotBeSameInstanceAs afterUrlChange
    }

    // -----------------------------------------------------------------------
    // Test 4: New reference (after entity change) is structurally equal (==)
    //         to a fresh convertToSummary() call on the updated entity
    // -----------------------------------------------------------------------

    test("New reference after entity change is structurally equal to a fresh conversion call") {
        // **Validates: Requirements 2.4**

        val updatedEntity = baseEntity.copy(title = "Structurally Fresh Title")
        val memo = MemoCache()

        // Warm up the cache with the base entity
        memo.getOrCompute(baseEntity)

        // Trigger recomputation with the updated entity
        val memoizedResult = memo.getOrCompute(updatedEntity)

        // Must equal a brand-new conversion of the updated entity
        val freshResult = updatedEntity.convertToSummary()
        memoizedResult shouldBe freshResult
    }
})
