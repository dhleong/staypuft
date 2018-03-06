package net.dhleong.staypuft.rx

import com.google.android.vending.licensing.LicenseChecker
import com.google.android.vending.licensing.LicenseCheckerCallback
import io.reactivex.Single

sealed class LicenceCheckerResult {
    class Allowed(reason: Int) : LicenceCheckerResult()

    class NotAllowed(
        val reason: Int
    ) : LicenceCheckerResult()
    class Error(
        val errorCode: Int
    ) : LicenceCheckerResult()
}

/**
 * @author dhleong
 */
fun LicenseChecker.checkAccess(): Single<LicenceCheckerResult> = Single.create { emitter ->
    checkAccess(object : LicenseCheckerCallback {
        override fun applicationError(errorCode: Int) {
            emitter.onSuccess(LicenceCheckerResult.Error(errorCode))
        }

        override fun dontAllow(reason: Int) {
            emitter.onSuccess(LicenceCheckerResult.NotAllowed(reason))
        }

        override fun allow(reason: Int) {
            emitter.onSuccess(LicenceCheckerResult.Allowed(reason))
        }
    })
}