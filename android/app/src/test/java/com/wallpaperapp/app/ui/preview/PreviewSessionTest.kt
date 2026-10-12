package com.wallpaperapp.app.ui.preview

import com.google.common.truth.Truth.assertThat
import com.wallpaperapp.app.data.remote.FakeWallpaperRemoteDataSource
import org.junit.Test

class PreviewSessionTest {

    private val all = FakeWallpaperRemoteDataSource().wallpapers

    @Test
    fun `keeps the grid order so swiping follows the grid`() {
        val session = PreviewSession()
        val list = all.take(20)
        session.open(list, list[5])
        assertThat(session.listFor(list[5].id)).isEqualTo(list)
    }

    @Test
    fun `unknown id means the preview must load it by itself`() {
        val session = PreviewSession()
        session.open(all.take(5), all[0])
        assertThat(session.listFor("not-in-session")).isNull()
    }

    @Test
    fun `selection missing from the list opens just that wallpaper`() {
        val session = PreviewSession()
        session.open(all.take(5), all[50])
        assertThat(session.listFor(all[50].id)).containsExactly(all[50])
    }

    @Test
    fun `long lists are capped around the selection`() {
        val long = List(4) { all }.flatten().mapIndexed { i, w -> w.copy(id = "id-$i") } // 480 items
        val selected = long[400]
        val session = PreviewSession()
        session.open(long, selected)
        val list = session.listFor(selected.id)!!
        assertThat(list).hasSize(PreviewSession.MAX_ITEMS)
        assertThat(list).contains(selected)
        assertThat(list.first().id).isEqualTo("id-180") // window shifted back so it still holds 300
    }

    @Test
    fun `overlay cycles none, lock, home`() {
        assertThat(OverlayMode.NONE.next()).isEqualTo(OverlayMode.LOCK)
        assertThat(OverlayMode.LOCK.next()).isEqualTo(OverlayMode.HOME)
        assertThat(OverlayMode.HOME.next()).isEqualTo(OverlayMode.NONE)
    }

    @Test
    fun `file sizes read naturally`() {
        assertThat(formatSize(812)).isEqualTo("812 KB")
        assertThat(formatSize(2662)).isEqualTo("2.6 MB")
    }
}
