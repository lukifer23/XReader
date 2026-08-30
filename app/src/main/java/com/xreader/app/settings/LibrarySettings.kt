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

enum class LibraryGroup {
    BOOKS,
    AUTHORS,
    SERIES,
    GENRES,
    FORMATS,
    YEARS,
    COLLECTIONS,
    RECENT,
    UNREAD,
    IN_PROGRESS,
    FINISHED,
    FAVORITES,
}

data class LibrarySettings(
    val sort: LibrarySort = LibrarySort.RECENT,
    val density: LibraryDensity = LibraryDensity.COMFORTABLE,
    val group: LibraryGroup = LibraryGroup.BOOKS,
)
