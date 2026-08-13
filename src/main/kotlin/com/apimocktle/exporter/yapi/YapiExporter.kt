package com.apimocktle.exporter.yapi

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.apimocktle.core.threading.read
import com.apimocktle.core.threading.swing
import com.apimocktle.exporter.ApiExporter
import com.apimocktle.exporter.model.*
import com.apimocktle.psi.helper.ApiMetadataResolver
import com.apimocktle.psi.helper.UnifiedDocHelper
import com.apimocktle.rule.RuleKeys
import com.apimocktle.rule.engine.RuleEngine
import com.apimocktle.settings.SettingBinder
import com.apimocktle.settings.YapiExportMode
import com.apimocktle.util.ide.ModuleHelper
import com.apimocktle.util.markdown.MarkdownRender
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.DefaultComboBoxModel

@Service(Service.Level.PROJECT)
class YapiExporter(private val project: Project) : ApiExporter {

    override val format: ExportFormat = ExportFormat.YAPI

    private val settingBinder by lazy { SettingBinder.getInstance(project) }

    companion object {
        fun getInstance(project: Project): YapiExporter {
            return project.getService(YapiExporter::class.java)
        }
    }

    override suspend fun export(context: ExportContext): ExportResult {
        val clientProvider = DefaultYapiApiClientProvider(project)
        val initResult = runCatching { clientProvider.init() }
        if (initResult.isFailure) {
            return ExportResult.Error(initResult.exceptionOrNull()?.message ?: "导出失败")
        }
        if (!initResult.getOrThrow()) {
            return ExportResult.Error("ApiMocktle 个人令牌未配置")
        }

        val serverUrl = clientProvider.serverUrl
        val usingDefaultServer = serverUrl == DefaultYapiSettingsHelper.DEFAULT_YAPI_SERVER_URL
        val settings = settingBinder.read()
        val engine = RuleEngine.getInstance(project)
        engine.evaluate(RuleKeys.YAPI_EXPORT_BEFORE)

        // Resolve project list
        val client = clientProvider.getClient("") // temp client for listing
        val projectsResult = client.listProjects()
        val projects = projectsResult.getOrNull()
            ?: return ExportResult.Error(projectsResult.errorMessage() ?: "获取项目列表失败")

        if (projects.isEmpty()) {
            return ExportResult.Error("没有可访问的 ApiMocktle 项目")
        }

        // Show project selection dialog
        val selectedProjectId = selectProject(projects)
            ?: return ExportResult.Error("未选择项目")

        val projectClient = clientProvider.getClient(selectedProjectId)
        val mockRules = MockRuleLoader.getInstance(project).getMockRules()
        val formatter = YapiFormatter(
            reqBodyJson5 = settings.yapiReqBodyJson5,
            resBodyJson5 = settings.yapiResBodyJson5,
            mockRules = mockRules,
            markdownRender = MarkdownRender.getInstance(project)
        )

        var successCount = 0
        var failCount = 0
        val errors = mutableListOf<String>()
        val exportedCarts = mutableMapOf<String, String>()

        val indicator = context.indicator
        val totalEndpoints = context.endpointsToExport.size
        var processedCount = 0

        val docHelper = UnifiedDocHelper.getInstance(project)
        val metadataResolver = ApiMetadataResolver(engine, docHelper)

        val exportMode = runCatching { YapiExportMode.valueOf(settings.yapiExportMode) }
            .getOrDefault(YapiExportMode.ALWAYS_UPDATE)

        val updateConfirmation = DefaultUpdateConfirmation(project, exportMode, projectClient)

        for (endpoint in context.endpointsToExport) {
            indicator?.checkCanceled()
            indicator?.text = endpoint.name ?: endpoint.path
            indicator?.fraction = processedCount.toDouble() / totalEndpoints

            try {
                val folderName = endpoint.folder ?: "anonymous"
                val cartResult = projectClient.findOrCreateCart(folderName)
                val catId = cartResult.getOrNull()
                if (catId == null) {
                    failCount++
                    errors.add("${endpoint.name}：${cartResult.errorMessage() ?: "无法解析分类 '$folderName'"}")
                    processedCount++
                    continue
                }

                val yapiDoc = formatter.formatWithMock(endpoint)
                val psiElement = endpoint.sourceMethod ?: endpoint.sourceClass

                psiElement?.let {
                    engine.evaluate(RuleKeys.YAPI_SAVE_BEFORE, it) { ctx ->
                        ctx.setExt("yapiInfo", yapiDoc)
                        ctx.setExt("endpoint", endpoint)
                    }
                }

                val result = projectClient.uploadApi(yapiDoc, catId, updateConfirmation)

                psiElement?.let {
                    engine.evaluate(RuleKeys.YAPI_SAVE_AFTER, it) { ctx ->
                        ctx.setExt("yapiInfo", yapiDoc)
                        ctx.setExt("endpoint", endpoint)
                        ctx.setExt("result", result)
                    }
                }

                if (result.isSuccess) {
                    successCount++
                    if (catId !in exportedCarts) {
                        exportedCarts[folderName] = YapiUrls.cartUrl(serverUrl, selectedProjectId, catId)
                    }
                } else {
                    failCount++
                    result.errorMessage()?.let { errors.add("${endpoint.name}: $it") }
                }
            } catch (e: Exception) {
                failCount++
                errors.add("${endpoint.name}: ${e.message}")
            }
            processedCount++
        }

        val metadata = if (exportedCarts.isNotEmpty()) {
            YapiExportMetadata(exportedCarts, usingDefaultServer, errors)
        } else null

        return when {
            failCount == 0 && successCount > 0 -> ExportResult.Success(
                count = successCount,
                target = "$serverUrl (ApiMocktle)",
                metadata = metadata
            )
            successCount == 0 && failCount > 0 -> ExportResult.Error(
                "全部导出失败：\n${errors.take(5).joinToString("\n")}"
            )
            successCount > 0 -> ExportResult.Success(
                count = successCount,
                target = "$serverUrl (ApiMocktle) - $failCount 个失败",
                metadata = metadata
            )
            else -> ExportResult.Error("没有可导出的端点")
        }
    }

