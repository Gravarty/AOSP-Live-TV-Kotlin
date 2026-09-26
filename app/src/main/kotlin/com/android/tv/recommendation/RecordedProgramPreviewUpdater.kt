package com.android.tv.recommendation

import android.content.Context
import com.android.tv.TvSingletons
import com.android.tv.data.PreviewDataManager
import com.android.tv.data.PreviewProgramContent
import com.android.tv.dvr.DvrDataManager
import com.android.tv.dvr.data.RecordedProgram

/** Neueste Aufnahmen (mit Poster, max. 6) als Vorschau-Kanal im Launcher. */
class RecordedProgramPreviewUpdater private constructor(context: Context) {
    private val context = context.applicationContext
    private val previewDataManager: PreviewDataManager = TvSingletons.getSingletons(this.context).getPreviewDataManager()
    private val dvrDataManager: DvrDataManager = TvSingletons.getSingletons(this.context).getDvrDataManager()

    init {
        dvrDataManager.addRecordedProgramListener(object : DvrDataManager.RecordedProgramListener {
            override fun onRecordedProgramsAdded(vararg recordedPrograms: RecordedProgram) = updatePreviewDataForRecordedPrograms()
            override fun onRecordedProgramsChanged(vararg recordedPrograms: RecordedProgram) = updatePreviewDataForRecordedPrograms()
            override fun onRecordedProgramsRemoved(vararg recordedPrograms: RecordedProgram) = updatePreviewDataForRecordedPrograms()
        })
    }

    fun updatePreviewDataForRecordedPrograms() {
        if (!previewDataManager.isLoadFinished) {
            previewDataManager.addListener(object : PreviewDataManager.PreviewDataListener {
                override fun onPreviewDataLoadFinished() {
                    previewDataManager.removeListener(this)
                    updatePreviewDataForRecordedPrograms()
                }

                override fun onPreviewDataUpdateFinished() {}
            })
            return
        }
        if (!dvrDataManager.isRecordedProgramLoadFinished) {
            dvrDataManager.addRecordedProgramLoadFinishedListener(object : DvrDataManager.OnRecordedProgramLoadFinishedListener {
                override fun onRecordedProgramLoadFinished() {
                    dvrDataManager.removeRecordedProgramLoadFinishedListener(this)
                    updatePreviewDataForRecordedPrograms()
                }
            })
            return
        }
        updateInternal()
    }

    private fun updateInternal() {
        val recordedPrograms = generateRecommendationRecordedPrograms()
        val previewChannelId = previewDataManager.getPreviewChannelId(
            PreviewDataManager.TYPE_RECORDED_PROGRAM_PREVIEW_CHANNEL.toLong())
        if (previewChannelId == PreviewDataManager.INVALID_PREVIEW_CHANNEL_ID) {
            // Bugfix: ohne Kanal und ohne Aufnahmen nicht für Kanal-ID -1 aktualisieren
            if (recordedPrograms.isNotEmpty()) {
                previewDataManager.createPreviewChannel(PreviewDataManager.TYPE_RECORDED_PROGRAM_PREVIEW_CHANNEL.toLong()) { createdId ->
                    if (createdId != PreviewDataManager.INVALID_PREVIEW_CHANNEL_ID) updateInternal()
                }
            }
        } else {
            previewDataManager.updatePreviewProgramsForChannel(previewChannelId,
                recordedPrograms.map { PreviewProgramContent.createFromRecordedProgram(context, previewChannelId, it) }.toSet(), null)
        }
    }

    private fun generateRecommendationRecordedPrograms(): Set<RecordedProgram> =
        dvrDataManager.getRecordedPrograms()
            .sortedWith(RecordedProgram.START_TIME_THEN_ID_COMPARATOR.reversed())
            .filter { !it.posterArtUri.isNullOrEmpty() }
            .take(RECOMMENDATION_COUNT)
            .toSet()

    companion object {
        private const val RECOMMENDATION_COUNT = 6
        private var instance: RecordedProgramPreviewUpdater? = null

        @JvmStatic
        fun getInstance(context: Context): RecordedProgramPreviewUpdater =
            instance ?: RecordedProgramPreviewUpdater(context.applicationContext).also { instance = it }
    }
}
