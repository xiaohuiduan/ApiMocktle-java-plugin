package com.apimocktle.settings.state

/**
 * 项目级设置属性接口。
 */
interface ProjectSettingsSupport {
    var yapiPersonalToken: String?

    fun copyTo(newSetting: ProjectSettingsSupport) {
        this.yapiPersonalToken?.let { newSetting.yapiPersonalToken = it }
    }
}

/**
 * 应用级设置属性接口。
 */
interface ApplicationSettingsSupport {
    var feignEnable: Boolean
    var extensionConfigs: String
    var queryExpanded: Boolean
    var formExpanded: Boolean
    var pathMulti: String
    /** 从包装类推断返回主类型 */
    var inferReturnMain: Boolean
    var yapiServer: String?
    var yapiPersonalToken: String?
    var enableUrlTemplating: Boolean
    var switchNotice: Boolean
    var yapiExportMode: String
    var yapiReqBodyJson5: Boolean
    var yapiResBodyJson5: Boolean
    var httpTimeOut: Int
    var unsafeSsl: Boolean
    var httpClient: String
    var logLevel: Int
    var outputDemo: Boolean
    var outputCharset: String
    var builtInConfig: String?
    var remoteConfig: Array<String>
    /** 文件变更时自动扫描API */
    var autoScanEnabled: Boolean
    /** 启用并发 API 扫描 */
    var concurrentScanEnabled: Boolean
    /** 自动注入 Mock Agent 到 Application 运行配置 */
    var autoInjectAgent: Boolean

    fun copyTo(newSetting: ApplicationSettingsSupport) {
        newSetting.feignEnable = this.feignEnable
        newSetting.extensionConfigs = this.extensionConfigs
        newSetting.queryExpanded = this.queryExpanded
        newSetting.formExpanded = this.formExpanded
        newSetting.pathMulti = this.pathMulti
        newSetting.inferReturnMain = this.inferReturnMain
        newSetting.yapiServer = this.yapiServer
        newSetting.yapiPersonalToken = this.yapiPersonalToken
        newSetting.enableUrlTemplating = this.enableUrlTemplating
        newSetting.switchNotice = this.switchNotice
        newSetting.yapiExportMode = this.yapiExportMode
        newSetting.yapiReqBodyJson5 = this.yapiReqBodyJson5
        newSetting.yapiResBodyJson5 = this.yapiResBodyJson5
        newSetting.logLevel = this.logLevel
        newSetting.outputDemo = this.outputDemo
        newSetting.outputCharset = this.outputCharset
        newSetting.builtInConfig = this.builtInConfig
        newSetting.httpTimeOut = this.httpTimeOut
        newSetting.unsafeSsl = this.unsafeSsl
        newSetting.httpClient = this.httpClient
        newSetting.remoteConfig = this.remoteConfig
        newSetting.autoScanEnabled = this.autoScanEnabled
        newSetting.concurrentScanEnabled = this.concurrentScanEnabled
        newSetting.autoInjectAgent = this.autoInjectAgent
    }
}
