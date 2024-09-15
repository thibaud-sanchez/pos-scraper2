package pos.common

import org.apache.http.client.methods.HttpRequestBase

data class RestaurantHttpRequest(
    override val id: String,
    val request: HttpRequestBase,
    override val type: String = "",
    val extra: String = ""
) : IRestaurantRequest {

}
