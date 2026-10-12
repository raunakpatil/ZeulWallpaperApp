package com.wallpaperapp.app.ui.onboarding

import androidx.paging.testing.asSnapshot
import com.google.common.truth.Truth.assertThat
import com.wallpaperapp.app.data.local.UserPreferences
import com.wallpaperapp.app.data.local.WallpaperDao
import com.wallpaperapp.app.data.local.WallpaperEntity
import com.wallpaperapp.app.data.remote.FakeWallpaperRemoteDataSource
import com.wallpaperapp.app.data.repository.AppConfigRepository
import com.wallpaperapp.app.data.repository.WallpaperRepository
import com.wallpaperapp.app.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class OnboardingViewModelTest {

    private val remote = FakeWallpaperRemoteDataSource().apply { latencyMs = 0 }
    private val prefs = UserPreferences(InMemoryPreferencesDataStore())

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.repo() = WallpaperRepository(remote, NoopDao(), prefs, backgroundScope)

    private suspend fun OnboardingViewModel.await(predicate: (StylePickerState) -> Boolean) =
        withTimeout(5_000) { state.first(predicate) }

    @Test
    fun `offers the common tags and needs three picks`() = runTest {
        val vm = OnboardingViewModel(repo(), prefs, AppConfigRepository(remote, prefs, backgroundScope))
        val state = vm.await { !it.loading }
        assertThat(state.styles).isNotEmpty()
        assertThat(state.styles).containsNoDuplicates()
        state.styles.take(2).forEach(vm::toggle)
        assertThat(vm.state.value.canContinue).isFalse()
        vm.toggle(state.styles[2])
        assertThat(vm.state.value.canContinue).isTrue()
    }

    @Test
    fun `finishing saves the picks and ends onboarding`() = runTest {
        val vm = OnboardingViewModel(repo(), prefs, AppConfigRepository(remote, prefs, backgroundScope))
        val styles = vm.await { !it.loading }.styles.take(3)
        styles.forEach(vm::toggle)
        vm.finish()
        vm.await { it.done }
        assertThat(prefs.seedTags.first()).containsExactlyElementsIn(styles)
        assertThat(prefs.onboardingDone.first()).isTrue()
    }

    @Test
    fun `skipping ends onboarding without picks`() = runTest {
        val vm = OnboardingViewModel(repo(), prefs, AppConfigRepository(remote, prefs, backgroundScope))
        vm.await { !it.loading }
        vm.skip()
        vm.await { it.done }
        assertThat(prefs.onboardingDone.first()).isTrue()
        assertThat(prefs.seedTags.first()).isEmpty()
    }

    @Test
    fun `For You puts wallpapers with the picked tags first`() = runTest {
        val items = repo().forYou(setOf("ocean")).asSnapshot()
        assertThat(items.take(5).all { "ocean" in it.tags }).isTrue()
    }
}

private class NoopDao : WallpaperDao {
    override suspend fun upsert(wallpaper: WallpaperEntity) = Unit
    override suspend fun upsertAll(wallpapers: List<WallpaperEntity>) = Unit
    override suspend fun get(id: String): WallpaperEntity? = null
    override suspend fun pruneUnreferenced(olderThan: Long) = Unit
}
