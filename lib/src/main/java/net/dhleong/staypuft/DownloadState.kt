package net.dhleong.staypuft

/**
 * @author dhleong
 */
sealed class DownloadState {
    /**
     * All files are downloaded and available
     */
    class Ready : DownloadState()

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