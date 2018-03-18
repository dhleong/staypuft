package net.dhleong.staypuft

import android.app.Activity
import android.support.annotation.StringRes
import io.reactivex.Observable
import net.dhleong.staypuft.impl.StaypuftFragment

/**
 * Primary entry point to [Staypuft] APK expansion file
 *  downloads. Sample usage:
 *
 *  ```
 *  val apkx = Staypuft.getInstance(activity)
 *      .setConfig(
 *          DownloadConfig(
 *              salt = // your custom salt array
 *              publicKey = "YOUR_PUBLIC_KEY base64",
 *              notifier = DefaultNotifier.withChannelId("expansions")
 *          )
 *      )
 *  apkx.stateEvents.subscribe { event ->
 *      when (event) {
 *          is DownloadState.Ready -> {
 *              println("Got main expansion file at: ${event.main}")
 *          }
 *      }
 *  }
 *  ```
 *
 * @author dhleong
 */
class Staypuft private constructor(
    private val fragment: StaypuftFragment
) {

    /**
     * Initialize a download request using the provided [DownloaderConfig],
     *  or update an existing request (to allow cellular data usage, for example)
     */
    fun setConfig(config: DownloaderConfig): Staypuft {
        fragment.setConfig(config)
        return this
    }

    /**
     * An observable stream of [DownloadState]
     */
    val stateEvents: Observable<DownloadState>
        get() = fragment.stateEvents

    companion object {
        /**
         * Get a new instance of [Staypuft] from the given Activity context.
         *  Each call may create a new [Staypuft] instance, but they will
         *  generally share internal state by relying on a retained fragment.
         *
         * NOTE: This does mean, however, that calling [getInstance] twice
         *  in a row will result in the first one not getting state updates,
         *  since it will have an old fragment that got replaced by the
         *  second's (thanks to fragment transactions).
         */
        fun getInstance(activity: Activity): Staypuft {
            val fm = activity.fragmentManager
            val existing = fm.findFragmentByTag(StaypuftFragment.TAG)
            if (existing is StaypuftFragment) {
                return Staypuft(existing)
            }

            val new = StaypuftFragment()
            fm.beginTransaction()
                .add(new, StaypuftFragment.TAG)
                .commit()
            return Staypuft(new)
        }

        /**
         * Given a [state] constant (see const fields on [Notifier]),
         *  get a string resource that describes that state.
         */
        @StringRes
        fun getStringResForState(state: Int): Int = when (state) {
            Notifier.STATE_IDLE -> R.string.state_idle
            Notifier.STATE_FETCHING_URL -> R.string.state_fetching_url
            Notifier.STATE_CONNECTING -> R.string.state_connecting
            Notifier.STATE_DOWNLOADING -> R.string.state_downloading
            Notifier.STATE_COMPLETED -> R.string.state_completed
            Notifier.STATE_PAUSED_NETWORK_UNAVAILABLE -> R.string.state_paused_network_unavailable
            Notifier.STATE_PAUSED_BY_REQUEST -> R.string.state_paused_by_request
            Notifier.STATE_PAUSED_WIFI_DISABLED_NEED_CELLULAR_PERMISSION -> R.string.state_paused_wifi_disabled
            Notifier.STATE_PAUSED_NEED_CELLULAR_PERMISSION -> R.string.state_paused_wifi_unavailable
            Notifier.STATE_PAUSED_ROAMING -> R.string.state_paused_roaming
            Notifier.STATE_PAUSED_NETWORK_SETUP_FAILURE -> R.string.state_paused_network_setup_failure
            Notifier.STATE_PAUSED_SDCARD_UNAVAILABLE -> R.string.state_paused_sdcard_unavailable
            Notifier.STATE_FAILED_UNLICENSED -> R.string.state_failed_unlicensed
            Notifier.STATE_FAILED_FETCHING_URL -> R.string.state_failed_fetching_url
            Notifier.STATE_FAILED_SDCARD_FULL -> R.string.state_failed_sdcard_full
            Notifier.STATE_FAILED_CANCELED -> R.string.state_failed_cancelled

            else -> R.string.state_unknown
        }
    }
}