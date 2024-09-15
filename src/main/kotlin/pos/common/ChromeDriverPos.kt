package pos.common

import net.lightbody.bmp.BrowserMobProxy
import net.lightbody.bmp.BrowserMobProxyServer
import net.lightbody.bmp.client.ClientUtil
import net.lightbody.bmp.core.har.HarEntry
import net.lightbody.bmp.proxy.CaptureType
import org.openqa.selenium.Proxy
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.logging.LogEntry
import org.openqa.selenium.logging.LogType
import org.openqa.selenium.logging.LoggingPreferences
import org.openqa.selenium.remote.CapabilityType
import pos.RestaurantStatus
import java.net.Inet4Address
import java.net.UnknownHostException
import java.time.LocalDateTime
import java.util.*
import java.util.logging.Level
import kotlin.random.Random

abstract class ChromeDriverPos(
    posName: String,
    private val logType: ChromeDriverLogType,
    private val sleepFromSec: Int = 5,
    private val sleepToSec: Int = 10
) :
    BasePos(posName) {
    var restaurantRequestQueue: Queue<RestaurantHttpRequest> = LinkedList<RestaurantHttpRequest>()
    override fun start() {

        initRequestsQueue()
        val totalItems = restaurantRequestQueue.size

        System.setProperty("webdriver.chrome.whitelistedIps", "")
        //Use selenium + chromium driver to listen for websocket data exchange.
        //sudo apt-get install chromium-driver

        val proxy = BrowserMobProxyServer()
        proxy.enableHarCaptureTypes(
            CaptureType.RESPONSE_CONTENT
        )
        proxy.setTrustAllServers(true)
        proxy.start(0)

        val preferences = LoggingPreferences()
        preferences.enable(LogType.PERFORMANCE, Level.ALL)
        val options = ChromeOptions()
        options.setCapability(CapabilityType.LOGGING_PREFS, preferences)
        options.setCapability("goog:loggingPrefs", preferences)
        if (logType == ChromeDriverLogType.HAR) {
            val seleniumProxy = getSeleniumProxy(proxy)
            options.setCapability(CapabilityType.PROXY, seleniumProxy)
        }
        options.addArguments("ignore-certificate-errors")
        options.addArguments("--headless")

        while (restaurantRequestQueue.size > 0) {
            val nextRequest = getNextRestaurantRequest()
            if (nextRequest != null) {

                var driver: ChromeDriver? = null
                try {
                    val treated = totalItems - restaurantRequestQueue.size
                    val percent = treated * 100 / totalItems
                    var extra = ""
                    if (database.restaurantMustBeCreatedOrUpdated(nextRequest.id)) {
                        driver = ChromeDriver(options)

                        println("Handling id ${nextRequest.id}  ($treated/$totalItems | $percent%) (waiting between $sleepFromSec and $sleepToSec sec)")
                        var url = nextRequest.request.uri.toString()
                        proxy.newHar(url)

                        driver.navigate().to(url)
                        //sleep is after request execution to handle all logs
                        Thread.sleep(Random.nextLong(sleepFromSec * 1000L, sleepToSec * 1000L))


                        val logEntries = if (logType == ChromeDriverLogType.LOG) {
                            driver.manage().logs()[LogType.PERFORMANCE]
                        } else {
                            proxy.har.log.entries
                        }
                        var found = false
                        for (entry in logEntries) {
                            var valid = when (entry) {
                                is LogEntry -> isValidLogEntry(nextRequest, entry)
                                is HarEntry -> isValidHarEntry(nextRequest, entry)
                                else -> false
                            }
                            if (valid) {
                                println("!! Restaurant with id #${nextRequest.id} is available !!")
                                try {
                                    var payload = when (entry) {
                                        is LogEntry -> getPayloadForValidLogEntry(nextRequest, entry)
                                        is HarEntry -> getPayloadForValidHarEntry(nextRequest, entry)
                                        else -> null
                                    }

                                    saveSuccessRestaurantResponse(nextRequest, payload, extra)
                                    found = true
                                } catch (e: Throwable) {
                                    println("Fail to fetch request #${nextRequest.id} : ${e.javaClass.simpleName}")
                                    var content =
                                        e.javaClass.simpleName + " : " + e.localizedMessage + "\r\n" + nextRequest
                                    database.saveRestaurant(
                                        nextRequest.id,
                                        RestaurantStatus.ERROR,
                                        nextRequest.request.uri.toString(),
                                        content,
                                        extra
                                    )
                                }
                                break
                            }
                        }

                        if (!found) {
                            println(
                                LocalDateTime.now()
                                    .toString() + " : Unable to find websocket data for #${nextRequest.id}"
                            )
                            saveFailedRestaurantResponse(nextRequest, RestaurantStatus.UNAVAILABLE);
                        }

                    } else {
                        println("Skip already executed ${nextRequest.id} ($treated/$totalItems | $percent%)")

                        ""
                    }


                } finally {

                    driver?.quit()

                }
            }

        }

    }

    abstract fun isValidHarEntry(request: RestaurantHttpRequest, entry: HarEntry): Boolean

    abstract fun isValidLogEntry(request: RestaurantHttpRequest, entry: LogEntry): Boolean

    abstract fun getPayloadForValidLogEntry(request: RestaurantHttpRequest, entry: LogEntry): String?
    abstract fun getPayloadForValidHarEntry(request: RestaurantHttpRequest, entry: HarEntry): String?

    abstract fun initRequestsQueue()

    private fun saveFailedRestaurantResponse(
        request: RestaurantHttpRequest,
        status: RestaurantStatus,
        responseBody: String? = "",
        extra: String = ""
    ) {
        database.saveRestaurant(request.id, status, request.request.uri.toString(), responseBody, extra)

        if (status == RestaurantStatus.ERROR) // re-add for retry
            restaurantRequestQueue.add(request)

    }

    protected open fun saveSuccessRestaurantResponse(
        request: RestaurantHttpRequest,
        responseBody: String?,
        extra: String = ""
    ) {
        database.saveRestaurant(
            request.id,
            RestaurantStatus.FOUND,
            request.request.toString(),
            responseBody,
            extra
        )
    }


    @Synchronized
    private fun getNextRestaurantRequest(): RestaurantHttpRequest? {

        try {
            return restaurantRequestQueue.remove()
        } catch (e: Exception) {
            println("REMOVE EXCEPTION  $e")
        }
        return null;
    }


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

}

enum class ChromeDriverLogType {
    HAR,
    LOG
}
