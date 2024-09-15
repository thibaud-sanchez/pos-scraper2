package resolver

import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.LaxRedirectStrategy
import org.json.JSONObject
import pos.PosDatabase
import java.io.File
import java.net.URI

class AhrefsResolver(
    val basePath: File,
    val database: PosDatabase,
    val subdomains: List<String>,
    val sinceLastResolve:Boolean=true,
    val resolver: RestaurantIdResolver
) {

    private val AHREFS_TOKEN = "K7LDoomPGFv27dekqp-x1VySeoYq-W7abzOpLkEu"
    private val requestBuilder: RequestConfig.Builder = RequestConfig.custom()
    private val client: CloseableHttpClient

    init {
        val timeoutMs = 60 * 1000
        requestBuilder.setConnectTimeout(timeoutMs);
        requestBuilder.setConnectionRequestTimeout(timeoutMs);
        requestBuilder.setSocketTimeout(timeoutMs);

        client = HttpClientBuilder.create().setDefaultRequestConfig(requestBuilder.build())
            .setRedirectStrategy(LaxRedirectStrategy()).build()
    }

    fun resolveAllIdsFromApi(
        resolveIntoWebsiteContent: Boolean = false,
        limitApi: Int =10000
    ) {

        val latestResolvedDate =  if(sinceLastResolve)
            database.getLatestResolvedDate()
        else "2020-01-01"


        subdomains.forEach { domain->
            val ahrefsUrl =
                """https://apiv2.ahrefs.com/?from=backlinks_one_per_domain&select=url_from,url_to,title,last_visited&where=last_visited%3E=%22$latestResolvedDate%22&limit=$limitApi&target=$domain&mode=subdomains&output=json&token=$AHREFS_TOKEN"""

            println("Executing ahrefs request : $ahrefsUrl")
            val response = executeUrl(ahrefsUrl)
            val responseJson = JSONObject(response)
            val pagesArray = responseJson.getJSONArray("refpages")

            println("Found ${pagesArray.length()} results from AHREFS")
            val ahrefsResults = mutableListOf<AhrefsItem>()
            for (i in 0 until pagesArray.length()) {

                val result = pagesArray.getJSONObject(i)

                ahrefsResults.add(
                    AhrefsItem(
                        result.optString("url_from", ""),
                        result.optString("title", ""),
                        result.optString("url_to", ""),
                        i.toString(),
                        "api"
                    )
                )
            }
            resolver.extractInfoForAllAhrefsItems(ahrefsResults, resolveIntoWebsiteContent)
        }
    }

    private fun executeUrl(url: String): String {
        val httpGet = HttpGet(URI.create(url))
        val response = client.execute(httpGet)
        val res = String(response.entity.content.readAllBytes())
        response.close()
        return res
    }
}