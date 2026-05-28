package com.nuvio.tv.ui.screens.stream

import android.content.Context

object TvStreamsAppearanceStorage {

    private const val PREFS_NAME = "nuvio_tv_streams_appearance"
    private const val KEY_DISPLAY_MODE = "display_mode"

    fun saveDisplayMode(context: Context, mode: TvDisplayMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DISPLAY_MODE, mode.name)
            .apply()
    }

    fun loadDisplayMode(context: Context): TvDisplayMode {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DISPLAY_MODE, null)
        return TvDisplayMode.fromString(raw)
    }
}