package net.dhleong.staypuft.impl

import android.app.IntentService
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.support.v4.content.ContextCompat
import android.util.Log
import net.dhleong.staypuft.DownloaderConfig
import net.dhleong.staypuft.Notifier

/**
 * @author dhleong
 */
class ExpansionDownloaderFgService
    : IntentService("net.dhleong.staypuft.fg"),
      IExpansionDownloaderService {

    private lateinit var engine: ExpansionDownloaderEngine

    override fun onCreate() {
        super.onCreate()

        engine = ExpansionDownloaderEngine(
            this,
            DefaultUIProxy(this)
        )
    }

    override fun onDestroy() {
        super.onDestroy()

        engine.stop()
    }

    override fun onHandleIntent(intent: Intent) {
        val config = intent.getParcelableExtra<DownloaderConfig>(EXTRA_CONFIG)

        startForeground(config.notificationId, buildInitialNotification(config))
        try {
            performDownload(config)
        } finally {
            stopForeground(true)
        }
    }

    private fun performDownload(config: DownloaderConfig) {
        val notifier = config.inflateNotifier(this)

        // FIXME TODO check network state

        engine.start()
        val success = engine.processDownload(config, notifier)
            .toSingleDefault(false)
            .onErrorReturn { e ->
                Log.w("staypuft", "Error processing download", e)
                true
            }
            .blockingGet()

        if (!success) {
            // FIXME better status:
            notifier.statusChanged(Notifier.STATE_PAUSED_NEED_WIFI)
            ExpansionDownloaderJobService.start(this, config)
        }
    }

    private fun buildInitialNotification(config: DownloaderConfig?): Notification? {
        TODO()
        return null
    }

    companion object {
        private const val EXTRA_CONFIG = "config"

        fun start(context: Context, config: DownloaderConfig) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ExpansionDownloaderFgService::class.java).apply {
                    putExtra(EXTRA_CONFIG, config)
                }
            )
        }
    }
}