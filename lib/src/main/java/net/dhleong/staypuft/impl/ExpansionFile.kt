package net.dhleong.staypuft.impl

import java.io.File

data class ExpansionFile(
    val isMain: Boolean,
    val name: String,
    val size: Long,
    val url: String,
    var downloaded: Long,
    var etag: String?
) {

    fun checkLocalExists(
        context: IHasSaveDirectory,
        expectedSize: Long = size,
        deleteOnSizeMismatch: Boolean = false
    ): Boolean {
        val file = localFile(context)
        if (!file.exists()) return false
        if (file.length() == expectedSize) {
            return true
        }

        if (deleteOnSizeMismatch) {
            // delete the file --- we won't be able to resume
            // because we cannot confirm the integrity of the file
            file.delete()
        }
        return false
    }

    fun localFile(context: IHasSaveDirectory): File =
        localFile(context.getExpansionFilesDirectory())

    fun localFile(saveDirectory: File): File = File(saveDirectory, name)

    /**
     * Temporary path where we should initially download the file
     */
    fun localTmpFile(context: IHasSaveDirectory): File =
        File(
            context.getExpansionFilesDirectory(),
            "$name.$TMP_EXT"
        )

    companion object {
        private const val TMP_EXT = "tmp"
    }
}