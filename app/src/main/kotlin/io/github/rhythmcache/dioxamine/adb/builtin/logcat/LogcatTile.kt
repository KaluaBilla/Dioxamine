package io.github.rhythmcache.dioxamine.adb.builtin.logcat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.rhythmcache.dioxamine.R
import io.github.rhythmcache.dioxamine.adb.builtin.AdbActionTile

@Composable
fun LogcatTile(
    isConnected: Boolean,
    onClick: () -> Unit
) {
    AdbActionTile(
        title = stringResource(R.string.adb_logcat_tile_title),
        description = stringResource(R.string.adb_logcat_tile_desc),
        icon = Icons.AutoMirrored.Filled.Article,
        enabled = isConnected,
        onClick = onClick
    )
}
