package com.apimocktle.exporter.model

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.apimocktle.settings.Settings

/**
 * Context for an API export operation.
 */
data class ExportContext(
    val project: Project,
    val endpoints: List<ApiEndpoint>,
    val selectedEndpoints: List<ApiEndpoint> = emptyList(),
    val sourceClasses: List<PsiClass> = emptyList(),
    val settings: Settings = Settings(),
    val exportFormat: ExportFormat = ExportFormat.YAPI,
    val outputConfig: OutputConfig = OutputConfig(),
    val indicator: ProgressIndicator? = null
) {
    val hasSelection: Boolean
        get() = selectedEndpoints.isNotEmpty()

    val endpointsToExport: List<ApiEndpoint>
        get() = if (hasSelection) selectedEndpoints else endpoints

    fun withSelectedEndpoints(endpoints: List<ApiEndpoint>): ExportContext =
        copy(selectedEndpoints = endpoints)

    fun withExportFormat(format: ExportFormat): ExportContext =
        copy(exportFormat = format)

    fun withOutputConfig(config: OutputConfig): ExportContext =
        copy(outputConfig = config)
}

enum class ExportFormat(
    val supportsHttp: Boolean = true,
    val supportsGrpc: Boolean,
    val displayName: String
) {
    YAPI(supportsGrpc = false, displayName = "YAPI");

    fun isAvailableFor(endpoints: List<ApiEndpoint>): Boolean {
        if (endpoints.isEmpty()) return true
        val hasGrpc = endpoints.any { it.isGrpc }
        val hasHttp = endpoints.any { it.isHttp }
        return (hasGrpc && supportsGrpc) || (hasHttp && supportsHttp)
    }
}

data class OutputConfig(
    val outputDir: String? = null,
    val fileName: String? = null,
    val host: String? = null
) {
    companion object {
        val DEFAULT = OutputConfig()
    }
}
