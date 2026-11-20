package com.apimocktle.settings.ui

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.CheckBoxList
import com.intellij.ui.JBColor
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
import com.apimocktle.i18n.ApiMocktleBundle
import com.apimocktle.repository.DefaultRepositories
import com.apimocktle.repository.RepositoryConfig
import com.apimocktle.repository.RepositoryType
import com.apimocktle.exporter.model.PathSelector
import com.apimocktle.extension.ExtensionConfigRegistry
import com.apimocktle.ide.ui.StatusColors
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
        toolTipText = ApiMocktleBundle.message("settings.help.tooltip")
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
    private val feignCb = JBCheckBox(ApiMocktleBundle.message("settings.scan.feign.label")).apply { toolTipText = ApiMocktleBundle.message("settings.scan.feign.tooltip") }
    private val autoCb = JBCheckBox(ApiMocktleBundle.message("settings.scan.autoScan.label")).apply { toolTipText = ApiMocktleBundle.message("settings.scan.autoScan.tooltip") }
    private val concurrentCb = JBCheckBox(ApiMocktleBundle.message("settings.scan.concurrent.label")).apply { toolTipText = ApiMocktleBundle.message("settings.scan.concurrent.tooltip") }
    private val queryExpCb = JBCheckBox(ApiMocktleBundle.message("settings.scan.queryExpand.label")).apply { toolTipText = ApiMocktleBundle.message("settings.scan.queryExpand.tooltip"); isSelected = true }
    private val formExpCb = JBCheckBox(ApiMocktleBundle.message("settings.scan.formExpand.label")).apply { toolTipText = ApiMocktleBundle.message("settings.scan.formExpand.tooltip"); isSelected = true }
    private val inferCb = JBCheckBox(ApiMocktleBundle.message("settings.scan.inferReturn.label")).apply { toolTipText = ApiMocktleBundle.message("settings.scan.inferReturn.tooltip"); isSelected = true }
    private val urlTplCb = JBCheckBox(ApiMocktleBundle.message("settings.scan.urlTemplate.label")).apply { toolTipText = ApiMocktleBundle.message("settings.scan.urlTemplate.tooltip"); isSelected = true }

    private val feign = withHelp(feignCb, project, ApiMocktleBundle.message("settings.help.feign.title"), ApiMocktleBundle.message("settings.help.feign.message"))
    private val autoScan = withHelp(autoCb, project, ApiMocktleBundle.message("settings.help.autoScan.title"), ApiMocktleBundle.message("settings.help.autoScan.message"))
    private val concurrent = withHelp(concurrentCb, project, ApiMocktleBundle.message("settings.help.concurrent.title"), ApiMocktleBundle.message("settings.help.concurrent.message"))
    private val queryExp = withHelp(queryExpCb, project, ApiMocktleBundle.message("settings.help.queryExpand.title"), ApiMocktleBundle.message("settings.help.queryExpand.message"))
    private val formExp = withHelp(formExpCb, project, ApiMocktleBundle.message("settings.help.formExpand.title"), ApiMocktleBundle.message("settings.help.formExpand.message"))
    private val infer = withHelp(inferCb, project, ApiMocktleBundle.message("settings.help.inferReturn.title"), ApiMocktleBundle.message("settings.help.inferReturn.message"))
    private val urlTpl = withHelp(urlTplCb, project, ApiMocktleBundle.message("settings.help.urlTemplate.title"), ApiMocktleBundle.message("settings.help.urlTemplate.message"))

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
            add(JLabel(ApiMocktleBundle.message("settings.pathMulti.label")), BorderLayout.WEST)
            add(pathMulti, BorderLayout.CENTER)
        },
        project,
        ApiMocktleBundle.message("settings.help.pathMulti.title"),
        ApiMocktleBundle.message("settings.help.pathMulti.message")
    )

    override val component: JComponent = FormBuilder.createFormBuilder()
        .addComponent(createSection(ApiMocktleBundle.message("settings.section.frameworks"), listOf(feign)))
        .addComponent(createSection(ApiMocktleBundle.message("settings.section.scanBehavior"), listOf(autoScan, concurrent)))
        .addComponent(createSection(ApiMocktleBundle.message("settings.section.docFormat"), listOf(queryExp, formExp, infer, urlTpl)))
        .addComponent(createSection(ApiMocktleBundle.message("settings.section.pathMulti"), listOf(pathMultiRow)))
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

