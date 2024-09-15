package resolver

import org.apache.commons.csv.CSVFormat
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.LaxRedirectStrategy
import org.apache.http.util.EntityUtils
import pos.PosDatabase
import pos.ResolvedIdDb
import pos.common.HttpHelper
import pos.common.HttpProxyHelper
import pos.common.ScraperUtils
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.URI
import java.nio.charset.Charset
import kotlin.random.Random

class RestaurantIdResolver(
    val basePath: File,
    val database: PosDatabase,
    val regex: List<RestaurantIdRegex>,
    private val sleepFromSec: Int = 3,
    private val sleepToSec: Int = 10,
    private val useProxyForContent: Boolean = true,
    private val ignoreUrlsPattern: List<Regex> = listOf<Regex>()
) {
    private val proxyHelper = HttpProxyHelper()


    private val requestBuilder: RequestConfig.Builder = RequestConfig.custom()

    private val client: CloseableHttpClient
    private val preContentRegex = regex.filter { it.preContent }

    init {
        val timeoutMs = 15 * 1000
        requestBuilder.setConnectTimeout(timeoutMs);
        requestBuilder.setConnectionRequestTimeout(timeoutMs);
        requestBuilder.setSocketTimeout(timeoutMs);

        client = HttpClientBuilder.create().setDefaultRequestConfig(requestBuilder.build())
            .setRedirectStrategy(LaxRedirectStrategy()).build()
    }

    private fun extractIdInString(string: String): List<RestaurantIdentifier> {
        val res = extractId(string, string)
        if (res.isNotEmpty()) println("- Found ids in string $string  : $res")
        return res
    }

    private fun extractIdInWebsiteContent(url: String): List<RestaurantIdentifier> {
        val content = executeUrl(url)
        val res = extractId(content, url)
        if (res.isNotEmpty()) println("- Found ids in content $url  : $res")
        return res
    }

    private fun extractIdInBaseDomainContent(url: String): List<RestaurantIdentifier> {
        val domain = ScraperUtils.extractDomain(url)
        val newurl = if (url.startsWith("https")) "https://$domain" else "http://$domain"
        val content = executeUrl(newurl)
        val res = extractId(content, url)
        if (res.isNotEmpty()) println("- Found ids in base domain $newurl  : $res")
        return res
    }

    private fun resolveId(
        ahrefsItem: AhrefsItem,
        resolveIntoWebsiteContent: Boolean = true
    ): List<RestaurantIdentifier> {
        var ids = resolveId(ahrefsItem.posUrl, resolveIntoWebsiteContent)
        if (!shouldIgnoreUrl(ahrefsItem.posUrl) &&
            shouldContinueResolvingIdIntoContent(ids, resolveIntoWebsiteContent)
        )
            ids = resolveId(ahrefsItem.sourceUrl, resolveIntoWebsiteContent)
        return ids
    }

    private fun shouldContinueResolvingIdIntoContent(
        currentListOfIds: List<RestaurantIdentifier>,
        resolveIntoWebsiteContent: Boolean
    ): Boolean {

        val preContentIds = currentListOfIds.filter {
            isPreContentId(it)
        }
        return (currentListOfIds.isEmpty() && resolveIntoWebsiteContent)
                || preContentIds.isNotEmpty()
    }

    fun shouldIgnoreUrl(url: String): Boolean {
        ignoreUrlsPattern.forEach {
            if (it.matches(url)) {
                println("- Ignore resolve content for URL $url")

                return true
            }
        }
        return false;
    }

    fun isPreContentId(restaurantId: RestaurantIdentifier): Boolean {
        return preContentRegex.firstOrNull { regex ->
            regex.type == restaurantId.type
        } != null
    }


    private fun resolveId(website: String, resolveIntoWebsiteContent: Boolean): List<RestaurantIdentifier> {

        var ids = extractIdInString(website)

        if (shouldContinueResolvingIdIntoContent(ids, resolveIntoWebsiteContent)) {
            ids = extractIdInWebsiteContent(website)
        }

        if (shouldContinueResolvingIdIntoContent(ids, resolveIntoWebsiteContent)) {
            ids = extractIdInBaseDomainContent(website)
        }

        if (ids.isEmpty()) {
            println("- Found nothing for $website (resolveIntoWebsiteContent=$resolveIntoWebsiteContent)")
        }
        return ids
    }

    fun resolveAllIdsFromPath(
        format: ResolverInputFormat = ResolverInputFormat.FORMAT_EXPORT_V1,
        resolveIntoWebsiteContent: Boolean = false
    ) {
        val linkPath = File(basePath, "links")
        linkPath.walkTopDown().forEach {
            if (it.isFile) {
                when (format) {
                    ResolverInputFormat.FORMAT_EXPORT_V1 -> resolveAllIdsFromAhrefsFilesFormatV1(
                        it, resolveIntoWebsiteContent
                    )
                    ResolverInputFormat.FORMAT_EXPORT_V2 -> resolveAllIdsFromAhrefsFilesFormatV2(
                        it, resolveIntoWebsiteContent
                    )
                    ResolverInputFormat.BASIC_TEXT -> resolveAllIdsFromTextfile(
                        it, resolveIntoWebsiteContent
                    )

                }
            }
        }
    }

    private fun resolveAllIdsFromTextfile(textFile: File, resolveIntoWebsiteContent: Boolean = false) {
        val notResolved = mutableListOf<String>()
        var newIds = 0

        BufferedReader(InputStreamReader(textFile.inputStream(), Charset.defaultCharset()))
            .use { reader ->
                try {
                    println("Start resolving ids for file ${textFile.name}")
                    var content = reader.readText()
                    val count = resolveAllIdsFromContent(content, textFile.name, resolveIntoWebsiteContent);
                    if (count == 0) {
                        notResolved.add(textFile.name)
                    } else newIds += count;

                    println("Finish resolving ids for file ${textFile.name} (total for all files:  $newIds)")

                } catch (e: Exception) {
                    println("- Error during extraction for ${textFile.name} \n->(${e.message})")
                    notResolved.add(textFile.name)
                }
            }

        val total = database.getResolvedIdsCount()
        println("Finish  resolve, total $total  (can't resolve : ${notResolved.size} )")
    }

    fun resolveAllIdsFromContent(
        content: String,
        contentIdentifier: String,
        resolveIntoWebsiteContent: Boolean = false
    ): Int {

        val ids = resolveId(content, resolveIntoWebsiteContent).distinct()
        if (ids.isNotEmpty()) {
            println("!! Saving ${ids.size} new ids")
            saveAllIds(ids, contentIdentifier)
            return ids.size
        } else {
            println("- Unable to resolve any id with current file $contentIdentifier")
            return 0;
        }
    }

    private fun saveAllIds(ids: List<RestaurantIdentifier>, source: String) {
        ids.filterNot { isPreContentId(it) }.forEach {
            database.saveResolvedId(source, it)
        }
    }


    private fun resolveAllIdsFromAhrefsFilesFormatV1(ahrefsFile: File, resolveIntoWebsiteContent: Boolean = false) {

        val ahrefsItems =
            BufferedReader(InputStreamReader(ahrefsFile.inputStream(), Charset.defaultCharset()))
                .use { reader ->
                    CSVFormat.EXCEL
                        .withHeader()
                        .withDelimiter(',')

                        .parse(reader).toList()
                }.map {
                    AhrefsItem(it.get(5), it.get(6), it.get(9), it.get(0), ahrefsFile.name)
                }

        extractInfoForAllAhrefsItems(ahrefsItems, resolveIntoWebsiteContent)
    }

    private fun resolveAllIdsFromAhrefsFilesFormatV2(ahrefsFile: File, resolveIntoWebsiteContent: Boolean = false) {
        val ahrefsItems = BufferedReader(InputStreamReader(ahrefsFile.inputStream(), "UTF-16"))
            .use { reader ->
                CSVFormat.EXCEL
                    .withHeader()
                    .withDelimiter('\t')
                    .parse(reader).toList()
            }.mapNotNull {
                try {
                    AhrefsItem(it.get(1), it.get(0), it.get(12), "", ahrefsFile.name)

                } catch (e: Exception) {
                    null;
                }
            }

        extractInfoForAllAhrefsItems(ahrefsItems, resolveIntoWebsiteContent)

    }

    fun extractInfoForAllAhrefsItems(
        ahrefsItems: List<AhrefsItem>,
        resolveIntoWebsiteContent: Boolean,
    ) {
        val notResolved = mutableListOf<AhrefsItem>()

        var treated = 0
        var newIds = 0
        ahrefsItems.forEach { ahrefsItem ->

            try {
                val percent = treated * 100 / ahrefsItems.size

                println("Start resolving  ($treated/${ahrefsItems.size}  - $percent%) $ahrefsItem")

                val ids = resolveId(ahrefsItem, resolveIntoWebsiteContent)
                if (ids.isNotEmpty()) {
                    ids.filterNot { isPreContentId(it) }.forEach {
                        if (database.saveResolvedId(ahrefsItem, it)) {
                            newIds++
                            println("!! Found a new id: ${it.id}");
                        } else {
                            println("Id already exist: ${it.id}");
                        }
                    }
                } else {
                    println("- Unable to resolve any id with current item $ahrefsItem")
                    notResolved.add(ahrefsItem)
                }
            } catch (e: Exception) {
                println("- Error during extraction for $ahrefsItem \n->(${e.message})")
                notResolved.add(ahrefsItem)
            } finally {
                treated++
            }
        }

        val total = database.getResolvedIdsCount()
        println("Finish  resolve, total $total  (new: $newIds / can't resolve : ${notResolved.size} )")
    }


    fun resolveAllIdsFromUrlList(urls: List<String>, resolveIntoWebsiteContent: Boolean = false) {

        loadLinksToSkip()
        var treated = 0

        var newIds = 0
        urls.forEach { url ->

            try {
                val percent = treated * 100 / urls.size

                println("Start resolving  ($treated/${urls.size}  - $percent%) $url")
                if (skipList.contains(url)) {
                    println("Ignore $url (previous resolve not succeded)")
                    return@forEach
                }
                val ids = resolveId(url, resolveIntoWebsiteContent).distinct()
                if (ids.isNotEmpty()) {
                    newIds += ids.size
                    println("!! Saving ${ids.size} new ids (total $newIds)")
                    saveAllIds(ids, url)
                } else {
                    println("- Unable to resolve any id with current item $url")
                    addLinkToSkipList(url)
                }
            } catch (e: Exception) {
                println("- Error during extraction for $url \n->(${e.message})")
                addLinkToSkipList(url)
            } finally {
                treated++
            }
        }

        val total = database.getResolvedIdsCount()
        println("Finish  resolve, total $total  (can't resolve : ${skipList.size} )")

    }

    var skipList = mutableSetOf<String>()

    fun loadLinksToSkip() {
        skipList.clear()
        val notresolvedPathPath = File(basePath, "links.skip")
        if (notresolvedPathPath.exists())
            notresolvedPathPath.readLines().map { skipList.add(it) }

    }

    fun addLinkToSkipList(url: String) {
        skipList.add(url)
        val notresolvedPathPath = File(basePath, "links.skip")
        notresolvedPathPath.writeText(skipList.joinToString("\n"))

    }

    private fun extractId(content: String, source: String): List<RestaurantIdentifier> {
        val resultIds = mutableListOf<RestaurantIdentifier>()

        regex.forEach { regex ->

            val regexResult = regex.regex.findAll(content)
            regexResult.forEach { result ->
                val res = result.groups["id"]?.value
                if (res != null && res.trim().isNotBlank()) {

                    resultIds.add(RestaurantIdentifier(res, regex.type, source, ""))
                }
            }
        }
        return resultIds.distinctBy { it.id }

    }


    private fun executeUrl(url: String): String {
        if (database.isSourceAlreadyResolved(url)) {
            println("Ignore content resolving for $url (content already used for extraction)")
            return ""
        }
        val httpGet = HttpGet(URI.create(url))
        val proxy = if (useProxyForContent) proxyHelper.proxies.random() else null

        var proxyDetails = if (proxy != null) "with  proxy ${proxy.host} " else ""
        println("Execute $url $proxyDetails (waiting between $sleepFromSec and $sleepToSec sec)")

        Thread.sleep(Random.nextLong(sleepFromSec * 1000L, sleepToSec * 1000L))

        HttpHelper.createClient(proxy)
            .use { closeableHttpClient ->
                closeableHttpClient.execute(httpGet)
                    .use { response ->
                        return EntityUtils.toString(response.entity)
                    }
            }
    }

    fun getResolvedRestaurantId(): List<ResolvedIdDb> {
        return database.getResolvedIds()
    }

    fun removeResolvedId(id: String): Boolean {
        return database.deleteResolvedId(id)
    }

}