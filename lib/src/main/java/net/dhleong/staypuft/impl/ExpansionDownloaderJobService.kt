package net.dhleong.staypuft.impl

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import io.reactivex.disposables.CompositeDisposable
import net.dhleong.staypuft.DownloaderConfig

/**
 * @author dhleong
 */
class ExpansionDownloaderJobService : JobService(), IExpansionDownloaderService {

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
        val config = DownloaderConfig.inflate(params.extras)
        val notifier = config.inflateNotifier(this)

        engine.start()
        subs.add(
            engine.processDownload(config, notifier)
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

    companion object {
        fun start(context: Context, config: DownloaderConfig) {
            val jobs = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            jobs.schedule(
                JobInfo.Builder(
                    config.jobId,
                    ComponentName(context, ExpansionDownloaderJobService::class.java)
                ).apply {

                    setExtras(config.toPersistableBundle())
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