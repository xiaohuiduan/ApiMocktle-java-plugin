package com.apimocktle.ide.action

import com.apimocktle.exporter.model.ExportFormat

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
    override val actionName: String = "导出到ApiMocktle"
}
