# staypuft [![Release](https://jitpack.io/v/dhleong/staypuft.svg)][2] [![Build Status](http://img.shields.io/travis/dhleong/staypuft.svg?style=flat)](https://travis-ci.org/dhleong/staypuft)

*Save the world, one oversized APK at a time*

## What?

Staypuft is a simple, reactive interface to Google's [APK Expansion Files][1] service.

It looks like this:

```kotlin
val apkx = Staypuft.getInstance(activity).setConfig(
    DownloadConfig(
        salt = // your custom salt array
        publicKey = "YOUR_PUBLIC_KEY base64",
        notifier = DefaultNotifier.withChannelId("expansions")
    )
)
```

That's it! If you want to get status updates, just subscribe to them:

```kotlin
apkx.stateEvents.subscribe { event ->
    when (event) {
        is DownloadState.Ready -> {
            // done!
            println("Got main expansion file at: ${event.main}")
        }
    }
}
```

## How?

Staypuft is distributed via [JitPack][2]. To use it, first add
the JitPack maven repo to your root build.gradle:

```gradle
    allprojects {
        repositories {
            ...
            maven { url 'https://jitpack.io' }
        }
    }
```

Then add the dependency:

```gradle
   dependencies {
         compile 'com.github.dhleong:staypuft:<VERSION>'
   }
```

where `<VERSION>` is the latest version (shown in the badge at the
top of this page).

Staypuft provides definitions for the services and permissions it needs,
so you shouldn't even need to modify your manifest. Simply copy and paste
the code above into an appropriate place, fill in the `salt` and
`publicKey` values, and you're good to go!

[1]: https://developer.android.com/google/play/expansion-files.html
[2]: https://jitpack.io/#dhleong/staypuft
