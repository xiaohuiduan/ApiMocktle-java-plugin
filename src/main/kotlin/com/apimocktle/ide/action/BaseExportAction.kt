package com.apimocktle.ide.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.apimocktle.core.event.ActionCompletedTopic
import com.apimocktle.core.event.ActionCompletedTopic.Companion.syncPublish
import com.apimocktle.core.threading.backgroundAsync
import com.apimocktle.core.threading.swing
import com.apimocktle.exporter.ApiExporterRegistry
import com.apimocktle.exporter.ExportOrchestrator
import com.apimocktle.exporter.model.ExportFormat
import com.apimocktle.exporter.model.ExportResult
import com.apimocktle.exporter.model.OutputConfig
import com.apimocktle.ide.support.SelectedHelper
import com.apimocktle.ide.support.SelectionScope
import com.apimocktle.ide.support.runWithProgress
import com.apimocktle.logging.IdeaConsoleProvider
import com.apimocktle.logging.IdeaLog
import kotlin.coroutines.cancellation.CancellationException

/**
 * Base class for API export actions.
 *
 * Provides common functionality for exporting APIs to various formats:
 * - Progress indicator management
 * - Selection resolution
 * - Result handling
 *
 * Subclasses must implement:
 * - [exportFormat]: The target export format
 * - [actionName]: The display name for the action
 *
 * @see ExportOrchestrator for the export process
 * @see ExportFormat for available formats
 */
abstract class BaseExportAction : ApiMocktleAction(), IdeaLog {

    /**
     * The export format for this action.
     */
    abstract val exportFormat: ExportFormat

    /**
     * The display name shown in the progress dialog.
     */
    protected abstract val actionName: String

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabled = false
            return
        }

        e.presentation.isEnabled = exportFormat.supportsHttp
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selection = SelectedHelper.resolveSelection(e)

        backgroundAsync {
            runWithProgress(project, actionName) { indicator ->
                performExport(project, selection, indicator)
            }
        }
    }

    private suspend fun performExport(
        project: Project,
        selection: SelectionScope?,
        indicator: ProgressIndicator
    ) {
        try {
            val orchestrator = ExportOrchestrator.getInstance(project)
            val result = orchestrator.orchestrateExport(selection, exportFormat, indicator = indicator)

            handleExportResult(project, result)
        } catch (ce: CancellationException) {
            LOG.info("Export cancelled")
            throw ce
        } catch (ex: Throwable) {
            LOG.warn("导出失败", ex)
            IdeaConsoleProvider.getInstance(project).getConsole().error("导出失败", ex)
            swing {
                showExportError(project, ex.message ?: "未知错误")
            }
        } finally {
            project.syncPublish(ActionCompletedTopic.TOPIC)
        }
    }

    protected open suspend fun handleExportResult(project: Project, result: ExportResult) {
        when (result) {
            is ExportResult.Success -> {
                val exporterRegistry = ApiExporterRegistry.getInstance(project)
                val exporter = exporterRegistry.getExporter(exportFormat)

                val handled = try {
                    exporter?.handleExportResult(project, result, OutputConfig.DEFAULT) ?: false
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LOG.warn("处理导出结果失败", e)
                    IdeaConsoleProvider.getInstance(project).getConsole()
                        .error("保存导出结果失败", e)
                    false
                }

                if (!handled) {
                    swing {
                        showSuccessMessage(project, result)
                    }
                }
            }

            is ExportResult.Cancelled -> {
            }

            is ExportResult.Error -> {
                swing {
                    showExportError(project, result.message)
                }
            }
        }
    }

    protected open fun showSuccessMessage(project: Project, result: ExportResult.Success) {
        val message = buildString {
            append("成功导出 ${result.count} 个端点到 ${result.target}")
            result.metadata?.formatDisplay()?.let { append(" $it") }
        }
        com.intellij.openapi.ui.Messages.showInfoMessage(
            project,
            message,
            "导出API"
        )
    }

    protected open fun showExportError(project: Project, message: String) {
        com.intellij.openapi.ui.Messages.showErrorDialog(
            project,
            "导出失败：$message",
            "导出API"
        )
    }
}
