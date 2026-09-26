package com.android.tv.license

import android.os.Parcel
import android.os.Parcelable

/** Eine Drittanbieter-Lizenz (Position im Lizenztext). */
class License private constructor(
    val libraryName: String,
    val licenseOffset: Long,
    val licenseLength: Int,
    val path: String,
) : Comparable<License>, Parcelable {

    private constructor(p: Parcel) : this(p.readString().orEmpty(), p.readLong(), p.readInt(), p.readString().orEmpty())

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(libraryName)
        dest.writeLong(licenseOffset)
        dest.writeInt(licenseLength)
        dest.writeString(path)
    }

    override fun compareTo(other: License) = libraryName.compareTo(other.libraryName, ignoreCase = true)
    override fun toString() = libraryName

    companion object {
        internal fun create(libraryName: String, offset: Long, length: Int, path: String) = License(libraryName, offset, length, path)

        @JvmField
        val CREATOR = object : Parcelable.Creator<License> {
            override fun createFromParcel(p: Parcel) = License(p)
            override fun newArray(size: Int) = arrayOfNulls<License>(size)
        }
    }
}
