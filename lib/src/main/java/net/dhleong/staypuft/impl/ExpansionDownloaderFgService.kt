package net.dhleong.staypuft.impl

import android.app.IntentService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.support.v4.app.ServiceCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import net.dhleong.staypuft.DownloaderConfig
import net.dhleong.staypuft.Notifier
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author dhleong
 */
class ExpansionDownloaderFgService
    : IntentService("net.dhleong.staypuft.fg"),
      IExpansionDownloaderService {

    private lateinit var engine: ExpansionDownloaderEngine
    private lateinit var uiProxy: UIProxy
    private lateinit var cm: ConnectivityManager

    override fun onCreate() {
        super.onCreate()

        uiProxy = DefaultUIProxy(this)
        engine = ExpansionDownloaderEngine(
            this,
            uiProxy
        )

        cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    override fun onDestroy() {
        super.onDestroy()

        engine.stop()
    }

    override fun onHandleIntent(intent: Intent) {
        val config = intent.getParcelableExtra<DownloaderConfig>(EXTRA_CONFIG)
        val notifier = config.inflateNotifier(this)

        // cancel any queued job
        ExpansionDownloaderJobService.cancel(this, config)

        startForeground(config.notificationId, notifier.build(Notifier.STATE_CONNECTING))
        try {
            performDownload(config, notifier)
        } catch (e: PausedException) {
            // something happened to pause our download... resume later
            Log.w("staypuft", "Download paused: " + e.state, e)
            notifier.statusChanged(e.state)
            uiProxy.statusChanged(e.state)
            engine.stop()
            ExpansionDownloaderJobService.start(this, config)
        } finally {
            // always stop foreground-ness, but leave the notification around if possible
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        }
    }

    private fun performDownload(
        config: DownloaderConfig,
        notifier: Notifier
    ) {

        checkNetworkState(config)

        engine.start()
        Observable.merge(
            engine.processDownload(config, notifier)
                .toSingleDefault(Unit)
                .toObservable(),

            watchNetworkState(config)
        ).firstOrError()
            .blockingGet()
    }

    private fun watchNetworkState(config: DownloaderConfig): Observable<Unit> = Observable.defer {
        val subject: PublishSubject<Unit> = PublishSubject.create()

        val broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                try {
                    checkNetworkState(config)
                } catch (e: Throwable) {
                    subject.onError(e)
                }
            }
        }

        registerReceiver(broadcastReceiver, IntentFilter().apply {
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        })

        val registered = AtomicBoolean(true)

        subject.doOnDispose {
            if (registered.getAndSet(false)) {
                // only unregister once
                Log.v("staypuft", "clean up network checker") // FIXME TODO: remove
                unregisterReceiver(broadcastReceiver)
            }
        }
    }

    private fun checkNetworkState(config: DownloaderConfig) {
        val networkInfo = cm.activeNetworkInfo
            ?: throw PausedException(Notifier.STATE_PAUSED_NETWORK_UNAVAILABLE)

        if (cm.isActiveNetworkMetered && !config.canUseCellularData) {
            // TODO separate state?
            throw PausedException(Notifier.STATE_PAUSED_NEED_CELLULAR_PERMISSION)
        }

        if (networkInfo.isCellular && !config.canUseCellularData) {
            throw PausedException(Notifier.STATE_PAUSED_NEED_CELLULAR_PERMISSION)
        }

        if (networkInfo.isRoaming) {
            throw PausedException(Notifier.STATE_PAUSED_ROAMING)
        }

        // TODO Check if wifi is disabled

        Log.v("staypuft", "Network state is fine")
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

private class PausedException(
    val state: Int
) : RuntimeException()

private val NetworkInfo.isCellular: Boolean
    get() = when (type) {
        ConnectivityManager.TYPE_MOBILE,
            ConnectivityManager.TYPE_WIMAX -> true

        else -> false
    }
