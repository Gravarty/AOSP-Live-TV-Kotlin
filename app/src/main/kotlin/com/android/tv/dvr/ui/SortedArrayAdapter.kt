package com.android.tv.dvr.ui

import androidx.annotation.VisibleForTesting
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.PresenterSelector
import com.android.tv.common.SoftPreconditions

/**
 * Hält Einträge sortiert.
 *
 * [T] muss stabile IDs haben.
 */
@Suppress("UNCHECKED_CAST")
abstract class SortedArrayAdapter<T : Any> @JvmOverloads constructor(
    presenterSelector: PresenterSelector,
    private val comparator: Comparator<T>,
    private val maxItemCount: Int = Int.MAX_VALUE,
) : ArrayObjectAdapter(presenterSelector) {
    private var extraItemCount = 0
    private val ids = HashSet<Long>()

    init {
        setHasStableIds(true)
    }

    /** Setzt die Einträge sortiert (höchstens [maxItemCount]). */
    @VisibleForTesting
    fun setInitialItems(items: List<T>) {
        for (item in items.sortedWith(comparator)) {
            add(item, true)
            if (size() == maxItemCount) break
        }
    }

    /** Fügt einen Eintrag sortiert hinzu. */
    final override fun add(item: Any) = add(item as T, false)

    fun isEmpty(): Boolean = size() == 0

    /**
     * Fügt einen Eintrag sortiert hinzu. Mit [insertToEnd] wird die Position vom Ende her gesucht
     * (schneller, wenn die Einträge ungefähr sortiert ankommen).
     */
    fun add(item: T, insertToEnd: Boolean) {
        val newItemId = getId(item)
        SoftPreconditions.checkState(!ids.contains(newItemId), null, null)
        ids.add(newItemId)
        val i = if (insertToEnd) findInsertPosition(item) else findInsertPositionBinary(item)
        super.add(i, item)
        if (maxItemCount < Int.MAX_VALUE && size() > maxItemCount + extraItemCount) {
            remove(get(maxItemCount)!!)
        }
    }

    /**
     * Hängt einen Zusatzeintrag ans Ende; unterliegt weder Sortierung noch Höchstzahl.
     * Mehrere Zusatzeinträge bleiben in Einfügereihenfolge.
     */
    fun addExtraItem(item: T): Int {
        val newItemId = getId(item)
        SoftPreconditions.checkState(!ids.contains(newItemId), null, null)
        ids.add(newItemId)
        super.add(item)
        return ++extraItemCount
    }

    override fun remove(item: Any): Boolean = removeWithId(item as T)

    /** Entfernt den Eintrag mit derselben ID wie [item]. */
    fun removeWithId(item: T): Boolean {
        val index = indexWithId(item)
        return index >= 0 && index < size() && removeItems(index, 1) == 1
    }

    override fun removeItems(position: Int, count: Int): Int {
        val upperBound = minOf(position + count, size())
        for (i in position until upperBound) ids.remove(getId(get(i) as T))
        if (upperBound > size() - extraItemCount) {
            extraItemCount -= upperBound - maxOf(size() - extraItemCount, position)
        }
        return super.removeItems(position, count)
    }

    override fun replace(position: Int, item: Any) {
        val wasExtra = position >= size() - extraItemCount
        removeItems(position, 1)
        if (!wasExtra) add(item) else addExtraItem(item as T)
    }

    override fun clear() {
        ids.clear()
        // Bugfix: Zähler der Zusatzeinträge zurücksetzen (Original vergisst das)
        extraItemCount = 0
        super.clear()
    }

    /** Ändert einen Eintrag; bei geänderter Sortierposition wird er neu einsortiert. */
    fun change(item: T) {
        val oldIndex = indexWithId(item)
        if (oldIndex != -1) {
            val old = get(oldIndex) as T
            if (comparator.compare(old, item) == 0) {
                replace(oldIndex, item as Any)
                return
            }
            remove(old as Any)
        }
        add(item as Any)
    }

    /** Prüft, ob der Eintrag (per ID) enthalten ist. */
    fun contains(item: T): Boolean = indexWithId(item) != -1

    override fun getId(position: Int): Long = getId(get(position) as T)

    /** Stabile ID des Eintrags; [change] erkennt damit bereits vorhandene Einträge. */
    protected abstract fun getId(item: T): Long

    private fun indexWithId(item: T): Int {
        val id = getId(item)
        for (i in 0 until size() - extraItemCount) {
            if (getId(get(i) as T) == id) return i
        }
        return -1
    }

    /** Position, an der [item] eingefügt werden muss, damit die Sortierung erhalten bleibt. */
    fun findInsertPosition(item: T): Int {
        for (i in size() - extraItemCount - 1 downTo 0) {
            if (comparator.compare(get(i) as T, item) <= 0) return i + 1
        }
        return 0
    }

    private fun findInsertPositionBinary(item: T): Int {
        var lb = 0
        var ub = size() - extraItemCount - 1
        while (lb <= ub) {
            val mid = (lb + ub) / 2
            val compareResult = comparator.compare(item, get(mid) as T)
            when {
                compareResult == 0 -> return mid
                compareResult > 0 -> lb = mid + 1
                else -> ub = mid - 1
            }
        }
        return lb
    }
}
