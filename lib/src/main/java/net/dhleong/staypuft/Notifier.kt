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

    interface Factory {

        /**
         * Create a [Notifier] configured with the given [config] and [args].
         */
        fun create(
            context: Context,
            config: DownloaderConfig,
            args: PersistableBundle?
        ): Notifier

        class Config(
            val klass: Class<out Factory>,
            val arg: PersistableBundle?
        ) : Parcelable {

            /**
             * Inflate an instance of the [Notifier] this [Config] refers to,
             *  using an instantiated object the [Factory] class provided
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
         * Both STATE_PAUSED_WIFI_DISABLED and STATE_PAUSED_NEED_WIFI imply that
         * Wi-Fi is unavailable and cellular permission will NOT restart the
         * service. Wi-Fi disabled means that the Wi-Fi manager is returning that
         * Wi-Fi is not enabled, while in the other case Wi-Fi is enabled but not
         * available.
         *
         *
         * The service does not return these values. We recommend that app
         * developers with very large payloads do not allow these payloads to be
         * downloaded over cellular connections.
         */
        const val STATE_PAUSED_WIFI_DISABLED = 10
        const val STATE_PAUSED_NEED_WIFI = 11

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

