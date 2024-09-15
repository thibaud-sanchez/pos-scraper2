package pos.common

class CurlClient {

    companion object {
        fun buildRequest(url: String, method: String, body: String,headers:List<String>, proxy: String): String {
            var cmd = """curl --location --request $method "$url" """ +
                    " --fail --silent --show-error"
            headers.forEach {
                cmd+=" --header \"$it\""
            }
       //    cmd+=" --header \"Content-Type: application/json\""+

            if (proxy.isNotBlank()) {
                cmd += " -x $proxy"
            }

            var bodyEscaped = body.replace("\"","\\\"").replace("\n"," ")
           cmd += """ --data-raw "$bodyEscaped" """
            return cmd.trimIndent()
        }

        fun executeCommand(curlCmd: String): String {
            println("executing: $curlCmd")
            val process = Runtime.getRuntime().exec(curlCmd)
            process.waitFor()

            val output: String = process.inputStream.bufferedReader().readText().trim()
            if (output.isBlank()) {
                val error=process.errorStream.bufferedReader().readText()
                throw CurlError(error)
            }
            return output
        }
    }

}

class CurlError(error: String) : Exception(error) {

}
