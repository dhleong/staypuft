package net.dhleong.staypuft.impl

import android.content.Context
import android.content.SharedPreferences
import net.dhleong.staypuft.rx.getPackageInfo
import kotlin.coroutines.experimental.buildSequence

/**
 * @author dhleong
 */
internal open class PrefsDownloadsTracker(
    private val prefs: SharedPreferences,
    private val apkVersionCode: Int
) : IDownloadsTracker {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        context.getPackageInfo().versionCode
    )

    override fun needsUpdate(): Boolean =
        prefs.getInt(APK_VERSION, 0) < apkVersionCode

    override fun markUpdated(service: IExpansionDownloaderService) {
        prefs.edit()
            .putInt(APK_VERSION, service.getApkVersionCode())
            .apply()
    }

    override fun getKnownDownload(index: Int): ExpansionFile? {
        return if (index == 0) {
            val mainName = prefs.getString(MAIN_NAME, null)
            mainName?.let {
                ExpansionFile(
                    isMain = true,
                    name = mainName,
                    url = prefs.getString(MAIN_URL, ""),
                    size = prefs.getLong(MAIN_SIZE, 0L),
                    downloaded = prefs.getLong(MAIN_DOWNLOADED, 0L),
                    etag = prefs.getString(MAIN_ETAG, null)
                )
            }
        } else {
            val patchName = prefs.getString(PATCH_NAME, null)
            patchName?.let {
                ExpansionFile(
                    isMain = false,
                    name =  patchName,
                    url = prefs.getString(PATCH_URL, ""),
                    size = prefs.getLong(PATCH_SIZE, 0L),
                    downloaded = prefs.getLong(PATCH_DOWNLOADED, 0L),
                    etag = prefs.getString(PATCH_ETAG, null)
                )
            }
        }
    }

    override fun getKnownDownloads(): Sequence<ExpansionFile> = buildSequence {
        getKnownDownload(0)?.let { yield(it) } ?: return@buildSequence
        getKnownDownload(1)?.let { yield(it) }
    }

    override fun save(expansionFile: ExpansionFile) {
        prefs.edit().apply {

            if (expansionFile.isMain) {
                putString(MAIN_NAME, expansionFile.name)
                putString(MAIN_URL, expansionFile.url)
                putLong(MAIN_SIZE, expansionFile.size)
                putLong(MAIN_DOWNLOADED, expansionFile.downloaded)
                putString(MAIN_ETAG, expansionFile.etag)
            } else {
                putString(PATCH_NAME, expansionFile.name)
                putString(PATCH_URL, expansionFile.url)
                putLong(PATCH_SIZE, expansionFile.size)
                putLong(PATCH_DOWNLOADED, expansionFile.downloaded)
                putString(PATCH_ETAG, expansionFile.etag)
            }

        }.apply()
    }

    override fun deleteFile(index: Int) {
        prefs.edit().apply {
            remove(
                if (index == 0) MAIN_NAME
                else PATCH_NAME
            )
        }.apply()
    }

    companion object {
        private const val PREFS_NAME = "staypuft"
        private const val APK_VERSION = "apk-version"

        private const val MAIN_NAME = "main-name"
        private const val MAIN_SIZE = "main-size"
        private const val MAIN_DOWNLOADED = "main-downloaded"
        private const val MAIN_ETAG = "main-etag"
        private const val MAIN_URL = "main-url"

        private const val PATCH_NAME = "patch-name"
        private const val PATCH_SIZE = "patch-size"
        private const val PATCH_DOWNLOADED = "patch-downloaded"
        private const val PATCH_ETAG = "patch-etag"
        private const val PATCH_URL = "main-url"
    }
}

