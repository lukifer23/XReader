package com.xreader.app.settings

enum class LibrarySort {
    RECENT,
    TITLE,
    AUTHOR,
    PROGRESS,
    SERIES,
}

enum class LibraryDensity {
    COMFORTABLE,
    COMPACT,
}

data class LibrarySettings(
    val sort: LibrarySort = LibrarySort.RECENT,
    val density: LibraryDensity = LibraryDensity.COMFORTABLE,
)
