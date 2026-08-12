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
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.UIUtil
import com.apimocktle.cache.AppCacheRepository
import com.apimocktle.cache.ProjectCacheRepository
import com.apimocktle.repository.DefaultRepositories
import com.apimocktle.repository.RepositoryConfig
import com.apimocktle.repository.RepositoryType
import com.apimocktle.exporter.model.PathSelector
import com.apimocktle.extension.ExtensionConfigRegistry
import com.apimocktle.logging.IdeaLog
import com.apimocktle.util.GsonUtils
import com.apimocktle.settings.Settings
import com.apimocktle.settings.YapiExportMode
import java.awt.*
import java.io.File
import javax.swing.*
import javax.swing.border.TitledBorder
import kotlin.concurrent.thread

interface SettingsPanel {
    val component: JComponent
    fun resetFrom(settings: Settings?)
    fun applyTo(settings: Settings)
    fun isModified(settings: Settings?): Boolean
}

private fun withHelp(
    comp: JComponent,
    project: com.intellij.openapi.project.Project?,
    title: String,
    message: String
): JPanel {
    val row = JPanel(BorderLayout(4, 0)).apply { isOpaque = false }
    row.add(comp, BorderLayout.CENTER)
    val btn = JButton("?").apply {
        font = font.deriveFont(font.size * 0.8f)
        preferredSize = Dimension(18, 18)
        minimumSize = Dimension(18, 18)
        maximumSize = Dimension(18, 18)
        margin = JBUI.emptyInsets()
        isFocusable = false
        isContentAreaFilled = false
        isBorderPainted = true
        toolTipText = "点击查看说明"
        addActionListener {
            if (project != null) Messages.showMessageDialog(project, message, title, null)
            else JOptionPane.showMessageDialog(null, message, title, JOptionPane.INFORMATION_MESSAGE)
        }
    }
    row.add(btn, BorderLayout.EAST)
    return row
}

// ── API 扫描 ────────────────────────────────────────────────
class ApiScanPanel(project: com.intellij.openapi.project.Project? = null) : SettingsPanel {
    private val feignCb = JBCheckBox("Feign 客户端").apply { toolTipText = "将 Feign 客户端接口解析为 API 端点" }
    private val autoCb = JBCheckBox("文件变更时自动扫描").apply { toolTipText = "源文件修改后自动重新扫描 API" }
    private val concurrentCb = JBCheckBox("启用并发扫描（实验性）").apply { toolTipText = "使用多线程进行 API 扫描" }
    private val queryExpCb = JBCheckBox("展开查询参数").apply { toolTipText = "将查询参数展开为独立字段"; isSelected = true }
    private val formExpCb = JBCheckBox("展开表单参数").apply { toolTipText = "将表单参数展开为独立字段"; isSelected = true }
    private val inferCb = JBCheckBox("推断响应体主类型（Result<T> → T）").apply { toolTipText = "自动提取实际数据类型"; isSelected = true }
    private val urlTplCb = JBCheckBox("URL 模板（/users/{id}）").apply { toolTipText = "使用 RFC 6570 语法"; isSelected = true }

    private val feign = withHelp(feignCb, project, "Feign 客户端支持", "扫描项目中的 @FeignClient 接口，提取其中的 API 信息并导出到 YAPI。")
    private val autoScan = withHelp(autoCb, project, "自动扫描", "文件修改后自动重新扫描 API。关闭后需手动刷新。")
    private val concurrent = withHelp(concurrentCb, project, "并发扫描", "多线程扫描，大型项目可加速。实验性功能。")
    private val queryExp = withHelp(queryExpCb, project, "展开查询参数", "把 @RequestParam 参数展开为文档中的独立字段。")
    private val formExp = withHelp(formExpCb, project, "展开表单参数", "把表单参数展开为独立字段。")
    private val infer = withHelp(inferCb, project, "推断响应体主类型", "Result<T> → T，自动提取实际数据类型。")
    private val urlTpl = withHelp(urlTplCb, project, "URL 模板", "路径变量用 {id} 语法显示。")

