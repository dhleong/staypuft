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
     * We have no idea, yet
     */
    class Checking : DownloadState()

    /**
     * Download in progress
     */
    class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : DownloadState()

    /**
     * One or both expansion files are not yet available,
     * no download is in progress yet
     */
    class Unavailable : DownloadState()
}