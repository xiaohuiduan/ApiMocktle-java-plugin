package com.apimocktle.ide.action

import com.apimocktle.exporter.model.ExportFormat
import com.apimocktle.i18n.ApiMocktleBundle

/**
 * Action to export APIs to ApiMocktle platform.
 *
 * Extends [BaseExportAction] with ApiMocktle-specific format configuration.
 *
 * @see BaseExportAction for the export implementation
 * @see ExportFormat.YAPI for the target format
 */
class ExportToYapiAction : BaseExportAction() {

    override val exportFormat: ExportFormat = ExportFormat.YAPI

    /**
     * 进度条 / 通知标题用的文案。
     *
     * 注意：这里**不能**复用 `action.<action-id>.text` 这类平台本地化 key ——
     * 那些 key 由平台在注册 action 时读取，若此处也引用同一个 key，
     * 一旦 key 改名，action 类会在**类初始化阶段**抛出 MissingResourceException，
     * 导致整个 action 无法实例化。UI 标题使用独立的 `ui.*` key。
     */
    override val actionName: String get() = ApiMocktleBundle.message("ui.action.exportToApiMocktle.title")
}
