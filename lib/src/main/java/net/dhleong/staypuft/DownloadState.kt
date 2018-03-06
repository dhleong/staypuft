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
     * One or both expansion files are not yet available,
     * no download is in progress yet
     */
    class Unavailable : DownloadState()
}