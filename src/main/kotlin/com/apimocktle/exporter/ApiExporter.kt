package com.apimocktle.exporter

import com.intellij.openapi.project.Project
import com.apimocktle.exporter.model.ExportContext
import com.apimocktle.exporter.model.ExportFormat
import com.apimocktle.exporter.model.ExportResult
import com.apimocktle.exporter.model.OutputConfig

/**
 * API 导出器接口，将 API 端点转换为指定格式。
 *
 * @see ApiExporterRegistry
 * @see ExportOrchestrator
 */
interface ApiExporter {
    /**
     * The export format this exporter handles.
     */
    val format: ExportFormat
    
    /**
     * Exports API endpoints according to this exporter's format.
     *
     * @param context The export context containing endpoints and configuration
     * @return The export result (success with output or error)
     */
    suspend fun export(context: ExportContext): ExportResult
    
    /**
     * Handles the export result after successful export.
     *
     * Override this to perform post-export actions like:
     * - Writing to file
     * - Uploading to remote server
     * - Showing in UI
     *
     * @param project The IntelliJ project
     * @param result The successful export result
     * @return true if the result was handled, false otherwise
     */
    suspend fun handleExportResult(
        project: Project,
        result: ExportResult.Success,
        outputConfig: OutputConfig = OutputConfig.DEFAULT
    ): Boolean {
        return false
    }
}
