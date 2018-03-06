package net.dhleong.staypuft.rx

import android.os.Environment
import android.os.StatFs
import java.io.File





/**
 * @author dhleong
 */
fun File.getFilesystemRoot(): File {
    val cache = Environment.getDownloadCacheDirectory()
    if (path.startsWith(cache.path)) {
        return cache
    }

    val external = Environment.getExternalStorageDirectory()
    if (path.startsWith(external.path)) {
        return external
    }

    throw IllegalArgumentException(
        "Cannot determine filesystem root for $this"
    )
}

/**
 * @return the number of bytes available on the filesystem
 *  rooted at this File
 */
fun File.getAvailableBytes(): Long {
    val stat = StatFs(path)
    // put a bit of margin (in case creating the file grows the system by a
    // few blocks)
    val availableBlocks = stat.availableBlocksLong - 4
    return stat.blockSizeLong * availableBlocks

}