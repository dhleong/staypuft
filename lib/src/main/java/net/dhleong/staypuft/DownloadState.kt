package net.dhleong.staypuft

import java.io.File

/**
 * @author dhleong
 */
sealed class DownloadState {

    /**
     * All files are downloaded and available
     */
    class Ready(
        files: List<File>
    ) : DownloadState() {

        val main: File = files[0]
        val patch: File? = files.elementAtOrNull(1)

    }

    /**
     * Download is paused, due to the [reason] provided,
     * where [reason] will be one of the `STATE_` constants
     * on [Notifier]
     */
    class Paused(
        val reason: Int
    ) : DownloadState()

    /**
     * We have no idea, yet
     */
    class Checking : DownloadState()

    /**
     * Download is in progress
     */
    class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : DownloadState()

    /**
     * One or both expansion files are not yet available,
     * no download is in progress yet. As with [Paused],
     * the [reason] will be one of the `STATE_` constants
     * on [Notifier]
     */
    class Unavailable(
        val reason: Int
    ): DownloadState()
}