    private val pathMulti = ComboBox<PathSelector>(PathSelector.values()).apply {
        renderer = object : ListCellRenderer<PathSelector> {
            private val label = JLabel()
            override fun getListCellRendererComponent(
                list: JList<out PathSelector>?,
                value: PathSelector?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                label.text = value?.displayName ?: ""
                label.isOpaque = true
                if (isSelected) {
                    label.background = list?.selectionBackground
                    label.foreground = list?.selectionForeground
                } else {
                    label.background = list?.background
                    label.foreground = list?.foreground
                }
                return label
            }
        }
    }
    private val pathMultiRow = withHelp(
        JPanel(BorderLayout(4, 0)).apply {
            add(JLabel("路径多选策略："), BorderLayout.WEST)
            add(pathMulti, BorderLayout.CENTER)
        },
        project,
        "路径多选策略",
        "一个接口映射了多个路径（如 @PostMapping({\"/a\", \"/b\"})）时，选择导出哪些路径。" +
            "全部路径：导出所有路径；仅第一条/最后一条：按声明顺序取；仅最短/仅最长：按路径长度取。"
    )

    override val component: JComponent = FormBuilder.createFormBuilder()
        .addComponent(createSection("框架支持", listOf(feign)))
        .addComponent(createSection("扫描行为", listOf(autoScan, concurrent)))
        .addComponent(createSection("文档格式", listOf(queryExp, formExp, infer, urlTpl)))
        .addComponent(createSection("路径多选", listOf(pathMultiRow)))
        .addComponentFillVertically(JPanel(), 0)
        .panel

    override fun resetFrom(s: Settings?) {
        feignCb.isSelected = s?.feignEnable ?: false; autoCb.isSelected = s?.autoScanEnabled ?: true
        concurrentCb.isSelected = s?.concurrentScanEnabled ?: false; queryExpCb.isSelected = s?.queryExpanded ?: true
        formExpCb.isSelected = s?.formExpanded ?: true; inferCb.isSelected = s?.inferReturnMain ?: true
        urlTplCb.isSelected = s?.enableUrlTemplating ?: true; pathMulti.selectedItem = s?.pathMulti?.let { runCatching { PathSelector.valueOf(it) }.getOrNull() } ?: PathSelector.ALL
    }
    override fun applyTo(s: Settings) {
        s.feignEnable = feignCb.isSelected; s.autoScanEnabled = autoCb.isSelected; s.concurrentScanEnabled = concurrentCb.isSelected
        s.queryExpanded = queryExpCb.isSelected; s.formExpanded = formExpCb.isSelected; s.inferReturnMain = inferCb.isSelected
        s.enableUrlTemplating = urlTplCb.isSelected; s.pathMulti = pathMulti.selectedItem?.toString() ?: "ALL"
    }
    override fun isModified(s: Settings?): Boolean {
        val ss = s ?: return false
        return feignCb.isSelected != ss.feignEnable || autoCb.isSelected != ss.autoScanEnabled || concurrentCb.isSelected != ss.concurrentScanEnabled ||
                queryExpCb.isSelected != ss.queryExpanded || formExpCb.isSelected != ss.formExpanded || inferCb.isSelected != ss.inferReturnMain ||
                urlTplCb.isSelected != ss.enableUrlTemplating || pathMulti.selectedItem?.toString() != ss.pathMulti
    }
}

// ── YAPI 导出 ───────────────────────────────────────────────
class YapiExportPanel(private val project: com.intellij.openapi.project.Project? = null) : SettingsPanel {
    private val server = JBTextField()
    private val token = JBTextField()
    private val tokenBtn = JButton("检测令牌")
    private val tokenResult = JBLabel()
    private val urlTpl = JBCheckBox("URL 模板").apply { isSelected = true }
    private val modeCombo = ComboBox<YapiExportMode>(YapiExportMode.entries.toTypedArray()).apply {
        renderer = object : ListCellRenderer<YapiExportMode> {
            private val label = JLabel()
            override fun getListCellRendererComponent(
                list: JList<out YapiExportMode>?,
                value: YapiExportMode?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                label.text = value?.desc ?: ""
                label.isOpaque = true
                if (isSelected) {
                    label.background = list?.selectionBackground
                    label.foreground = list?.selectionForeground
                } else {
                    label.background = list?.background
                    label.foreground = list?.foreground
                }
                return label
            }
        }
    }
    private val reqJson5 = JBCheckBox("请求体 JSON5")
    private val resJson5 = JBCheckBox("响应体 JSON5")

    init { tokenBtn.addActionListener { checkToken() } }