// ── 导出到 ApiMocktle ────────────────────────────────────────
class YapiExportPanel(private val project: com.intellij.openapi.project.Project? = null) : SettingsPanel {
    private val server = JBTextField()
    private val token = JBTextField()
    private val tokenBtn = JButton(ApiMocktleBundle.message("settings.yapi.tokenCheck.button"))
    private val tokenResult = JBLabel()
    private val urlTpl = JBCheckBox(ApiMocktleBundle.message("settings.yapi.urlTemplate.label")).apply { isSelected = true }
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
    private val reqJson5 = JBCheckBox(ApiMocktleBundle.message("settings.yapi.reqJson5.label"))
    private val resJson5 = JBCheckBox(ApiMocktleBundle.message("settings.yapi.resJson5.label"))

    init { tokenBtn.addActionListener { checkToken() } }

    override val component: JComponent = FormBuilder.createFormBuilder()
        .addComponent(createSection(ApiMocktleBundle.message("settings.section.server"), listOf(
            withHelp(JPanel(BorderLayout()).apply { add(JLabel(ApiMocktleBundle.message("settings.yapi.server.label")), BorderLayout.WEST); add(server, BorderLayout.CENTER) }, project, ApiMocktleBundle.message("settings.yapi.server.help.title"), ApiMocktleBundle.message("settings.yapi.server.help.message")),
            withHelp(JPanel(BorderLayout(5, 0)).apply { add(JLabel(ApiMocktleBundle.message("settings.yapi.token.label")), BorderLayout.WEST); add(JPanel(BorderLayout(5,0)).apply { add(token, BorderLayout.CENTER); add(tokenBtn, BorderLayout.EAST) }, BorderLayout.CENTER) }, project, ApiMocktleBundle.message("settings.yapi.token.help.title"), ApiMocktleBundle.message("settings.yapi.token.help.message"))
        )))
        .addComponent(tokenResult)
        .addComponent(createSection(ApiMocktleBundle.message("settings.section.exportOptions"), listOf(urlTpl, JPanel(BorderLayout()).apply { add(JLabel(ApiMocktleBundle.message("settings.yapi.mode.label")), BorderLayout.WEST); add(modeCombo, BorderLayout.CENTER) }, reqJson5, resJson5)))
        .addComponentFillVertically(JPanel(), 0)
        .panel

