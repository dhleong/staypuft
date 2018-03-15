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
import android.util.Log
import com.google.android.vending.licensing.AESObfuscator
import com.google.android.vending.licensing.APKExpansionPolicy
import com.google.android.vending.licensing.LicenseChecker
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import net.dhleong.staypuft.DownloaderConfig
import net.dhleong.staypuft.Notifier
import net.dhleong.staypuft.rx.LicenceCheckerResult
import net.dhleong.staypuft.rx.checkAccess

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

        engine = ExpansionDownloaderEngine(
            this,
            DefaultUIProxy(this)
        )
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
        subs.add(
            engine.processDownload(config!!, notifier!!)
                .toSingleDefault(false)
                .onErrorReturn { e ->
                    Log.w("staypuft", "Error processing download", e)
                    true
                }
                .subscribe { needsReschedule ->
                    jobFinished(params, needsReschedule)
                }
        )

        return true // going async
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