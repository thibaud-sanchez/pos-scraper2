package resolver

import net.lightbody.bmp.BrowserMobProxy
import net.lightbody.bmp.BrowserMobProxyServer
import net.lightbody.bmp.client.ClientUtil
import net.lightbody.bmp.proxy.CaptureType
import org.openqa.selenium.Proxy
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.logging.LogType
import org.openqa.selenium.logging.LoggingPreferences
import org.openqa.selenium.remote.CapabilityType
import pos.PosDatabase
import java.io.File
import java.net.Inet4Address
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.logging.Level
import kotlin.random.Random

class GoogleSearchResolver(val basePath: File, val database: PosDatabase, val resolver: RestaurantIdResolver) {

    val ignoreKeywords =
        mutableListOf(
            "google",
            "w3.org",
            "schema.org",
            "gstatic",
            "facebook.com",
            "youtube.com",
            "instagram.com",
            "waze.com"
        )

    fun getSeleniumProxy(proxyServer: BrowserMobProxy): Proxy {
        //https://techblog.dotdash.com/selenium-browsermob-integration-c35f4713fb59
        val seleniumProxy = ClientUtil.createSeleniumProxy(proxyServer)
        try {
            val hostIp = Inet4Address.getLocalHost().getHostAddress()
            seleniumProxy.setHttpProxy(hostIp + ":" + proxyServer.getPort())
            seleniumProxy.setSslProxy(hostIp + ":" + proxyServer.getPort())
        } catch (e: UnknownHostException) {
            e.printStackTrace()

        }
        return seleniumProxy
    }

    fun parseGoogleResult(searchKeywords: String, resolveIntoWebsiteContent:Boolean=true) {
        System.setProperty("webdriver.chrome.whitelistedIps", "");
        //sudo apt-get install chromium-driver
        val options = ChromeOptions()

        val proxy = BrowserMobProxyServer();
        proxy.enableHarCaptureTypes(CaptureType.RESPONSE_CONTENT, CaptureType.REQUEST_CONTENT);
        proxy.setTrustAllServers(true)
        proxy.start(0)

        val seleniumProxy = getSeleniumProxy(proxy)
        options.setCapability(CapabilityType.PROXY, seleniumProxy);

        val preferences = LoggingPreferences()
        preferences.enable(LogType.PERFORMANCE, Level.ALL)
        preferences.enable(LogType.SERVER, Level.ALL)
        options.setCapability(CapabilityType.LOGGING_PREFS, preferences);
        options.setCapability("goog:loggingPrefs", preferences);



        options.addArguments("ignore-certificate-errors")
        options.addArguments("--headless")
        options.addArguments()

        proxy.newHar()

        val driver = ChromeDriver(options)
        val fulllist = mutableListOf<String>()


        try {
            var step = 10
            var current = 0
            //val ids = listOf<Int>(30400, 15000, 12454, 30261)
            do {
                val urls = mutableListOf<String>()
                val encodedKeywords = URLEncoder.encode(searchKeywords, "utf-8")
                val url = "https://www.google.co.uk/search?q=$encodedKeywords&start=$current"
                println("executing $url")
                driver.get(url)
                Thread.sleep(Random.nextLong(1000, 2000));
                //println(driver.pageSource)
                val regex =
                    "\\b(https?|ftp|file):\\/\\/[-a-zA-Z0-9+&@#\\/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#\\/%=~_|]".toRegex()
                val result = regex.findAll(driver.pageSource)

                result.forEach {
                    urls.add(it.value)
                }

                val cleanList = urls.filterNot { url ->
                    ignoreKeywords.firstOrNull { ignore ->
                        url.contains(ignore)
                    } != null

                }.distinct()
                println("google return ${cleanList.size} websites for step $current")
                resolver.resolveAllIdsFromUrlList(cleanList, resolveIntoWebsiteContent)
                fulllist.addAll(cleanList)

                current += step
            } while (cleanList.size > 3)

            println("finished !")

            var linksPath = File(basePath, "links")
            if (!linksPath.exists()) linksPath.mkdirs()
            val actualLinks = File(linksPath, "google-links.txt").readLines()

            val allLines = listOf(*actualLinks.toTypedArray(),*fulllist.toTypedArray())
            val dedudepLines =allLines.toSet()
            val finalList = dedudepLines.joinToString("\n")
            File(linksPath, "google-links.txt").writeText(finalList)
            println(finalList)

        } finally {
            driver.quit()
        }
    }
}