    /**
     * Shows a combo box dialog for project selection.
     * Supports keyboard search filtering.
     */
    private suspend fun selectProject(projects: List<com.apimocktle.exporter.yapi.model.YapiProjectInfo>): String? {
        return swing {
            var selected: com.apimocktle.exporter.yapi.model.YapiProjectInfo? = null
            val comboBox = ComboBox(DefaultComboBoxModel(projects.toTypedArray())).apply {
                renderer = object : ListCellRenderer<com.apimocktle.exporter.yapi.model.YapiProjectInfo> {
                    private val label = JLabel()
                    override fun getListCellRendererComponent(
                        list: JList<out com.apimocktle.exporter.yapi.model.YapiProjectInfo>?,
                        value: com.apimocktle.exporter.yapi.model.YapiProjectInfo,
                        index: Int,
                        isSelected: Boolean,
                        cellHasFocus: Boolean
                    ): Component {
                        label.text = "${value.name}  (${value.id.take(8)}...)"
                        if (isSelected) {
                            label.background = list?.selectionBackground
                            label.foreground = list?.selectionForeground
                        } else {
                            label.background = list?.background
                            label.foreground = list?.foreground
                        }
                        label.isOpaque = true
                        return label
                    }
                }
                isEditable = true
                toolTipText = "输入项目名进行搜索"
            }
            val dialog = object : DialogWrapper(project) {
                init { title = "选择 ApiMocktle 项目"; init() }
                override fun createCenterPanel(): JComponent = JPanel(BorderLayout(0, 8)).apply {
                    add(JLabel("请选择要同步的 ApiMocktle 项目："), BorderLayout.NORTH)
                    add(comboBox, BorderLayout.CENTER)
                }
            }
            if (dialog.showAndGet()) {
                selected = comboBox.selectedItem as? com.apimocktle.exporter.yapi.model.YapiProjectInfo
            }
            selected?.id
        }
    }

    override suspend fun handleExportResult(
        project: Project,
        result: ExportResult.Success,
        outputConfig: OutputConfig
    ): Boolean {
        val metadata = result.metadata as? YapiExportMetadata ?: return false
        swing {
            val content = buildString {
                append("已导出 ${result.count} 个端点到 ApiMocktle")
                if (metadata.usedDefaultServer) {
                    append("\n未配置服务器地址，已使用默认地址 ${DefaultYapiSettingsHelper.DEFAULT_YAPI_SERVER_URL}，可在 设置 → 导出到 ApiMocktle 中修改")
                }
                if (metadata.failures.isNotEmpty()) {
                    append("，${metadata.failures.size} 个失败：\n")
                    append(metadata.failures.take(5).joinToString("\n"))
                    if (metadata.failures.size > 5) append("\n…")
                }
            }
            val type = if (metadata.failures.isEmpty()) {
                com.intellij.notification.NotificationType.INFORMATION
            } else {
                com.intellij.notification.NotificationType.WARNING
            }
            val notification = com.intellij.notification.Notification(
                "ApiMocktle Notifications",
                "导出到 ApiMocktle",
                content,
                type
            )
            for ((cartName, cartUrl) in metadata.cartLinks) {
                notification.addAction(object : com.intellij.notification.NotificationAction(cartName) {
                    override fun actionPerformed(
                        e: com.intellij.openapi.actionSystem.AnActionEvent,
                        notification: com.intellij.notification.Notification
                    ) {
                        com.intellij.ide.BrowserUtil.browse(cartUrl)
                    }
                })
            }
            com.intellij.notification.Notifications.Bus.notify(notification, project)
        }
        return true
    }
}

class YapiExportMetadata(
    val cartLinks: Map<String, String>,
    val usedDefaultServer: Boolean = false,
    val failures: List<String> = emptyList()
) : ExportMetadata {
    override fun formatDisplay(): String {
        return cartLinks.entries.joinToString("\n") { (name, url) -> "$name: $url" }
    }
}
