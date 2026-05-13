package com.apimocktle.settings.ui

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.CheckBoxList
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.*
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.UIUtil
import com.apimocktle.cache.AppCacheRepository
import com.apimocktle.cache.ProjectCacheRepository
import com.apimocktle.repository.DefaultRepositories
import com.apimocktle.repository.RepositoryConfig
import com.apimocktle.repository.RepositoryType
import com.apimocktle.exporter.model.PathSelector
import com.apimocktle.http.ApacheHttpClient
import com.apimocktle.extension.ExtensionConfigRegistry
import com.apimocktle.logging.IdeaLog
import com.apimocktle.util.GsonUtils
import com.apimocktle.settings.HttpClientType
import com.apimocktle.settings.Settings
import com.apimocktle.settings.YapiExportMode
import java.awt.*
import java.io.File
import javax.swing.*
import javax.swing.border.TitledBorder
import kotlin.concurrent.thread

/**
 * Interface for settings UI panels.
 * 
 * Provides a contract for panels that display and edit plugin settings.
 * Each panel handles a specific category of settings.
 */
interface SettingsPanel {
    /** The UI component for this panel */
    val component: JComponent
    
    /**
     * Resets the panel UI to reflect the given settings.
     * 
     * @param settings The settings to display
     */
    fun resetFrom(settings: Settings?)
    
    /**
     * Applies the panel UI values to the given settings.
     * 
     * @param settings The settings to modify
     */
    fun applyTo(settings: Settings)
    
    /**
     * Checks if the panel has unsaved changes.
     * 
     * @param settings The current settings
     * @return true if the panel has modifications
     */
    fun isModified(settings: Settings?): Boolean
}

/**
 * 通用设置面板。
 *
 * 提供以下配置：
 * - 框架支持开关（Feign、JAX-RS、Actuator）
 * - 日志级别选择
 * - 输出字符集和示例设置
 * - 缓存管理
 */
class GeneralSettingsPanel(private val project: com.intellij.openapi.project.Project) : SettingsPanel {
    private val feignEnable = JBCheckBox("启用 Feign 客户端支持").apply {
        toolTipText = "将 Feign 客户端接口解析为 API 端点"
    }
    private val jaxrsEnable = JBCheckBox("启用 JAX-RS 支持", true).apply {
        toolTipText = "解析 JAX-RS 注解（@Path、@GET 等）为 API 端点"
    }
    private val actuatorEnable = JBCheckBox("启用 Spring Actuator 支持").apply {
        toolTipText = "导出 Spring Boot Actuator 端点（如 /health、/metrics）"
    }
    private val autoScanEnabled = JBCheckBox("文件变更时自动扫描API", true).apply {
        toolTipText = "源文件修改后自动重新扫描 API"
    }
    private val concurrentScanEnabled = JBCheckBox("启用并发API扫描（实验性）", false).apply {
        toolTipText = "使用多线程进行 API 扫描（可能提升性能，但属于实验性功能）"
    }
    private val switchNotice = JBCheckBox("切换设置时显示通知", true).apply {
        toolTipText = "切换不同设置配置时显示通知"
    }

    private val logLevelCombo = ComboBox(CommonSettingsHelper.VerbosityLevel.values())
    private val outputCharsetCombo = ComboBox(arrayOf("UTF-8", "GBK", "ISO-8859-1"))
    private val outputDemoCheckBox = JBCheckBox("在文档中输出示例", true).apply {
        toolTipText = "在生成的文档中包含示例/演示值"
    }

    private val projectCacheSizeLabel = JBLabel("0 B")
    private val globalCacheSizeLabel = JBLabel("0 B")
    private val clearProjectCacheButton = JButton("清除")
    private val clearGlobalCacheButton = JButton("清除")

    private val cachePanel: JPanel

    private val repositoryTableModel = ListTableModel<RepositoryConfig>(
        arrayOf(
            object : ColumnInfo<RepositoryConfig, String>("类型") {
                override fun valueOf(item: RepositoryConfig?): String? = item?.displayName()
            },
            object : ColumnInfo<RepositoryConfig, String>("路径") {
                override fun valueOf(item: RepositoryConfig?): String? = item?.path
            },
            object : ColumnInfo<RepositoryConfig, Boolean>("启用") {
                override fun valueOf(item: RepositoryConfig?): Boolean = item?.enabled ?: true
                override fun getColumnClass(): Class<*> = java.lang.Boolean::class.java
                override fun isCellEditable(item: RepositoryConfig?): Boolean = true
                override fun setValue(item: RepositoryConfig?, value: Boolean) {
                    item?.enabled = value
                }
            }
        ),
        mutableListOf()
    )

