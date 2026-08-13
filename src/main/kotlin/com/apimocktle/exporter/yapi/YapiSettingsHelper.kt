package com.apimocktle.exporter.yapi

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.apimocktle.core.threading.swing
import com.apimocktle.settings.SettingBinder

interface YapiSettingsHelper {

    suspend fun resolveServerUrl(dumb: Boolean = false): String?

    suspend fun resolvePersonalToken(): String?

    companion object {
        fun getInstance(project: Project): YapiSettingsHelper = project.service()
    }
}

@Service(Service.Level.PROJECT)
class DefaultYapiSettingsHelper(private val project: Project) : YapiSettingsHelper {

    private val settingBinder: SettingBinder by lazy {
        SettingBinder.getInstance(project)
    }

    override suspend fun resolveServerUrl(dumb: Boolean): String? {
        val settings = settingBinder.read()
        settings.yapiServer
            ?.let(YapiUrls::normalizeBaseUrl)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        if (dumb) return null

        return DEFAULT_YAPI_SERVER_URL
    }

    companion object {
        const val DEFAULT_YAPI_SERVER_URL = "http://localhost:49128"
    }

    override suspend fun resolvePersonalToken(): String? {
        val settings = settingBinder.read()
        settings.yapiPersonalToken?.takeIf { it.isNotBlank() }?.let { return it }

        val token = swing {
            Messages.showInputDialog(
                project,
                "请输入 ApiMocktle 个人令牌：",
                "ApiMocktle 个人令牌",
                Messages.getInformationIcon(),
                null,
                null
            )
        }
        if (token.isNullOrBlank()) return null

        settingBinder.save(settings.copy(yapiPersonalToken = token))
        return token
    }
}
