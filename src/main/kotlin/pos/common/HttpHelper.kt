package pos.common

import org.apache.http.HttpHost
import org.apache.http.config.Registry
import org.apache.http.config.RegistryBuilder
import org.apache.http.conn.socket.ConnectionSocketFactory
import org.apache.http.conn.socket.PlainConnectionSocketFactory
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.protocol.HttpContext
import org.apache.http.ssl.SSLContexts
import java.io.IOException
import java.net.*
import javax.net.ssl.SSLContext

object HttpHelper {
    data class ProxyParams(val host: String, val port: Int, val user: String?, val password: String?) {

    }

    fun createClient(proxy: ProxyParams? = null): CloseableHttpClient {

        if (proxy != null && proxy.user!=null && proxy.password!=null) {
            Authenticator.setDefault(object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(proxy.user, proxy.password.toCharArray());
                }
            })
        }
        val reg: Registry<ConnectionSocketFactory> = RegistryBuilder.create<ConnectionSocketFactory>()
            .register("http", ProxySelectorPlainConnectionSocketFactory.withProxy(proxy))
            .register("https", ProxySelectorSSLConnectionSocketFactory(SSLContexts.createSystemDefault(), proxy))
            .build()
        val cm = PoolingHttpClientConnectionManager(reg)
        var clientBuilder = HttpClients.custom()

        if (proxy != null)
            clientBuilder = clientBuilder.setConnectionManager(cm)

        return clientBuilder.build()
    }

    private fun createSocket(context: HttpContext): Socket {
        val socksaddr = context.getAttribute("socks.address") as InetSocketAddress
        val proxy = Proxy(Proxy.Type.SOCKS, socksaddr)
        return Socket(proxy)
    }

    private fun saveProxyIntoContext(context: HttpContext, proxy: ProxyParams?) {
        if (proxy != null) {
            val socksaddr = InetSocketAddress(proxy.host, proxy.port)
            context.setAttribute("socks.address", socksaddr)
        }
    }

    private object ProxySelectorPlainConnectionSocketFactory : ConnectionSocketFactory {
        private var proxy: ProxyParams? = null
        override fun createSocket(context: HttpContext): Socket {
            saveProxyIntoContext(context, proxy)
            return HttpHelper.createSocket(context)
        }

        @Throws(IOException::class)
        override fun connectSocket(
            connectTimeout: Int,
            sock: Socket?,
            host: HttpHost?,
            remoteAddress: InetSocketAddress?,
            localAddress: InetSocketAddress?,
            context: HttpContext?
        ): Socket {
            return PlainConnectionSocketFactory.INSTANCE.connectSocket(
                connectTimeout,
                sock,
                host,
                remoteAddress,
                localAddress,
                context
            )
        }

        fun withProxy(proxy: ProxyParams?): ProxySelectorPlainConnectionSocketFactory {
            this.proxy = proxy
            return this
        }
    }

    private class ProxySelectorSSLConnectionSocketFactory internal constructor(
        sslContext: SSLContext?,
        val proxy: ProxyParams?
    ) :
        SSLConnectionSocketFactory(sslContext) {
        override fun createSocket(context: HttpContext): Socket {
            saveProxyIntoContext(context, proxy)
            return HttpHelper.createSocket(context)
        }
    }

}