    private fun checkToken() {
        val sv = server.text.trim(); val tk = token.text.trim()
        if (sv.isBlank()) { Messages.showWarningDialog(project, ApiMocktleBundle.message("settings.yapi.serverRequired"), ApiMocktleBundle.message("settings.yapi.warningTitle")); return }
        if (tk.isBlank()) { Messages.showWarningDialog(project, ApiMocktleBundle.message("settings.yapi.tokenRequired"), ApiMocktleBundle.message("settings.yapi.warningTitle")); return }
        tokenBtn.isEnabled = false; tokenResult.text = ApiMocktleBundle.message("settings.yapi.checking")
        thread {
            try {
                val url = java.net.URL("${sv.removeSuffix("/")}/api/project/list?token=${java.net.URLEncoder.encode(tk, "UTF-8")}")
                val conn = url.openConnection() as java.net.HttpURLConnection; conn.connectTimeout = 5000; conn.readTimeout = 5000; conn.requestMethod = "GET"
                val body = if (conn.responseCode == 200) conn.inputStream.bufferedReader().readText() else conn.errorStream?.bufferedReader()?.readText() ?: ""
                val json = com.google.gson.JsonParser.parseString(body).asJsonObject
                val code = json.get("errcode")?.asInt ?: json.get("code")?.asInt
                val msg = json.get("errmsg")?.asString ?: json.get("message")?.asString ?: ""
                SwingUtilities.invokeLater {
                    // 注意：这里的 "成功" 是对服务端响应文本的协议性判断，不是界面文案，不能国际化。
                    if (code == 0 || msg.contains("成功")) { tokenResult.text = ApiMocktleBundle.message("settings.yapi.tokenValid"); tokenResult.foreground = StatusColors.Success }
                    else { tokenResult.text = ApiMocktleBundle.message("settings.yapi.tokenInvalid", msg); tokenResult.foreground = StatusColors.Error }
                    tokenBtn.isEnabled = true
                }
            } catch (e: Exception) { SwingUtilities.invokeLater { tokenResult.text = ApiMocktleBundle.message("settings.yapi.connectFailed"); tokenResult.foreground = StatusColors.Error; tokenBtn.isEnabled = true } }
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
    private val injectCb = JBCheckBox(ApiMocktleBundle.message("settings.agent.inject.label")).apply { isSelected = false; toolTipText = ApiMocktleBundle.message("settings.agent.inject.tooltip") }
    private val inject = withHelp(injectCb, project, ApiMocktleBundle.message("settings.help.agentInject.title"), ApiMocktleBundle.message("settings.help.agentInject.message"))

    override val component: JComponent = FormBuilder.createFormBuilder()
        .addComponent(createSection(ApiMocktleBundle.message("settings.section.mockIntercept"), listOf(inject)))
        .addComponentFillVertically(JPanel(), 0)
        .panel
    override fun resetFrom(s: Settings?) { injectCb.isSelected = s?.autoInjectAgent ?: false }
    override fun applyTo(s: Settings) { s.autoInjectAgent = injectCb.isSelected }
    override fun isModified(s: Settings?): Boolean = injectCb.isSelected != (s?.autoInjectAgent ?: true)
}

// ── 扩展配置 ────────────────────────────────────────────────
class ExtensionConfigPanel(project: com.intellij.openapi.project.Project? = null) : SettingsPanel {
    private val list = CheckBoxList<String>()
    private val preview = JBTextArea().apply { isEditable = false }
    private val allExts = ExtensionConfigRegistry.allExtensions()

    private val resetBtn = JButton(ApiMocktleBundle.message("settings.extensions.reset")).apply {
        toolTipText = ApiMocktleBundle.message("settings.extensions.reset.tooltip")
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
                    if (ext.defaultEnabled) append(ApiMocktleBundle.message("settings.extensions.defaultEnabled"))
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
        val idx = list.selectedIndex; if (idx < 0 || idx >= allExts.size) { preview.text = ApiMocktleBundle.message("settings.extensions.preview.hint"); return }
        val ext = allExts[idx]
        preview.text = buildString {
            appendLine("# ${ext.code}"); appendLine("# ${ext.description}")
            if (ext.onClass != null) appendLine(ApiMocktleBundle.message("settings.extensions.preview.condition", ext.onClass!!))
            appendLine(ApiMocktleBundle.message("settings.extensions.preview.state",
                if (list.isItemSelected(ext.code)) ApiMocktleBundle.message("settings.extensions.enabled") else ApiMocktleBundle.message("settings.extensions.disabled"),
                if (ext.defaultEnabled) ApiMocktleBundle.message("settings.extensions.enabled") else ApiMocktleBundle.message("settings.extensions.disabled")))
            appendLine(); append(ext.content.ifBlank { ApiMocktleBundle.message("settings.extensions.preview.empty") })
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
    private val unsafeSslCb = JBCheckBox(ApiMocktleBundle.message("settings.advanced.unsafeSsl.label")).apply { toolTipText = ApiMocktleBundle.message("settings.advanced.unsafeSsl.tooltip") }
    private val unsafeSsl = withHelp(unsafeSslCb, project, ApiMocktleBundle.message("settings.help.unsafeSsl.title"), ApiMocktleBundle.message("settings.help.unsafeSsl.message"))
    private val logLevel = ComboBox(VerbosityLevel.values())
    private val charset = ComboBox(arrayOf("UTF-8", "GBK", "ISO-8859-1"))
    private val outputDemo = JBCheckBox(ApiMocktleBundle.message("settings.advanced.outputDemo.label")).apply { isSelected = true }
    private val switchNotice = JBCheckBox(ApiMocktleBundle.message("settings.advanced.switchNotice.label")).apply { isSelected = true }

    private val pCacheLbl = JBLabel("0 B"); private val gCacheLbl = JBLabel("0 B")
    private val clrP = JButton(ApiMocktleBundle.message("settings.advanced.clear")); private val clrG = JButton(ApiMocktleBundle.message("settings.advanced.clear"))
    private val repoModel = ListTableModel<RepositoryConfig>(
        arrayOf(
            object : ColumnInfo<RepositoryConfig, String>(ApiMocktleBundle.message("settings.advanced.repo.column.type")) { override fun valueOf(i: RepositoryConfig?) = i?.displayName() },
            object : ColumnInfo<RepositoryConfig, String>(ApiMocktleBundle.message("settings.advanced.repo.column.path")) { override fun valueOf(i: RepositoryConfig?) = i?.path },
            object : ColumnInfo<RepositoryConfig, Boolean>(ApiMocktleBundle.message("settings.advanced.repo.column.enabled")) {
                override fun valueOf(i: RepositoryConfig?) = i?.enabled ?: true; override fun getColumnClass() = java.lang.Boolean::class.java
                override fun isCellEditable(i: RepositoryConfig?) = true; override fun setValue(i: RepositoryConfig?, v: Boolean) { i?.enabled = v }
            }
        ), mutableListOf()
    )
    private val repoTable = TableView(repoModel)
    private val importBtn = JButton(ApiMocktleBundle.message("settings.advanced.import")); private val exportBtn = JButton(ApiMocktleBundle.message("settings.advanced.export"))
    private var curSettings: Settings? = null
    /** resetFrom 时实际加载进表格的仓库配置（序列化形式），isModified 用它判断表格是否被改动 */
    private var loadedRepos: List<String> = emptyList()

    private fun validateTimeout() {
        val text = timeout.text.trim()
        val ok = text.toIntOrNull()?.let { it in 1..600 } == true
        if (!ok) {
            timeout.toolTipText = ApiMocktleBundle.message("settings.advanced.timeoutInvalid")
            timeout.background = JBColor(Color(255, 230, 230), Color(0x5a2d2d))
        } else {
            timeout.toolTipText = null
            timeout.background = null
        }
    }

    override val component: JComponent = FormBuilder.createFormBuilder()
        .addComponent(createSection(ApiMocktleBundle.message("settings.section.http"), listOf(JPanel(BorderLayout()).apply { add(JLabel(ApiMocktleBundle.message("settings.advanced.timeout.label")), BorderLayout.WEST); add(timeout, BorderLayout.CENTER) }, unsafeSsl)))
        .addComponent(createSection(ApiMocktleBundle.message("settings.section.logAndOutput"), listOf(JPanel(BorderLayout()).apply { add(JLabel(ApiMocktleBundle.message("settings.advanced.logLevel.label")), BorderLayout.WEST); add(logLevel, BorderLayout.CENTER) }, JPanel(BorderLayout()).apply { add(JLabel(ApiMocktleBundle.message("settings.advanced.charset.label")), BorderLayout.WEST); add(charset, BorderLayout.CENTER) }, outputDemo, switchNotice)))
        .addComponent(createSection(ApiMocktleBundle.message("settings.section.cache"), listOf(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply { add(JLabel(ApiMocktleBundle.message("settings.advanced.projectCache.label"))); add(pCacheLbl); add(clrP) }, JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply { add(JLabel(ApiMocktleBundle.message("settings.advanced.globalCache.label"))); add(gCacheLbl); add(clrG) })))
        .addComponent(createSection(ApiMocktleBundle.message("settings.section.repos"), listOf(createRepoPanel())))
        .addComponent(createSection(ApiMocktleBundle.message("settings.section.importExport"), listOf(JPanel(FlowLayout(FlowLayout.LEFT)).apply { add(importBtn); add(exportBtn) })))
        .addComponentFillVertically(JPanel(), 0)
        .panel

    private val prj: com.intellij.openapi.project.Project? = project
    init {
        clrP.addActionListener { prj?.let { ProjectCacheRepository.getInstance(it).clear() }; refreshSizes(); Messages.showInfoMessage(ApiMocktleBundle.message("settings.advanced.cache.cleared.project"), ApiMocktleBundle.message("settings.advanced.cache.clearTitle")) }
        clrG.addActionListener { AppCacheRepository.getInstance().clear(); refreshSizes(); Messages.showInfoMessage(ApiMocktleBundle.message("settings.advanced.cache.cleared.global"), ApiMocktleBundle.message("settings.advanced.cache.clearTitle")) }
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
            dialogTitle = ApiMocktleBundle.message("settings.advanced.import.title")
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter(ApiMocktleBundle.message("settings.advanced.settingsFileFilter"), "json")
        }
        if (c.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            runCatching {
                val i = GsonUtils.fromJson<Settings>(c.selectedFile!!.readText())
                applyImp(s, i)
                resetFrom(s)
            }.onFailure { Messages.showErrorDialog(ApiMocktleBundle.message("settings.advanced.import.failed", it.message ?: ""), "ApiMocktle") }
        }
    }
    private fun doExport() {
        val s = curSettings ?: return
        val c = JFileChooser().apply {
            dialogTitle = ApiMocktleBundle.message("settings.advanced.export.title")
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter(ApiMocktleBundle.message("settings.advanced.settingsFileFilter"), "json")
        }
        if (c.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            var f = c.selectedFile
            if (!f.name.lowercase().endsWith(".json")) f = java.io.File(f.parentFile, "${f.name}.json")
            if (f.exists() && Messages.showYesNoDialog(ApiMocktleBundle.message("settings.advanced.export.overwrite"), ApiMocktleBundle.message("settings.advanced.export.title"), ApiMocktleBundle.message("dialog.yes"), ApiMocktleBundle.message("dialog.no"), null) != Messages.YES) return
            runCatching { f.writeText(GsonUtils.toJson(s)) }.onFailure { Messages.showErrorDialog(ApiMocktleBundle.message("settings.advanced.export.failed", it.message ?: ""), "ApiMocktle") }
        }
    }

    override fun resetFrom(s: Settings?) {
        curSettings = s; timeout.text = (s?.httpTimeOut ?: 30).toString(); unsafeSslCb.isSelected = s?.unsafeSsl ?: false
        logLevel.selectedItem = VerbosityLevel.toLevel(s?.logLevel ?: 50); charset.selectedItem = s?.outputCharset ?: "UTF-8"
        outputDemo.isSelected = s?.outputDemo ?: true; switchNotice.isSelected = s?.switchNotice ?: true; refreshSizes()
        val ur = s?.remoteConfig?.mapNotNull { RepositoryConfig.parse(it) }
        val items = if (!ur.isNullOrEmpty()) ur else DefaultRepositories.detectFromEnvironment()
        loadedRepos = items.map { RepositoryConfig.serialize(it) }
        repoModel.items = items.toMutableList()
    }
    override fun applyTo(s: Settings) {
        s.httpTimeOut = timeout.text.trim().toIntOrNull()?.takeIf { it in 1..600 } ?: 30; s.unsafeSsl = unsafeSslCb.isSelected
        s.logLevel = (logLevel.selectedItem as? VerbosityLevel)?.level ?: 50; s.outputCharset = charset.selectedItem?.toString() ?: "UTF-8"
        s.outputDemo = outputDemo.isSelected; s.switchNotice = switchNotice.isSelected
        s.remoteConfig = repoModel.items.map { RepositoryConfig.serialize(it) }.toTypedArray()
    }
    override fun isModified(s: Settings?): Boolean {
        val ss = s ?: return false
        if (timeout.text != ss.httpTimeOut.toString() || unsafeSslCb.isSelected != ss.unsafeSsl ||
                (logLevel.selectedItem as? VerbosityLevel)?.level != ss.logLevel || charset.selectedItem?.toString() != ss.outputCharset ||
                outputDemo.isSelected != ss.outputDemo || switchNotice.isSelected != ss.switchNotice) return true
        return repoModel.items.map { RepositoryConfig.serialize(it) } != loadedRepos
    }
    private fun applyImp(s: Settings, i: Settings) {
        s.feignEnable = i.feignEnable; s.queryExpanded = i.queryExpanded; s.formExpanded = i.formExpanded
        s.yapiServer = i.yapiServer; s.yapiPersonalToken = i.yapiPersonalToken; s.enableUrlTemplating = i.enableUrlTemplating
        s.yapiExportMode = i.yapiExportMode; s.yapiReqBodyJson5 = i.yapiReqBodyJson5; s.yapiResBodyJson5 = i.yapiResBodyJson5
        s.httpTimeOut = i.httpTimeOut; s.unsafeSsl = i.unsafeSsl; s.extensionConfigs = i.extensionConfigs
        s.logLevel = i.logLevel; s.outputDemo = i.outputDemo; s.outputCharset = i.outputCharset
        s.remoteConfig = i.remoteConfig; s.autoInjectAgent = i.autoInjectAgent; s.pathMulti = i.pathMulti
        s.autoScanEnabled = i.autoScanEnabled; s.concurrentScanEnabled = i.concurrentScanEnabled; s.inferReturnMain = i.inferReturnMain
    }
}

enum class VerbosityLevel(val level: Int) {
    SILENT(0), ERROR(10), WARN(20), INFO(30), DEBUG(40), TRACE(50);
    /** 面向用户的显示名，按当前 IDE 语言动态求值。 */
    val displayName: String
        get() = ApiMocktleBundle.message("enum.verbosityLevel.${name.lowercase()}")
    override fun toString() = displayName
    companion object { fun toLevel(l: Int) = values().minByOrNull { kotlin.math.abs(it.level - l) } ?: TRACE }
}

private fun createSection(title: String, comps: List<JComponent>) = JPanel(BorderLayout()).apply {
    border = BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), title, TitledBorder.LEFT, TitledBorder.TOP)
    JPanel(GridLayout(0, 1, 0, 2)).also { comps.forEach { c -> it.add(c) } }.let { add(it, BorderLayout.CENTER) }
}

private class AddRepoDlg : DialogWrapper(null) {
    private val type = JComboBox(RepositoryType.entries.toTypedArray()).apply {
        // 用枚举承载选择，渲染文本来自动源；否则翻译后中文字面量比较会让所有类型落回 CUSTOM。
        renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                label.text = (value as? RepositoryType)?.displayName() ?: ""
                return label
            }
        }
    }
    private val path = JTextField(40)
    private val browse = JButton(ApiMocktleBundle.message("settings.repo.browse"))
    lateinit var config: RepositoryConfig
    init {
        title = ApiMocktleBundle.message("settings.repo.add.title")
        browse.addActionListener { JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }.apply { if (showOpenDialog(contentPane) == JFileChooser.APPROVE_OPTION) path.text = selectedFile.absolutePath } }
        type.addActionListener { updatePath() }; updatePath(); init()
    }
    private fun updatePath() {
        val selected = type.selectedItem as? RepositoryType ?: RepositoryType.CUSTOM
        val custom = selected == RepositoryType.CUSTOM
        path.isEnabled = custom; browse.isEnabled = custom
        if (!custom) path.text = when (selected) {
            RepositoryType.MAVEN_LOCAL -> DefaultRepositories.MAVEN_LOCAL.toString()
            RepositoryType.GRADLE_CACHE -> DefaultRepositories.GRADLE_CACHE.toString()
            RepositoryType.CUSTOM -> ""
        }
    }
    override fun createCenterPanel() = JPanel(GridLayout(0, 2, 4, 4)).apply {
        add(JLabel(ApiMocktleBundle.message("settings.repo.type.label"))); add(type)
        add(JLabel(ApiMocktleBundle.message("settings.repo.path.label"))); add(JPanel(BorderLayout()).apply { add(path, BorderLayout.CENTER); add(browse, BorderLayout.EAST) })
        preferredSize = Dimension(500, preferredSize.height)
    }
    override fun doOKAction() {
        if (path.text.trim().isEmpty()) return
        config = RepositoryConfig(
            type.selectedItem as? RepositoryType ?: RepositoryType.CUSTOM,
            path.text.trim()
        )
        super.doOKAction()
    }
}

private class EditRepoDlg(val config: RepositoryConfig) : DialogWrapper(null) {
    private val path = JTextField(40)
    init {
        title = ApiMocktleBundle.message("settings.repo.edit.title", config.displayName()); path.text = config.path; path.isEnabled = config.type == RepositoryType.CUSTOM
        val b = JButton(ApiMocktleBundle.message("settings.repo.browse")).apply { isEnabled = config.type == RepositoryType.CUSTOM }
        b.addActionListener { JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY; selectedFile = File(config.path) }.apply { if (showOpenDialog(contentPane) == JFileChooser.APPROVE_OPTION) path.text = selectedFile.absolutePath } }
        init()
    }
    override fun createCenterPanel() = JPanel(GridLayout(0, 2, 4, 4)).apply {
        add(JLabel(ApiMocktleBundle.message("settings.repo.type.label"))); add(JLabel(config.displayName()))
        add(JLabel(ApiMocktleBundle.message("settings.repo.path.label"))); add(path)
        preferredSize = Dimension(500, preferredSize.height)
    }
    override fun doOKAction() { if (config.type == RepositoryType.CUSTOM) { if (path.text.trim().isEmpty()) return; config.path = path.text.trim() }; super.doOKAction() }
}
