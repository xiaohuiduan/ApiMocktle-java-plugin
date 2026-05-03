package com.apimocktle.ide.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.apimocktle.exporter.ApiExporterRegistry
import com.apimocktle.exporter.ExportOrchestrator
import com.apimocktle.exporter.model.ExportResult
import com.apimocktle.ide.dialog.ExportDialog
import com.apimocktle.ide.dialog.ExportDialogResult
import com.apimocktle.ide.support.SelectedHelper
import com.apimocktle.ide.support.SelectionScope
import com.apimocktle.ide.support.runWithProgress
import com.apimocktle.cache.ApiIndex
import com.apimocktle.core.threading.backgroundAsync
import com.apimocktle.core.threading.swing
import com.apimocktle.dashboard.ApiScanner
import com.apimocktle.logging.IdeaLog
import kotlinx.coroutines.CancellationException

/**
 * 导出 API 的 Action，显示 [ExportDialog] 配置对话框。
 *
 * @see ExportDialog
 * @see ExportOrchestrator
 */
class ExportApiAction : AnAction(), IdeaLog {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selection = SelectedHelper.resolveSelection(e)

        backgroundAsync {
            val apiIndex = ApiIndex.getInstance(project)
            val scanner = ApiScanner.getInstance(project)
            val endpoints = if (selection != null) {
                val classes = selection.classes().toList()
                if (classes.isNotEmpty()) {
                    scanner.scanClasses(classes).toList()
                } else {
                    apiIndex.endpoints()
                }
            } else {
                apiIndex.endpoints()
            }

            swing {
                val result = ExportDialog.show(project, endpoints.size, endpoints)
                if (result != null) {
                    performExport(project, selection, result)
                }
            }
        }
    }

    private fun performExport(
        project: Project,
        selection: SelectionScope?,
        dialogResult: ExportDialogResult
    ) {
        backgroundAsync {
            runWithProgress(project, "正在导出API...") { indicator ->
                val orchestrator = ExportOrchestrator.getInstance(project)

                if (dialogResult.selectedEndpoints.isNotEmpty()) {
                    val endpoints = dialogResult.selectedEndpoints.map { it.endpoint }
                    val exportResult = orchestrator.exportEndpoints(
                        endpoints, dialogResult.format, dialogResult.outputConfig, indicator
                    )
                    handleExportResult(project, exportResult, dialogResult)
                } else {
                    val exportResult = orchestrator.orchestrateExport(
                        selection, dialogResult.format, dialogResult.outputConfig, indicator
                    )
                    handleExportResult(project, exportResult, dialogResult)
                }
            }
        }
    }

    private suspend fun handleExportResult(
        project: Project,
        result: ExportResult,
        dialogResult: ExportDialogResult
    ) {
        when (result) {
            is ExportResult.Success -> {
                val exporter = ApiExporterRegistry.getInstance(project)
                    .getExporter(dialogResult.format)
                val handled = try {
                    exporter?.handleExportResult(project, result, dialogResult.outputConfig) ?: false
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LOG.warn("Failed to handle export result", e)
                    false
                }
                if (!handled) {
                    swing {
                        val message = "成功导出 ${result.count} 个端点到 ${result.target}"
                        Messages.showInfoMessage(project, message, "导出成功")
                    }
                }
            }

            is ExportResult.Error -> {
                swing {
                    Messages.showErrorDialog(project, result.message, "导出失败")
                }
            }

            is ExportResult.Cancelled -> {}
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
