package com.apimocktle.dashboard

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.apimocktle.core.threading.readSync
import com.apimocktle.exporter.model.ApiEndpoint
import com.apimocktle.exporter.model.HttpMetadata
import com.apimocktle.exporter.model.HttpMethod
import com.apimocktle.exporter.model.ParameterBinding
import com.apimocktle.exporter.model.httpMetadata
import com.apimocktle.logging.IdeaLog
import com.apimocktle.psi.model.ObjectModelJsonConverter
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.*
import javax.swing.table.DefaultTableModel

/**
 * Panel for displaying API endpoint details (read-only).
 *
 * Shows endpoint information including:
 * - Name, HTTP method, path
 * - Path/query/header/form parameters
 * - Request body (JSON)
 * - Response body demo (JSON)
 */
class EndpointDetailsPanel(
    private val project: Project
) : JBPanel<EndpointDetailsPanel>(BorderLayout()) {
    companion object : IdeaLog

    private val nameLabel = JBLabel("").apply { font = font.deriveFont(font.size + 2f) }
    private val methodLabel = JBLabel("")
    private val pathLabel = JBLabel("")

    // Path params table (read-only: Key / Value / Description)
    private val pathParamsTableModel = object : DefaultTableModel(arrayOf("键", "值", "描述"), 0) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val pathParamsTable = JBTable(pathParamsTableModel).apply {
        autoResizeMode = JBTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
    }

    // Query params table (read-only)
    private val paramsTableModel = object : DefaultTableModel(arrayOf("键", "值", "描述"), 0) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val paramsTable = JBTable(paramsTableModel).apply {
        autoResizeMode = JBTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
    }

    // Headers table (read-only)
    private val headersTableModel = object : DefaultTableModel(arrayOf("名称", "值"), 0) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val headersTable = JBTable(headersTableModel).apply {
        autoResizeMode = JBTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
    }

    // Form data table (read-only)
    private val formTableModel = object : DefaultTableModel(arrayOf("键", "值", "描述"), 0) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val formTable = JBTable(formTableModel).apply {
        autoResizeMode = JBTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
    }

    private val jsonFileType = FileTypeManager.getInstance().getFileTypeByExtension("json")

    // Body area (read-only)
    private val bodyArea = EditorTextField("", project, jsonFileType).apply {
        setOneLineMode(false)
        isViewer = true
    }

    // Response body area (read-only)
    private val responseBodyArea = EditorTextField("", project, jsonFileType).apply {
        setOneLineMode(false)
        isViewer = true
    }

    private val tabPane = JTabbedPane()
    private val responseTabPane = JTabbedPane()

    private var currentEndpoint: ApiEndpoint? = null

    init {
        val namePanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = JBUI.Borders.empty(6, 8, 2, 8)
            add(nameLabel)
            add(Box.createHorizontalGlue())
        }

        val requestLinePanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = JBUI.Borders.empty(2, 8)
            add(methodLabel)
            add(Box.createHorizontalStrut(8))
            add(pathLabel)
        }

        val headerPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(namePanel)
            add(requestLinePanel)
        }

        // Build response tabs
        responseTabPane.addTab("响应体", JBScrollPane(responseBodyArea))
        responseTabPane.preferredSize = java.awt.Dimension(0, 200)

        val responseTitlePanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyTop(8)
            add(JPanel().apply {
                add(JBLabel("响应").apply { font = font.deriveFont(font.size + 1f) })
            }, BorderLayout.WEST)
        }

        val requestWrapper = JPanel(BorderLayout()).apply {
            add(tabPane, BorderLayout.CENTER)
            preferredSize = java.awt.Dimension(0, 150)
            minimumSize = java.awt.Dimension(0, 80)
        }

        val responseContainer = JPanel(BorderLayout()).apply {
            add(responseTitlePanel, BorderLayout.NORTH)
            add(responseTabPane, BorderLayout.CENTER)
            preferredSize = java.awt.Dimension(0, 200)
            minimumSize = java.awt.Dimension(0, 120)
        }

        val contentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(requestWrapper)
            add(responseContainer)
        }

        val scrollPane = JBScrollPane(contentPanel).apply {
            border = BorderFactory.createEmptyBorder()
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }

        add(headerPanel, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
    }

    fun showEndpoint(endpoint: ApiEndpoint) {
        currentEndpoint = endpoint

        when (val meta = endpoint.metadata) {
            is HttpMetadata -> {
                nameLabel.text = endpoint.name ?: "未命名"
                methodLabel.text = meta.method.name
                methodLabel.foreground = getMethodColor(meta.method)
                pathLabel.text = meta.path

                loadFromEndpoint(endpoint)
                showResponseDemo()
            }
        }
    }

    private fun loadFromEndpoint(endpoint: ApiEndpoint) {
        val meta = endpoint.httpMetadata ?: return
        val parameters = meta.parameters
        val headers = meta.headers

        // Path params
        pathParamsTableModel.rowCount = 0
        parameters.filter { it.binding == ParameterBinding.Path }.forEach { p ->
            pathParamsTableModel.addRow(arrayOf(p.name, p.defaultValue ?: p.example ?: "", p.description ?: ""))
        }

        // Query params
        paramsTableModel.rowCount = 0
        parameters.filter { it.binding == ParameterBinding.Query || it.binding == ParameterBinding.Cookie }.forEach { p ->
            paramsTableModel.addRow(arrayOf(p.name, p.defaultValue ?: p.example ?: "", p.description ?: ""))
        }

        // Headers
        headersTableModel.rowCount = 0
        headers.forEach { h ->
            headersTableModel.addRow(arrayOf(h.name, h.value ?: ""))
        }
        parameters.filter { it.binding == ParameterBinding.Header }.forEach { p ->
            headersTableModel.addRow(arrayOf(p.name, p.defaultValue ?: p.example ?: ""))
        }

        // Form params
        val formParams = parameters.filter { it.binding == ParameterBinding.Form }
        formTableModel.rowCount = 0
        formParams.forEach { p ->
            formTableModel.addRow(arrayOf(p.name, p.defaultValue ?: p.example ?: "", p.description ?: ""))
        }

        val contentType = meta.contentType
        val isFormData = formParams.isNotEmpty() ||
                contentType?.contains("form-urlencoded", ignoreCase = true) == true ||
                contentType?.contains("form-data", ignoreCase = true) == true

        // Body
        bodyArea.text = if (!isFormData) {
            meta.body?.let { ObjectModelJsonConverter.toJson(it) } ?: ""
        } else ""

        // Rebuild tabs
        tabPane.removeAll()
        if (parameters.any { it.binding == ParameterBinding.Path }) {
            tabPane.addTab("路径", JBScrollPane(pathParamsTable))
        }
        if (paramsTableModel.rowCount > 0) {
            tabPane.addTab("参数", JBScrollPane(paramsTable))
        }
        if (headersTableModel.rowCount > 0) {
            tabPane.addTab("请求头", JBScrollPane(headersTable))
        }
        if (isFormData) {
            tabPane.addTab("表单", JBScrollPane(formTable))
        } else if (bodyArea.text.isNotBlank()) {
            tabPane.addTab("请求体", JBScrollPane(bodyArea))
        }
    }

    private fun showResponseDemo() {
        val endpoint = currentEndpoint ?: return
        val responseBody = when (val meta = endpoint.metadata) {
            is HttpMetadata -> meta.responseBody
            else -> null
        } ?: return

        val demoJson = ObjectModelJsonConverter.toJson(responseBody)
        if (demoJson.isBlank() || demoJson == "{}") return

        responseBodyArea.text = EndpointDetailsPanelLogic.formatJson(demoJson)
    }

    fun clear() {
        currentEndpoint = null
        nameLabel.text = ""
        methodLabel.text = ""
        pathLabel.text = ""
        pathParamsTableModel.rowCount = 0
        paramsTableModel.rowCount = 0
        headersTableModel.rowCount = 0
        formTableModel.rowCount = 0
        bodyArea.text = ""
        responseBodyArea.text = ""
        tabPane.removeAll()
    }

    private fun getMethodColor(method: HttpMethod): Color = when (method) {
        HttpMethod.GET -> Color(0x61affe)
        HttpMethod.POST -> Color(0x49cc90)
        HttpMethod.PUT -> Color(0xfca130)
        HttpMethod.DELETE -> Color(0xf93e3e)
        HttpMethod.PATCH -> Color(0x50e3c2)
        HttpMethod.HEAD -> Color(0x9012fe)
        HttpMethod.OPTIONS -> Color(0x0d5aa7)
        HttpMethod.NO_METHOD -> Color(0x999999)
    }

    fun dispose() {}
}

