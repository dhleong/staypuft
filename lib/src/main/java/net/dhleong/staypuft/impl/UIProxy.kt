package net.dhleong.staypuft.impl

import net.dhleong.staypuft.ApkExpansionException
import net.dhleong.staypuft.Notifier

/**
 * Proxy for marshalling messages back to the UI,
 * if any is interested
 *
 * @author dhleong
 */
internal interface UIProxy {

    fun statusChanged(status: Int)
    fun progress(downloaded: Long, total: Long)
    fun done() {
        statusChanged(Notifier.STATE_COMPLETED)
    }

    fun error(e: ApkExpansionException) {
        statusChanged(e.state)
    }

    companion object {
        const val ACTION_STATUS_CHANGE = "net.dhleong.staypuft.action.STATUS_CHANGE"
        const val ACTION_PROGRESS = "net.dhleong.staypuft.action.PROGRESS"

        const val EXTRA_STATUS = "status"
        const val EXTRA_DOWNLOADED = "downloaded"
        const val EXTRA_TOTAL_BYTES = "total-bytes"
    }
}