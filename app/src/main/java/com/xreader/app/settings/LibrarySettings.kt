package com.xreader.app.settings

enum class LibrarySort {
    RECENT,
    DATE_ADDED,
    TITLE,
    AUTHOR,
    PROGRESS,
    SERIES,
    LENGTH,
}

enum class LibraryDensity {
    COMFORTABLE,
    COMPACT,
}

data class LibrarySettings(
    val sort: LibrarySort = LibrarySort.RECENT,
    val density: LibraryDensity = LibraryDensity.COMFORTABLE,
)
