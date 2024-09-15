package pos.common

class HttpProxyHelper() {
    private val user = "sfOHRZ0Xsi"
    private val pwd = "94y44ixl"
    private val port = 1080

    val proxies = listOf<HttpHelper.ProxyParams>(
//        HttpHelper.ProxyParams("iad.socks.ipvanish.com", port, user, pwd),
//        HttpHelper.ProxyParams("atl.socks.ipvanish.com", port, user, pwd),
//        HttpHelper.ProxyParams("bos.socks.ipvanish.com", port, user, pwd),
//        HttpHelper.ProxyParams("clt.socks.ipvanish.com", port, user, pwd),
//        HttpHelper.ProxyParams("chi.socks.ipvanish.com", port, user, pwd),
//        HttpHelper.ProxyParams("dal.socks.ipvanish.com", port, user, pwd),
//        HttpHelper.ProxyParams("den.socks.ipvanish.com", port, user, pwd),
//        HttpHelper.ProxyParams("lax.socks.ipvanish.com", port, user, pwd),
//        HttpHelper.ProxyParams("mia.socks.ipvanish.com", port, user, pwd),
//        HttpHelper.ProxyParams("msy.socks.ipvanish.com", port, user, pwd),
//        HttpHelper.ProxyParams("nyc.socks.ipvanish.com", port, user, pwd),
//        HttpHelper.ProxyParams("phx.socks.ipvanish.com", port, user, pwd),
//        HttpHelper.ProxyParams("sea.socks.ipvanish.com", port, user, pwd),
        HttpHelper.ProxyParams("10.64.0.1", port, null, null),
        HttpHelper.ProxyParams("10.64.0.1", port, null, null),
        HttpHelper.ProxyParams("10.64.0.1", port, null, null),
        HttpHelper.ProxyParams("10.64.0.1", port, null, null),
        HttpHelper.ProxyParams("10.64.0.1", port, null, null),

        )
}
