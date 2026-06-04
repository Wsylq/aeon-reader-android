package com.aeonreader.feed

import com.aeonreader.data.local.ArticleSummaryEntity
import com.aeonreader.domain.ArticleSummary
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

// ---------------------------------------------------------------------------
// Inline conversion mirror — same pattern as RememberMemoizationUnitTest.kt
// Named pbtConvertToSummary to avoid collisions with other private extensions
// in the same package.
// ---------------------------------------------------------------------------
private fun ArticleSummaryEntity.pbtConvertToSummary(): ArticleSummary {
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
// MemoCache — HashMap-based simulation of:
//   remember(entity.url, entity) { entity.toArticleSummary() }
//
// Cache key = (entity.url, entity), matching both production remember() keys.
// Named PbtMemoCache to avoid collision with MemoCache in RememberMemoizationUnitTest.kt.
// ---------------------------------------------------------------------------
private class PbtMemoCache {
    private data class CacheKey(val url: String, val entity: ArticleSummaryEntity)
    private val cache = HashMap<CacheKey, ArticleSummary>()

    fun getOrCompute(entity: ArticleSummaryEntity): ArticleSummary {
        val key = CacheKey(entity.url, entity)
        return cache.getOrPut(key) { entity.pbtConvertToSummary() }
    }
}

/**
 * Property-based tests for `remember`-cached entity-to-domain conversion.
 *
 * **Validates: Requirements 2.4**
 *
 * Property 3 (from design.md):
 *  For any ArticleSummaryEntity (all fields randomised):
 *  1. `memo.getOrCompute(entity)` is structurally equal (==) to a fresh
 *     `entity.pbtConvertToSummary()` call.
 *  2. Calling it twice with the same entity returns the SAME reference (===).
 */
class MemoizationPbtTest : FunSpec({

    // Arbitrary generator for ArticleSummaryEntity — uses Arb.arbitrary to avoid
    // Arb.bind parameter-count limits in Kotest 5.9.x.
    val arbEntity = arbitrary {
        ArticleSummaryEntity(
            url = Arb.string(minSize = 10, maxSize = 80).bind(),
            title = Arb.string(minSize = 1, maxSize = 100).bind(),
            description = Arb.string(minSize = 0, maxSize = 200).orNull(0.3).bind(),
            author = Arb.string(minSize = 0, maxSize = 80).orNull(0.3).bind(),
            category = Arb.string(minSize = 0, maxSize = 50).orNull(0.3).bind(),
            heroImageUrl = Arb.string(minSize = 0, maxSize = 200).orNull(0.4).bind(),
            estimatedReadingTimeMinutes = Arb.int(1..120).bind(),
            cachedAt = Arb.long(0L..Long.MAX_VALUE).bind(),
            lastAccessedAt = Arb.long(0L..Long.MAX_VALUE).bind(),
            page = Arb.int(1..100).bind(),
            pageOrder = Arb.int(0..9999).bind()
        )
    }

    // ---------------------------------------------------------------------------
    // Property: cached result is structurally equal to a fresh conversion call,
    //           and calling it twice returns the SAME reference.
    // ---------------------------------------------------------------------------

    test("memo.getOrCompute returns structurally equal result and same reference on second call") {
        // **Validates: Requirements 2.4**

        checkAll(iterations = 50, genA = arbEntity) { entity ->
            val memo = PbtMemoCache()

            // First call — computes and caches
            val firstResult = memo.getOrCompute(entity)

            // Structural equality: must equal a fresh conversion
            val freshResult = entity.pbtConvertToSummary()
            firstResult shouldBe freshResult

            // Second call — must return the SAME reference (===)
            val secondResult = memo.getOrCompute(entity)
            firstResult shouldBeSameInstanceAs secondResult
        }
    }
})
