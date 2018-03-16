package net.dhleong.staypuft.impl

import android.os.Build
import android.util.Log
import com.google.android.vending.licensing.APKExpansionPolicy
import com.google.android.vending.licensing.Policy
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import net.dhleong.staypuft.ApkExpansionException
import net.dhleong.staypuft.DownloaderConfig
import net.dhleong.staypuft.Notifier
import net.dhleong.staypuft.rx.LicenceCheckerResult
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author dhleong
 */
internal class ExpansionDownloaderEngine(
    private val service: IExpansionDownloaderService,
    private val uiProxy: UIProxy,
    private val tracker: IDownloadsTracker = PrefsDownloadsTracker(service.getApplicationContext()),
    private val bufferSize: Int = 4096
) {

    private val userAgent: String by lazy(LazyThreadSafetyMode.NONE) {
        "APKXDL (Linux; U; Android ${Build.VERSION.RELEASE}; ${Locale.getDefault()}; ${Build.DEVICE}/${Build.ID}) ${service.getPackageName()}"
    }

    private val running = AtomicBoolean(true)

    /**
     * Completes if successful, else errors if it should be retried
     */
    fun processDownload(
        config: DownloaderConfig,
        notifier: Notifier,
        scheduler: Scheduler = Schedulers.io()
    ): Completable = Completable.defer {

        val policy = service.createPolicy(config)

        // reset the policy to force a re-check
        policy.resetPolicy()

        service.checkLicenseAccess(config, policy)
            .subscribeOn(scheduler)
            .map {
                throwIfNotRunning()
                it
            }
            .flatMap { result -> when (result) {
                is LicenceCheckerResult.Error ->
                    Single.error(ApkExpansionException(
                        Notifier.STATE_FAILED_FETCHING_URL
                    ))

                is LicenceCheckerResult.NotAllowed ->
                    Single.error(ApkExpansionException(when (result.reason) {
                        Policy.NOT_LICENSED -> Notifier.STATE_FAILED_UNLICENSED
                        Policy.RETRY -> Notifier.STATE_FAILED_FETCHING_URL

                        else -> Notifier.STATE_FAILED
                    }))

                is LicenceCheckerResult.Allowed ->
                    updateLVLCacheAndGetFilesToDownload(policy)
            } }
            .flatMap { files ->
                throwIfNotRunning()

                Observable.fromIterable(files)
                    .flatMapSingle {
                        downloadFile(it, notifier)
                    }.toList()
            }
            .doOnSuccess { files ->
                // completed successfully
                notifier.done()
                uiProxy.done(files)
            }
            .doOnError { e ->
                // failed, or paused
                when (e) {
                    is ApkExpansionException -> {
                        notifier.error(e)
                        uiProxy.error(e)
                    }
                    is IOException -> {
                        Log.e(TAG, "IOE downloading APK expansion", e)
                        val err = ApkExpansionException(Notifier.STATE_PAUSED_NETWORK_UNAVAILABLE)
                        notifier.error(err)
                        uiProxy.error(err)
                    }
                    else -> {
                        // TODO ?
                        Log.e(TAG, "Unexpected error downloading APK expansion", e)
                    }
                }
            }.toCompletable()
    }

    /**
     * This obnoxiously named function updates the LVL cache as necessary,
     *  and returns each [ExpansionFile] that is not already up to date.
     */
    private fun updateLVLCacheAndGetFilesToDownload(
        policy: APKExpansionPolicy
    ): Single<List<ExpansionFile>> = Single.fromCallable {
        val urls = policy.expansionURLCount
        when (urls) {
            0 -> {
                tracker.deleteFile(0)
                tracker.deleteFile(1)
            }
            1 -> {
                tracker.deleteFile(1)
            }
        }

        (0 until urls).mapNotNull { i ->
            val old = tracker.getKnownDownload(i)
            val new = ExpansionFile(
                isMain = i == 0,
                name = policy.getExpansionFileName(i),
                size = policy.getExpansionFileSize(i),
                url = policy.getExpansionURL(i),
                downloaded = 0,
                etag = null
            )

            val localExists = new.checkLocalExists(service)
            val nameMatches = old?.name == new.name
            val sizeMatches = old?.size == new.size
            if (old != null && nameMatches && sizeMatches && !localExists
                && old.downloaded < old.size)  {
                // attempt to continue downloading the old file
                old
            } else if (old == null || !nameMatches || !sizeMatches || !localExists) {
                // return and save the new file
                new
            } else {
                // nothing changed
                null
            }
        }.onEach {
            tracker.save(it)
        }.filter {
            it.downloaded < it.size
        }.also {
            tracker.markUpdated(service)
        }
    }

    private fun downloadFile(file: ExpansionFile, notifier: Notifier): Single<ExpansionFile> = Single.fromCallable {
        val dest = prepareDestFile(file)

        val conn = service.openUrl(file.url)
        prepareConnection(file, conn)
        throwIfNotRunning()

        notifier.statusChanged(Notifier.STATE_CONNECTING)
        uiProxy.statusChanged(Notifier.STATE_CONNECTING)
        val response = try {
            conn.responseCode
        } catch (e: IOException) {
            throw ApkExpansionException(Notifier.STATE_PAUSED_NETWORK_UNAVAILABLE)
        }
        throwOnExceptionalResponse(file, response)
        throwIfNotRunning()

        processResponseHeaders(file, conn)
        notifier.statusChanged(Notifier.STATE_DOWNLOADING)
        uiProxy.statusChanged(Notifier.STATE_DOWNLOADING)
        try {
            downloadResponseTo(file, conn, dest, notifier)
        } finally {
            conn.disconnect()
        }

        finalizeDownload(file, dest)

        file
    }

    private fun prepareDestFile(file: ExpansionFile): File {
        val path = file.localTmpFile(service)
        if (path.exists()) {

            // check if we can continue the download
            val fileLength = path.length()
            if (fileLength != file.downloaded || file.etag == null) {
                // file size mismatch, or empty etag; start from scratch
                file.etag = null
                file.downloaded = 0
                path.delete()
            }

        }

        if (service.getAvailableBytes(path) < file.size) {
            throw ApkExpansionException(Notifier.STATE_FAILED_SDCARD_FULL)
        }

        return path
    }

    private fun prepareConnection(file: ExpansionFile, conn: HttpURLConnection) {
        conn.setRequestProperty("User-Agent", userAgent)

        if (file.downloaded > 0) {
            if (file.etag != null) {
                conn.setRequestProperty("If-Match", file.etag)
            }
            conn.setRequestProperty("Range", "bytes=${file.downloaded}-")
        }
    }

    private fun processResponseHeaders(file: ExpansionFile, conn: HttpURLConnection) {
        if (file.etag != null && file.downloaded > 0) {
            // we're resuming a download; there's nothing new to process
            return
        }

        file.etag = conn.getHeaderField("ETag")

        conn.contentType?.let { contentType ->
            if (contentType != "application/vnd.android.obb") {
                throw ApkExpansionException(Notifier.STATE_PAUSED_NETWORK_SETUP_FAILURE)
            }
        }

        val transferEncoding = conn.getHeaderField("Transfer-Encoding")
        if (transferEncoding == null) {
            val contentLength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                conn.contentLengthLong
            } else {
                conn.contentLength.toLong()
            }

            if (contentLength != file.size) {
                throw ApkExpansionException(
                    Notifier.STATE_PAUSED_NETWORK_SETUP_FAILURE,
                    "Incorrect file size delivered"
                )
            }

        } else {
            // NOTE: the official APKX lib ignores content length if Transfer-Encoding
            // is specified, with this cryptic note: 2616 4.4 3

            if (!transferEncoding.equals("chunked", ignoreCase = true)) {
                throw ApkExpansionException(
                    Notifier.STATE_PAUSED_NETWORK_SETUP_FAILURE,
                    "Unable to verify download size"
                )
            }
        }
    }

    private fun downloadResponseTo(
        file: ExpansionFile,
        conn: HttpURLConnection,
        dest: File,
        notifier: Notifier
    ) {
        var bytesNotified = 0L
        var timeNotified = 0L

        FileOutputStream(dest, /* append = */true).use { out ->
            conn.inputStream.use { input ->
                val buffer = ByteArray(bufferSize)
                while (true) {
                    // make sure we've not been paused
                    if (!running.get()) {
                        // save state eagerly
                        out.flush()
                        tracker.save(file)

                        // this will throw:
                        throwIfNotRunning()
                    }

                    val read = input.read(buffer)
                    if (read == -1) {
                        // success! end of stream reached
                        break
                    }

                    out.write(buffer, 0, read)
                    out.flush()

                    file.downloaded += read

                    val now = System.currentTimeMillis()
                    if (file.downloaded - bytesNotified > MIN_PROGRESS_BYTES
                            && now - timeNotified > MIN_PROGRESS_TIME) {
                        bytesNotified = file.downloaded
                        timeNotified = now

                        tracker.save(file)
                        notifier.progress(file.downloaded, file.size)
                        uiProxy.progress(file.downloaded, file.size)
                    }
                }
            }
        }
    }

    private fun finalizeDownload(file: ExpansionFile, tempLocation: File) {
        if (file.downloaded != file.size) {
            throw ApkExpansionException(Notifier.STATE_PAUSED_NETWORK_SETUP_FAILURE,
                "file delivered with ${file.downloaded} bytes but expected ${file.size}")
        }

        val finalDestination = file.localFile(service)
        if (!tempLocation.renameTo(finalDestination)) {
            throw ApkExpansionException(Notifier.STATE_PAUSED_SDCARD_UNAVAILABLE,
                "Unable to finalize $tempLocation to $finalDestination")
        }
    }

    private fun throwIfNotRunning() {
        if (!running.get()) {
            throw ApkExpansionException(Notifier.STATE_PAUSED_BY_REQUEST)
        }
    }

    private fun throwOnExceptionalResponse(file: ExpansionFile, responseCode: Int) {
        val expected = if (file.downloaded > 0 && file.etag != null) {
            206 // "partial content"
        } else 200

        if (responseCode != expected) {
            // TODO better state?
            throw ApkExpansionException(Notifier.STATE_PAUSED_NETWORK_UNAVAILABLE,
                "Expected $expected from ${file.url} but got $responseCode"
            )
        }
    }

    fun stop() {
        running.set(false)
    }

    fun start() {
        running.set(true)
    }

    companion object {
        private const val TAG = "staypuft_engine"

        /**
         * The minimum amount of progress that has to be done before the
         *  progress bar gets updated
         */
        private const val MIN_PROGRESS_BYTES = 4096

        /**
         * The minimum amount of time (ms) that has to elapse before the
         *  progress bar gets updated
         */
        private const val MIN_PROGRESS_TIME = 1000L
    }
}