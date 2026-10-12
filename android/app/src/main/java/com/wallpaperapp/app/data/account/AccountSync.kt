package com.wallpaperapp.app.data.account

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.room.withTransaction
import com.wallpaperapp.app.billing.PremiumManager
import com.wallpaperapp.app.data.local.AppDatabase
import com.wallpaperapp.app.data.local.FavoriteEntity
import com.wallpaperapp.app.data.local.UnlockEntity
import com.wallpaperapp.app.data.local.UserPreferences
import com.wallpaperapp.app.data.local.toEntity
import com.wallpaperapp.app.data.remote.WallpaperDto
import com.wallpaperapp.app.data.remote.toDomain
import com.wallpaperapp.app.di.ApplicationScope
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** What a sign-in sync should do with favourites, given what's on the phone and what's in the account. */
internal data class SyncPlan(
    val push: Set<String>,
    val delete: Set<String>,
    val pull: Set<String>,
    /** Synced on this phone but no longer in the account: removed on another device. */
    val removeLocal: Set<String>,
)

/**
 * - push:        items for this account not yet on the server (guest favourites at first sign-in, offline toggles)
 * - delete:      removals that failed to reach the server earlier, unless re-added locally since
 * - pull:        account items missing on this phone (added on another device), except ones being deleted
 * - removeLocal: synced copies the account no longer has (removed on another device)
 */
internal fun planSync(
    localIds: Set<String>,
    localSynced: Set<String>,
    toPush: Set<String>,
    pendingDeletes: Set<String>,
    remoteIds: Set<String>,
): SyncPlan {
    val delete = pendingDeletes - localIds
    return SyncPlan(
        push = toPush - delete,
        delete = delete,
        pull = remoteIds - localIds - delete,
        removeLocal = localSynced - remoteIds - toPush,
    )
}

/** Unlocks only ever grow; the newest `unlocked_at` wins (matters in the '24h' unlock mode). */
internal data class UnlockPlan(val push: Map<String, Long>, val pull: Map<String, Long>)

internal fun planUnlocks(local: Map<String, Long>, toPush: Map<String, Long>, remote: Map<String, Long>): UnlockPlan = UnlockPlan(
    push = toPush.filter { (id, at) -> (remote[id] ?: Long.MIN_VALUE) < at },
    pull = remote.filter { (id, at) -> (local[id] ?: Long.MIN_VALUE) < at },
)

/** PostgREST returns `2026-10-01T12:00:00.123456+00:00`; Instant.parse can't read offsets before Android 14. */
internal fun parseTimestamp(value: String?): Long? =
    value?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }

/**
 * Keeps favourites, unlocks and style picks in the signed-in account (B7) and tells [PremiumManager]
 * who is signed in. Room stays the source of truth for the UI, so everything works offline and for guests.
 *
 * Ownership: the phone remembers which account its synced copies belong to (LAST_UID). Whenever that
 * account leaves (sign-out, session revoked while the app was closed, another account signing in), its
 * not-yet-uploaded changes are parked under its own id and all its rows are removed from the phone, so
 * nothing leaks into a guest session or another account. Guest items do merge into the first account
 * that signs in (that's the point of signing in).
 */
