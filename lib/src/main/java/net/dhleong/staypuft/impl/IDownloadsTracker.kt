package net.dhleong.staypuft.impl

/**
 * @author dhleong
 */
interface IDownloadsTracker {
    fun needsUpdate(): Boolean
    fun markUpdated(service: IExpansionDownloaderService)
    fun getKnownDownload(index: Int): ExpansionFile?
    fun getKnownDownloads(): Sequence<ExpansionFile>
    fun save(expansionFile: ExpansionFile)
    fun deleteFile(index: Int)
}