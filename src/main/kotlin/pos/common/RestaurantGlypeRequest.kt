package pos.common

import model.Restaurant

data class RestaurantGlypeRequest(override val id:String, val url:String, override val type:String="") :IRestaurantRequest{

}