/**
 * Pure logic extracted from [EndpointDetailsPanel] for testability.
 */
internal object EndpointDetailsPanelLogic {

    fun formatJson(json: String): String {
        if (json.isBlank()) return json
        return runCatching {
            val gson = com.google.gson.GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create()
            val element = gson.fromJson(json, com.google.gson.JsonElement::class.java)
            gson.toJson(element)
        }.getOrElse { json }
    }

    fun resolvePath(pathTemplate: String, pathParams: List<Pair<String, String>>): String {
        var path = pathTemplate
        for ((key, value) in pathParams) {
            if (key.isNotEmpty() && value.isNotEmpty()) {
                path = path.replace("{$key}", java.net.URLEncoder.encode(value, "UTF-8"))
            }
        }
        return path
    }

    fun buildFormParams(
        rows: List<Triple<String, String, Boolean>>,
        fileLoader: (String) -> Pair<String, ByteArray>? = { path ->
            val f = java.io.File(path)
            if (!f.exists()) null
            else f.name to f.readBytes()
        }
    ): List<com.apimocktle.http.FormParam> {
        return rows.mapNotNull { (name, value, isFile) ->
            if (name.isEmpty()) return@mapNotNull null
            if (isFile) {
                if (value.isBlank()) return@mapNotNull null
                val (fileName, bytes) = fileLoader(value) ?: return@mapNotNull null
                val mimeType = runCatching {
                    java.nio.file.Files.probeContentType(java.io.File(value).toPath())
                }.getOrNull() ?: "application/octet-stream"
                com.apimocktle.http.FormParam.File(name, fileName, mimeType, bytes)
            } else {
                com.apimocktle.http.FormParam.Text(name, value)
            }
        }
    }
}
