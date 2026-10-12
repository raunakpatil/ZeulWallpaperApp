package com.wallpaperapp.app.ui.home

import androidx.paging.testing.asSnapshot
import com.google.common.truth.Truth.assertThat
import com.wallpaperapp.app.ads.FakeAdsManager
import com.wallpaperapp.app.billing.FakePremiumManager
import com.wallpaperapp.app.data.local.UserPreferences
import com.wallpaperapp.app.data.local.WallpaperDao
import com.wallpaperapp.app.data.local.WallpaperEntity
import com.wallpaperapp.app.data.remote.FakeWallpaperRemoteDataSource
import com.wallpaperapp.app.data.repository.AppConfigRepository
import com.wallpaperapp.app.data.repository.WallpaperRepository
import com.wallpaperapp.app.domain.model.PopularRange
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val remote = FakeWallpaperRemoteDataSource().apply { latencyMs = 0 }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private suspend fun TestScope.viewModel(): HomeViewModel {
        val prefs = UserPreferences(InMemoryPreferencesDataStore())
        val config = AppConfigRepository(remote, prefs, backgroundScope)
        assertThat(config.refresh()).isTrue() // fail here, clearly, rather than time out later
        val repo = WallpaperRepository(remote, InMemoryWallpaperDao(), prefs, backgroundScope)
        val premium = FakePremiumManager()
        return HomeViewModel(repo, config, WallpaperOpener(PreviewSession(), repo, backgroundScope), HomeSectionRequest(), FakeAdsManager(premium)).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        }
    }

    private suspend fun HomeViewModel.awaitState(predicate: (HomeUiState) -> Boolean) =
        withTimeout(5_000) { uiState.first(predicate) }

    @Test
    fun `chips follow app_config order and skip unknown keys`() = runTest {
        remote.appConfigValues = mapOf(
            "home_sections" to JsonArray(listOf("new", "bogus", "popular", "curated").map(::JsonPrimitive)),
        )
        val state = viewModel().awaitState { it.chips.size == 3 }
        assertThat(state.chips).containsExactly(HomeChip.NEW, HomeChip.POPULAR, HomeChip.CURATED).inOrder()
        assertThat(state.selected).isEqualTo(HomeChip.NEW)
        assertThat(state.showHeader).isTrue()
    }

    @Test
    fun `tablets open on the Tablet chip until the user picks one`() = runTest {
        val vm = viewModel()
        vm.setPreferTablet(true)
        vm.awaitState { it.selected == HomeChip.TABLET }

        vm.select(HomeChip.NEW)
        val state = vm.awaitState { it.selected == HomeChip.NEW }
        assertThat(state.showHeader).isFalse()
    }

    @Test
    fun `popular chip pages by popularity without duplicates`() = runTest {
        val vm = viewModel()
        vm.select(HomeChip.POPULAR)
        vm.selectPopularRange(PopularRange.ALL)
        vm.awaitState { it.selected == HomeChip.POPULAR && it.popularRange == PopularRange.ALL }

        val items = vm.wallpapers.asSnapshot { scrollTo(index = 60) }
        assertThat(items.size).isAtLeast(60)
        assertThat(items.map { it.id }).containsNoDuplicates()
        assertThat(items.map { it.popularityScore }).isInOrder(Comparator.reverseOrder<Double>())
    }
}

private class InMemoryWallpaperDao : WallpaperDao {
    private val rows = mutableMapOf<String, WallpaperEntity>()
    override suspend fun upsert(wallpaper: WallpaperEntity) { rows[wallpaper.id] = wallpaper }
    override suspend fun upsertAll(wallpapers: List<WallpaperEntity>) = wallpapers.forEach { upsert(it) }
    override suspend fun get(id: String) = rows[id]
    override suspend fun pruneUnreferenced(olderThan: Long) = Unit
}