    override val component: JComponent = FormBuilder.createFormBuilder()
        .addComponent(createSection("服务器", listOf(
            withHelp(JPanel(BorderLayout()).apply { add(JLabel("地址："), BorderLayout.WEST); add(server, BorderLayout.CENTER) }, project, "YAPI 服务器地址", "你的 YAPI 服务地址，插件会向这个地址同步 API 文档。"),
            withHelp(JPanel(BorderLayout(5, 0)).apply { add(JLabel("令牌："), BorderLayout.WEST); add(JPanel(BorderLayout(5,0)).apply { add(token, BorderLayout.CENTER); add(tokenBtn, BorderLayout.EAST) }, BorderLayout.CENTER) }, project, "个人令牌", "YAPI 个人访问令牌。获取：YAPI → 个人中心 → 令牌")
        )))
        .addComponent(tokenResult)
        .addComponent(createSection("导出选项", listOf(urlTpl, JPanel(BorderLayout()).apply { add(JLabel("模式："), BorderLayout.WEST); add(modeCombo, BorderLayout.CENTER) }, reqJson5, resJson5)))
        .addComponentFillVertically(JPanel(), 0)
        .panel

    private fun checkToken() {
        val sv = server.text.trim(); val tk = token.text.trim()
        if (sv.isBlank()) { Messages.showWarningDialog(project, "请先输入 YAPI 服务器地址", "提示"); return }
        if (tk.isBlank()) { Messages.showWarningDialog(project, "请先输入个人令牌", "提示"); return }
        tokenBtn.isEnabled = false; tokenResult.text = "检测中..."
        thread {
            try {
                val url = java.net.URL("${sv.removeSuffix("/")}/api/project/list?token=${java.net.URLEncoder.encode(tk, "UTF-8")}")
                val conn = url.openConnection() as java.net.HttpURLConnection; conn.connectTimeout = 5000; conn.readTimeout = 5000; conn.requestMethod = "GET"
                val body = if (conn.responseCode == 200) conn.inputStream.bufferedReader().readText() else conn.errorStream?.bufferedReader()?.readText() ?: ""
                val json = com.google.gson.JsonParser.parseString(body).asJsonObject
                val code = json.get("errcode")?.asInt ?: json.get("code")?.asInt
                val msg = json.get("errmsg")?.asString ?: json.get("message")?.asString ?: ""
                SwingUtilities.invokeLater {
                    if (code == 0 || msg.contains("成功")) { tokenResult.text = "✓ 有效"; tokenResult.foreground = Color(0x2da44e) }
                    else { tokenResult.text = "✗ $msg"; tokenResult.foreground = Color(0xcf222e) }
                    tokenBtn.isEnabled = true
                }
            } catch (e: Exception) { SwingUtilities.invokeLater { tokenResult.text = "✗ 连接失败"; tokenResult.foreground = Color(0xcf222e); tokenBtn.isEnabled = true } }
        }
    }

    override fun resetFrom(s: Settings?) {
        server.text = s?.yapiServer ?: ""; token.text = s?.yapiPersonalToken ?: ""; tokenResult.text = ""
        urlTpl.isSelected = s?.enableUrlTemplating ?: true
        modeCombo.selectedItem = s?.yapiExportMode?.let { runCatching { YapiExportMode.valueOf(it) }.getOrNull() } ?: YapiExportMode.ALWAYS_UPDATE
        reqJson5.isSelected = s?.yapiReqBodyJson5 ?: false; resJson5.isSelected = s?.yapiResBodyJson5 ?: false
    }
    override fun applyTo(s: Settings) {
        s.yapiServer = server.text.takeIf { it.isNotBlank() }; s.yapiPersonalToken = token.text.takeIf { it.isNotBlank() }
        s.enableUrlTemplating = urlTpl.isSelected; s.yapiExportMode = (modeCombo.selectedItem as? YapiExportMode)?.name ?: YapiExportMode.ALWAYS_UPDATE.name
        s.yapiReqBodyJson5 = reqJson5.isSelected; s.yapiResBodyJson5 = resJson5.isSelected
    }
    override fun isModified(s: Settings?): Boolean {
        val ss = s ?: return false
        return server.text != (ss.yapiServer ?: "") || token.text != (ss.yapiPersonalToken ?: "") || urlTpl.isSelected != ss.enableUrlTemplating ||
                (modeCombo.selectedItem as? YapiExportMode)?.name != ss.yapiExportMode || reqJson5.isSelected != ss.yapiReqBodyJson5 || resJson5.isSelected != ss.yapiResBodyJson5
    }
}

