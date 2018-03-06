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