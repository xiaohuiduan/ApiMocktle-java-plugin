package com.apimocktle.exporter

import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.apimocktle.core.threading.IdeDispatchers
import com.apimocktle.cache.ApiIndex
import com.apimocktle.dashboard.ApiScanner
import com.apimocktle.exporter.model.ApiEndpoint
import com.apimocktle.exporter.model.ExportContext
import com.apimocktle.exporter.model.ExportFormat
import com.apimocktle.exporter.model.ExportResult
import com.apimocktle.exporter.model.OutputConfig
import com.apimocktle.ide.support.SelectionScope
import com.apimocktle.settings.SettingBinder
import kotlinx.coroutines.withContext

/**
 * 编排 API 导出流程：扫描端点 → 构建上下文 → 调用导出器。
 *
 * @see ApiExporter
 * @see ApiScanner
 */
@Service(Service.Level.PROJECT)
class ExportOrchestrator(private val project: Project) {
    
    private val apiScanner: ApiScanner = ApiScanner.getInstance(project)
    private val apiIndex: ApiIndex = ApiIndex.getInstance(project)
    private val exporterRegistry: ApiExporterRegistry = ApiExporterRegistry.getInstance(project)
    
    companion object {
        /**
         * Gets the export orchestrator instance for the given project.
         */
        fun getInstance(project: Project): ExportOrchestrator {
            return project.getService(ExportOrchestrator::class.java)
        }
    }
    
    /**
     * Orchestrates the complete export process from selection to output.
     *
     * @param selection The selection scope, or null to export all indexed endpoints
     * @param format The target export format
     * @param outputConfig Output configuration options
     * @return The export result
     */
    suspend fun orchestrateExport(
        selection: SelectionScope?,
        format: ExportFormat,
        outputConfig: OutputConfig = OutputConfig.DEFAULT,
        indicator: ProgressIndicator? = null
    ): ExportResult {
        indicator?.text = "正在扫描API端点..."
        indicator?.isIndeterminate = true
        val endpoints = scanEndpoints(selection, indicator)
        
        if (endpoints.isEmpty()) {
            return ExportResult.Error("未找到API端点")
        }

        indicator?.text = "正在导出 ${endpoints.size} 个端点..."
        indicator?.isIndeterminate = false
        indicator?.fraction = 0.0
        
        val context = buildExportContext(endpoints, format, outputConfig, indicator)
        
        val exporter = exporterRegistry.getExporter(format)
            ?: return ExportResult.Error("不支持的导出格式: $format")
        
        return exporter.export(context)
    }
    
    /**
     * Exports pre-collected endpoints to the specified format.
     *
     * Use this when endpoints have already been collected.
     *
     * @param endpoints The endpoints to export
     * @param format The target export format
     * @param outputConfig Output configuration options
     * @return The export result
     */
    suspend fun exportEndpoints(
        endpoints: List<ApiEndpoint>,
        format: ExportFormat,
        outputConfig: OutputConfig,
        indicator: ProgressIndicator? = null
    ): ExportResult {
        indicator?.text = "正在导出 ${endpoints.size} 个端点..."
        indicator?.isIndeterminate = false
        indicator?.fraction = 0.0

        val context = buildExportContext(endpoints, format, outputConfig, indicator)
        val exporter = exporterRegistry.getExporter(format)
            ?: return ExportResult.Error("不支持的导出格式: $format")
        
        return exporter.export(context)
    }
    
    /**
     * Scans for API endpoints from the given selection or index.
     */
    private suspend fun scanEndpoints(
        selection: SelectionScope?,
        indicator: ProgressIndicator? = null
    ): List<ApiEndpoint> {
        if (selection != null) {
            val classes = withContext(IdeDispatchers.ReadAction) {
                selection.classes().toList()
            }
            if (classes.isNotEmpty()) {
                return apiScanner.scanClasses(classes, indicator).toList()
            }
        }
        return apiIndex.endpoints()
    }
    
    /**
     * Builds the export context with all required dependencies.
     */
    private fun buildExportContext(
        endpoints: List<ApiEndpoint>,
        format: ExportFormat,
        outputConfig: OutputConfig,
        indicator: ProgressIndicator? = null
    ): ExportContext {
        val settings = SettingBinder.getInstance(project).read()
        return ExportContext(
            project = project,
            endpoints = endpoints,
            exportFormat = format,
            settings = settings,
            outputConfig = outputConfig,
            indicator = indicator
        )
    }
}