    private val repositoryTable = TableView(repositoryTableModel)

    init {
        val projectRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(JLabel("项目缓存："))
            add(projectCacheSizeLabel)
            add(clearProjectCacheButton)
        }
        val globalRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(JLabel("全局缓存："))
            add(globalCacheSizeLabel)
            add(clearGlobalCacheButton)
        }
        cachePanel = JPanel(GridLayout(0, 1, 0, 2)).apply {
            add(projectRow)
            add(globalRow)
        }

        clearProjectCacheButton.addActionListener {
            ProjectCacheRepository.getInstance(project).clear()
            refreshCacheSizes()
            Messages.showInfoMessage("项目缓存已清除。", "清除缓存")
        }

        clearGlobalCacheButton.addActionListener {
            AppCacheRepository.getInstance().clear()
            refreshCacheSizes()
            Messages.showInfoMessage("全局缓存已清除。", "清除缓存")
        }

        repositoryTable.setShowGrid(false)
        repositoryTable.intercellSpacing = Dimension(0, 0)
        repositoryTable.columnModel.getColumn(0).preferredWidth = 120
        repositoryTable.columnModel.getColumn(1).preferredWidth = 350
        repositoryTable.columnModel.getColumn(2).preferredWidth = 60
    }

    private fun refreshCacheSizes() {
        projectCacheSizeLabel.text = "..."
        globalCacheSizeLabel.text = "..."
        thread {
            LOG.info("refreshCacheSizes: project=${project.name}@${project.basePath}")
            var projectSize: Long = -1L
            try {
                val repo = ProjectCacheRepository.getInstance(project)
                projectSize = repo.cacheSize()
            } catch (e: Exception) {
                LOG.warn("Failed to get project cache size", e)
            }

            val globalSize = try {
                AppCacheRepository.getInstance().cacheSize()
            } catch (e: Exception) {
                -1L
            }

            LOG.info("Cache refresh: projectSize=$projectSize, globalSize=$globalSize")

            SwingUtilities.invokeLater {
                projectCacheSizeLabel.text = when {
                    projectSize < 0 -> "N/A"
                    else -> formatFileSize(projectSize)
                }
                projectCacheSizeLabel.toolTipText = null
                globalCacheSizeLabel.text = if (globalSize < 0) "N/A" else formatFileSize(globalSize)
            }
        }
    }

    /**
     * Formats a file size in bytes to human-readable format.
     */
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
        }
    }

    private fun createRepositoryPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "仓库",
                TitledBorder.LEFT,
                TitledBorder.TOP
            )
            val toolbarDecorator = ToolbarDecorator.createDecorator(repositoryTable)
                .setAddAction {
                    showAddRepositoryDialog()
                }
                .setRemoveAction {
                    val selected = repositoryTable.selectedRow
                    if (selected >= 0) {
                        repositoryTableModel.removeRow(selected)
                    }
                }
                .setEditAction {
                    val selected = repositoryTable.selectedRow
                    if (selected >= 0) {
                        val config = repositoryTableModel.getItem(selected)
                        showEditRepositoryDialog(config)
                    }
                }
                .disableUpDownActions()
            add(toolbarDecorator.createPanel(), BorderLayout.CENTER)
        }
    }

    private fun showAddRepositoryDialog() {
        val dialog = AddRepositoryDialog()
        if (dialog.showAndGet()) {
            repositoryTableModel.addRow(dialog.config)
        }
    }

    private fun showEditRepositoryDialog(config: RepositoryConfig) {
        val dialog = EditRepositoryDialog(config)
        if (dialog.showAndGet()) {
            repositoryTableModel.fireTableDataChanged()
        }
    }

    private inner class AddRepositoryDialog : DialogWrapper(false) {
        private val typeCombo = JComboBox(arrayOf("Maven本地", "Gradle缓存", "自定义"))
        private val pathField = JTextField(40)
        private val browseButton = JButton("浏览...")

        lateinit var config: RepositoryConfig

        init {
            title = "添加仓库"
            browseButton.addActionListener {
                val fileChooser = JFileChooser()
                fileChooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                fileChooser.isMultiSelectionEnabled = false
                if (fileChooser.showOpenDialog(contentPane) == JFileChooser.APPROVE_OPTION) {
                    pathField.text = fileChooser.selectedFile.absolutePath
                }
            }
            typeCombo.addActionListener {
                updatePathField()
            }
            updatePathField()
            init()
        }

        private fun updatePathField() {
            val isCustom = typeCombo.selectedItem == "自定义"
            pathField.isEnabled = isCustom
            browseButton.isEnabled = isCustom

            if (!isCustom) {
                val path = when (typeCombo.selectedItem) {
                    "Maven本地" -> DefaultRepositories.MAVEN_LOCAL.toString()
                    "Gradle缓存" -> DefaultRepositories.GRADLE_CACHE.toString()
                    else -> ""
                }
                pathField.text = path
            }
        }

        override fun createCenterPanel(): JComponent {
            return JPanel(GridLayout(0, 2, 4, 4)).apply {
                add(JLabel("类型："))
                add(typeCombo)
                add(JLabel("路径："))
                val pathPanel = JPanel(BorderLayout()).apply {
                    add(pathField, BorderLayout.CENTER)
                    add(browseButton, BorderLayout.EAST)
                }
                add(pathPanel)
                preferredSize = Dimension(500, preferredSize.height)
            }
        }

        override fun doOKAction() {
            val path = pathField.text.trim()
            if (path.isEmpty()) {
                return
            }
            val type = when (typeCombo.selectedItem) {
                "Maven本地" -> RepositoryType.MAVEN_LOCAL
                "Gradle缓存" -> RepositoryType.GRADLE_CACHE
                else -> RepositoryType.CUSTOM
            }
            config = RepositoryConfig(type, path)
            super.doOKAction()
        }
    }

    private inner class EditRepositoryDialog(private val config: RepositoryConfig) : DialogWrapper(false) {
        private val pathField = JTextField(40)
        private val browseButton = JButton("浏览...")

        init {
            title = "编辑仓库：${config.displayName()}"
            pathField.text = config.path
            pathField.isEnabled = config.type == RepositoryType.CUSTOM
            browseButton.isEnabled = config.type == RepositoryType.CUSTOM
            browseButton.addActionListener {
                val fileChooser = JFileChooser()
                fileChooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                fileChooser.selectedFile = File(config.path)
                if (fileChooser.showOpenDialog(contentPane) == JFileChooser.APPROVE_OPTION) {
                    pathField.text = fileChooser.selectedFile.absolutePath
                }
            }
            init()
        }

        override fun createCenterPanel(): JComponent {
            return JPanel(GridLayout(0, 2, 4, 4)).apply {
                add(JLabel("类型："))
                add(JLabel(config.displayName()))
                add(JLabel("路径："))
                val pathPanel = JPanel(BorderLayout()).apply {
                    add(pathField, BorderLayout.CENTER)
                    add(browseButton, BorderLayout.EAST)
                }
                add(pathPanel)
                preferredSize = Dimension(500, preferredSize.height)
            }
        }

        override fun doOKAction() {
            if (config.type == RepositoryType.CUSTOM) {
                val path = pathField.text.trim()
                if (path.isEmpty()) {
                    return
                }
                config.path = path
            }
            super.doOKAction()
        }
    }

    override val component: JComponent = FormBuilder.createFormBuilder()
        .addComponent(
            createTitledPanel(
                "框架支持", listOf(
                    feignEnable, jaxrsEnable, actuatorEnable
                )
            )
        )
        .addComponent(autoScanEnabled)
        .addComponent(concurrentScanEnabled)
        .addComponent(switchNotice)
        .addLabeledComponent("日志级别：", logLevelCombo)
        .addLabeledComponent("输出字符集：", outputCharsetCombo)
        .addComponent(outputDemoCheckBox)
        .addComponent(createTitledPanel("缓存管理", listOf(cachePanel)))
        .addComponent(createRepositoryPanel())
        .addComponentFillVertically(JPanel(), 0)
        .panel

    override fun resetFrom(settings: Settings?) {
        feignEnable.isSelected = settings?.feignEnable ?: false
        jaxrsEnable.isSelected = settings?.jaxrsEnable ?: true
        actuatorEnable.isSelected = settings?.actuatorEnable ?: false
        autoScanEnabled.isSelected = settings?.autoScanEnabled ?: true
        concurrentScanEnabled.isSelected = settings?.concurrentScanEnabled ?: false
        switchNotice.isSelected = settings?.switchNotice ?: true
        logLevelCombo.selectedItem = CommonSettingsHelper.VerbosityLevel.toLevel(settings?.logLevel ?: 50)
        outputCharsetCombo.selectedItem = settings?.outputCharset ?: "UTF-8"
        outputDemoCheckBox.isSelected = settings?.outputDemo ?: true
        refreshCacheSizes()

        val userRepos = settings?.grpcRepositories?.mapNotNull { RepositoryConfig.parse(it) }
        repositoryTableModel.items = if (!userRepos.isNullOrEmpty()) {
            userRepos.toMutableList()
        } else {
            DefaultRepositories.detectFromEnvironment().toMutableList()
        }
    }

    override fun applyTo(settings: Settings) {
        settings.feignEnable = feignEnable.isSelected
        settings.jaxrsEnable = jaxrsEnable.isSelected
        settings.actuatorEnable = actuatorEnable.isSelected
        settings.autoScanEnabled = autoScanEnabled.isSelected
        settings.concurrentScanEnabled = concurrentScanEnabled.isSelected
        settings.switchNotice = switchNotice.isSelected
        settings.logLevel = (logLevelCombo.selectedItem as? CommonSettingsHelper.VerbosityLevel)?.level ?: 50
        settings.outputCharset = outputCharsetCombo.selectedItem?.toString() ?: "UTF-8"
        settings.outputDemo = outputDemoCheckBox.isSelected

        val repos = repositoryTableModel.items.map { RepositoryConfig.serialize(it) }
        settings.grpcRepositories = repos.toTypedArray()
    }

    override fun isModified(settings: Settings?): Boolean {
        val s = settings ?: return false
        val currentRepos = repositoryTableModel.items.map { RepositoryConfig.serialize(it) }.toTypedArray()
        return feignEnable.isSelected != s.feignEnable ||
                jaxrsEnable.isSelected != s.jaxrsEnable ||
                actuatorEnable.isSelected != s.actuatorEnable ||
                autoScanEnabled.isSelected != s.autoScanEnabled ||
                concurrentScanEnabled.isSelected != s.concurrentScanEnabled ||
                switchNotice.isSelected != s.switchNotice ||
                (logLevelCombo.selectedItem as? CommonSettingsHelper.VerbosityLevel)?.level != s.logLevel ||
                outputCharsetCombo.selectedItem?.toString() != s.outputCharset ||
                outputDemoCheckBox.isSelected != s.outputDemo ||
                !currentRepos.contentEquals(s.grpcRepositories)
    }

    companion object : IdeaLog
}

