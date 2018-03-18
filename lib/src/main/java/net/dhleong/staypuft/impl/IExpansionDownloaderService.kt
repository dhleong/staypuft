package net.dhleong.staypuft.impl

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.google.android.vending.licensing.AESObfuscator
import com.google.android.vending.licensing.APKExpansionPolicy
import com.google.android.vending.licensing.LicenseChecker
import io.reactivex.Single
import net.dhleong.staypuft.DownloaderConfig
import net.dhleong.staypuft.rx.LicenceCheckerResult
import net.dhleong.staypuft.rx.checkAccess
import net.dhleong.staypuft.rx.getAvailableBytes
import net.dhleong.staypuft.rx.getExpansionFilesDirectory
import net.dhleong.staypuft.rx.getFilesystemRoot
import net.dhleong.staypuft.rx.getPackageInfo
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * @author dhleong
 */
interface IExpansionDownloaderService : IHasSaveDirectory {
    fun getApplicationContext(): Context

    fun getPackageName(): String

    fun checkLicenseAccess(
        config: DownloaderConfig,
        policy: APKExpansionPolicy
    ): Single<LicenceCheckerResult> {
        val checker = LicenseChecker(getApplicationContext(), policy, config.publicKey)
        return checker.checkAccess()
    }

    /**
     * create the APKExpansionPolicy
     */
    fun createPolicy(config: DownloaderConfig): APKExpansionPolicy {
        val context = getApplicationContext()

        @SuppressLint("HardwareIds")
        val deviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )

        return APKExpansionPolicy(context, AESObfuscator(
            config.salt,
            getPackageName(),
            deviceId
        ))
    }

    fun openUrl(url: String): HttpURLConnection =
        URL(url).openConnection() as HttpURLConnection

    /**
     * Get the number of available bytes on the Filesystem
     *  containing the given path
     */
    fun getAvailableBytes(path: File): Long =
        path.getFilesystemRoot().getAvailableBytes()

    override fun getExpansionFilesDirectory(): File =
        getApplicationContext().getExpansionFilesDirectory()

    fun getApkVersionCode(): Int =
        getApplicationContext().getPackageInfo().versionCode
}