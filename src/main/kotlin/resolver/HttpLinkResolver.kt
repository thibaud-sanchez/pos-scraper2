package resolver

import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.LaxRedirectStrategy
import pos.PosDatabase
import java.io.File

class HttpLinkResolver(
    val basePath: File,
    val database: PosDatabase,
    val requests: List<HttpRequestBase>,
    val resolver: RestaurantIdResolver
) {

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

    fun resolveAllIdsFromRequest() {
        requests.forEach { request ->
            println("Executing request : ${request.uri}")
            val response = executeRequest(request)
            resolver.resolveAllIdsFromContent(response,request.uri.toString())
        }
    }

    private fun executeRequest(request: HttpRequestBase): String {
        val response = client.execute(request)
        val res = String(response.entity.content.readAllBytes())
        response.close()
        return res
    }
}