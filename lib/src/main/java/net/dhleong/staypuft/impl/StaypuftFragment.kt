package net.dhleong.staypuft.impl

import android.app.Activity
import android.app.Fragment
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.support.v4.content.LocalBroadcastManager
import io.reactivex.Observer
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import net.dhleong.staypuft.DownloadState
import net.dhleong.staypuft.DownloaderConfig
import net.dhleong.staypuft.Notifier
import net.dhleong.staypuft.rx.getSaveDirectory
import java.io.File


/**
 * @author dhleong
 */
class StaypuftFragment : Fragment() {

    val stateEvents: BehaviorSubject<DownloadState> =
        BehaviorSubject.createDefault(DownloadState.Checking())

    private val subs = CompositeDisposable()

    private lateinit var downloadsTracker: IDownloadsTracker
    private var myConfig: DownloaderConfig? = null
    private var serviceStateReceiver: BroadcastReceiver? = null

    fun setConfig(config: DownloaderConfig) {
        myConfig = config

        if (activity != null) {
            performDownloadStatusCheck(config)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // stick around
        retainInstance = true

        stateEvents.onNext(DownloadState.Checking())
        downloadsTracker = PrefsDownloadsTracker(activity)

        // if we have a config, go ahead and check
        myConfig?.let(::performDownloadStatusCheck)
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterStateReceiver()
        subs.clear()
    }

    private fun performDownloadStatusCheck(config: DownloaderConfig) {
        subs.add(
            checkDownloadStatus()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { status -> when (status) {
                    DownloadStatus.READY -> {
                        stateEvents.onNext(DownloadState.Ready())
                    }

                    DownloadStatus.UNKNOWN -> {
                        stateEvents.onNext(DownloadState.Checking())
                    }

                    else -> {
                        stateEvents.onNext(DownloadState.Unavailable())

                        // TODO raise notification immediately?
                        activity?.let { context ->
                            registerStateReceiver()
                            ExpansionDownloaderService.start(
                                context,
                                config
                            )
                        }
                    }
                } }
        )
    }

    private fun checkDownloadStatus(): Single<Int> = Single.fromCallable {
        if (downloadsTracker.needsUpdate()) {
            DownloadStatus.LVL_CHECK_REQUIRED
        } else {
            val context = activity
            if (context == null) DownloadStatus.DOWNLOAD_NEEDED
            else {
                val allFilesExist = downloadsTracker.getKnownDownloads().any {
                    it.checkLocalExists(SaveDirectoryWrapper(context))
                }
                if (allFilesExist) DownloadStatus.READY
                else DownloadStatus.DOWNLOAD_NEEDED
            }
        }
    }

    private fun registerStateReceiver() {
        unregisterStateReceiver()

        activity?.let { context ->
            serviceStateReceiver = ServiceStateReceiver(stateEvents).also {
                LocalBroadcastManager.getInstance(context)
                    .registerReceiver(
                        it,
                        IntentFilter().apply {
                            addAction(UIProxy.ACTION_PROGRESS)
                            addAction(UIProxy.ACTION_STATUS_CHANGE)
                        }
                    )
            }
        }
    }

    private fun unregisterStateReceiver() {
        activity?.let { context ->
            serviceStateReceiver?.let {
                LocalBroadcastManager.getInstance(context)
                    .unregisterReceiver(it)
            }
        }
    }

    private class SaveDirectoryWrapper(
        private val context: Activity
    ) : IHasSaveDirectory {
        override fun getSaveDirectory(): File =
            (context as Context).getSaveDirectory()
    }

    companion object {
        const val TAG = "net.dhleong.staypuft"
    }
}

private class ServiceStateReceiver(
    private val events: Observer<DownloadState>
) : BroadcastReceiver() {

    private var lastDownloaded: Long = 0
    private var lastTotalBytes: Long = 1

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            UIProxy.ACTION_STATUS_CHANGE -> {
                notifyStatusChange(intent.getIntExtra(UIProxy.EXTRA_STATUS, 0))
            }

            UIProxy.ACTION_PROGRESS -> {
                lastDownloaded = intent.getLongExtra(UIProxy.EXTRA_DOWNLOADED, 0L)
                lastTotalBytes = intent.getLongExtra(UIProxy.EXTRA_TOTAL_BYTES, 1L)
                notifyStatusChange(Notifier.STATE_DOWNLOADING)
            }
        }
    }

    private fun notifyStatusChange(status: Int) {
        val state = when (status) {
            Notifier.STATE_CONNECTING,
            Notifier.STATE_FETCHING_URL,
            Notifier.STATE_DOWNLOADING -> DownloadState.Downloading(lastDownloaded, lastTotalBytes)

            Notifier.STATE_COMPLETED -> DownloadState.Ready()

            // TODO

            else -> null
        }

        if (state != null) events.onNext(state)
    }
}