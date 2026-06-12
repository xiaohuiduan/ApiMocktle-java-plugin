package com.apimocktle.http

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.apimocktle.core.event.EventBus
import com.apimocktle.core.event.EventKeys
import com.apimocktle.http.HttpClientProvider.Companion.getInstance
import com.apimocktle.logging.IdeaLog
import com.apimocktle.settings.SettingBinder

/**
 * Creates [HttpClient] instances using Apache HttpClient.
 *
 * This is the single entry point for obtaining an [HttpClient].
 * Every client returned is wrapped with [LoggingHttpClient].
 *
 * This class is action-scoped — prefer [getInstance] to reuse the instance
 */
@Service(Service.Level.PROJECT)
class HttpClientProvider(private val project: Project) {

    @Volatile
    private var cached: Pair<String, HttpClient>? = null

    init {
        EventBus.getInstance(project).register(EventKeys.ON_COMPLETED) {
            dispose()
        }
    }

    fun getClient(
        httpTimeOut: Int? = null,
        unsafeSsl: Boolean? = null
    ): HttpClient {
        val settings = SettingBinder.getInstance(project).read()
        val resolvedHttpTimeOutSec = httpTimeOut ?: settings.httpTimeOut ?: 30
        val resolvedHttpTimeOutMs = resolvedHttpTimeOutSec * 1000
        val resolvedUnsafeSsl = unsafeSsl ?: settings.unsafeSsl ?: false

        val raw = getRawClient(resolvedHttpTimeOutMs, resolvedUnsafeSsl)
        return raw.logging()
    }

    fun dispose() {
        synchronized(this) {
            cached?.second?.close()
            cached = null
        }
    }

    private fun getRawClient(httpTimeOut: Int, unsafeSsl: Boolean): HttpClient {
        val key = "$httpTimeOut|$unsafeSsl"
        cached?.let { (k, p) -> if (k == key) return p }
        synchronized(this) {
            cached?.let { (k, p) -> if (k == key) return p }
            cached?.second?.close()
            val client = ApacheHttpClient(httpTimeOut, unsafeSsl)
            cached = key to client
            return client
        }
    }

    companion object {
        fun getInstance(project: Project): HttpClientProvider = project.service()
    }
}

fun HttpClient.logging() = LoggingHttpClient(this)

class LoggingHttpClient(private val delegate: HttpClient) : HttpClient by delegate {
    companion object : IdeaLog

    override suspend fun execute(request: HttpRequest): HttpResponse {
        val start = System.currentTimeMillis()
        LOG.info("--> ${request.method} ${request.buildUrl()}")
        try {
            val response = delegate.execute(request)
            val elapsed = System.currentTimeMillis() - start
            LOG.info(
                "<-- ${request.method} ${request.buildUrl()}: ${response.code} (${elapsed}ms):\n-------\n" +
                        "${response.body}\n" +
                        "-------"
            )
            return response
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - start
            LOG.info("<-- FAILED (${elapsed}ms) ${e.message}", e)
            throw e
        }
    }
}
