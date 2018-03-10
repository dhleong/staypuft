package net.dhleong.staypuft.impl

import assertk.assert
import assertk.assertions.hasText
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
        val (file, connection) = prepareEnvironment {
            withMainFile(content = "main-content")
        }

        processDownload {
            licenseAccessResults.onNext(LicenceCheckerResult.Allowed(0))
        }

        verify(connection, never()).setRequestProperty(eq("Range"), any())
        verify(connection).disconnect()
        verify(tracker).save(eq(file))
        verify(notifier).done()

        assert(file.localTmpFile(service)).doesNotExist()
        assert(file.localFile(service)).hasText("main-content")
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

    private inline fun <T> prepareEnvironment(block: EnvConfig.() -> T) =
        block(EnvConfig())

    private inner class EnvConfig {

        val connections = arrayOfNulls<HttpURLConnection>(2)

        fun withMainFile(
            content: String,
            statusCode: Int = 200
        ) = withFile(0, "main", statusCode, content)

        fun withPatchFile(
            content: String,
            statusCode: Int = 200
        ) = withFile(1, "patch", statusCode, content)

        private fun withFile(
            index: Int,
            name: String,
            statusCode: Int,
            content: String
        ): Pair<ExpansionFile, HttpURLConnection> {

            val url = "https://google/file/$name"
            val existingURLCount = policy.expansionURLCount
            policy.stub {
                on { expansionURLCount } doReturn maxOf(existingURLCount, index + 1)
                on { getExpansionFileName(index) } doReturn name
                on { getExpansionFileSize(index) } doReturn content.length.toLong()
                on { getExpansionURL(index) } doReturn url
            }

            connections[index] = mockConnection(
                content = content,
                statusCode = statusCode,
                etag = name
            )

            service.stub {
                on { openUrl(url) } doReturn connections[index]!!
            }

            return ExpansionFile(
                isMain = index == 0,
                name = name,
                url = url,
                size = content.length.toLong(),
                downloaded = content.length.toLong(),
                etag = name
            ) to connections[index]!!
        }
    }
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

