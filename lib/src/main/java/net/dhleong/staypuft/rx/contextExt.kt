package net.dhleong.staypuft.rx

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.os.Environment
import java.io.File

/**
 * @author dhleong
 */
fun Context.getPackageInfo(): PackageInfo = packageManager.getPackageInfo(
    packageName, 0
)

/**
 * Get the name (NOT full path) to the expansion file of the given
 *  type (main/patch) with the given [versionCode]
 */
fun Context.getExpansionFileName(mainFile: Boolean, versionCode: Int): String {
    val baseName =
        if (mainFile) "main"
        else "patch"
    return "$baseName.$versionCode.$packageName.obb"
}

/**
 * Get a [File] pointing to the directory where expansion files will be saved
 */
fun Context.getSaveDirectory(): File = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
    // This technically existed since Honeycomb, but it is critical
    // on KitKat and greater versions since it will create the
    // directory if needed
    obbDir
} else {
    File(
        File(
            File(
                Environment.getExternalStorageDirectory(),
                "Android"
            ),
            "obb"
        ),
        packageName
    )
}