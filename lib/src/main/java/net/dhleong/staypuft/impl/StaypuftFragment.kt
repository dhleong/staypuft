package net.dhleong.staypuft.impl

import android.app.Activity
import android.app.Fragment
import android.content.Context
import android.os.Bundle
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import net.dhleong.staypuft.DownloadState
import net.dhleong.staypuft.DownloaderConfig
import net.dhleong.staypuft.rx.getSaveDirectory
import java.io.File


/**
 * @author dhleong
 */
class StaypuftFragment : Fragment() {

    val statusEvents: BehaviorSubject<DownloadState> =
        BehaviorSubject.createDefault(DownloadState.Checking())

    private val subs = CompositeDisposable()

    private lateinit var downloadsTracker: DownloadsTracker
    private var myConfig: DownloaderConfig? = null

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

        statusEvents.onNext(DownloadState.Checking())
        downloadsTracker = DownloadsTracker(activity)

        // if we have a config, go ahead and check
        myConfig?.let(::performDownloadStatusCheck)
    }

    override fun onDestroy() {
        super.onDestroy()

        subs.clear()
    }

    private fun performDownloadStatusCheck(config: DownloaderConfig) {
        subs.add(
            checkDownloadStatus()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { status -> when (status) {
                    DownloadStatus.READY -> {
                        statusEvents.onNext(DownloadState.Ready())
                    }

                    DownloadStatus.UNKNOWN -> {
                        statusEvents.onNext(DownloadState.Checking())
                    }

                    else -> {
                        statusEvents.onNext(DownloadState.Unavailable())

                        // TODO forward messages from the Service to [statusEvents]
                        // TODO raise notification immediately?
                        activity?.let { context ->
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