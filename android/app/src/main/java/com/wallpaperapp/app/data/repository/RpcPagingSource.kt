package com.wallpaperapp.app.data.repository

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.wallpaperapp.app.domain.model.Wallpaper
import kotlin.coroutines.cancellation.CancellationException

/** Page-number paging over our RPCs (0-based `page`, fixed `page_size`). */
class RpcPagingSource(
    private val pageSize: Int,
    private val load: suspend (page: Int, pageSize: Int) -> List<Wallpaper>,
) : PagingSource<Int, Wallpaper>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Wallpaper> {
        val page = params.key ?: 0
        return try {
            val items = load(page, pageSize)
            LoadResult.Page(
                data = items,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (items.size < pageSize) null else page + 1,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Wallpaper>): Int? =
        state.anchorPosition?.let { anchor ->
            val page = state.closestPageToPosition(anchor)
            page?.prevKey?.plus(1) ?: page?.nextKey?.minus(1)
        }

    companion object {
        const val PAGE_SIZE = 30

        /** Initial load must equal one page, because keys are page numbers, not offsets. */
        val config = PagingConfig(pageSize = PAGE_SIZE, initialLoadSize = PAGE_SIZE, enablePlaceholders = false)
    }
}
