package net.dhleong.staypuft

import android.app.job.JobInfo
import android.os.PersistableBundle
import java.util.Arrays

/**
 * @author dhleong
 */
data class DownloaderConfig(
    val salt: ByteArray,
    val publicKey: String,
    val notifier: Notifier.Factory.Config,
    val canUseCellularData: Boolean = false,
    val jobId: Int = 78297838
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DownloaderConfig) return false

        if (!Arrays.equals(salt, other.salt)) return false
        if (publicKey != other.publicKey) return false
        if (canUseCellularData != other.canUseCellularData) return false
        if (notifier != other.notifier) return false
        if (jobId != other.jobId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = Arrays.hashCode(salt)
        result = 31 * result + publicKey.hashCode()
        result = 31 * result + canUseCellularData.hashCode()
        result = 31 * result + notifier.hashCode()
        result = 31 * result + jobId
        return result
    }

    val requiredNetworkType: Int
        get() = if (canUseCellularData) {
            JobInfo.NETWORK_TYPE_UNMETERED
        } else JobInfo.NETWORK_TYPE_ANY

    fun toPeristableBundle(): PersistableBundle = PersistableBundle().apply {
        putIntArray("salt", IntArray(salt.size) {
            salt[it].toInt()
        })

        putString("publicKey", publicKey)
        putInt("canUseCellularData", if (canUseCellularData) 1 else 0)
        putInt("jobId", jobId)

        putString("notifierClass", notifier.klass.name)
        putPersistableBundle("notifierArg", notifier.arg)
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
            notifier = Notifier.Factory.Config.from(
                bundle.getString("notifierClass"),
                bundle.getPersistableBundle("notifierArg")
            )
        )
    }
}