object CommonSettingsHelper {
    enum class VerbosityLevel(val level: Int, val displayName: String) {
        SILENT(0, "静默"),
        ERROR(10, "错误"),
        WARN(20, "警告"),
        INFO(30, "信息"),
        DEBUG(40, "调试"),
        TRACE(50, "跟踪");

        override fun toString(): String = displayName

        companion object {
            fun toLevel(level: Int): VerbosityLevel {
                return values().minByOrNull { kotlin.math.abs(it.level - level) } ?: TRACE
            }
        }
    }
}

class YapiSettingsPanel(private val project: com.intellij.openapi.project.Project) : SettingsPanel {
    private val yapiServer = JBTextField()
    private val yapiPersonalToken = JBTextField()
    private val testTokenButton = JButton("检测令牌")
    private val testTokenResult = JBLabel()
    private val enableUrlTemplating = JBCheckBox("启用URL模板", true)
    private val switchNotice = JBCheckBox("切换通知", true)
    private val yapiExportModeCombo = ComboBox(YapiExportMode.entries.toTypedArray())
    private val yapiReqBodyJson5 = JBCheckBox("请求体JSON5")
    private val yapiResBodyJson5 = JBCheckBox("响应体JSON5")

    private val tokenInputPanel = JPanel(BorderLayout(5, 0)).apply {
        add(yapiPersonalToken, BorderLayout.CENTER)
        val btnPanel = JPanel(BorderLayout(5, 0)).apply {
            add(testTokenButton, BorderLayout.CENTER)
            add(testTokenResult, BorderLayout.EAST)
        }
        add(btnPanel, BorderLayout.EAST)
    }

