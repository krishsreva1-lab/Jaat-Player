package com.krish.jaatplayer.applecanvas

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object AppleMusicTokenProvider {
    private var cachedToken: String? = null
    private val mutex = Mutex()

    private val httpClient = HttpClient(OkHttp) {
        expectSuccess = true
    }

    suspend fun getToken(): String {
        return mutex.withLock {
            cachedToken?.let { return@withLock it }
            try {
                val htmlResponse = httpClient.get("https://beta.music.apple.com")
                val htmlBody = htmlResponse.bodyAsText()
                val indexJsRegex = Regex("""src="(/assets/index-[^"]+\.js)"""")
                val match = indexJsRegex.find(htmlBody) ?: throw Exception("Could not find index.js")
                val indexJsUri = match.groupValues[1]

                val indexJsResponse = httpClient.get("https://beta.music.apple.com$indexJsUri")
                val indexJsBody = indexJsResponse.bodyAsText()

                val tokenRegex = Regex("""eyJ[A-Za-z0-9\-_=]+\.[A-Za-z0-9\-_=]+\.[A-Za-z0-9\-_=]+""")
                val all = tokenRegex.findAll(indexJsBody).map { it.value }.toList()
                // Prefer a token that is not about to expire, otherwise behave like before (first match)
                val token = all.firstOrNull { isUsable(it) }
                    ?: all.firstOrNull()
                    ?: throw Exception("Could not find token")
                println("AppleMusicCanvas: token refreshed")
                cachedToken = token
                token
            } catch (e: Exception) {
                println("AppleMusicCanvas: token fetch failed: ${e.message}")
                "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6IldlYlBsYXlLaWQifQ.eyJpc3MiOiJBTVBXZWJQbGF5IiwiaWF0IjoxNzc0NDU2MzgyLCJleHAiOjE3ODE3MTM5ODIsInJvb3RfaHR0cHNfb3JpZ2luIjpbImFwcGxlLmNvbSJdfQ.4n8qYF4qa18sL1E0G9A3qX35cD8wQ-IJcS9Bh8ZT8JV_yLBtVq46B-9-2ZS3EvWHuw3yK9BYFYAhAdTaDm38vQ"
            }
        }
    }

    private fun isUsable(jwt: String): Boolean = try {
        val payload = String(
            java.util.Base64.getUrlDecoder().decode(jwt.split(".")[1].trimEnd('=')),
        )
        val exp = Regex(""""exp"\s*:\s*(\d+)""").find(payload)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        exp > System.currentTimeMillis() / 1000 + 3600
    } catch (_: Exception) {
        false
    }

    fun invalidate() { cachedToken = null }
}
