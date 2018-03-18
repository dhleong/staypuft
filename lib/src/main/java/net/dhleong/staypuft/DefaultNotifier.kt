package net.dhleong.staypuft

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.PersistableBundle
import android.support.v4.app.NotificationCompat
import net.dhleong.staypuft.DefaultNotifier.Companion.withChannelId

/**
 * The default [Notifier] implementation. You should construct a
 *  [Notifier.Factory.Config] using the [withChannelId] method.
 *
 * @author dhleong
 */
@Suppress("MemberVisibilityCanBePrivate")
open class DefaultNotifier(
    protected val context: Context,
    protected val notificationId: Int,
    channelId: String
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
        notify(buildForStatus(state))
    }

    override fun build(state: Int): Notification = buildForStatus(state).build()

    private fun buildForStatus(state: Int): NotificationCompat.Builder {
        val stateText = context.getString(Staypuft.getStringResForState(state))
        return simpleBuilder.apply {
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
        }
    }

    override fun error(e: ApkExpansionException) {
        statusChanged(e.state) // ?
    }

    protected fun notify(builder: NotificationCompat.Builder) {
        nm.notify(notificationId, builder.build())
    }

    protected open fun getPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(context, 0,

            context.packageManager.getLaunchIntentForPackage(
                context.packageName
            ),

            PendingIntent.FLAG_UPDATE_CURRENT
        )

    class Factory : Notifier.Factory {
        override fun create(context: Context, config: DownloaderConfig, args: PersistableBundle?) =
            DefaultNotifier(context, config.notificationId, args!!.getString("channelId"))
    }

    companion object {

        /**
         * Create a [Notifier.Factory.Config] for the [DefaultNotifier]
         *  that creates its notifications using the provided [channelId]
         *  on Android O and above. You are responsible for creating
         *  the channel yourself at an appropriate time!
         *
         * NOTE: It is STRONGLY recommended that you create the channel
         *  associated with the [channelId] using [NotificationManager.IMPORTANCE_LOW]
         *  so as not to spam the user with notifications and noise while
         *  the download progresses.
         */
        fun withChannelId(channelId: String) = Notifier.Factory.Config(
            DefaultNotifier.Factory::class.java,
            PersistableBundle().apply {
                putString("channelId", channelId)
            }
        )
    }
}