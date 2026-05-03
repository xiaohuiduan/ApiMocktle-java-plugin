package com.apimocktle.exporter.yapi

import com.intellij.openapi.project.Project
import com.apimocktle.http.HttpClientProvider

/**
 * Creates a [YapiApiClient] for the current export operation.
 */
interface YapiApiClientProvider {

    val serverUrl: String

    /**
     * Initializes the provider by resolving the server URL and personal token.
     * Must be called before [getClient].
     * Returns null if token resolution failed.
     */
    suspend fun init(): Boolean

    /**
     * Returns a [YapiApiClient] bound to the given project.
     */
    suspend fun getClient(projectId: String): YapiApiClient
}

class DefaultYapiApiClientProvider(
    private val project: Project
) : YapiApiClientProvider {

    private val settingsHelper = YapiSettingsHelper.getInstance(project)
    private val httpClient by lazy { HttpClientProvider.getInstance(project).getClient() }

    private var _serverUrl: String = ""
    private var token: String = ""

    override val serverUrl: String get() = _serverUrl

    override suspend fun init(): Boolean {
        _serverUrl = settingsHelper.resolveServerUrl()
            ?: throw IllegalStateException("YAPI server URL is not configured.")
        token = settingsHelper.resolvePersonalToken() ?: return false
        return true
    }

    override suspend fun getClient(projectId: String): YapiApiClient {
        return DefaultYapiApiClient(_serverUrl, token, projectId, httpClient)
    }
}
