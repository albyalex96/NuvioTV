package com.nuvio.tv.ui.screens.stream

enum class TvDisplayMode {
    POLISHED,
    ORIGINAL;

    companion object {
        fun fromString(value: String?): TvDisplayMode =
            entries.firstOrNull { it.name == value } ?: ORIGINAL
    }
}