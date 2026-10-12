package com.wallpaperapp.app.data.repository

import androidx.paging.PagingConfig
import androidx.paging.PagingSource.LoadResult
import androidx.paging.testing.TestPager
import com.google.common.truth.Truth.assertThat
import com.wallpaperapp.app.data.remote.FakeWallpaperRemoteDataSource
import com.wallpaperapp.app.domain.model.DeviceType
import com.wallpaperapp.app.domain.model.Section
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

class RpcPagingSourceTest {

    private val remote = FakeWallpaperRemoteDataSource().apply { latencyMs = 0 }

    @Test
    fun `pages through a section until a short page`() = runTest {
        val pageSize = 10
        val source = RpcPagingSource(pageSize) { page, size -> remote.getSection(Section.NEW, page, size) }
        val pager = TestPager(configOf(pageSize), source)

        val first = pager.refresh() as LoadResult.Page
        assertThat(first.data).hasSize(pageSize)
        assertThat(first.prevKey).isNull()
        assertThat(first.nextKey).isEqualTo(1)

        var last = first
        while (last.nextKey != null) last = pager.append() as LoadResult.Page
        val all = pager.getPages().flatMap { it.data }

        assertThat(last.data.size).isLessThan(pageSize)
        assertThat(all.map { it.id }).containsNoDuplicates()
        assertThat(all.none { it.deviceType == DeviceType.TABLET }).isTrue() // phone sections exclude tablet-only
    }

    @Test
    fun `errors become LoadResult Error`() = runTest {
        val source = RpcPagingSource(10) { _, _ -> throw IOException("offline") }
        val result = TestPager(RpcPagingSource.config, source).refresh()
        assertThat(result).isInstanceOf(LoadResult.Error::class.java)
    }

    private fun configOf(pageSize: Int) =
        PagingConfig(pageSize = pageSize, initialLoadSize = pageSize, enablePlaceholders = false)
}