    init {
        testTokenButton.addActionListener {
            testToken()
        }
    }

    override val component: JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("YAPI服务器：", yapiServer)
        .addLabeledComponent("个人令牌：", tokenInputPanel)
        .addComponent(enableUrlTemplating)
        .addComponent(switchNotice)
        .addLabeledComponent("导出模式：", yapiExportModeCombo)
        .addComponent(yapiReqBodyJson5)
        .addComponent(yapiResBodyJson5)
        .addComponentFillVertically(JPanel(), 0)
        .panel

    private fun testToken() {
        val server = yapiServer.text.trim()
        val token = yapiPersonalToken.text.trim()
        if (server.isBlank()) {
            com.intellij.openapi.ui.Messages.showWarningDialog(project, "请先输入YAPI服务器地址", "提示")
            return
        }
        if (token.isBlank()) {
            com.intellij.openapi.ui.Messages.showWarningDialog(project, "请先输入个人令牌", "提示")
            return
        }
        testTokenButton.isEnabled = false
        testTokenResult.text = "检测中..."
        kotlin.concurrent.thread {
            try {
                val url = java.net.URL("${server.removeSuffix("/")}/api/project/list?token=${java.net.URLEncoder.encode(token, "UTF-8")}")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.requestMethod = "GET"
                val code = conn.responseCode
                val body = if (code == 200) conn.inputStream.bufferedReader().readText()
                           else conn.errorStream?.bufferedReader()?.readText() ?: ""
                val json = com.google.gson.JsonParser.parseString(body).asJsonObject
                val errcode = json.get("errcode")?.asInt ?: json.get("code")?.asInt
                val errmsg = json.get("errmsg")?.asString ?: json.get("message")?.asString ?: ""
                javax.swing.SwingUtilities.invokeLater {
                    if (errcode == 0 || errmsg.contains("成功")) {
                        testTokenResult.text = "✓ 令牌有效"
                        testTokenResult.foreground = java.awt.Color(0x2da44e)
                    } else {
                        testTokenResult.text = "✗ $errmsg"
                        testTokenResult.foreground = java.awt.Color(0xcf222e)
                    }
                    testTokenButton.isEnabled = true
                }
            } catch (e: Exception) {
                javax.swing.SwingUtilities.invokeLater {
                    testTokenResult.text = "✗ 连接失败：${e.message}"
                    testTokenResult.foreground = java.awt.Color(0xcf222e)
                    testTokenButton.isEnabled = true
                }
            }
        }
    }

