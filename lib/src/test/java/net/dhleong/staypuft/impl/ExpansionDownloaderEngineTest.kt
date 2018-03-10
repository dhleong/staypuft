package net.dhleong.staypuft.impl

import assertk.assert
import assertk.assertions.exists
import assertk.assertions.hasText
import assertk.assertions.isFile
import com.google.android.vending.licensing.APKExpansionPolicy
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.anyOrNull
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.stub
import com.nhaarman.mockito_kotlin.verify
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import net.dhleong.staypuft.DefaultNotifier
import net.dhleong.staypuft.DownloaderConfig
import net.dhleong.staypuft.Notifier
import net.dhleong.staypuft.doesNotExist
import net.dhleong.staypuft.rx.LicenceCheckerResult
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection

/**
 * @author dhleong
 */
class ExpansionDownloaderEngineTest {

//    private val scheduler = TestScheduler()
    private val scheduler = Schedulers.from { it.run() }

    private val testDownloadsDir = File(".test-obbs")

    private lateinit var service: IExpansionDownloaderService
    private lateinit var uiProxy: UIProxy
    private lateinit var tracker: IDownloadsTracker
    private lateinit var policy: APKExpansionPolicy
    private lateinit var engine: ExpansionDownloaderEngine

    private lateinit var notifier: Notifier

    private lateinit var licenseAccessResults: BehaviorSubject<LicenceCheckerResult>

    @Before fun setUp() {
        if (!testDownloadsDir.isDirectory) {
            testDownloadsDir.mkdirs()
        } else {
            // clear out any old test data
            testDownloadsDir.listFiles().forEach {
                it.delete()
            }
        }

        licenseAccessResults = BehaviorSubject.create()
        policy = mock {  }
        service = mock {
            on { createPolicy(any()) } doReturn policy
            on { checkLicenseAccess(anyOrNull(), anyOrNull()) } doReturn licenseAccessResults.firstOrError()
            on { getAvailableBytes(any()) } doReturn 1024 * 1024
            on { getSaveDirectory() } doReturn testDownloadsDir
        }
        uiProxy = mock {  }
        tracker = mock { }
        notifier = mock {  }

        engine = ExpansionDownloaderEngine(
            service = service,
            uiProxy = uiProxy,
            tracker = tracker
        )
    }

    @Test fun `Download from scratch`() {
        val fileName = "from-scratch"
        policy.stub {
            on { expansionURLCount } doReturn 1
            on { getExpansionFileName(0) } doReturn fileName
            on { getExpansionFileSize(0) } doReturn "main-content".length.toLong()
            on { getExpansionURL(0) } doReturn "https://google/file"
        }

        val connection = mockConnection(
            content = "main-content"
        )

        service.stub {
            on { openUrl(eq(policy.getExpansionURL(0))) } doReturn connection
        }

        processDownload {
            licenseAccessResults.onNext(LicenceCheckerResult.Allowed(0))
        }

        verify(connection, never()).setRequestProperty(eq("Range"), any())
        verify(connection).disconnect()
        verify(tracker).save(eq(
            ExpansionFile(
                isMain = true,
                name = fileName,
                url = policy.getExpansionURL(0),
                size = policy.getExpansionFileSize(0),
                downloaded = policy.getExpansionFileSize(0),
                etag = "etag"
            )
        ))
        verify(notifier).done()

        assert(File(testDownloadsDir, "$fileName.tmp")) {
            doesNotExist()
        }

        assert(File(testDownloadsDir, fileName)) {
            exists()
            isFile()
            hasText("main-content")
        }
    }

    @Ignore("TODO")
    @Test fun `Resume partial download`() {

    }

    private inline fun processDownload(
        config: DownloaderConfig = newConfig(),
        events: () -> Unit
    ) {
        val process = engine.processDownload(config, notifier, scheduler = scheduler)
            .toSingleDefault(true)

        events()

        process.test()
            .assertNoErrors()
            .assertNoTimeout()
            .assertValue { it }
    }

    private fun newConfig(): DownloaderConfig =
        DownloaderConfig(
            salt = ByteArray(0),
            publicKey = "",
            notifier = Notifier.Factory.Config(
                DefaultNotifier.Factory::class.java,
                null
            )
        )
}

private fun mockConnection(
    content: String,
    etag: String = "etag",
    statusCode: Int = 200
): HttpURLConnection =
    mock {
        on { responseCode } doReturn statusCode
        on { inputStream } doReturn content.byteInputStream()
        on { contentLength } doReturn content.length
        on { contentLengthLong } doReturn content.length.toLong()
        on { getHeaderField(eq("ETag")) } doReturn etag
    }