// ── Mock Agent ──────────────────────────────────────────────
class MockAgentPanel(project: com.intellij.openapi.project.Project? = null) : SettingsPanel {
    private val injectCb = JBCheckBox("自动注入 Mock Agent").apply { isSelected = true; toolTipText = "启动运行配置时自动注入 -javaagent" }
    private val inject = withHelp(injectCb, project, "自动注入 Mock Agent", "启动 Spring Boot 运行配置时自动添加 -javaagent:mock-agent.jar，拦截 Feign/MyBatis 调用返回模拟数据。")

    override val component: JComponent = FormBuilder.createFormBuilder()
        .addComponent(createSection("Mock 拦截", listOf(inject)))
        .addComponentFillVertically(JPanel(), 0)
        .panel
    override fun resetFrom(s: Settings?) { injectCb.isSelected = s?.autoInjectAgent ?: true }
    override fun applyTo(s: Settings) { s.autoInjectAgent = injectCb.isSelected }
    override fun isModified(s: Settings?): Boolean = injectCb.isSelected != (s?.autoInjectAgent ?: true)
}

// ── 扩展配置 ────────────────────────────────────────────────
class ExtensionConfigPanel(project: com.intellij.openapi.project.Project? = null) : SettingsPanel {
    private val list = CheckBoxList<String>()
    private val preview = JBTextArea().apply { isEditable = false }
    private val allExts = ExtensionConfigRegistry.allExtensions()

    private val resetBtn = JButton("恢复默认").apply {
        toolTipText = "按各扩展的默认启用状态重新勾选"
        addActionListener {
            allExts.forEach { ext -> list.setItemSelected(ext.code, ext.defaultEnabled) }
            refreshPreview()
        }
    }

    override val component: JComponent = JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
        leftComponent = JPanel(BorderLayout()).apply {
            add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply { add(resetBtn) }, BorderLayout.NORTH)
            add(JScrollPane(list), BorderLayout.CENTER)
        }
        rightComponent = JScrollPane(preview)
        resizeWeight = 0.45
    }

    init {
        list.setItems(allExts.map { it.code }) { code ->
            allExts.find { it.code == code }?.let { ext ->
                buildString {
                    append(ext.code)
                    append("  —  ")
                    append(ext.description)
                    if (ext.defaultEnabled) append("（默认启用）")
                }
            } ?: code
        }
        list.setCheckBoxListListener { _, _ -> refreshPreview() }
        list.addListSelectionListener { refreshPreview() }
    }

    override fun resetFrom(s: Settings?) {
        val saved = ExtensionConfigRegistry.stringToCodes(s?.extensionConfigs ?: "").toSet()
        allExts.forEach { ext ->
            val sel = saved.contains(ext.code) || (ext.defaultEnabled && !saved.contains("-${ext.code}"))
            list.setItemSelected(ext.code, sel)
        }
        refreshPreview()
    }
    override fun applyTo(s: Settings) { s.extensionConfigs = ExtensionConfigRegistry.codesToString(selectedCodes().toTypedArray()) }
    override fun isModified(s: Settings?): Boolean {
        val ss = s ?: return false
        val cur = selectedCodes().toSet(); val saved = ExtensionConfigRegistry.stringToCodes(ss.extensionConfigs ?: "").toSet()
        val defaults = allExts.filter { it.defaultEnabled }.map { it.code }.toSet()
        return cur != (saved + defaults)
    }
    private fun selectedCodes(): List<String> = allExts.mapNotNull { if (list.isItemSelected(it.code)) it.code else null }

    private fun refreshPreview() {
        val idx = list.selectedIndex; if (idx < 0 || idx >= allExts.size) { preview.text = "# 选择扩展以预览"; return }
        val ext = allExts[idx]
        preview.text = buildString {
            appendLine("# ${ext.code}"); appendLine("# ${ext.description}")
            if (ext.onClass != null) appendLine("# 条件：存在 ${ext.onClass}")
            appendLine("# 当前：${if (list.isItemSelected(ext.code)) "已启用" else "已禁用"}  |  默认：${if (ext.defaultEnabled) "已启用" else "已禁用"}")
            appendLine(); append(ext.content.ifBlank { "# （无内容）" })
        }
    }
}