@Singleton
class AccountSync @Inject constructor(
    private val auth: AuthRepository,
    private val supabase: Provider<SupabaseClient>,
    private val db: AppDatabase,
    private val prefs: UserPreferences,
    private val dataStore: DataStore<Preferences>,
    private val premium: PremiumManager,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)
    private val lock = Mutex()
    private val premiumResolved = CompletableDeferred<Unit>()

    /** Call once at app start (MainActivity). Safe to call again. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        val uids = auth.state
            .filter { it != AuthState.Loading }
            .map { (it as? AuthState.SignedIn)?.user?.id }
            .distinctUntilChanged()
        // RevenueCat identity runs on its own, so a slow network never delays the account clean-up below.
        scope.launch {
            uids.collect { uid ->
                runCatching { premium.initialize(uid) }.onFailure { Log.w(TAG, "premium init failed", it) }
                premiumResolved.complete(Unit)
            }
        }
        scope.launch {
            uids.collect { uid ->
                lock.withLock {
                    val last = dataStore.data.first()[LAST_UID]
                    if (last != null && last != uid) leaveAccount(last)
                    if (uid != null) {
                        dataStore.edit { it[LAST_UID] = uid }
                        fullSync(uid)
                    }
                }
            }
        }
        // Style picks: send whenever they change while signed in (and once after each sign-in).
        scope.launch {
            combine(auth.state.map { (it as? AuthState.SignedIn)?.user?.id }, prefs.seedTags) { uid, tags -> uid to tags }
                .distinctUntilChanged()
                .collect { (uid, tags) -> if (uid != null && tags.isNotEmpty()) syncStyles(uid, tags) }
        }
    }

    /** Ads wait for this (briefly) so a premium user never gets an ad before RevenueCat answers. */
    suspend fun awaitPremiumIdentity() = premiumResolved.await()

    // ------------------------------------------------------------------ write-through

    /** Called by FavoritesRepository after every toggle. */
    fun onFavoriteChanged(wallpaperId: String, nowFavorite: Boolean) {
        scope.launch {
            // At startup the saved session may still be loading: wait instead of treating it as a guest toggle.
            val uid = (auth.state.first { it != AuthState.Loading } as? AuthState.SignedIn)?.user?.id ?: return@launch
            // Recorded before taking the lock, so a sync running right now can't pull the favourite back.
            dataStore.edit {
                if (nowFavorite) it.removeFrom(pendingDeletesKey(uid), wallpaperId) else it.addTo(pendingDeletesKey(uid), wallpaperId)
            }
            lock.withLock {
                if (auth.currentUser?.id != uid) return@withLock // account changed meanwhile; the parked state handles it
                try {
                    if (nowFavorite) {
                        if (!wallpaperExists(wallpaperId)) return@withLock // unpublished/deleted: stays unsynced, retried
                        favorites().upsert(FavoriteRow(uid, wallpaperId)) { ignoreDuplicates = true }
                        db.favoriteDao().markSynced(listOf(wallpaperId))
                    } else {
                        favorites().delete { filter { eq("user_id", uid); eq("wallpaper_id", wallpaperId) } }
                        dataStore.edit { it.removeFrom(pendingDeletesKey(uid), wallpaperId) }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.w(TAG, "favourite sync failed, will retry at next sync: ${e.message}")
                }
            }
        }
    }

    /** Called by UnlockRepository after a rewarded unlock. */
    fun onUnlocked(wallpaperId: String, unlockedAt: Long) {
        scope.launch {
            val uid = (auth.state.first { it != AuthState.Loading } as? AuthState.SignedIn)?.user?.id ?: return@launch
            lock.withLock {
                if (auth.currentUser?.id != uid) return@withLock
                runCatching {
                    // Merge (not ignore) so a re-unlock refreshes unlocked_at for the '24h' mode.
                    unlocks().upsert(UnlockRow(uid, wallpaperId, Instant.ofEpochMilli(unlockedAt).toString()))
                    db.unlockDao().markSynced(listOf(wallpaperId))
                }.onFailure { Log.w(TAG, "unlock sync failed, will retry at next sync: ${it.message}") }
            }
        }
    }

    // ------------------------------------------------------------------ account deletion

    /**
     * Deletes the account on the server and everything of it on this phone (favourites, unlocks, style
     * picks, parked changes). Runs in the app scope, so leaving the screen can't interrupt it halfway.
     */
    suspend fun deleteAccount(): AuthResult {
        val uid = auth.currentUser?.id ?: return AuthResult.Error(auth.notSignedInMessage())
        val result = auth.deleteAccount()
        if (result == AuthResult.Success) {
            lock.withLock {
                val last = dataStore.data.first()[LAST_UID]
                if (last == null || last == uid) {
                    db.withTransaction { db.favoriteDao().deleteAll(); db.unlockDao().deleteAll() }
                }
                dataStore.edit { p ->
                    p.remove(pendingDeletesKey(uid)); p.remove(pendingFavPushKey(uid)); p.remove(pendingUnlockPushKey(uid))
                    p.remove(STYLES_SYNCED)
                    if (p[LAST_UID] == uid) p.remove(LAST_UID)
                }
            }
            prefs.setSeedTags(emptySet())
        }
        return result
    }

    // ------------------------------------------------------------------ sync internals (call with lock held)

    /** Parks the account's unsynced changes under its id, then removes all its rows from the phone. */
    private suspend fun leaveAccount(uid: String) {
        val favs = db.favoriteDao().unsynced().map { it.wallpaperId }
        val unl = db.unlockDao().unsynced().map { "${it.wallpaperId}|${it.unlockedAt}" }
        dataStore.edit { p ->
            if (favs.isNotEmpty()) p[pendingFavPushKey(uid)] = p[pendingFavPushKey(uid)].orEmpty() + favs
            if (unl.isNotEmpty()) p[pendingUnlockPushKey(uid)] = p[pendingUnlockPushKey(uid)].orEmpty() + unl
        }
        db.withTransaction { db.favoriteDao().deleteAll(); db.unlockDao().deleteAll() }
        prefs.setSeedTags(emptySet()) // those picks were this account's; don't carry them into the next one
        dataStore.edit { it.remove(LAST_UID); it.remove(STYLES_SYNCED) }
        Log.i(TAG, "left account: parked ${favs.size} favourites, ${unl.size} unlocks")
    }

    private suspend fun fullSync(uid: String) {
        try {
            syncFavorites(uid)
            syncUnlocks(uid)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "sync failed (offline?), will retry next launch: ${e.message}")
        }
    }

    private suspend fun syncFavorites(uid: String) {
        val dao = db.favoriteDao()
        val remote = selectAll<FavoriteRemoteRow>("favorites", "wallpaper_id", "created_at", uid = uid)
        val parked = dataStore.data.first()[pendingFavPushKey(uid)].orEmpty()
        val local = dao.allIds().toSet()
        val plan = planSync(
            localIds = local,
            localSynced = local - dao.unsynced().map { it.wallpaperId }.toSet(),
            toPush = dao.unsynced().map { it.wallpaperId }.toSet() + parked,
            pendingDeletes = dataStore.data.first()[pendingDeletesKey(uid)].orEmpty(),
            remoteIds = remote.map { it.wallpaperId }.toSet(),
        )

        // Push only wallpapers that still exist and are public, so one deleted wallpaper can't fail the batch
        // (favorites.wallpaper_id is a foreign key). Unpublished ones stay unsynced and are retried later.
        val pushable = existingIds(plan.push)
        pushable.chunked(CHUNK).forEach { chunk -> favorites().upsert(chunk.map { FavoriteRow(uid, it) }) { ignoreDuplicates = true } }
        pushable.chunked(CHUNK).forEach { dao.markSynced(it) }
        plan.delete.chunked(CHUNK).forEach { chunk ->
            favorites().delete { filter { eq("user_id", uid); isIn("wallpaper_id", chunk) } }
        }
        dataStore.edit { p ->
            p.removeAllFrom(pendingDeletesKey(uid), plan.delete)
            p.removeAllFrom(pendingFavPushKey(uid), pushable.toSet() + plan.delete) // the rest is retried next sync
        }
        if (plan.removeLocal.isNotEmpty()) plan.removeLocal.chunked(CHUNK).forEach { dao.deleteIds(it) }

        // Parked favourites from this account's last session are on the server now; show them here too.
        val toPull = plan.pull + (pushable.toSet().intersect(parked) - local)
        if (toPull.isNotEmpty()) {
            val createdAt = remote.associate { it.wallpaperId to (parseTimestamp(it.createdAt) ?: System.currentTimeMillis()) }
            val found = fetchWallpapers(toPull)
            // Re-read: the user may have un-favourited something while we were downloading.
            val deletedMeanwhile = dataStore.data.first()[pendingDeletesKey(uid)].orEmpty()
            db.withTransaction {
                db.wallpaperDao().upsertAll(found.map { it.toEntity() })
                found.filter { it.id !in deletedMeanwhile }.forEach {
                    dao.insert(FavoriteEntity(it.id, createdAt = createdAt[it.id] ?: System.currentTimeMillis(), synced = true))
                }
            }
        }
        Log.i(TAG, "favourites synced: pushed ${pushable.size}/${plan.push.size}, deleted ${plan.delete.size}, " +
            "pulled ${plan.pull.size}, removed locally ${plan.removeLocal.size}")
    }

    private suspend fun syncUnlocks(uid: String) {
        val dao = db.unlockDao()
        val remote = selectAll<UnlockRow>("unlocks", "wallpaper_id", "unlocked_at", uid = uid)
            .mapNotNull { r -> parseTimestamp(r.unlockedAt)?.let { r.wallpaperId to it } }.toMap()
        val parked = dataStore.data.first()[pendingUnlockPushKey(uid)].orEmpty().mapNotNull {
            val (id, at) = it.split('|').let { p -> p.getOrNull(0) to p.getOrNull(1)?.toLongOrNull() }
            if (id != null && at != null) id to at else null
        }.toMap()
        val local = dao.all().associate { it.wallpaperId to it.unlockedAt }
        val toPush = parked + dao.unsynced().associate { it.wallpaperId to it.unlockedAt }
        val plan = planUnlocks(local, toPush, remote)

        val pushable = existingIds(plan.push.keys)
        pushable.chunked(CHUNK).forEach { chunk ->
            unlocks().upsert(chunk.map { UnlockRow(uid, it, Instant.ofEpochMilli(plan.push.getValue(it)).toString()) })
        }
        // Mark only the snapshot rows: pushed now, or already newer on the server. A re-unlock made during
        // the sync has a later unlockedAt and stays unsynced for the next round.
        val unsyncedSnapshot = dao.unsynced()
        unsyncedSnapshot.filter { it.wallpaperId in pushable || it.wallpaperId !in plan.push }
            .forEach { dao.markSyncedIfNotNewer(it.wallpaperId, it.unlockedAt) }
        val pushedSet = pushable.toSet()
        dataStore.edit { p ->
            p[pendingUnlockPushKey(uid)] = p[pendingUnlockPushKey(uid)].orEmpty().filterNot { it.substringBefore('|') in pushedSet }.toSet()
        }

        // Bring down newer server unlocks, plus parked unlocks we just pushed that this phone doesn't have.
        val restored = parked.filterKeys { it in pushedSet && (local[it] ?: Long.MIN_VALUE) < parked.getValue(it) }
        val toPull = plan.pull + restored.filter { (id, at) -> (plan.pull[id] ?: Long.MIN_VALUE) < at }
        if (toPull.isNotEmpty()) {
            val found = fetchWallpapers(toPull.keys)
            db.withTransaction {
                db.wallpaperDao().upsertAll(found.map { it.toEntity() })
                found.forEach {
                    val at = toPull.getValue(it.id)
                    // Re-read inside the transaction: never overwrite a fresher re-unlock made meanwhile.
                    if ((dao.get(it.id)?.unlockedAt ?: Long.MIN_VALUE) < at) dao.insert(UnlockEntity(it.id, unlockedAt = at, synced = true))
                }
            }
        }
    }

    private suspend fun syncStyles(uid: String, tags: Set<String>) {
        val marker = "$uid:${tags.sorted().joinToString(",")}"
        if (dataStore.data.first()[STYLES_SYNCED] == marker) return
        runCatching {
            supabase.get().postgrest.rpc("set_style_preferences", buildJsonObject {
                putJsonArray("tag_slugs") { tags.forEach { add(JsonPrimitive(it)) } }
            })
            dataStore.edit { it[STYLES_SYNCED] = marker }
        }.onFailure { Log.w(TAG, "style sync failed: ${it.message}") }
    }

    // ------------------------------------------------------------------ remote helpers

    /** Pages through PostgREST (Supabase caps each response at 1000 rows). */
    private suspend inline fun <reified T : Any> selectAll(table: String, vararg columns: String, uid: String): List<T> {
        val out = mutableListOf<T>()
        var from = 0L
        while (true) {
            val page = supabase.get().from(table).select(Columns.list(*columns)) {
                filter { eq("user_id", uid) }
                order("wallpaper_id", Order.ASCENDING) // stable order, so pages never skip or repeat rows
                range(from, from + PAGE - 1)
            }.decodeList<T>()
            if (page.isEmpty()) return out
            out += page
            from += page.size // don't assume the server returns a full PAGE
        }
    }

    /** Ids that exist as published wallpapers (also what the foreign key needs). */
    private suspend fun existingIds(ids: Collection<String>): List<String> = ids.chunked(CHUNK).flatMap { chunk ->
        supabase.get().from("wallpapers_public").select(Columns.list("id")) { filter { isIn("id", chunk) } }
            .decodeList<IdOnly>().map { it.id }
    }

    private suspend fun wallpaperExists(id: String) = existingIds(listOf(id)).isNotEmpty()

    /** Published wallpapers only; ids that were unpublished or deleted are simply skipped. */
    private suspend fun fetchWallpapers(ids: Collection<String>) = ids.chunked(CHUNK).flatMap { chunk ->
        supabase.get().from("wallpapers_public").select { filter { isIn("id", chunk) } }
            .decodeList<WallpaperDto>().map { it.toDomain() }
    }

    private fun favorites() = supabase.get().from("favorites")
    private fun unlocks() = supabase.get().from("unlocks")

    @Serializable private data class FavoriteRow(@SerialName("user_id") val userId: String, @SerialName("wallpaper_id") val wallpaperId: String)
    @Serializable private data class FavoriteRemoteRow(
        @SerialName("wallpaper_id") val wallpaperId: String,
        @SerialName("created_at") val createdAt: String? = null,
    )
    @Serializable private data class IdOnly(val id: String)
    @Serializable private data class UnlockRow(
        @SerialName("user_id") val userId: String? = null,
        @SerialName("wallpaper_id") val wallpaperId: String,
        @SerialName("unlocked_at") val unlockedAt: String,
    )

    private fun MutablePreferences.addTo(key: Preferences.Key<Set<String>>, value: String) { this[key] = this[key].orEmpty() + value }
    private fun MutablePreferences.removeFrom(key: Preferences.Key<Set<String>>, value: String) { this[key] = this[key].orEmpty() - value }
    private fun MutablePreferences.removeAllFrom(key: Preferences.Key<Set<String>>, values: Set<String>) { this[key] = this[key].orEmpty() - values }

    private companion object {
        const val TAG = "AccountSync"
        const val CHUNK = 200   // keeps SQLite (999 variables before Android 11) and URL lengths safe
        const val PAGE = 1000L
        val LAST_UID = stringPreferencesKey("account_last_uid")
        val STYLES_SYNCED = stringPreferencesKey("account_styles_synced")
        fun pendingDeletesKey(uid: String) = stringSetPreferencesKey("account_pending_fav_deletes_$uid")
        fun pendingFavPushKey(uid: String) = stringSetPreferencesKey("account_pending_fav_push_$uid")
        fun pendingUnlockPushKey(uid: String) = stringSetPreferencesKey("account_pending_unlock_push_$uid")
    }
}
