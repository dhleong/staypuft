package net.dhleong.staypuft

import android.app.Activity
import android.support.annotation.StringRes
import net.dhleong.staypuft.impl.StaypuftFragment



/**
 * @author dhleong
 */
class Staypuft private constructor(
    private val fragment: StaypuftFragment
) {

    fun setConfig(config: DownloaderConfig) {
        fragment.setConfig(config)
    }

    companion object {
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
            Notifier.STATE_PAUSED_WIFI_DISABLED -> R.string.state_paused_wifi_disabled
            Notifier.STATE_PAUSED_NEED_WIFI -> R.string.state_paused_wifi_unavailable
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