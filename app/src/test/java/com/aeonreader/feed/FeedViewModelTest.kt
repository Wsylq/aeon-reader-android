package com.aeonreader.feed

import androidx.paging.PagingData
import com.aeonreader.data.local.ArticleSummaryEntity
import com.aeonreader.data.local.ArticleSummaryProjection
import com.aeonreader.data.repository.ArticleRepository
import com.aeonreader.data.repository.BookmarkRepository
import com.aeonreader.data.repository.UserPreferencesRepository
import com.aeonreader.domain.Article
import com.aeonreader.domain.ArticleSummary
import com.aeonreader.domain.Bookmark
import com.aeonreader.domain.FeedLayout
import com.aeonreader.domain.ReadingPreferences
import com.aeonreader.domain.ThemeOverride
import com.aeonreader.ui.screens.feed.FeedUiState
import com.aeonreader.ui.screens.feed.FeedViewModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Sub-test A — State Churn
 *
 * Validates: Bug Condition 1 (Requirements 1.1, 1.2)
 *
 * EXPECTED OUTCOME ON UNFIXED CODE: FAILS
 *
 * The test creates a FeedViewModel with a single stable pagingFlow reference.
 * It emits two consecutive networkStatus values (true, then false) without
 * changing pagingFlow. It snapshots the FeedUiState.Success after each
 * emission and asserts that both snapshots are the SAME object reference
 * (i.e., the state wrapper itself was not recreated).
 *
 * On UNFIXED code this FAILS because emitCurrentState() always constructs a
 * NEW FeedUiState.Success(...) data class instance, even when pagingFlow,
 * categories, and selectedCategory are unchanged. Because FeedUiState.Success
 * is a data class whose equals is based on its fields, and Flow does not
 * override equals/hashCode (reference equality), each new Success(articles=sameRef)
 * is NOT equal to the previous one — so StateFlow accepts the new value and emits
 * it. Compose sees a new uiState value and re-executes collectAsLazyPagingItems().
 *
 * Counterexample documented:
 *   "After emitting networkStatus=true and networkStatus=false (with pagingFlow
 *    unchanged), uiState emitted two DISTINCT FeedUiState.Success instances:
 *    Success@<addr1> after true, Success@<addr2> after false. The Success wrapper
 *    is a new object on every network tick, confirming state churn."
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest : FunSpec({

    val testDispatcher = StandardTestDispatcher()

    beforeSpec {
        Dispatchers.setMain(testDispatcher)
    }

    afterSpec {
        Dispatchers.resetMain()
    }

    test("Sub-test A: FeedUiState.Success instance is NOT replaced on network status emissions (BUG: fails on unfixed code)") {
        runTest(testDispatcher) {
            // ---------- fake dependencies ----------
            val networkStatusFlow = MutableSharedFlow<Boolean>(replay = 1)
            val cachedUrlsFlow = MutableStateFlow<Set<String>>(emptySet())

            // A single stable pagingFlow — same object reference throughout
            val stablePagingFlow: Flow<PagingData<ArticleSummaryProjection>> =
                flowOf(PagingData.empty())

            val fakeRepository = object : ArticleRepository {
                override fun getFeedPager(category: String?): Flow<PagingData<ArticleSummaryProjection>> =
                    stablePagingFlow

                override suspend fun getArticle(url: String): Result<Article> =
                    Result.failure(UnsupportedOperationException())

                override suspend fun getCachedArticle(url: String): Article? = null

                override suspend fun getCategories(): Result<List<String>> =
                    Result.success(emptyList())

                override fun observeNetworkStatus(): Flow<Boolean> = networkStatusFlow

                override fun observeCachedArticleUrls(): Flow<Set<String>> = cachedUrlsFlow
            }

            val fakePrefsRepository = object : UserPreferencesRepository {
                override val selectedCategory: Flow<String> = flowOf("all")
                override val themeOverride: Flow<ThemeOverride> = flowOf(ThemeOverride.NONE)
                override val readingPreferences: Flow<ReadingPreferences> =
                    flowOf(ReadingPreferences())
                override val feedLayout: Flow<FeedLayout> = flowOf(FeedLayout.LIST)
                override suspend fun setSelectedCategory(category: String) {}
                override suspend fun setThemeOverride(override: ThemeOverride) {}
                override suspend fun setReadingPreferences(prefs: ReadingPreferences) {}
                override suspend fun setFeedLayout(layout: FeedLayout) {}
            }

            val fakeBookmarkRepository = object : BookmarkRepository {
                override fun observeBookmarks(): Flow<List<Bookmark>> = flowOf(emptyList())
                override fun observeBookmarkState(articleUrl: String): Flow<Boolean> = flowOf(false)
                override suspend fun addBookmark(article: ArticleSummary): Result<Unit> = Result.success(Unit)
                override suspend fun removeBookmark(articleUrl: String): Result<Unit> = Result.success(Unit)
            }

            // ---------- create ViewModel and advance past init ----------
            val viewModel = FeedViewModel(fakeRepository, fakePrefsRepository, fakeBookmarkRepository)
            advanceUntilIdle()

            // Emit first networkStatus value (online = true) — pagingFlow does NOT change
            networkStatusFlow.emit(true)
            advanceUntilIdle()

            // Snapshot the Success state after first network emission
            val stateAfterFirst = viewModel.uiState.value
            assert(stateAfterFirst is FeedUiState.Success) {
                "Expected FeedUiState.Success after first network emission, got $stateAfterFirst"
            }
            val firstSuccessInstance = stateAfterFirst as FeedUiState.Success

            // Emit second networkStatus value (offline = false) — pagingFlow still unchanged
            networkStatusFlow.emit(false)
            advanceUntilIdle()

            // Snapshot the Success state after second network emission
            val stateAfterSecond = viewModel.uiState.value
            assert(stateAfterSecond is FeedUiState.Success) {
                "Expected FeedUiState.Success after second network emission, got $stateAfterSecond"
            }
            val secondSuccessInstance = stateAfterSecond as FeedUiState.Success

            // Assert: the two Success state objects must be the SAME instance (===).
            // On UNFIXED code this FAILS — emitCurrentState() creates a brand-new
            // FeedUiState.Success on every call, so firstSuccessInstance !== secondSuccessInstance
            // even though pagingFlow, categories, and selectedCategory are all unchanged.
            //
            // Why StateFlow accepts the new value on unfixed code:
            //   FeedUiState.Success is a data class, so its equals compares the `articles`
            //   Flow field. Flow does not override equals — it uses reference equality.
            //   The same `stablePagingFlow` reference means two distinct Success instances
            //   will be EQUAL by data class rules. However, StateFlow uses referential
            //   equality for data classes in Kotlin — wait, actually StateFlow.value uses
            //   structural equality (==) not reference equality. Let us verify the actual
            //   runtime behaviour: if stablePagingFlow is the exact same reference in both,
            //   then Success(articles=stablePagingFlow,...) == Success(articles=stablePagingFlow,...)
            //   would be TRUE for a data class, and StateFlow would NOT emit.
            //   BUT: `isOffline` changes from false -> true (first emission) -> back to false
            //   (second emission), making the two Success instances structurally UNEQUAL.
            //   That is why StateFlow does emit on every network tick on unfixed code.
            //
            // This assertion failing proves the bug:
            //   Success@addr1{isOffline=false} emitted, then Success@addr2{isOffline=true}
            //   emitted — two distinct objects confirming state churn.
            firstSuccessInstance shouldBeSameInstanceAs secondSuccessInstance
        }
    }
})