// ── 高级 ────────────────────────────────────────────────────
class AdvancedSettingsPanel(project: com.intellij.openapi.project.Project? = null) : SettingsPanel {
    private val timeout = JBTextField("30").apply {
        document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = validateTimeout()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = validateTimeout()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = validateTimeout()
        })
    }
    private val unsafeSslCb = JBCheckBox("允许不安全的 SSL").apply { toolTipText = "允许连接到自签名证书的服务器" }
    private val unsafeSsl = withHelp(unsafeSslCb, project, "不安全 SSL", "允许插件通过 HTTPS 连接到使用自签名证书的服务器。")
    private val logLevel = ComboBox(VerbosityLevel.values())
    private val charset = ComboBox(arrayOf("UTF-8", "GBK", "ISO-8859-1"))
    private val outputDemo = JBCheckBox("在文档中输出示例值").apply { isSelected = true }
    private val switchNotice = JBCheckBox("切换设置时显示通知").apply { isSelected = true }

    private val pCacheLbl = JBLabel("0 B"); private val gCacheLbl = JBLabel("0 B")
    private val clrP = JButton("清除"); private val clrG = JButton("清除")
    private val repoModel = ListTableModel<RepositoryConfig>(
        arrayOf(
            object : ColumnInfo<RepositoryConfig, String>("类型") { override fun valueOf(i: RepositoryConfig?) = i?.displayName() },
            object : ColumnInfo<RepositoryConfig, String>("路径") { override fun valueOf(i: RepositoryConfig?) = i?.path },
            object : ColumnInfo<RepositoryConfig, Boolean>("启用") {
                override fun valueOf(i: RepositoryConfig?) = i?.enabled ?: true; override fun getColumnClass() = java.lang.Boolean::class.java
                override fun isCellEditable(i: RepositoryConfig?) = true; override fun setValue(i: RepositoryConfig?, v: Boolean) { i?.enabled = v }
            }
        ), mutableListOf()
    )
    private val repoTable = TableView(repoModel)
    private val importBtn = JButton("导入设置"); private val exportBtn = JButton("导出设置")
    private var curSettings: Settings? = null

    private fun validateTimeout() {
        val text = timeout.text.trim()
        val ok = text.toIntOrNull()?.let { it in 1..600 } == true
        if (!ok) {
            timeout.toolTipText = "超时必须是 1-600 的整数（秒）"
            timeout.background = java.awt.Color(255, 230, 230)
        } else {
            timeout.toolTipText = null
            timeout.background = null
        }
    }

    override val component: JComponent = FormBuilder.createFormBuilder()
        .addComponent(createSection("HTTP 请求", listOf(JPanel(BorderLayout()).apply { add(JLabel("超时（秒）："), BorderLayout.WEST); add(timeout, BorderLayout.CENTER) }, unsafeSsl)))
        .addComponent(createSection("日志与输出", listOf(JPanel(BorderLayout()).apply { add(JLabel("日志级别："), BorderLayout.WEST); add(logLevel, BorderLayout.CENTER) }, JPanel(BorderLayout()).apply { add(JLabel("字符集："), BorderLayout.WEST); add(charset, BorderLayout.CENTER) }, outputDemo, switchNotice)))
        .addComponent(createSection("缓存", listOf(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply { add(JLabel("项目缓存：")); add(pCacheLbl); add(clrP) }, JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply { add(JLabel("全局缓存：")); add(gCacheLbl); add(clrG) })))
        .addComponent(createSection("仓库", listOf(createRepoPanel())))
        .addComponent(createSection("导入 / 导出", listOf(JPanel(FlowLayout(FlowLayout.LEFT)).apply { add(importBtn); add(exportBtn) })))
        .addComponentFillVertically(JPanel(), 0)
        .panel

    private val prj: com.intellij.openapi.project.Project? = project
    init {
        clrP.addActionListener { prj?.let { ProjectCacheRepository.getInstance(it).clear() }; refreshSizes(); Messages.showInfoMessage("项目缓存已清除。", "清除缓存") }
        clrG.addActionListener { AppCacheRepository.getInstance().clear(); refreshSizes(); Messages.showInfoMessage("全局缓存已清除。", "清除缓存") }
        repoTable.setShowGrid(false); repoTable.intercellSpacing = Dimension(0, 0)
        repoTable.columnModel.getColumn(0).preferredWidth = 120; repoTable.columnModel.getColumn(1).preferredWidth = 350; repoTable.columnModel.getColumn(2).preferredWidth = 60
        importBtn.addActionListener { doImport() }; exportBtn.addActionListener { doExport() }
    }

    private fun createRepoPanel(): JPanel = JPanel(BorderLayout()).apply {
        add(ToolbarDecorator.createDecorator(repoTable)
            .setAddAction { val d = AddRepoDlg(); if (d.showAndGet()) repoModel.addRow(d.config) }
            .setRemoveAction { val s = repoTable.selectedRow; if (s >= 0) repoModel.removeRow(s) }
            .setEditAction { val s = repoTable.selectedRow; if (s >= 0) EditRepoDlg(repoModel.getItem(s)).show() }
            .disableUpDownActions().createPanel(), BorderLayout.CENTER)
    }

    private fun refreshSizes() {
        pCacheLbl.text = "..."; gCacheLbl.text = "..."
        thread {
            val ps = try { prj?.let { ProjectCacheRepository.getInstance(it).cacheSize() } ?: -1L } catch (_: Exception) { -1L }
            val gs = try { AppCacheRepository.getInstance().cacheSize() } catch (_: Exception) { -1L }
            SwingUtilities.invokeLater { pCacheLbl.text = if (ps < 0) "N/A" else fmt(ps); gCacheLbl.text = if (gs < 0) "N/A" else fmt(gs) }
        }
    }
    private fun fmt(s: Long) = when { s < 1024 -> "$s B"; s < 1024 * 1024 -> String.format("%.1f KB", s / 1024.0); s < 1024 * 1024 * 1024 -> String.format("%.1f MB", s / (1024.0 * 1024.0)); else -> String.format("%.1f GB", s / (1024.0 * 1024.0 * 1024.0)) }

    private fun doImport() {
        val s = curSettings ?: return
        val c = JFileChooser().apply {
            dialogTitle = "导入设置"
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter("JSON 设置文件 (*.json)", "json")
        }
        if (c.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            runCatching {
                val i = GsonUtils.fromJson<Settings>(c.selectedFile!!.readText())
                applyImp(s, i)
                resetFrom(s)
            }.onFailure { Messages.showErrorDialog("导入失败：${it.message}", "ApiMocktle") }
        }
    }
    private fun doExport() {
        val s = curSettings ?: return
        val c = JFileChooser().apply {
            dialogTitle = "导出设置"
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter("JSON 设置文件 (*.json)", "json")
        }
        if (c.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            var f = c.selectedFile
            if (!f.name.lowercase().endsWith(".json")) f = java.io.File(f.parentFile, "${f.name}.json")
            if (f.exists() && Messages.showYesNoDialog("文件已存在，是否覆盖？", "导出设置", "是", "否", null) != Messages.YES) return
            runCatching { f.writeText(GsonUtils.toJson(s)) }.onFailure { Messages.showErrorDialog("导出失败：${it.message}", "ApiMocktle") }
        }
    }

    override fun resetFrom(s: Settings?) {
        curSettings = s; timeout.text = (s?.httpTimeOut ?: 30).toString(); unsafeSslCb.isSelected = s?.unsafeSsl ?: false
        logLevel.selectedItem = VerbosityLevel.toLevel(s?.logLevel ?: 50); charset.selectedItem = s?.outputCharset ?: "UTF-8"
        outputDemo.isSelected = s?.outputDemo ?: true; switchNotice.isSelected = s?.switchNotice ?: true; refreshSizes()
        val ur = s?.remoteConfig?.mapNotNull { RepositoryConfig.parse(it) }; repoModel.items = if (!ur.isNullOrEmpty()) ur.toMutableList() else DefaultRepositories.detectFromEnvironment().toMutableList()
    }
    override fun applyTo(s: Settings) {
        s.httpTimeOut = timeout.text.trim().toIntOrNull()?.takeIf { it in 1..600 } ?: 30; s.unsafeSsl = unsafeSslCb.isSelected
        s.logLevel = (logLevel.selectedItem as? VerbosityLevel)?.level ?: 50; s.outputCharset = charset.selectedItem?.toString() ?: "UTF-8"
        s.outputDemo = outputDemo.isSelected; s.switchNotice = switchNotice.isSelected
    }
    override fun isModified(s: Settings?): Boolean {
        val ss = s ?: return false
        return timeout.text != ss.httpTimeOut.toString() || unsafeSslCb.isSelected != ss.unsafeSsl ||
                (logLevel.selectedItem as? VerbosityLevel)?.level != ss.logLevel || charset.selectedItem?.toString() != ss.outputCharset ||
                outputDemo.isSelected != ss.outputDemo || switchNotice.isSelected != ss.switchNotice
    }
    private fun applyImp(s: Settings, i: Settings) {
        s.feignEnable = i.feignEnable; s.queryExpanded = i.queryExpanded; s.formExpanded = i.formExpanded
        s.yapiServer = i.yapiServer; s.yapiPersonalToken = i.yapiPersonalToken; s.enableUrlTemplating = i.enableUrlTemplating
        s.yapiExportMode = i.yapiExportMode; s.yapiReqBodyJson5 = i.yapiReqBodyJson5; s.yapiResBodyJson5 = i.yapiResBodyJson5
        s.httpTimeOut = i.httpTimeOut; s.unsafeSsl = i.unsafeSsl; s.extensionConfigs = i.extensionConfigs
        s.logLevel = i.logLevel; s.outputDemo = i.outputDemo; s.outputCharset = i.outputCharset
    }
}

