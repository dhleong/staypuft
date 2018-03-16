package net.dhleong.staypuft

import android.app.job.JobInfo
import android.content.Context
import android.os.Parcelable
import android.os.PersistableBundle
import kotlinx.android.parcel.Parcelize
import java.util.Arrays

/**
 * @author dhleong
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
            JobInfo.NETWORK_TYPE_UNMETERED
        } else JobInfo.NETWORK_TYPE_ANY

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