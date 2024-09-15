package pos.common

import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit


class BasicHttpClient2 {
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

    var proxyAuthenticator: Authenticator = Authenticator { route, response ->
        val credential = Credentials.basic(username, password)
        response.request.newBuilder()
            .header("Proxy-Authorization", credential)
            .build()
    }

    fun executeRequest(url: String, method: String, body: String,headers:List<String>, proxy: String): String {

        val client =  OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .proxy( Proxy(Proxy.Type.HTTP,  InetSocketAddress(proxyHost, proxyPort)))
        .proxyAuthenticator(proxyAuthenticator)
            .build();
        val credential: String = Credentials.basic(username, password)
//        response.request.newBuilder()
        val request: Request = Request.Builder()
            .url("http://ip.jsontest.com/")
            .get()
            //.header("Proxy-Authorization", credential)
            .build()

       return  client.newCall(request).execute().use { response -> return response.body!!.string() }
    }

}