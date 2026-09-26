package com.android.tv.license

import android.content.Context
import android.util.Log
import androidx.annotation.RawRes
import com.android.tv.R

/** Liest Lizenz-Metadaten ("offset:länge name") und -texte aus res/raw. */
object Licenses {
    const val TAG = "Licenses"

    @JvmStatic
    fun hasLicenses(context: Context): Boolean =
        getTextFromResource(context.applicationContext, R.raw.third_party_license_metadata, 0, -1).isNotEmpty()

    @JvmStatic
    fun getLicenses(context: Context): ArrayList<License> =
        getLicenseListFromMetadata(getTextFromResource(context.applicationContext, R.raw.third_party_license_metadata, 0, -1), "")

    private fun getLicenseListFromMetadata(metadata: String, filePath: String): ArrayList<License> {
        val licenses = ArrayList<License>()
        for (entry in metadata.split("\n")) {
            // Bugfix: leere/ungültige Zeilen (z. B. abschließender Zeilenumbruch) führten zum Absturz
            val delimiter = entry.indexOf(' ')
            val location = if (delimiter > 0) entry.substring(0, delimiter).split(":") else emptyList()
            if (location.size != 2) {
                if (entry.isNotBlank()) Log.w(TAG, "Invalid license meta-data line:\n$entry")
                continue
            }
            licenses.add(License.create(entry.substring(delimiter + 1), location[0].toLong(), location[1].toInt(), filePath))
        }
        licenses.sort()
        return licenses
    }

    @JvmStatic
    fun getLicenseText(context: Context, license: License): String =
        getTextFromResource(context, R.raw.third_party_licenses, license.licenseOffset, license.licenseLength)

    /** Liest [length] Bytes ab [offset] (length ≤ 0 = bis zum Ende) als UTF-8. */
    private fun getTextFromResource(context: Context, @RawRes resId: Int, offset: Long, length: Int): String =
        context.applicationContext.resources.openRawResource(resId).use { stream ->
            stream.skip(offset)
            var remaining = if (length > 0) length else Int.MAX_VALUE
            val buffer = ByteArray(1024)
            val out = java.io.ByteArrayOutputStream()
            while (remaining > 0) {
                val bytes = stream.read(buffer, 0, minOf(remaining, buffer.size))
                if (bytes == -1) break
                out.write(buffer, 0, bytes)
                remaining -= bytes
            }
            out.toString("UTF-8")
        }
}