    override fun resetFrom(settings: Settings?) {
        yapiServer.text = settings?.yapiServer ?: ""
        yapiPersonalToken.text = settings?.yapiPersonalToken ?: ""
        testTokenResult.text = ""
        enableUrlTemplating.isSelected = settings?.enableUrlTemplating ?: true
        switchNotice.isSelected = settings?.switchNotice ?: true
        yapiExportModeCombo.selectedItem = settings?.yapiExportMode?.let {
            runCatching { YapiExportMode.valueOf(it) }.getOrNull()
        } ?: YapiExportMode.ALWAYS_UPDATE
        yapiReqBodyJson5.isSelected = settings?.yapiReqBodyJson5 ?: false
        yapiResBodyJson5.isSelected = settings?.yapiResBodyJson5 ?: false
    }

    override fun applyTo(settings: Settings) {
        settings.yapiServer = yapiServer.text.takeIf { it.isNotBlank() }
        settings.yapiPersonalToken = yapiPersonalToken.text.takeIf { it.isNotBlank() }
        settings.enableUrlTemplating = enableUrlTemplating.isSelected
        settings.switchNotice = switchNotice.isSelected
        settings.yapiExportMode =
            (yapiExportModeCombo.selectedItem as? YapiExportMode)?.name ?: YapiExportMode.ALWAYS_UPDATE.name
        settings.yapiReqBodyJson5 = yapiReqBodyJson5.isSelected
        settings.yapiResBodyJson5 = yapiResBodyJson5.isSelected
    }

    override fun isModified(settings: Settings?): Boolean {
        val s = settings ?: return false
        return yapiServer.text != (s.yapiServer ?: "") ||
                yapiPersonalToken.text != (s.yapiPersonalToken ?: "") ||
                enableUrlTemplating.isSelected != s.enableUrlTemplating ||
                switchNotice.isSelected != s.switchNotice ||
                yapiExportModeCombo.selectedItem?.toString() != s.yapiExportMode ||
                yapiReqBodyJson5.isSelected != s.yapiReqBodyJson5 ||
                yapiResBodyJson5.isSelected != s.yapiResBodyJson5
    }
}

class HttpSettingsPanel : SettingsPanel {
    private val httpClientCombo = ComboBox(HttpClientType.values().map { it.value }.toTypedArray())
    private val httpTimeout = JBTextField("30")
    private val unsafeSsl = JBCheckBox("允许不安全的SSL").apply {
        toolTipText = "允许连接到不受信任或自签名 SSL 证书的 HTTPS 服务器"
    }

    override val component: JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("HTTP客户端：", httpClientCombo)
        .addLabeledComponent("超时（秒）：", httpTimeout)
        .addComponent(unsafeSsl)
        .addComponentFillVertically(JPanel(), 0)
        .panel

    override fun resetFrom(settings: Settings?) {
        httpClientCombo.selectedItem = settings?.httpClient ?: HttpClientType.APACHE.value
        httpTimeout.text = settings?.httpTimeOut?.toString() ?: "30"
        unsafeSsl.isSelected = settings?.unsafeSsl ?: false
    }

    override fun applyTo(settings: Settings) {
        settings.httpClient = httpClientCombo.selectedItem?.toString() ?: HttpClientType.APACHE.value
        settings.httpTimeOut = httpTimeout.text.toIntOrNull() ?: 30
        settings.unsafeSsl = unsafeSsl.isSelected
    }

