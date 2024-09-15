package pos.common

import org.apache.http.client.methods.HttpPost
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.config.Registry
import org.apache.http.config.RegistryBuilder
import org.apache.http.conn.socket.ConnectionSocketFactory
import org.apache.http.conn.socket.PlainConnectionSocketFactory
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.protocol.HttpContext
import org.apache.http.ssl.SSLContexts
import java.io.IOException
import java.net.*
import javax.net.ssl.SSLContext


object BasicHttpClient {
//
//    fun createClient(): CloseableHttpClient {
//        val reg = RegistryBuilder.create<ConnectionSocketFactory>()
//            .register("http", ProxySelectorPlainConnectionSocketFactory.INSTANCE)
//            .register("https", ProxySelectorSSLConnectionSocketFactory(SSLContexts.createSystemDefault()))
//            .build()
//        val cm = PoolingHttpClientConnectionManager(reg)
//        return HttpClients.custom()
//            .setConnectionManager(cm)
//            .build()
//    }
//
//    private fun createSocket(context: HttpContext): Socket {
//        val httpTargetHost = context.getAttribute(HttpCoreContext.HTTP_TARGET_HOST) as HttpHost
//        val uri = URI.create(httpTargetHost.toURI())
//        val proxy = ProxySelector.getDefault().select(uri).iterator().next()
//        return Socket(proxy)
//    }
//
//    private enum class ProxySelectorPlainConnectionSocketFactory : ConnectionSocketFactory {
//        INSTANCE;
//
//        override fun createSocket(context: HttpContext): Socket {
//            return BasicHttpClient.createSocket(context)
//        }
//
//        @Throws(IOException::class)
//        override fun connectSocket(
//            connectTimeout: Int,
//            sock: Socket?,
//            host: HttpHost?,
//            remoteAddress: InetSocketAddress?,
//            localAddress: InetSocketAddress?,
//            context: HttpContext?
//        ): Socket {
//            return PlainConnectionSocketFactory.INSTANCE.connectSocket(
//                connectTimeout,
//                sock,
//                host,
//                remoteAddress,
//                localAddress,
//                context
//            )
//        }
//    }
//
//    private class ProxySelectorSSLConnectionSocketFactory internal constructor(sslContext: SSLContext?) :
//        SSLConnectionSocketFactory(sslContext) {
//        override fun createSocket(context: HttpContext): Socket {
//            return BasicHttpClient.createSocket(context)
//        }
//    }

//    iad.socks.ipvanish.com
//    atl.socks.ipvanish.com
//    bos.socks.ipvanish.com
//    clt.socks.ipvanish.com
//    chi.socks.ipvanish.com
//    dal.socks.ipvanish.com
//    den.socks.ipvanish.com
//    lax.socks.ipvanish.com
//    mia.socks.ipvanish.com
//    msy.socks.ipvanish.c
//    nyc.socks.ipvanish.com
//    phx.socks.ipvanish.com
//    sea.socks.ipvanish.com
//    lon.socks.ipvanish.com

    private val proxyPort = 1080;
    val proxyHost = "lax.socks.ipvanish.com";
    val username = "Vetdfm0k";
    val password = "UTMM60n7a";
//
//     Authenticator { route, response ->
//        val credential: String = Credentials.basic(username, password)
//        response.request.newBuilder()
//            .header("Proxy-Authorization", credential)
//            .build()
//    }

    internal class MyAuthent : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication {
            return PasswordAuthentication("Vetdfm0k", "UTMM60n7a".toCharArray());
        }
    }

    fun executeRequest(url: String, method: String, body: String, headers: List<String>, proxy: String): String {

        Authenticator.setDefault(MyAuthent())
        val reg: Registry<ConnectionSocketFactory> = RegistryBuilder.create<ConnectionSocketFactory>()
            .register("http", PlainConnectionSocketFactory.INSTANCE)
            .register("https", MyConnectionSocketFactory(SSLContexts.createSystemDefault()))
            .build()
        val cm = PoolingHttpClientConnectionManager(reg)
        val httpclient = HttpClients.custom()
            .setConnectionManager(cm)
            .build()
        try {
            val socksaddr = InetSocketAddress("lax.socks.ipvanish.com", 1080)
            val context = HttpClientContext.create()
            context.setAttribute("socks.address", socksaddr)
           // val target = HttpHost("", 80, "https")
            val request = HttpPost("https://reqres.in/api/users")
            println("Executing request $request via SOCKS proxy $socksaddr")
            val response = httpclient.execute( request, context)
            try {
                println("----------------------------------------")
                println(response.statusLine)
                println(String(response.entity.content.readAllBytes()))
                // EntityUtils.consume(response.entity)
            } finally {
                response.close()
            }
        } finally {
            httpclient.close()
        }
        return ""
    }

    internal class MyConnectionSocketFactory(sslContext: SSLContext?) : SSLConnectionSocketFactory(sslContext) {
        @Throws(IOException::class)
        override fun createSocket(context: HttpContext): Socket {
            val socksaddr = context.getAttribute("socks.address") as InetSocketAddress
            val proxy = Proxy(Proxy.Type.SOCKS, socksaddr)
            return Socket(proxy)
        }
    }

}