enum class VerbosityLevel(val level: Int, val displayName: String) {
    SILENT(0, "静默"), ERROR(10, "错误"), WARN(20, "警告"), INFO(30, "信息"), DEBUG(40, "调试"), TRACE(50, "跟踪");
    override fun toString() = displayName
    companion object { fun toLevel(l: Int) = values().minByOrNull { kotlin.math.abs(it.level - l) } ?: TRACE }
}

private fun createSection(title: String, comps: List<JComponent>) = JPanel(BorderLayout()).apply {
    border = BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), title, TitledBorder.LEFT, TitledBorder.TOP)
    JPanel(GridLayout(0, 1, 0, 2)).also { comps.forEach { c -> it.add(c) } }.let { add(it, BorderLayout.CENTER) }
}

private class AddRepoDlg : DialogWrapper(null) {
    private val type = JComboBox(arrayOf("Maven本地", "Gradle缓存", "自定义"))
    private val path = JTextField(40)
    private val browse = JButton("浏览...")
    lateinit var config: RepositoryConfig
    init {
        title = "添加仓库"
        browse.addActionListener { JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }.apply { if (showOpenDialog(contentPane) == JFileChooser.APPROVE_OPTION) path.text = selectedFile.absolutePath } }
        type.addActionListener { updatePath() }; updatePath(); init()
    }
    private fun updatePath() {
        val c = type.selectedItem == "自定义"; path.isEnabled = c; browse.isEnabled = c
        if (!c) path.text = when (type.selectedItem) { "Maven本地" -> DefaultRepositories.MAVEN_LOCAL.toString(); "Gradle缓存" -> DefaultRepositories.GRADLE_CACHE.toString(); else -> "" }
    }
    override fun createCenterPanel() = JPanel(GridLayout(0, 2, 4, 4)).apply {
        add(JLabel("类型：")); add(type); add(JLabel("路径：")); add(JPanel(BorderLayout()).apply { add(path, BorderLayout.CENTER); add(browse, BorderLayout.EAST) })
        preferredSize = Dimension(500, preferredSize.height)
    }
    override fun doOKAction() {
        if (path.text.trim().isEmpty()) return
        config = RepositoryConfig(when (type.selectedItem) { "Maven本地" -> RepositoryType.MAVEN_LOCAL; "Gradle缓存" -> RepositoryType.GRADLE_CACHE; else -> RepositoryType.CUSTOM }, path.text.trim())
        super.doOKAction()
    }
}

private class EditRepoDlg(val config: RepositoryConfig) : DialogWrapper(null) {
    private val path = JTextField(40)
    init {
        title = "编辑仓库：${config.displayName()}"; path.text = config.path; path.isEnabled = config.type == RepositoryType.CUSTOM
        val b = JButton("浏览...").apply { isEnabled = config.type == RepositoryType.CUSTOM }
        b.addActionListener { JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY; selectedFile = File(config.path) }.apply { if (showOpenDialog(contentPane) == JFileChooser.APPROVE_OPTION) path.text = selectedFile.absolutePath } }
        init()
    }
    override fun createCenterPanel() = JPanel(GridLayout(0, 2, 4, 4)).apply { add(JLabel("类型：")); add(JLabel(config.displayName())); add(JLabel("路径：")); add(path); preferredSize = Dimension(500, preferredSize.height) }
    override fun doOKAction() { if (config.type == RepositoryType.CUSTOM) { if (path.text.trim().isEmpty()) return; config.path = path.text.trim() }; super.doOKAction() }
}
