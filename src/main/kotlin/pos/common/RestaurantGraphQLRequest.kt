package pos.common

data class RestaurantGraphQLRequest(
    override val id:String,
    val url:String,
    val query:String,
    override val type:String="") : IRestaurantRequest {

}
