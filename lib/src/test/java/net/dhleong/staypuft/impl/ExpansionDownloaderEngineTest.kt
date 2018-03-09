package net.dhleong.staypuft.impl

import com.nhaarman.mockito_kotlin.mock
import net.dhleong.staypuft.DefaultNotifier
import net.dhleong.staypuft.DownloaderConfig
import net.dhleong.staypuft.Notifier
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

/**
 * @author dhleong
 */
class ExpansionDownloaderEngineTest {

    private lateinit var service: IExpansionDownloaderService
    private lateinit var uiProxy: UIProxy
    private lateinit var tracker: DownloadsTracker
    private lateinit var engine: ExpansionDownloaderEngine

    private lateinit var notifier: Notifier

    @Before fun setUp() {
        service = mock {  }
        uiProxy = mock {  }
        tracker = mock {  }
        notifier = mock {  }

        engine = ExpansionDownloaderEngine(
            service = service,
            uiProxy = uiProxy,
            tracker = tracker
        )
    }

    @Test fun `Download from scratch`() {
        processDownload()
    }

    @Ignore("TODO")
    @Test fun `Resume partial download`() {

    }

    private fun processDownload(config: DownloaderConfig = newConfig()) {
        engine.processDownload(config, notifier)
            .toSingleDefault(true)
            .test()
            .assertComplete()
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