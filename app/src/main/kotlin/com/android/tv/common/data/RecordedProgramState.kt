package com.android.tv.common.data

/** Zustand einer Aufnahme im TvProvider (Spalte "state", falls vorhanden). */
enum class RecordedProgramState { NOT_SET, STARTED, FINISHED, PARTIAL, FAILED, DELETE, DELETED }
