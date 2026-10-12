package com.wallpaperapp.app.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.paging.testing.asSnapshot
import com.google.common.truth.Truth.assertThat
import com.wallpaperapp.app.data.local.UserPreferences
import com.wallpaperapp.app.data.local.WallpaperDao
import com.wallpaperapp.app.data.local.WallpaperEntity
import com.wallpaperapp.app.data.remote.FakeWallpaperRemoteDataSource
import com.wallpaperapp.app.data.repository.AppConfigRepository
import com.wallpaperapp.app.data.repository.WallpaperRepository
import com.wallpaperapp.app.domain.model.SearchSuggestion
import com.wallpaperapp.app.testing.InMemoryPreferencesDataStore
import com.wallpaperapp.app.ui.preview.PreviewSession
import com.wallpaperapp.app.ui.preview.WallpaperOpener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val remote = FakeWallpaperRemoteDataSource().apply { latencyMs = 0 }
    private lateinit var prefs: UserPreferences

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    // Route args as Navigation would put them in the SavedStateHandle.
    private fun TestScope.viewModel(query: String? = null, category: String? = null, color: String? = null): SearchViewModel {
        prefs = UserPreferences(InMemoryPreferencesDataStore())
        val repo = WallpaperRepository(remote, NoopWallpaperDao(), prefs, backgroundScope)
        val handle = SavedStateHandle(mapOf("query" to query, "category" to category, "color" to color))
        return SearchViewModel(
            handle, repo, prefs,
            WallpaperOpener(PreviewSession(), repo, backgroundScope),
            AppConfigRepository(remote, prefs, backgroundScope),
        ).also { vm -> backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} } }
    }

    private suspend fun SearchViewModel.await(predicate: (SearchUiState) -> Boolean) =
        withTimeout(5_000) { uiState.first(predicate) }

    @Test
    fun `opens on the start page with trending tags`() = runTest {
        val state = viewModel().await { it.trending.isNotEmpty() }
        assertThat(state.showResults).isFalse()
        assertThat(state.trending.size).isAtMost(10)
    }

    @Test
    fun `colour from Explore filters results straight away`() = runTest {
        val vm = viewModel(color = "blue")
        assertThat(vm.await { true }.showResults).isTrue()
        val items = vm.results.asSnapshot()
        assertThat(items).isNotEmpty()
        assertThat(items.all { it.colorFamily == "blue" }).isTrue()
    }

    @Test
    fun `submitting saves a recent search, newest first, without duplicates`() = runTest {
        val vm = viewModel()
        vm.submit("neon")
        vm.submit("ocean")
        vm.submit("Neon")
        val state = vm.await { it.recent.size == 2 }
        assertThat(state.recent).containsExactly("Neon", "ocean").inOrder()
        assertThat(state.submitted).isEqualTo("Neon")
    }

    @Test
    fun `category suggestion becomes a filter, not a word search`() = runTest {
        val vm = viewModel()
        vm.await { it.categories.isNotEmpty() }
        vm.onSuggestion(SearchSuggestion("Space", SearchSuggestion.Kind.CATEGORY))
        val state = vm.await { it.filters.category == "space" }
        assertThat(state.submitted).isNull()
        assertThat(vm.results.asSnapshot().all { it.categorySlug == "space" }).isTrue()
    }

    @Test
    fun `tag suggestion searches for the tag`() = runTest {
        val vm = viewModel()
        vm.onSuggestion(SearchSuggestion("neon", SearchSuggestion.Kind.TAG))
        assertThat(vm.await { it.submitted == "neon" }.text).isEqualTo("neon")
        assertThat(vm.results.asSnapshot().all { w -> w.tags.any { it.contains("neon") } || w.title.contains("Neon") }).isTrue()
    }
}

private class NoopWallpaperDao : WallpaperDao {
    override suspend fun upsert(wallpaper: WallpaperEntity) = Unit
    override suspend fun upsertAll(wallpapers: List<WallpaperEntity>) = Unit
    override suspend fun get(id: String): WallpaperEntity? = null
    override suspend fun pruneUnreferenced(olderThan: Long) = Unit
}
