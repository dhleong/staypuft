package net.dhleong.staypuft

import android.app.job.JobInfo
import android.content.Context
import android.os.Parcelable
import android.os.PersistableBundle
import kotlinx.android.parcel.Parcelize
import java.util.Arrays

/**
 * [DownloaderConfig] is how you configure Staypuft for use in your app.
 * Where possible, sensible default values are provided, but you must
 * provide your own [salt] and [publicKey] for security. Your [publicKey]
 * is the one provided by Google's licensing service from the Play Store
 * console.
 *
 * You should NOT pass a `true` value for [canUseCellularData] unless the
 *  user has explicitly approved it. If you do, you may be wasting a
 *  significant amount of their monthly data budget!
 *
 * @author dhleong
 * @see [Notifier] and [DefaultNotifier]
 */
@Parcelize
data class DownloaderConfig(
    val salt: ByteArray,
    val publicKey: String,
    val notifier: Notifier.Factory.Config,
    val canUseCellularData: Boolean = false,
    val jobId: Int = 78297838,
    val notificationId: Int = 78297838
) : Parcelable {

    val requiredNetworkType: Int
        get() = if (canUseCellularData) {
            JobInfo.NETWORK_TYPE_ANY
        } else JobInfo.NETWORK_TYPE_UNMETERED

    /**
     * Convenience to instantiate the configured [Notifier]
     */
    fun inflateNotifier(context: Context): Notifier = notifier.inflate(context, this)

    /**
     * For when [Parcelable] isn't enough, this method will serialize
     *  this config to a [PersistableBundle], which can later be inflated
     *  using the static [inflate] method.
     */
    fun toPersistableBundle(): PersistableBundle = PersistableBundle().apply {
        putIntArray("salt", IntArray(salt.size) {
            salt[it].toInt()
        })

        putString("publicKey", publicKey)
        putInt("canUseCellularData", if (canUseCellularData) 1 else 0)
        putInt("jobId", jobId)
        putInt("notificationId", notificationId)

        putString("notifierClass", notifier.klass.name)
        putPersistableBundle("notifierArg", notifier.arg)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DownloaderConfig) return false

        if (!Arrays.equals(salt, other.salt)) return false
        if (publicKey != other.publicKey) return false
        if (canUseCellularData != other.canUseCellularData) return false
        if (notifier != other.notifier) return false
        if (jobId != other.jobId) return false
        if (notificationId != other.notificationId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = Arrays.hashCode(salt)
        result = 31 * result + publicKey.hashCode()
        result = 31 * result + canUseCellularData.hashCode()
        result = 31 * result + notifier.hashCode()
        result = 31 * result + jobId
        result = 31 * result + notificationId
        return result
    }

    companion object {
        fun inflate(bundle: PersistableBundle): DownloaderConfig = DownloaderConfig(
            salt = bundle.getIntArray("salt")!!.let { intArray ->
                ByteArray(intArray.size) {
                    intArray[it].toByte()
                }
            },
            publicKey = bundle.getString("publicKey"),
            canUseCellularData = bundle.getInt("canUseCellularData") != 0,
            jobId = bundle.getInt("jobId"),
            notificationId = bundle.getInt("notificationId"),
            notifier = Notifier.Factory.Config.from(
                bundle.getString("notifierClass"),
                bundle.getPersistableBundle("notifierArg")
            )
        )
    }
}
