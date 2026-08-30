package com.apimocktle.http

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import com.apimocktle.http.HttpClientProvider.Companion.getInstance
import com.apimocktle.logging.IdeaLog
import com.apimocktle.settings.SettingBinder
import java.util.concurrent.TimeUnit

/**
 * Creates [HttpClient] instances using Apache HttpClient.
 *
 * This is the single entry point for obtaining an [HttpClient].
 * Every client returned is wrapped with [LoggingHttpClient].
 *
 * The underlying client is shared per project and closed when the project closes.
 * Do NOT close it per action — concurrent exports would get their connection pool
 * pulled out from under them.
 */
@Service(Service.Level.PROJECT)
class HttpClientProvider(private val project: Project) : Disposable {

    @Volatile
    private var cached: Pair<String, HttpClient>? = null

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

    /** Client retired on a timeout/ssl settings change; kept briefly so in-flight requests finish. */
    private fun retire(client: HttpClient) {
        AppExecutorUtil.getAppScheduledExecutorService().schedule({
            runCatching { client.close() }
        }, 30, TimeUnit.SECONDS)
    }

    private fun getRawClient(httpTimeOut: Int, unsafeSsl: Boolean): HttpClient {
        val key = "$httpTimeOut|$unsafeSsl"
        cached?.let { (k, p) -> if (k == key) return p }
        synchronized(this) {
            cached?.let { (k, p) -> if (k == key) return p }
            val old = cached?.second
            val client = ApacheHttpClient(httpTimeOut, unsafeSsl)
            cached = key to client
            old?.let { retire(it) }
            return client
        }
    }

    /** Called by the platform when the project closes; also available for explicit cleanup. */
    override fun dispose() {
        synchronized(this) {
            cached?.second?.let { runCatching { it.close() } }
            cached = null
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
