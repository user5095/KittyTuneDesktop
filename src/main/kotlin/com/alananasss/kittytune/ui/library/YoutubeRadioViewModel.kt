package com.alananasss.kittytune.ui.library

import com.alananasss.kittytune.core.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alananasss.kittytune.core.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.models.WatchEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class YoutubeRadioViewModel(application: Application) : AndroidViewModel(application) {
    val tracks = mutableStateListOf<Track>()
    var isLoading by mutableStateOf(true)
    var isLoadingMore by mutableStateOf(false)

    var playlistTitle by mutableStateOf("")
    var playlistCover by mutableStateOf<String?>(null)
    var playlistUser by mutableStateOf<User?>(null)

    // radio continuation: next page of the same mix (never a title search)
    private var nextEndpoint: WatchEndpoint? = null
    private var continuation: String? = null

    private fun SongItem.toTrack() = Track(
        id = kotlin.math.abs(id.hashCode().toLong()),
        title = title,
        user = User(0L, artists.joinToString(", ") { it.name }.ifEmpty { "YouTube Music" }, null),
        artworkUrl = thumbnail,
        durationMs = (duration ?: 0) * 1000L,
        permalinkUrl = "https://youtube.com/watch?v=$id",
        source = "youtube"
    )

    fun loadInitial(youtubeUrl: String) {
        if (tracks.isNotEmpty()) return
        viewModelScope.launch {
            isLoading = true
            withContext(Dispatchers.IO) {
                try {
                    val cleanId = youtubeUrl.substringAfter("v=").substringBefore("&")
                    val result = YouTube.next(WatchEndpoint(videoId = cleanId, playlistId = "RDAMVM$cleanId")).getOrNull()
                    nextEndpoint = result?.endpoint
                    continuation = result?.continuation
                    val newTracks = result?.items?.map { it.toTrack() } ?: emptyList()

                    withContext(Dispatchers.Main) {
                        val firstArtist = newTracks.firstOrNull()?.user?.username
                        playlistTitle = if (firstArtist != null) "Mix • $firstArtist" else "YouTube Mix"
                        playlistCover = newTracks.firstOrNull()?.artworkUrl
                        playlistUser = User(0, "YouTube", null)
                        tracks.clear()
                        tracks.addAll(newTracks)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    withContext(Dispatchers.Main) { isLoading = false }
                }
            }
        }
    }

    fun loadMore() {
        val endpoint = nextEndpoint
        val token = continuation
        if (isLoadingMore || endpoint == null || token == null) return
        viewModelScope.launch {
            isLoadingMore = true
            withContext(Dispatchers.IO) {
                try {
                    val result = YouTube.next(endpoint, token).getOrNull()
                    continuation = result?.continuation
                    val newTracks = result?.items?.map { it.toTrack() } ?: emptyList()

                    withContext(Dispatchers.Main) {
                        val existingIds = tracks.map { it.id }.toSet()
                        tracks.addAll(newTracks.filter { it.id !in existingIds })
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    withContext(Dispatchers.Main) { isLoadingMore = false }
                }
            }
        }
    }
}
