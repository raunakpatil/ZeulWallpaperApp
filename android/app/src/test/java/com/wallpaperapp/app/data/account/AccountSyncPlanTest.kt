package com.wallpaperapp.app.data.account

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AccountSyncPlanTest {
    @Test fun `first sign-in pushes guest favourites and pulls account ones`() {
        val plan = planSync(localIds = setOf("a", "b"), localSynced = emptySet(), toPush = setOf("a", "b"), pendingDeletes = emptySet(), remoteIds = setOf("b", "c"))
        assertThat(plan.push).containsExactly("a", "b") // upsert ignores the duplicate b server-side
        assertThat(plan.pull).containsExactly("c")
        assertThat(plan.delete).isEmpty()
        assertThat(plan.removeLocal).isEmpty()
    }

    @Test fun `an offline un-favourite is deleted remotely and not pulled back`() {
        val plan = planSync(localIds = setOf("a"), localSynced = setOf("a"), toPush = emptySet(), pendingDeletes = setOf("x"), remoteIds = setOf("a", "x"))
        assertThat(plan.delete).containsExactly("x")
        assertThat(plan.pull).isEmpty()
    }

    @Test fun `re-favouriting after a failed delete keeps it`() {
        val plan = planSync(localIds = setOf("x"), localSynced = emptySet(), toPush = setOf("x"), pendingDeletes = setOf("x"), remoteIds = setOf("x"))
        assertThat(plan.delete).isEmpty()
        assertThat(plan.push).containsExactly("x")
    }

    @Test fun `a favourite removed on another device is removed here too`() {
        val plan = planSync(localIds = setOf("a", "b"), localSynced = setOf("a", "b"), toPush = emptySet(), pendingDeletes = emptySet(), remoteIds = setOf("a"))
        assertThat(plan.removeLocal).containsExactly("b")
    }

    @Test fun `unsynced local favourites are never removed even if the server lacks them`() {
        val plan = planSync(localIds = setOf("n"), localSynced = emptySet(), toPush = setOf("n"), pendingDeletes = emptySet(), remoteIds = emptySet())
        assertThat(plan.removeLocal).isEmpty()
        assertThat(plan.push).containsExactly("n")
    }

    @Test fun `parked items from an earlier session are pushed but never pushed and deleted at once`() {
        val plan = planSync(localIds = emptySet(), localSynced = emptySet(), toPush = setOf("p", "q"), pendingDeletes = setOf("q"), remoteIds = emptySet())
        assertThat(plan.push).containsExactly("p")
        assertThat(plan.delete).containsExactly("q")
    }

    @Test fun `already in sync does nothing`() {
        val plan = planSync(localIds = setOf("a"), localSynced = setOf("a"), toPush = emptySet(), pendingDeletes = emptySet(), remoteIds = setOf("a"))
        assertThat(plan.push).isEmpty(); assertThat(plan.pull).isEmpty(); assertThat(plan.delete).isEmpty(); assertThat(plan.removeLocal).isEmpty()
    }

    @Test fun `unlocks keep the newest time on both sides`() {
        val plan = planUnlocks(
            local = mapOf("a" to 100L, "b" to 500L),
            toPush = mapOf("b" to 500L, "c" to 50L),
            remote = mapOf("a" to 300L, "b" to 200L, "c" to 90L, "d" to 10L),
        )
        assertThat(plan.push).containsExactly("b", 500L)          // re-unlock refreshes the server; c is older than the server
        assertThat(plan.pull).containsExactly("a", 300L, "c", 90L, "d", 10L) // newer server times and new unlocks come down
    }

    @Test fun `timestamps from PostgREST parse with offsets and microseconds`() {
        assertThat(parseTimestamp("2026-10-01T12:00:00.123456+00:00")).isEqualTo(1790856000123L)
        assertThat(parseTimestamp("2026-10-01T17:30:00+05:30")).isEqualTo(1790856000000L)
        assertThat(parseTimestamp("2026-10-01T12:00:00Z")).isEqualTo(1790856000000L)
        assertThat(parseTimestamp("not a date")).isNull()
        assertThat(parseTimestamp(null)).isNull()
    }
}
