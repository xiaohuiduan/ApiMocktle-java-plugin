package com.apimocktle.exporter

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.apimocktle.exporter.model.ExportFormat
import com.apimocktle.exporter.yapi.YapiExporter

/**
 * API 导出器注册中心，按格式提供导出器实例。
 *
 * @see ApiExporter
 * @see ExportFormat
 */
@Service(Service.Level.PROJECT)
class ApiExporterRegistry(private val project: Project) {

    companion object {
        fun getInstance(project: Project): ApiExporterRegistry {
            return project.getService(ApiExporterRegistry::class.java)
        }
    }

    /**
     * 获取指定格式的导出器。
     */
    fun getExporter(format: ExportFormat): ApiExporter? {
        return when (format) {
            ExportFormat.YAPI -> YapiExporter.getInstance(project)
        }
    }

    /**
     * 获取所有可用的导出器。
     */
    fun getAllExporters(): Collection<ApiExporter> {
        return listOf(YapiExporter.getInstance(project))
    }
}
