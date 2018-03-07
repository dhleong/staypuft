# staypuft

*Save the world, one oversized APK at a time*

## What?

Staypuft is a simple, reactive interface to Google's [APK Expansion Files][1] service.

It looks like this:

```kotlin
val apkx = Staypuft.getInstance(activity).apply {
    setConfig(
        DownloadConfig(
            salt = // your custom salt array
            publicKey = "YOUR_PUBLIC_KEY base64",
            notifier = DefaultNotifier.withChannelId("expansions")
        )
    )
}
```

That's it! If you want to get status updates, just subscribe to them:

```kotlin
apkx.statusEvents
    .subscribe { event ->
        when (event) {
            is DownloadStatus.Ready -> {
                // done!
            }
        }
    }
```

[1]: https://developer.android.com/google/play/expansion-files.html
