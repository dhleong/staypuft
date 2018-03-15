package net.dhleong.staypuft.impl

import android.content.Context
import android.content.Intent
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
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

internal class DefaultUIProxy(
    context: Context
) : UIProxy {

    private val lbm: LocalBroadcastManager = LocalBroadcastManager.getInstance(context)

    override fun statusChanged(status: Int) {
        broadcastIntent(UIProxy.ACTION_STATUS_CHANGE) {
            putExtra(UIProxy.EXTRA_STATUS, status)
        }
    }

    override fun progress(downloaded: Long, total: Long) {
        broadcastIntent(UIProxy.ACTION_PROGRESS) {
            putExtra(UIProxy.EXTRA_DOWNLOADED, downloaded)
            putExtra(UIProxy.EXTRA_TOTAL_BYTES, total)
        }
    }

    private fun broadcastIntent(action: String, intentBuilder: Intent.() -> Unit) {
        if (!lbm.sendBroadcast(Intent(action).apply(intentBuilder))) {
            Log.v(NOTIFY_TAG, "Nobody received $action")
        }
    }

    companion object {
        private const val NOTIFY_TAG = "net.dhleong.staypuft.ui"
    }
}