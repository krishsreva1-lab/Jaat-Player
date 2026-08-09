

package com.krish.jaatplayer.lyrics

import android.content.Context
import com.krish.jaatplayer.betterlyrics.BetterLyrics
import com.krish.jaatplayer.constants.EnableBetterLyricsKey
import com.krish.jaatplayer.utils.dataStore
import com.krish.jaatplayer.utils.get

object BetterLyricsProvider : LyricsProvider {
    override val name = "BetterLyrics"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableBetterLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = BetterLyrics.getLyrics(title, artist, duration, album)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        BetterLyrics.getAllLyrics(title, artist, duration, album, callback)
    }
}