    override fun isModified(settings: Settings?): Boolean {
        val s = settings ?: return false
        return httpClientCombo.selectedItem?.toString() != s.httpClient ||
                httpTimeout.text != s.httpTimeOut.toString() ||
                unsafeSsl.isSelected != s.unsafeSsl
    }
}

class IntelligentSettingsPanel : SettingsPanel {
    private val queryExpanded = JBCheckBox("展开查询参数", true).apply {
        toolTipText = "将查询参数展开为导出文档中的独立字段"
    }
    private val formExpanded = JBCheckBox("展开表单参数", true).apply {
        toolTipText = "将表单参数展开为导出文档中的独立字段"
    }
    private val inferReturnMain = JBCheckBox("从包装类推断返回主类型", true).apply {
        toolTipText = "自动检测泛型响应包装类中的实际数据类型（如 Result<T>）"
    }
    private val enableUrlTemplating = JBCheckBox("启用URL模板（RFC 6570）", true).apply {
        toolTipText = "使用 RFC 6570 URI 模板语法表示路径变量（如 /users/{id}）"
    }
    private val pathMultiCombo = ComboBox(PathSelector.values().map { it.name }.toTypedArray())

    override val component: JComponent = FormBuilder.createFormBuilder()
        .addComponent(queryExpanded)
        .addComponent(formExpanded)
        .addComponent(inferReturnMain)
        .addComponent(enableUrlTemplating)
        .addLabeledComponent("路径多选策略：", pathMultiCombo)
        .addComponentFillVertically(JPanel(), 0)
        .panel

    override fun resetFrom(settings: Settings?) {
        queryExpanded.isSelected = settings?.queryExpanded ?: true
        formExpanded.isSelected = settings?.formExpanded ?: true
        inferReturnMain.isSelected = settings?.inferReturnMain ?: true
        enableUrlTemplating.isSelected = settings?.enableUrlTemplating ?: true
        pathMultiCombo.selectedItem = settings?.pathMulti ?: "ALL"
    }

    override fun applyTo(settings: Settings) {
        settings.queryExpanded = queryExpanded.isSelected
        settings.formExpanded = formExpanded.isSelected
        settings.inferReturnMain = inferReturnMain.isSelected
        settings.enableUrlTemplating = enableUrlTemplating.isSelected
        settings.pathMulti = pathMultiCombo.selectedItem?.toString() ?: "ALL"
    }

    override fun isModified(settings: Settings?): Boolean {
        val s = settings ?: return false
        return queryExpanded.isSelected != s.queryExpanded ||
                formExpanded.isSelected != s.formExpanded ||
                inferReturnMain.isSelected != s.inferReturnMain ||
                enableUrlTemplating.isSelected != s.enableUrlTemplating ||
                pathMultiCombo.selectedItem?.toString() != s.pathMulti
    }
}

class ExtensionConfigPanel : SettingsPanel {
    private val extensionList = CheckBoxList<String>()
    private val preview = JBTextArea()

    override val component: JComponent = JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
        val left = JPanel(BorderLayout())
        left.add(JScrollPane(extensionList), BorderLayout.CENTER)
        val right = JPanel(BorderLayout())
        preview.isEditable = false
        right.add(JScrollPane(preview), BorderLayout.CENTER)
        leftComponent = left
        rightComponent = right
        resizeWeight = 0.45
    }

    init {
        val allCodes = ExtensionConfigRegistry.allExtensions().map { it.code }
        extensionList.setItems(allCodes) { it }
        extensionList.setCheckBoxListListener { _, _ -> refreshPreview() }
        extensionList.addListSelectionListener { refreshPreview() }
    }

    override fun resetFrom(settings: Settings?) {
        val selected = ExtensionConfigRegistry.stringToCodes(settings?.extensionConfigs ?: "").toSet()
        ExtensionConfigRegistry.allExtensions().forEachIndexed { index, extension ->
            val isSelected =
                selected.contains(extension.code) || (extension.defaultEnabled && !selected.contains("-${extension.code}"))
            extensionList.setItemSelected(extension.code, isSelected)
        }
        refreshPreview()
    }

    override fun applyTo(settings: Settings) {
        settings.extensionConfigs = ExtensionConfigRegistry.codesToString(selectedCodes().toTypedArray())
    }

    override fun isModified(settings: Settings?): Boolean {
        val s = settings ?: return false
        val currentSelected = selectedCodes().toSet()
        val savedSelected = ExtensionConfigRegistry.stringToCodes(s.extensionConfigs ?: "").toSet()
        val defaultEnabled = ExtensionConfigRegistry.allExtensions()
            .filter { it.defaultEnabled }
            .map { it.code }
            .toSet()
        val effectiveSaved = savedSelected + defaultEnabled
        return currentSelected != effectiveSaved
    }

    private fun selectedCodes(): List<String> {
        return ExtensionConfigRegistry.allExtensions().mapNotNull { extension ->
            if (extensionList.isItemSelected(extension.code)) extension.code else null
        }
    }

    private fun refreshPreview() {
        val selectedIndex = extensionList.selectedIndex
        if (selectedIndex >= 0) {
            val allExtensions = ExtensionConfigRegistry.allExtensions()
            if (selectedIndex < allExtensions.size) {
                val extension = allExtensions[selectedIndex]
                val sb = StringBuilder()
                sb.appendLine("# Code: ${extension.code}")
                sb.appendLine("# Description: ${extension.description}")
                if (extension.onClass != null) {
                    sb.appendLine("# Condition: on-class ${extension.onClass}")
                }
                sb.appendLine("# 默认：${if (extension.defaultEnabled) "已启用" else "已禁用"}")
                sb.appendLine()
                if (extension.content.isNotBlank()) {
                    sb.append(extension.content)
                } else {
                    sb.append("# （无内容）")
                }
                preview.text = sb.toString()
                return
            }
        }
        preview.text = "# 选择扩展以预览"
    }
}

