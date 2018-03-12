package net.dhleong.staypuft.impl

/**
 * @author dhleong
 */
internal interface DownloadStatus {
    companion object {
        const val UNKNOWN = 1

        /**
         * We need to check with the LVL service before we can
         *  know for certain the actual status. This probably
         *  means the user just updated the APK.
         */
        const val LVL_CHECK_REQUIRED = 2

        /**
         * We need to download one or both files
         */
        const val DOWNLOAD_NEEDED = 3

        /**
         * Everything is up-to-date and ready to go
         */
        const val READY = 4
    }
}