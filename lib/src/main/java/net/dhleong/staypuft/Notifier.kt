package net.dhleong.staypuft

import android.app.Notification
import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.os.PersistableBundle

class ApkExpansionException(
    /**
     * One of the [Notifier] constants
     */
    val state: Int,
    message: String? = "Error [$state]"
) : Exception(message)

/**
 * [Notifier] is used to notify the user of the current download
 *  status via [Notification] so they don't need to be in the app
 *  while it happens. To provide a custom [Notifier], you will also
 *  need to implement a [Notifier.Factory], and pass an instance of
 *  [Notifier.Factory.Config] to your [DownloaderConfig]. This is
 *  all a bit squirrely due to the use of [android.app.job.JobScheduler]
 *  if unable to complete theh download right away.
 *
 * A default implementation is provided via [DefaultNotifier], and a
 *  [Notifier.Factory.Config] may be created for it using the
 *  static factory method [DefaultNotifier.withChannelId].
 *
 * @author dhleong
 */
interface Notifier {

    fun done()
    fun progress(downloaded: Long, size: Long)
    fun statusChanged(state: Int)
    fun error(e: ApkExpansionException)

    /**
     * Build a notification for the given [state]. This
     *  will probably only be used for [android.app.Service.startForeground].
     */
    fun build(state: Int): Notification

    /**
     * A [Notifier.Factory] is used to instantiate a [Factory] from a
     *  [PersistableBundle]. This is used instead of the more flexible
     *  [android.os.Bundle] because it may have to be inflated from a
     *  [android.app.job.JobScheduler], which only supports [PersistableBundle].
     */
    interface Factory {

        /**
         * Create a [Notifier] configured with the given [config] and [args].
         */
        fun create(
            context: Context,
            config: DownloaderConfig,
            args: PersistableBundle?
        ): Notifier

        /**
         * A [Notifier.Factory.Config] contains the [PersistableBundle] arguments
         *  to be used to inflate its associated [Notifier.Factory]. It can be
         *  serialized into and read from a [PersistableBundle] and is also [Parcelable].
         */
        class Config(
            val klass: Class<out Factory>,
            val arg: PersistableBundle?
        ) : Parcelable {

            /**
             * Inflate an instance of the [Notifier] this [Config] refers to.
             *  You MUST use the provided [DownloaderConfig.notificationId]
             *  for any [Notification] you show, or else risk an inconsistent
             *  Notification experience for the user.
             */
            fun inflate(context: Context, config: DownloaderConfig): Notifier =
                klass.newInstance().create(context, config, arg)

            /*
                Parcelable implementation
             */

            @Suppress("UNCHECKED_CAST")
            private constructor(parcel: Parcel) : this(
                Class.forName(parcel.readString()) as Class<out Factory>,
                parcel.readParcelable(PersistableBundle::class.java.classLoader)
            )

            override fun writeToParcel(parcel: Parcel, flags: Int) {
                parcel.writeString(klass.name)
                parcel.writeParcelable(arg, flags)
            }

            override fun describeContents(): Int {
                return 0
            }

            /* end parcelable */

            companion object {
                @Suppress("UNCHECKED_CAST")
                fun from(
                    className: String,
                    arg: PersistableBundle
                ) = Config(
                    Class.forName(className) as Class<out Factory>,
                    arg
                )

                @JvmField val CREATOR = object : Parcelable.Creator<Config> {
                    override fun createFromParcel(parcel: Parcel): Config {
                        return Config(parcel)
                    }

                    override fun newArray(size: Int): Array<Config?> {
                        return arrayOfNulls(size)
                    }
                }
            }
        }
    }

    companion object {
        const val STATE_IDLE = 1
        const val STATE_FETCHING_URL = 2
        const val STATE_CONNECTING = 3
        const val STATE_DOWNLOADING = 4
        const val STATE_COMPLETED = 5

        const val STATE_PAUSED_NETWORK_UNAVAILABLE = 6
        const val STATE_PAUSED_BY_REQUEST = 7

        /**
         * Both STATE_PAUSED_WIFI_DISABLED_NEED_CELLULAR_PERMISSION and
         * STATE_PAUSED_NEED_CELLULAR_PERMISSION imply that Wi-Fi is unavailable and
         * cellular permission will restart the service. Wi-Fi disabled means that
         * the Wi-Fi manager is returning that Wi-Fi is not enabled, while in the
         * other case Wi-Fi is enabled but not available.
         */
        const val STATE_PAUSED_WIFI_DISABLED_NEED_CELLULAR_PERMISSION = 8
        const val STATE_PAUSED_NEED_CELLULAR_PERMISSION = 9

        /**
         * The user is on a roaming cellular network, so we will not attempt to download.
         */
        const val STATE_PAUSED_ROAMING = 12

        /**
         * Scary case. We were on a network that redirected us to another website
         * that delivered us the wrong file.
         */
        const val STATE_PAUSED_NETWORK_SETUP_FAILURE = 13

        const val STATE_PAUSED_SDCARD_UNAVAILABLE = 14

        const val STATE_FAILED_UNLICENSED = 15
        const val STATE_FAILED_FETCHING_URL = 16
        const val STATE_FAILED_SDCARD_FULL = 17
        const val STATE_FAILED_CANCELED = 18

        const val STATE_FAILED = 19
    }
}

