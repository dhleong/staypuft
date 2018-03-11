package net.dhleong.staypuft.impl

import assertk.assert
import assertk.assertions.hasText
import com.google.android.vending.licensing.APKExpansionPolicy
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.anyOrNull
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.stub
import com.nhaarman.mockito_kotlin.verify
import io.reactivex.observers.TestObserver
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import net.dhleong.staypuft.ApkExpansionException
import net.dhleong.staypuft.DefaultNotifier
import net.dhleong.staypuft.DownloaderConfig
import net.dhleong.staypuft.Notifier
import net.dhleong.staypuft.doesNotExist
import net.dhleong.staypuft.rx.LicenceCheckerResult
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection

/**
 * @author dhleong
 */
class ExpansionDownloaderEngineTest {

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

        processDownload(
            licenceCheckerResult = LicenceCheckerResult.Allowed(0)
        )

        verify(connection, never()).setRequestProperty(eq("Range"), any())
        verify(connection).disconnect()
        verify(tracker).save(eq(file))
        verify(notifier).done()

        assert(file.localTmpFile(service)).doesNotExist()
        assert(file.localFile(service)).hasText("main-content")
    }

    @Test fun `Resume partial download`() {
        val (file, connection) = prepareEnvironment {
            withKnownMain(
                existingContent = "main-",
                sizeFromContent = "main-content"
            )

            withMainFile(
                content = "main-content",
                statusCode = 206
            )
        }

        processDownload(
            licenceCheckerResult = LicenceCheckerResult.Allowed(0)
        )

        verify(connection).setRequestProperty(eq("Range"), any())
        verify(tracker).save(eq(file))
        verify(notifier).done()

        assert(file.localTmpFile(service)).doesNotExist()
        assert(file.localFile(service)).hasText("main-content")
    }

    @Test fun `Handle captured portal`() {
        prepareEnvironment {
            withMainFile(
                content = "bogus-content",
                reportedSize = 42
            )
        }

        processDownload(
            licenceCheckerResult = LicenceCheckerResult.Allowed(0)
        ) {
            assertError {
                (it as ApkExpansionException).state == Notifier.STATE_PAUSED_NETWORK_SETUP_FAILURE
                    && it.message!!.contains("Incorrect file size")
            }
        }
    }

    private inline fun processDownload(
        config: DownloaderConfig = newConfig(),
        licenceCheckerResult: LicenceCheckerResult,
        asserts: TestObserver<Boolean>.() -> Unit = {
            assertNoErrors()
            assertNoTimeout()
            assertValue { it }
        }
    ) {
        val process = engine.processDownload(config, notifier, scheduler = scheduler)
            .toSingleDefault(true)

        licenseAccessResults.onNext(licenceCheckerResult)

        process.test().asserts()
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
        private val knownFiles = arrayOfNulls<ExpansionFile>(2)

        init {
            tracker.stub {
                on { getKnownDownloads() } doReturn knownFiles.filterNotNull().asSequence()
            }
        }

        fun withKnownMain(
            existingContent: String,
            size: Long = -1,
            sizeFromContent: String? = null,
            doCreateFile: Boolean = true
        ) = withKnownDownload(
            0, "main", existingContent,
            size = size,
            sizeFromContent = sizeFromContent,
            doCreateFile = doCreateFile
        )

        private fun withKnownDownload(
            index: Int,
            name: String,
            existingContent: String,
            size: Long,
            sizeFromContent: String?,
            doCreateFile: Boolean
        ): ExpansionFile {

            val actualSize = when {
                size != -1L -> size
                sizeFromContent != null -> sizeFromContent.length.toLong()
                else -> throw IllegalArgumentException("No way to size full file")
            }

            val download = ExpansionFile(
                isMain = index == 0,
                name = name,
                size = actualSize,
                url = "https://google/file/$name",
                downloaded = existingContent.length.toLong(),
                etag = name
            )
            tracker.stub {
                on { getKnownDownload(eq(index)) } doReturn download
            }

            if (doCreateFile) {
                download.localTmpFile(service)
                    .writeText(existingContent)
            }

            return download
        }

        fun withMainFile(
            content: String,
            reportedSize: Long? = null,
            statusCode: Int = 200
        ) = withFile(0, "main", statusCode, reportedSize, content)

        fun withPatchFile(
            content: String,
            reportedSize: Long? = null,
            statusCode: Int = 200
        ) = withFile(1, "patch", statusCode, reportedSize, content)

        private fun withFile(
            index: Int,
            name: String,
            statusCode: Int,
            reportedSize: Long?,
            content: String
        ): Pair<ExpansionFile, HttpURLConnection> {

            val url = "https://google/file/$name"
            val existingURLCount = policy.expansionURLCount
            policy.stub {
                on { expansionURLCount } doReturn maxOf(existingURLCount, index + 1)
                on { getExpansionFileName(index) } doReturn name
                on { getExpansionFileSize(index) } doReturn(
                    reportedSize ?: content.length.toLong()
                )
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
): HttpURLConnection {
    var skipBytes = 0
    return mock {
        on { setRequestProperty(eq("Range"), any()) } doAnswer { invocationOnMock ->
            val headerValue: String = invocationOnMock.getArgument(1)
            val matches = Regex("bytes=(\\d+)-").find(headerValue)
            if (matches != null) {
                skipBytes = matches.groups[1]!!.value.toInt()
            }

            Unit
        }

        on { responseCode } doReturn statusCode
        on { contentLength } doReturn content.length
        on { contentLengthLong } doReturn content.length.toLong()
        on { getHeaderField(eq("ETag")) } doReturn etag

        on { inputStream } doAnswer {
            content.substring(skipBytes).byteInputStream()
        }
    }

}