class RemoteConfigPanel : SettingsPanel {
    private val list = CheckBoxList<String>()
    private val preview = JBTextArea()
    private val add = JButton("添加")
    private val remove = JButton("移除")
    private val refresh = JButton("刷新")
    private var remoteItems: MutableList<Pair<Boolean, String>> = mutableListOf()

    override val component: JComponent = JPanel(BorderLayout()).apply {
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(add)
            add(remove)
            add(refresh)
        }
        add(toolbar, BorderLayout.NORTH)
        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
            leftComponent = JScrollPane(list)
            preview.isEditable = false
            rightComponent = JScrollPane(preview)
            resizeWeight = 0.45
        }
        add(split, BorderLayout.CENTER)
    }

    init {
        list.setCheckBoxListListener { index, value ->
            if (index in remoteItems.indices) remoteItems[index] = value to remoteItems[index].second
            refreshPreview()
        }
        list.addListSelectionListener { refreshPreview() }
        add.addActionListener {
            val url =
                Messages.showInputDialog("请输入远程配置URL", "远程配置", Messages.getInformationIcon())
            if (!url.isNullOrBlank()) {
                remoteItems.add(true to url.trim())
                refreshList()
            }
        }
        remove.addActionListener {
            val selected = list.selectedIndices.sortedDescending()
            selected.forEach { index ->
                if (index in remoteItems.indices) remoteItems.removeAt(index)
            }
            refreshList()
        }
        refresh.addActionListener { refreshPreview(force = true) }
    }

    override fun resetFrom(settings: Settings?) {
        val raw = settings?.remoteConfig ?: emptyArray()
        remoteItems = if (raw.isEmpty()) mutableListOf(true to DEFAULT_REMOTE_URL) else raw.map {
            val clean = it.trim()
            if (clean.startsWith("!")) false to clean.removePrefix("!").trim() else true to clean
        }.filter { it.second.isNotBlank() }.toMutableList()
        refreshList()
    }

    override fun applyTo(settings: Settings) {
        settings.remoteConfig = remoteItems.map { if (it.first) it.second else "!${it.second}" }.toTypedArray()
    }

    override fun isModified(settings: Settings?): Boolean {
        val s = settings ?: return false
        val current = remoteItems.map { if (it.first) it.second else "!${it.second}" }
        return current != s.remoteConfig.toList()
    }

    private fun refreshList() {
        list.setItems(remoteItems.map { it.second }) { it }
        remoteItems.forEach { item -> list.setItemSelected(item.second, item.first) }
        refreshPreview()
    }

    private fun refreshPreview(force: Boolean = false) {
        val index = list.selectedIndex
        if (index !in remoteItems.indices) {
            preview.text = ""
            return
        }
        val target = remoteItems[index].second
        if (!force && target == preview.getClientProperty("url")) return
        preview.putClientProperty("url", target)
        preview.text = "加载中..."
        thread {
            val content = runCatching { java.net.URI(target).toURL().readText() }.getOrElse { "加载失败：${it.message}" }
            SwingUtilities.invokeLater {
                if (list.selectedIndex == index) {
                    preview.text = content
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_REMOTE_URL =
            "https://raw.githubusercontent.com/tangcent/easy-yapi/master/.default.remote.easy.api.config"
    }
}

class BuiltInConfigPanel : SettingsPanel {
    private val editor = JBTextArea()
    override val component: JComponent = JPanel(BorderLayout()).apply {
        add(JScrollPane(editor), BorderLayout.CENTER)
    }

    override fun resetFrom(settings: Settings?) {
        editor.text = settings?.builtInConfig?.takeIf { it.isNotBlank() } ?: defaultBuiltInConfig()
    }

    override fun applyTo(settings: Settings) {
        val content = editor.text
        settings.builtInConfig = if (content == defaultBuiltInConfig()) "" else content
    }

    override fun isModified(settings: Settings?): Boolean {
        val s = settings ?: return false
        val current = editor.text
        val stored = s.builtInConfig?.takeIf { it.isNotBlank() } ?: defaultBuiltInConfig()
        return current != stored
    }

    private fun defaultBuiltInConfig(): String {
        return javaClass.classLoader.getResourceAsStream("config/builtin.apimocktle.config")
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: ""
    }
}

class OtherSettingsPanel : SettingsPanel {
    private val importButton = JButton("导入设置")
    private val exportButton = JButton("导出设置")
    private var currentSettings: Settings? = null

    override val component: JComponent = JPanel(BorderLayout()).apply {
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        buttonPanel.add(importButton)
        buttonPanel.add(exportButton)
        add(buttonPanel, BorderLayout.NORTH)

        val infoPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("信息")
            val infoText = JBTextArea().apply {
                text = """
                    |EasyYapi 插件设置
                    |
                    |以 JSON 文件格式导入/导出设置。
                    |
                    |版本：3.0.0.212.0
                """.trimMargin()
                isEditable = false
                rows = 10
            }
            add(JScrollPane(infoText), BorderLayout.CENTER)
        }
        add(infoPanel, BorderLayout.CENTER)
    }

    init {
        importButton.addActionListener {
            val settings = currentSettings ?: return@addActionListener
            val chooser = JFileChooser()
            chooser.dialogTitle = "导入设置"
            chooser.fileSelectionMode = JFileChooser.FILES_ONLY
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                val file = chooser.selectedFile ?: return@addActionListener
                runCatching {
                    val imported = GsonUtils.fromJson<Settings>(file.readText())
                    applyImported(settings, imported)
                    resetFrom(settings)
                }.onFailure {
                    Messages.showErrorDialog("导入失败：${it.message}", "ApiMocktle 设置")
                }
            }
        }
        exportButton.addActionListener {
            val settings = currentSettings ?: return@addActionListener
            val chooser = JFileChooser()
            chooser.dialogTitle = "导出设置"
            chooser.fileSelectionMode = JFileChooser.FILES_ONLY
            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                val file = chooser.selectedFile ?: return@addActionListener
                runCatching {
                    file.writeText(GsonUtils.toJson(settings))
                }.onFailure {
                    Messages.showErrorDialog("导出失败：${it.message}", "ApiMocktle 设置")
                }
            }
        }
    }

    override fun resetFrom(settings: Settings?) {
        currentSettings = settings
    }

    override fun applyTo(settings: Settings) {
    }

    override fun isModified(settings: Settings?): Boolean = false

    private fun applyImported(settings: Settings, imported: Settings) {
        settings.feignEnable = imported.feignEnable
        settings.jaxrsEnable = imported.jaxrsEnable
        settings.actuatorEnable = imported.actuatorEnable
        settings.queryExpanded = imported.queryExpanded
        settings.formExpanded = imported.formExpanded
        settings.yapiServer = imported.yapiServer
        settings.yapiPersonalToken = imported.yapiPersonalToken
        settings.enableUrlTemplating = imported.enableUrlTemplating
        settings.switchNotice = imported.switchNotice
        settings.yapiExportMode = imported.yapiExportMode
        settings.yapiReqBodyJson5 = imported.yapiReqBodyJson5
        settings.yapiResBodyJson5 = imported.yapiResBodyJson5
        settings.httpTimeOut = imported.httpTimeOut
        settings.unsafeSsl = imported.unsafeSsl
        settings.httpClient = imported.httpClient
        settings.extensionConfigs = imported.extensionConfigs
        settings.logLevel = imported.logLevel
        settings.outputDemo = imported.outputDemo
        settings.outputCharset = imported.outputCharset
        settings.builtInConfig = imported.builtInConfig
        settings.remoteConfig = imported.remoteConfig
    }
}

private fun createTitledPanel(title: String, components: List<JComponent>): JPanel {
    return JPanel(BorderLayout()).apply {
        border = BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            title,
            TitledBorder.LEFT,
            TitledBorder.TOP
        )
        val inner = JPanel(GridLayout(0, 1, 0, 2))
        components.forEach { inner.add(it) }
        add(inner, BorderLayout.CENTER)
    }
}
