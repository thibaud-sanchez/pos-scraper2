package resolver

class RestaurantIdRegex(val type:String,
                        val regex:Regex,
                        // This means that this regex is only use to determine website to look inside to look for id (with other regex)
                        val preContent:Boolean=false) {
}