package net.dhleong.staypuft

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.PersistableBundle
import android.support.v4.app.NotificationCompat
import android.util.Log

/**
 * @author dhleong
 */
@Suppress("MemberVisibilityCanBePrivate")
open class DefaultNotifier(
    protected val context: Context,
    protected val channelId: String
) : Notifier {

    protected val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    protected val simpleBuilder: NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

    protected val progressBuilder: NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)

    private val appLabel: CharSequence by lazy(LazyThreadSafetyMode.NONE) {
        context.packageManager.getApplicationLabel(
            context.applicationInfo
        )
    }

    private val pendingIntent: PendingIntent by lazy(LazyThreadSafetyMode.NONE) {
        getPendingIntent(context)
    }

    override fun done() {
        statusChanged(Notifier.STATE_COMPLETED)
    }

    override fun progress(downloaded: Long, size: Long) {
        notify(progressBuilder.apply {
            setContentIntent(pendingIntent)
            setContentTitle(appLabel)
            setSmallIcon(android.R.drawable.stat_sys_download)

            setOngoing(true)
            setOnlyAlertOnce(true)
            setProgress(size.toInt(), downloaded.toInt(), false)
        })
    }

    override fun statusChanged(state: Int) {
        val stateText = context.getString(Staypuft.getStringResForState(state))

        Log.v(NOTIFY_TAG, "Downloader status: $stateText")
        notify(progressBuilder.apply {
            setContentIntent(pendingIntent)
            setContentTitle(appLabel)
            setContentText(stateText)

            setAutoCancel(true)

            setSmallIcon(when (state) {
                Notifier.STATE_COMPLETED,
                Notifier.STATE_CONNECTING,
                Notifier.STATE_FETCHING_URL,
                Notifier.STATE_PAUSED_BY_REQUEST -> android.R.drawable.stat_sys_download_done

                else -> android.R.drawable.stat_sys_warning
            })
        })
    }

    override fun error(e: ApkExpansionException) {
        statusChanged(e.state) // ?
    }

    protected fun notify(builder: NotificationCompat.Builder) {
        nm.notify(NOTIFY_TAG, NOTIFY_ID, builder.build())
    }

    protected open fun getPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(context, 0,

            context.packageManager.getLaunchIntentForPackage(
                context.packageName
            ),

            PendingIntent.FLAG_UPDATE_CURRENT
        )

    class Factory : Notifier.Factory {
        override fun create(context: Context, args: PersistableBundle?) =
            DefaultNotifier(context, args!!.getString("channelId"))
    }

    companion object {

        private const val NOTIFY_TAG = "net.dhleong.staypuft"
        private const val NOTIFY_ID = 78297838

        fun withChannelId(channelId: String) = Notifier.Factory.Config(
            Notifier.Factory::class.java,
            PersistableBundle().apply {
                putString("channelId", channelId)
            }
        )
    }
}