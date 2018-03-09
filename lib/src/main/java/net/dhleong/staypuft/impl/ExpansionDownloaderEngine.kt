package net.dhleong.staypuft.impl

import android.os.Build
import android.util.Log
import com.google.android.vending.licensing.APKExpansionPolicy
import com.google.android.vending.licensing.Policy
import io.reactivex.Completable
import io.reactivex.Observable
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
    private val tracker: DownloadsTracker = DownloadsTracker(service.getApplicationContext()),
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
        notifier: Notifier
    ): Completable {

        val policy = service.createPolicy(config)
        return service.checkLicenseAccess(config, policy)
            .subscribeOn(Schedulers.io())
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

                is LicenceCheckerResult.Allowed -> updateLVLCache(policy)
            } }
            .flatMapCompletable { files ->
                throwIfNotRunning()

                Observable.fromIterable(files)
                    .flatMapCompletable {
                        downloadFile(it, notifier)
                    }
            }
            .doOnComplete {
                // completed successfully
                notifier.done()
                uiProxy.done()
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
            }
    }

    private fun updateLVLCache(
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

        val files = (0 until urls).map { i ->
            tracker.getKnownDownload(i)?.copy(
                name = policy.getExpansionFileName(i),
                size = policy.getExpansionFileSize(i),
                url = policy.getExpansionURL(i)
            ) ?: ExpansionFile(
                isMain = i == 0,
                name = policy.getExpansionFileName(i),
                size = policy.getExpansionFileSize(i),
                url = policy.getExpansionURL(i),
                downloaded = 0,
                etag = null
            )
        }

        files.forEach(tracker::save)

        tracker.markUpdated(service.getApplicationContext())

        files
    }

    private fun downloadFile(file: ExpansionFile, notifier: Notifier): Completable = Completable.fromAction {
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
        downloadResponseTo(file, conn, dest, notifier)
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

        FileOutputStream(dest).use { out ->
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
            throw ApkExpansionException(Notifier.STATE_PAUSED_NETWORK_UNAVAILABLE)
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