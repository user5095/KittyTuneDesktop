package com.alananasss.kittytune.data.ytmusic

import com.alananasss.kittytune.core.NamedPrefs
import com.zionhuang.innertube.YouTube

/**
 * Login state of the user's YouTube Music account: the browser cookie from music.youtube.com,
 * handed to innertube (which signs requests with its SAPISID) plus the account name for display.
 */
object YtmSession {
    private val prefs = NamedPrefs("ytmusic_session")
    private const val KEY_COOKIE = "cookie"
    private const val KEY_NAME = "account_name"
    private const val KEY_SYNCED_LIKES = "synced_like_ids"
    private const val KEY_SYNCED_ARTISTS = "synced_artist_ids"
    private const val KEY_LAST_SYNC = "last_sync_ms"
    private val SEP = 31.toChar().toString()

    /** Pushes the stored cookie into innertube. Call once at startup. */
    fun init() {
        YouTube.cookie = prefs.getString(KEY_COOKIE, null)
    }

    fun isLoggedIn(): Boolean = prefs.getString(KEY_COOKIE, null)?.contains("SAPISID=") == true

    fun accountName(): String? = prefs.getString(KEY_NAME, null)

    fun save(cookie: String, name: String?) {
        prefs.putString(KEY_COOKIE, cookie)
        prefs.putString(KEY_NAME, name)
        YouTube.cookie = cookie
    }

    private fun getSet(key: String): Set<String> =
        prefs.getString(key, null)?.split(SEP)?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    private fun putSet(key: String, ids: Set<String>) = prefs.putString(key, ids.joinToString(SEP))

    /** Video ids known to be liked on YouTube Music at the last sync. */
    fun syncedLikes(): Set<String> = getSet(KEY_SYNCED_LIKES)
    fun setSyncedLikes(ids: Set<String>) = putSet(KEY_SYNCED_LIKES, ids)

    /** Channel ids of the artists followed on YouTube Music at the last sync. */
    fun syncedArtists(): Set<String> = getSet(KEY_SYNCED_ARTISTS)
    fun setSyncedArtists(ids: Set<String>) = putSet(KEY_SYNCED_ARTISTS, ids)

    fun lastSync(): Long = prefs.getLong(KEY_LAST_SYNC, 0L)
    fun markSynced() = prefs.putLong(KEY_LAST_SYNC, System.currentTimeMillis())

    fun logout() {
        prefs.clear()
        YouTube.cookie = null
    }
}
