package com.android.tv.dvr.data

/** Serien-Metadaten (vom Input). */
class SeriesInfo(
    val id: String?,
    val title: String?,
    val description: String?,
    val longDescription: String?,
    val canonicalGenreIds: IntArray?,
    val posterUri: String?,
    val photoUri: String?,
)
