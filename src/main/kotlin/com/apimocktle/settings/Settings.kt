package com.apimocktle.settings

import com.apimocktle.extension.ExtensionConfigRegistry
import com.apimocktle.settings.state.ApplicationSettingsSupport
import com.apimocktle.settings.state.ProjectSettingsSupport

/**
 * 插件设置，包含所有配置选项。
 *
 * @see DefaultSettingBinder
 * @see ProjectSettingsSupport
 * @see ApplicationSettingsSupport
 */
data class Settings(
    override var feignEnable: Boolean = false,
    override var extensionConfigs: String = defaultExtensionCodes(),
    override var queryExpanded: Boolean = true,
    override var formExpanded: Boolean = true,
    override var pathMulti: String = "ALL",
    override var inferReturnMain: Boolean = true,
    override var yapiServer: String? = null,
    override var yapiPersonalToken: String? = null,
    override var enableUrlTemplating: Boolean = true,
    override var switchNotice: Boolean = true,
    override var yapiExportMode: String = YapiExportMode.ALWAYS_UPDATE.name,
    override var yapiReqBodyJson5: Boolean = false,
    override var yapiResBodyJson5: Boolean = false,
    override var httpTimeOut: Int = 30,
    override var unsafeSsl: Boolean = false,
    override var httpClient: String = HttpClientType.APACHE.value,
    override var logLevel: Int = 50,
    override var outputDemo: Boolean = true,
    override var outputCharset: String = "UTF-8",
    override var builtInConfig: String? = null,
    override var remoteConfig: Array<String> = emptyArray(),
    override var autoScanEnabled: Boolean = true,
    override var concurrentScanEnabled: Boolean = false,
    override var autoInjectAgent: Boolean = false
) : ProjectSettingsSupport, ApplicationSettingsSupport {

    companion object {
        private fun defaultExtensionCodes(): String {
            return ExtensionConfigRegistry.codesToString(ExtensionConfigRegistry.defaultCodes())
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Settings

        if (feignEnable != other.feignEnable) return false
        if (queryExpanded != other.queryExpanded) return false
        if (formExpanded != other.formExpanded) return false
        if (pathMulti != other.pathMulti) return false
        if (inferReturnMain != other.inferReturnMain) return false
        if (enableUrlTemplating != other.enableUrlTemplating) return false
        if (switchNotice != other.switchNotice) return false
        if (yapiReqBodyJson5 != other.yapiReqBodyJson5) return false
        if (yapiResBodyJson5 != other.yapiResBodyJson5) return false
        if (httpTimeOut != other.httpTimeOut) return false
        if (unsafeSsl != other.unsafeSsl) return false
        if (logLevel != other.logLevel) return false
        if (outputDemo != other.outputDemo) return false
        if (yapiServer != other.yapiServer) return false
        if (yapiPersonalToken != other.yapiPersonalToken) return false
        if (yapiExportMode != other.yapiExportMode) return false
        if (httpClient != other.httpClient) return false
        if (extensionConfigs != other.extensionConfigs) return false
        if (outputCharset != other.outputCharset) return false
        if (builtInConfig != other.builtInConfig) return false
        if (!remoteConfig.contentEquals(other.remoteConfig)) return false
        if (autoScanEnabled != other.autoScanEnabled) return false
        if (concurrentScanEnabled != other.concurrentScanEnabled) return false
        if (autoInjectAgent != other.autoInjectAgent) return false

        return true
    }

    override fun hashCode(): Int {
        var result = feignEnable.hashCode()
        result = 31 * result + queryExpanded.hashCode()
        result = 31 * result + formExpanded.hashCode()
        result = 31 * result + pathMulti.hashCode()
        result = 31 * result + inferReturnMain.hashCode()
        result = 31 * result + enableUrlTemplating.hashCode()
        result = 31 * result + switchNotice.hashCode()
        result = 31 * result + yapiReqBodyJson5.hashCode()
        result = 31 * result + yapiResBodyJson5.hashCode()
        result = 31 * result + httpTimeOut
        result = 31 * result + unsafeSsl.hashCode()
        result = 31 * result + logLevel
        result = 31 * result + outputDemo.hashCode()
        result = 31 * result + (yapiServer?.hashCode() ?: 0)
        result = 31 * result + (yapiPersonalToken?.hashCode() ?: 0)
        result = 31 * result + yapiExportMode.hashCode()
        result = 31 * result + httpClient.hashCode()
        result = 31 * result + extensionConfigs.hashCode()
        result = 31 * result + outputCharset.hashCode()
        result = 31 * result + (builtInConfig?.hashCode() ?: 0)
        result = 31 * result + remoteConfig.contentHashCode()
        result = 31 * result + autoScanEnabled.hashCode()
        result = 31 * result + concurrentScanEnabled.hashCode()
        result = 31 * result + autoInjectAgent.hashCode()
        return result
    }
}
