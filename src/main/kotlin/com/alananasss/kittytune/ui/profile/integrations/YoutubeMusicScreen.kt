package com.alananasss.kittytune.ui.profile.integrations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.core.trackTextInput
import com.alananasss.kittytune.data.ytmusic.YtmImporter
import com.alananasss.kittytune.data.ytmusic.YtmSession
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Connect YouTube Music with a pasted browser cookie (the desktop has no WebView) and sync the library. */
@Composable
fun YoutubeMusicScreen(onBackClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loggedIn by remember { mutableStateOf(YtmSession.isLoggedIn()) }
    var name by remember { mutableStateOf(YtmSession.accountName()) }
    var showCookieDialog by remember { mutableStateOf(false) }
    var cookieError by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    if (showCookieDialog) {
        var temp by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCookieDialog = false },
            title = { Text(str("ytm_title")) },
            text = {
                Column {
                    Text(str("ytm_cookie_helper"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = temp,
                        onValueChange = { temp = it; cookieError = false },
                        placeholder = { Text(str("ytm_cookie_placeholder")) },
                        isError = cookieError,
                        supportingText = if (cookieError) ({ Text(str("ytm_cookie_invalid")) }) else null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 240.dp).trackTextInput()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cookie = temp.trim().removePrefix("cookie:").removePrefix("Cookie:").trim()
                    if (!cookie.contains("SAPISID=")) { cookieError = true; return@TextButton }
                    showCookieDialog = false
                    scope.launch {
                        YtmSession.save(cookie, null)
                        val account = withContext(Dispatchers.IO) { YouTube.accountInfo().getOrNull()?.name }
                        YtmSession.save(cookie, account)
                        name = account
                        loggedIn = true
                    }
                }) { Text(str("btn_ok")) }
            },
            dismissButton = { TextButton(onClick = { showCookieDialog = false }) { Text(str("btn_cancel")) } }
        )
    }

    SettingsScaffold(title = str("ytm_title"), onBackClick = onBackClick) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if (loggedIn) str("ytm_subtitle_connected", name ?: "") else str("ytm_subtitle_guest"))
            if (!loggedIn) {
                Button(onClick = { showCookieDialog = true }, modifier = Modifier.fillMaxWidth()) { Text(str("ytm_login")) }
            } else {
                Text(str("ytm_import_desc"))
                Button(
                    enabled = !importing,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        importing = true
                        scope.launch {
                            status = try {
                                val s = withContext(Dispatchers.IO) {
                                    YtmImporter.import { step ->
                                        status = str(
                                            when (step) {
                                                YtmImporter.Step.LIKES -> "ytm_step_likes"
                                                YtmImporter.Step.ARTISTS -> "ytm_step_artists"
                                                YtmImporter.Step.HISTORY -> "ytm_step_history"
                                            }
                                        )
                                    }
                                }
                                str("ytm_import_done", s.likes, s.artists, s.history)
                            } catch (e: Exception) {
                                str("ytm_import_failed", e.message ?: e.javaClass.simpleName)
                            }
                            importing = false
                        }
                    }
                ) { Text(str("ytm_import")) }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { YtmSession.logout(); loggedIn = false; name = null; status = null }
                ) { Text(str("ytm_logout")) }
            }
            status?.let { Text(it) }
        }
    }
}
