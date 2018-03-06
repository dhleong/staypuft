package net.dhleong.staypuft.impl

import android.annotation.SuppressLint
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.android.vending.licensing.AESObfuscator
import com.google.android.vending.licensing.APKExpansionPolicy
import com.google.android.vending.licensing.LicenseChecker
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import net.dhleong.staypuft.DownloaderConfig
import net.dhleong.staypuft.Notifier
import net.dhleong.staypuft.rx.LicenceCheckerResult
import net.dhleong.staypuft.rx.checkAccess
import net.dhleong.staypuft.rx.getAvailableBytes
import net.dhleong.staypuft.rx.getFilesystemRoot
import net.dhleong.staypuft.rx.getSaveDirectory
import java.io.File

/**
 * @author dhleong
 */
class ExpansionDownloaderService : JobService(), IExpansionDownloaderService {

    private var config: DownloaderConfig? = null
    private var notifier: Notifier? = null
    private lateinit var engine: ExpansionDownloaderEngine

    private val subs = CompositeDisposable()

    override fun onCreate() {
        super.onCreate()

        engine = ExpansionDownloaderEngine(this)
    }

    override fun onStopJob(params: JobParameters): Boolean {
        engine.stop()
        subs.clear()
        return true
    }

    override fun onStartJob(params: JobParameters): Boolean {
        if (config == null) {
            config = DownloaderConfig.inflate(params.extras).also { config ->
                notifier = config.notifier.inflate(this)
            }
        }

        engine.start()
        engine.processDownload(config!!, notifier!!) { needsReschedule ->
            jobFinished(params, needsReschedule)
        }

        return true
    }

    override fun checkLicenseAccess(
        config: DownloaderConfig,
        policy: APKExpansionPolicy
    ): Single<LicenceCheckerResult> {
        val checker = LicenseChecker(applicationContext, policy, config.publicKey)
        return checker.checkAccess()
    }

    override fun createPolicy(config: DownloaderConfig): APKExpansionPolicy {
        @SuppressLint("HardwareIds")
        val deviceId = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ANDROID_ID
        )

        return APKExpansionPolicy(applicationContext, AESObfuscator(
            config.salt,
            packageName,
            deviceId
        ))
    }

    override fun getSaveDirectory(): File {
        return (this as Context).getSaveDirectory()
    }

    override fun getAvailableBytes(path: File): Long =
        path.getFilesystemRoot().getAvailableBytes()

    companion object {
        fun start(context: Context, config: DownloaderConfig) {
            val jobs = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            jobs.schedule(
                JobInfo.Builder(
                    config.jobId,
                    ComponentName(context, ExpansionDownloaderService::class.java)
                ).apply {

                    setExtras(config.toPeristableBundle())
                    setRequiredNetworkType(config.requiredNetworkType)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setRequiresBatteryNotLow(true)
                        setRequiresStorageNotLow(true)
                    }
                }.build()
            )
        }
    }
}