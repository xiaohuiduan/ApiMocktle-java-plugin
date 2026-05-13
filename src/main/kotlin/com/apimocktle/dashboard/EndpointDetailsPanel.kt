package com.apimocktle.dashboard

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.apimocktle.cache.DefaultHttpContextCacheHelper
import com.apimocktle.cache.HttpContextCacheHelper
import com.apimocktle.core.threading.IdeDispatchers
import com.apimocktle.core.threading.readSync
import com.apimocktle.core.threading.swing
import com.apimocktle.dashboard.env.EnvironmentService
import com.apimocktle.dashboard.script.*
import com.apimocktle.exporter.model.ApiEndpoint
import com.apimocktle.exporter.model.GrpcMetadata
import com.apimocktle.exporter.model.GrpcStreamingType
import com.apimocktle.exporter.model.HttpMetadata
import com.apimocktle.exporter.model.HttpMethod
import com.apimocktle.exporter.model.ParameterBinding
import com.apimocktle.exporter.model.ParameterType
import com.apimocktle.http.*
import com.apimocktle.grpc.GrpcStatus
import com.apimocktle.logging.IdeaLog
import com.intellij.openapi.ui.Messages
import com.apimocktle.exporter.model.httpMetadata
import com.apimocktle.exporter.model.grpcMetadata
import com.apimocktle.exporter.model.isGrpc
import com.apimocktle.psi.model.ObjectModelJsonConverter
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.net.URLEncoder
import javax.swing.*
import javax.swing.event.TableModelEvent
import javax.swing.event.TableModelListener
import javax.swing.table.DefaultTableModel

/**
 * Panel for displaying and editing API endpoint details.
 * 
 * This panel provides a comprehensive interface for:
 * - Viewing endpoint information (name, method, path)
 * - Editing request parameters (path, query, headers, form data, body)
 * - Sending HTTP requests and viewing responses
 * - Persisting user edits for later sessions
 * 
 * Features:
 * - Tabbed interface for different parameter types
 * - JSON syntax highlighting for body and response
 * - Auto-save of user modifications
 * - Host history management
 * 
 * @param project The IntelliJ IDEA project context
 * @param httpClient The HTTP client for executing requests
 */
class EndpointDetailsPanel(
    private val project: Project,
    private val httpClient: HttpClient
) : JBPanel<EndpointDetailsPanel>(BorderLayout()) {
    companion object : IdeaLog

    /** Coroutine scope for managing background HTTP requests */
    private val scope = CoroutineScope(SupervisorJob() + IdeDispatchers.Background)

    /** Current active HTTP request job, can be cancelled */
    private var currentRequestJob: Job? = null

    /** Timer for showing loading animation during requests */
    private var loadingTimer: Timer? = null

    /** Helper for managing host history cache */
    private val hostCacheHelper: HttpContextCacheHelper = DefaultHttpContextCacheHelper.getInstance(project)

    /** Service for persisting user edits */
    private val editCacheService: RequestEditCacheService = RequestEditCacheService.getInstance(project)

    private val scriptCacheService: ScriptCacheService = ScriptCacheService.getInstance(project)

    private val environmentService: EnvironmentService = EnvironmentService.getInstance(project)

    /** Request executor for HTTP/gRPC request execution with script pipeline */
    private val requestExecutor: RequestExecutor = RequestExecutor.getInstance(project)

    /** Label displaying the endpoint name */
    private val nameLabel = JBLabel("").apply { font = font.deriveFont(font.size + 2f) }

    /** Label displaying the HTTP method with color coding */
    private val methodLabel = JBLabel("")

    /** Combo box for selecting/editing the host URL */
    private val hostComboBox: JComboBox<String> = JComboBox<String>().apply {
        isEditable = true
        preferredSize = Dimension(200, preferredSize.height)
    }

    /** Text field for the request path */
    private val pathField = JTextField().apply { isEditable = true }

    /** Button to send the HTTP request */
    private val sendButton = JButton("发送")

    /** Button to reset modifications to default values */
    private val resetButton = JButton("重置").apply {
        toolTipText = "重置为默认值"
    }

    /** Combo box for selecting the active environment */
    val envComboBox: JComboBox<String> = JComboBox<String>().apply {
        preferredSize = Dimension(140, preferredSize.height)
        maximumSize = Dimension(200, maximumSize.height)
        toolTipText = "选择环境"
    }

    /** Toggle button to show/hide the inline environment editor */
    val envToggleBtn = JButton().apply {
        icon = com.intellij.icons.AllIcons.General.GearPlain
        toolTipText = "编辑环境变量"
        isBorderPainted = false
        isFocusable = false
        margin = JBUI.emptyInsets()
    }

    /** Table model for test results */
    private val testResultsTableModel = object : DefaultTableModel(arrayOf("测试", "状态", "错误"), 0) {
        override fun isCellEditable(row: Int, column: Int) = false
    }

    /** Table for displaying test results */
    private val testResultsTable = JBTable(testResultsTableModel).apply {
        autoResizeMode = JBTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
        columnModel.getColumn(0).preferredWidth = 200
        columnModel.getColumn(1).preferredWidth = 60
        columnModel.getColumn(2).preferredWidth = 300
    }

    // Path params table (Key / Value / Description)
    /** Table model for path parameters */
    private val pathParamsTableModel = object : DefaultTableModel(arrayOf("键", "值", "描述", ""), 0) {
        override fun isCellEditable(row: Int, column: Int) = column != 0 && column < columnCount - 1
        override fun getColumnClass(columnIndex: Int): Class<*> =
            if (columnIndex == columnCount - 1) JButton::class.java else Any::class.java
    }

    /** Table for editing path parameters */
    private val pathParamsTable = JBTable(pathParamsTableModel).apply {
        autoResizeMode = JBTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
        setupDeleteButtonColumn(this, pathParamsTableModel)
        setupPathParamsComboBoxEditor()
    }

    /** Map of row index to enum values for path parameter dropdowns */
    private var pathParamsEnumValues: MutableMap<Int, List<String>> = mutableMapOf()

    /**
     * Sets up combo box editor for path parameter value column.
     */
    private fun JBTable.setupPathParamsComboBoxEditor() {
        val valueColumn = this.columnModel.getColumn(1)
        valueColumn.cellEditor = DefaultCellEditor(JComboBox<String>().apply {
            isEditable = true
        })
    }

    /**
     * Updates the combo box editor for a path parameter row with enum values.
     * 
     * @param row The row index
     * @param enumValues The list of allowed values, or null for free-form input
     */
    private fun updatePathParamComboBox(row: Int, enumValues: List<String>?) {
        if (enumValues != null && enumValues.isNotEmpty()) {
            pathParamsEnumValues[row] = enumValues
            val comboBox = JComboBox(enumValues.toTypedArray()).apply {
                isEditable = true
                val currentValue = pathParamsTableModel.getValueAt(row, 1)?.toString() ?: ""
                if (currentValue.isNotBlank()) {
                    selectedItem = currentValue
                } else {
                    selectedItem = enumValues.first()
                    pathParamsTableModel.setValueAt(enumValues.first(), row, 1)
                }
            }
            pathParamsTable.columnModel.getColumn(1).cellEditor = DefaultCellEditor(comboBox)
        }
    }

    // Query params table (Key / Value / Description)
    /** Table model for query parameters */
    private val paramsTableModel = object : DefaultTableModel(arrayOf("键", "值", "描述", ""), 0) {
        override fun isCellEditable(row: Int, column: Int) = column < columnCount - 1
        override fun getColumnClass(columnIndex: Int): Class<*> =
            if (columnIndex == columnCount - 1) JButton::class.java else Any::class.java
    }

    /** Table for editing query parameters */
    private val paramsTable = JBTable(paramsTableModel).apply {
        autoResizeMode = JBTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
        setupDeleteButtonColumn(this, paramsTableModel)
    }

    // Headers table (editable)
    /** Table model for request headers */
    private val headersTableModel = object : DefaultTableModel(arrayOf("名称", "值", ""), 0) {
        override fun isCellEditable(row: Int, column: Int) = column < columnCount - 1
        override fun getColumnClass(columnIndex: Int): Class<*> =
            if (columnIndex == columnCount - 1) JButton::class.java else Any::class.java
    }

    /** Table for editing request headers */
    private val headersTable = JBTable(headersTableModel).apply {
        autoResizeMode = JBTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
        setupDeleteButtonColumn(this, headersTableModel)
    }

    // Form data table (Key / Value / Description)
    /** Table model for form data parameters */
    private val formTableModel = object : DefaultTableModel(arrayOf("键", "值", "描述", ""), 0) {
        override fun isCellEditable(row: Int, column: Int) = column < columnCount - 1
        override fun getColumnClass(columnIndex: Int): Class<*> =
            if (columnIndex == columnCount - 1) JButton::class.java else Any::class.java
    }

    /** Tracks which form table rows are file-type params (row index -> true) */
    private val formFileRows: MutableSet<Int> = mutableSetOf()

    /** Table for editing form data parameters */
    private val formTable = JBTable(formTableModel).apply {
        autoResizeMode = JBTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
        setupDeleteButtonColumn(this, formTableModel)
    }

    private val jsonFileType = FileTypeManager.getInstance().getFileTypeByExtension("json")

    /** Editor for pre-request script */
    private val preRequestScriptArea = EditorTextField("", project, jsonFileType).apply {
        setOneLineMode(false)
    }

    /** Editor for post-response script */
    private val postResponseScriptArea = EditorTextField("", project, jsonFileType).apply {
        setOneLineMode(false)
    }

    // Body area (editable, raw JSON with syntax highlighting)
    /** Editor for request body with JSON syntax highlighting */
    private val bodyArea = EditorTextField("", project, jsonFileType).apply {
        setOneLineMode(false)
    }

    /** Button to format/beautify JSON body */
    private val formatBodyBtn = JButton("格式化").apply {
        toolTipText = "格式化/美化JSON"
        addActionListener { formatRequestBody() }
    }

    // Response body area
    /** Editor for response body with JSON syntax highlighting (read-only) */
    private val responseBodyArea = EditorTextField("", project, jsonFileType).apply {
        setOneLineMode(false)
        isViewer = true
    }

    // Response headers table (read-only)
    /** Table model for response headers */
    private val responseHeadersTableModel = object : DefaultTableModel(arrayOf("名称", "值"), 0) {
        override fun isCellEditable(row: Int, column: Int) = false
    }

    /** Table for displaying response headers */
    private val responseHeadersTable = JBTable(responseHeadersTableModel).apply {
        autoResizeMode = JBTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
    }

    // Response status label
    /** Label displaying the response status code */
    private val responseStatusLabel = JBLabel("").apply { font = font.deriveFont(font.size + 1f) }

    // Response tabs
    /** Tab pane for response body and headers */
    private val responseTabPane = JTabbedPane()

    // Response body state
    /** Raw response body text */
    private var rawResponseBody: String = ""

    /** Whether response is displayed in pretty JSON format */
    private var isPrettyJson: Boolean = true

    // Response toolbar buttons
    /** Button to toggle between formatted and raw JSON view */
    private val prettyToggleBtn = JButton("原始").apply {
        toolTipText = "在格式化和原始JSON之间切换"
        addActionListener { togglePrettyJson() }
    }

    /** Button to copy response body to clipboard */
    private val copyResponseBtn = JButton("复制").apply {
        toolTipText = "复制响应体到剪贴板"
        addActionListener { copyResponseBody() }
    }

    /** Tab pane for request parameters (Path, Params, Headers, Form/Body) */
    private val tabPane = JTabbedPane()

    /** The currently displayed API endpoint */
    private var currentEndpoint: ApiEndpoint? = null

    /** Cached key for the current endpoint (computed under read action) */
    private var currentEndpointKey: String = ""

    /** Tracks whether the current endpoint uses form-data body */
    private var hasFormData = false

    /** The content type of the current endpoint (used to distinguish urlencoded vs multipart) */
    private var endpointContentType: String? = null

    /** Flag to prevent auto-save during programmatic updates */
    private var isLoading: Boolean = false

    /** Debounce timer for auto-save */
    private val autoSaveTimer = Timer(500) {
        if (!isLoading && currentEndpointKey.isNotEmpty()) {
            doSaveCurrentEdit()
        }
    }.apply { isRepeats = false }

    /**
     * Initializes the panel with UI components and event listeners.
     */
    init {
        loadHostHistory()
        loadEnvironments()
        setupAutoSaveListeners()

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
            add(Box.createHorizontalStrut(2))
            add(hostComboBox)
            add(Box.createHorizontalStrut(2))
            add(pathField)
            add(Box.createHorizontalStrut(6))
            add(sendButton)
            add(Box.createHorizontalStrut(4))
            add(resetButton)
        }

        val headerPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(namePanel)
            add(requestLinePanel)
        }

        // Build response tabs
        buildResponseTabs()

        // Title panel with "Response" on left and status/buttons on right
        val responseTitlePanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyTop(8)
            val titleLabel = JBLabel("响应").apply {
                font = font.deriveFont(font.size + 1f)
            }
            add(JPanel().apply {
                add(Box.createHorizontalStrut(8))
                add(titleLabel)
            }, BorderLayout.WEST)
            add(JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                add(responseStatusLabel)
                add(Box.createHorizontalStrut(8))
                add(copyResponseBtn)
                add(Box.createHorizontalStrut(8))
            }, BorderLayout.EAST)
        }

        // Request tabs with fixed preferred height
        val requestWrapper = JPanel(BorderLayout()).apply {
            add(tabPane, BorderLayout.CENTER)
            preferredSize = Dimension(0, 200)
            minimumSize = Dimension(0, 120)
        }

        // Response section with fixed preferred height
        val responseContainer = JPanel(BorderLayout()).apply {
            add(responseTitlePanel, BorderLayout.NORTH)
            add(responseTabPane, BorderLayout.CENTER)
            preferredSize = Dimension(0, 300)
            minimumSize = Dimension(0, 200)
        }

        // Stack request + response vertically
        val contentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(requestWrapper)
            add(responseContainer)
        }

        // Wrap in scroll pane so the whole thing scrolls when panel is small
        val scrollPane = JBScrollPane(contentPanel).apply {
            border = BorderFactory.createEmptyBorder()
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }

        add(headerPanel, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)

        sendButton.addActionListener { sendRequest() }
        resetButton.addActionListener { resetCurrentEndpoint() }
        envComboBox.addActionListener { onEnvironmentChanged() }
    }

    private fun buildResponseTabs() {
        responseTabPane.removeAll()

        val responseBodyScrollPane = JBScrollPane(responseBodyArea).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        }

        val wrapper = object : JPanel(null) {
            override fun getPreferredSize(): java.awt.Dimension = responseBodyScrollPane.preferredSize
            override fun getMinimumSize(): java.awt.Dimension = responseBodyScrollPane.minimumSize

            override fun doLayout() {
                responseBodyScrollPane.setBounds(0, 0, width, height)
                val btnSize = prettyToggleBtn.preferredSize
                prettyToggleBtn.setBounds(
                    width - btnSize.width - 22,
                    4,
                    btnSize.width,
                    btnSize.height
                )
            }
        }
        wrapper.add(prettyToggleBtn)
        wrapper.add(responseBodyScrollPane)

        responseTabPane.addTab("响应体", wrapper)
        responseTabPane.addTab("响应头", JBScrollPane(responseHeadersTable).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        })
        responseTabPane.addTab("测试结果", JBScrollPane(testResultsTable).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        })
    }

    private fun buildGrpcResponseView() {
        responseTabPane.removeAll()

        val responseBodyScrollPane = JBScrollPane(responseBodyArea).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        }

        val wrapper = object : JPanel(null) {
            override fun getPreferredSize(): java.awt.Dimension = responseBodyScrollPane.preferredSize
            override fun getMinimumSize(): java.awt.Dimension = responseBodyScrollPane.minimumSize

            override fun doLayout() {
                responseBodyScrollPane.setBounds(0, 0, width, height)
                val btnSize = prettyToggleBtn.preferredSize
                prettyToggleBtn.setBounds(
                    width - btnSize.width - 22,
                    4,
                    btnSize.width,
                    btnSize.height
                )
            }
        }
        wrapper.add(prettyToggleBtn)
        wrapper.add(responseBodyScrollPane)

        responseTabPane.addTab("响应", wrapper)
    }

    private fun setupDeleteButtonColumn(table: JBTable, model: DefaultTableModel) {
        val deleteColIndex = model.columnCount - 1
        val deleteColumn = table.columnModel.getColumn(deleteColIndex)
        deleteColumn.maxWidth = 40
        deleteColumn.minWidth = 40
        deleteColumn.preferredWidth = 40
        deleteColumn.resizable = false

        if (model.columnCount >= 3) {
            val keyColumn = table.columnModel.getColumn(0)
            keyColumn.preferredWidth = 150
            keyColumn.minWidth = 80

            val valueColumn = table.columnModel.getColumn(1)
            valueColumn.preferredWidth = 300
            valueColumn.minWidth = 100

            if (model.columnCount >= 3 && deleteColIndex == 3) {
                val descColumn = table.columnModel.getColumn(2)
                descColumn.preferredWidth = 200
                descColumn.minWidth = 80
            }
        }

        table.setDefaultRenderer(Any::class.java, object : javax.swing.table.DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
            ): java.awt.Component {
                if (column == deleteColIndex) {
                    return JButton("✕").apply {
                        isOpaque = true
                        toolTipText = "删除行"
                    }
                }
                return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            }
        })

        table.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                val col = table.columnAtPoint(e.point)
                if (col == deleteColIndex) {
                    val row = table.rowAtPoint(e.point)
                    if (row >= 0 && row < model.rowCount) {
                        model.removeRow(row)
                        ensureEmptyRow(model)
                    }
                }
            }
        })

        model.addTableModelListener { e ->
            if (e.type == javax.swing.event.TableModelEvent.UPDATE && e.firstRow == model.rowCount - 1) {
                ensureEmptyRow(model)
            }
        }
    }

    private fun ensureEmptyRow(model: DefaultTableModel) {
        val lastRow = model.rowCount - 1
        val hasEmptyRow = if (lastRow >= 0) {
            (0 until model.columnCount - 1).all { col ->
                model.getValueAt(lastRow, col)?.toString().isNullOrBlank()
            }
        } else false

        if (!hasEmptyRow) {
            model.addRow(arrayOfNulls(model.columnCount))
        }
    }

    private fun createEditableTable(table: JBTable): JPanel {
        return JPanel(BorderLayout()).apply {
            add(JBScrollPane(table).apply {
                verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
                horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
            }, BorderLayout.CENTER)
        }
    }

    private fun rebuildTabs(hasPathParams: Boolean, hasFormParams: Boolean) {
        tabPane.removeAll()
        if (hasPathParams) {
            tabPane.addTab("路径", createEditableTable(pathParamsTable))
        }
        tabPane.addTab("参数", createEditableTable(paramsTable))
        tabPane.addTab("请求头", createEditableTable(headersTable))
        if (hasFormParams) {
            tabPane.addTab("表单", createEditableTable(formTable))
        } else {
            tabPane.addTab("请求体", createBodyPanel())
        }
        tabPane.addTab("前置脚本", createScriptPanel(preRequestScriptArea))
        tabPane.addTab("后置脚本", createScriptPanel(postResponseScriptArea))
    }

    private fun createBodyPanel(): JPanel {
        val scrollPane = JBScrollPane(bodyArea).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        }

        // Null-layout panel that floats the Format button at the top-right
        // corner over the scroll pane. Preferred size delegates to the scroll pane.
        val wrapper = object : JPanel(null) {
            override fun getPreferredSize(): java.awt.Dimension = scrollPane.preferredSize
            override fun getMinimumSize(): java.awt.Dimension = scrollPane.minimumSize

            override fun doLayout() {
                scrollPane.setBounds(0, 0, width, height)
                val btnSize = formatBodyBtn.preferredSize
                formatBodyBtn.setBounds(
                    width - btnSize.width - 22,
                    4,
                    btnSize.width,
                    btnSize.height
                )
            }
        }
        // Button added first so it paints on top (lower z-index = painted later)
        wrapper.add(formatBodyBtn)
        wrapper.add(scrollPane)

        return wrapper
    }

    private fun createScriptPanel(editor: EditorTextField): JPanel {
        return JPanel(BorderLayout()).apply {
            add(JBScrollPane(editor).apply {
                verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
                horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
            }, BorderLayout.CENTER)
        }
    }

    private fun loadHostHistory() {
        val hosts = hostCacheHelper.getHosts()
        hostComboBox.removeAllItems()
        if (hosts.isEmpty()) {
            hostComboBox.addItem("http://localhost:8080")
        } else {
            hosts.forEach { hostComboBox.addItem(it) }
        }
        hostComboBox.selectedIndex = 0
    }

    fun loadEnvironments() {
        isLoading = true
        try {
            envComboBox.removeAllItems()
            envComboBox.addItem("无环境")
            val envs = environmentService.getEnvironments()
            envs.forEach { envComboBox.addItem(it.name) }
            val activeName = environmentService.getActiveEnvironmentName()
            if (activeName != null) {
                envComboBox.selectedItem = activeName
            } else {
                envComboBox.selectedIndex = 0
            }
        } finally {
            isLoading = false
        }
    }

    fun onEnvironmentChanged() {
        if (isLoading) return
        val selected = envComboBox.selectedItem as? String
        val name = if (selected == "无环境") null else selected
        environmentService.setActiveEnvironment(name)
        val env = environmentService.getActiveEnvironment()
        if (env != null) {
            val hostVar = env.variables["host"] ?: env.variables["baseUrl"] ?: env.variables["base_url"]
            if (hostVar != null) {
                hostComboBox.selectedItem = hostVar
            }
        }
        onEnvironmentChangedCallback?.invoke()
    }

    var onEnvironmentChangedCallback: (() -> Unit)? = null

    var scriptScopesProvider: ((ApiEndpoint) -> List<ScriptScope>)? = null

    fun getSelectedHost(): String {
        val host = (hostComboBox.editor.item as? String ?: hostComboBox.selectedItem as? String ?: "")
            .trim().trimEnd('/')
        return if (host.isEmpty()) "http://localhost:8080" else host
    }

    fun showEndpoint(endpoint: ApiEndpoint) {
        saveEndpointScripts()
        isLoading = true
        try {
            currentEndpoint = endpoint
            currentEndpointKey = computeCacheKey(endpoint)

            when (val meta = endpoint.metadata) {
                is GrpcMetadata -> showGrpcEndpoint(endpoint, meta)
                is HttpMetadata -> {
                    nameLabel.text = endpoint.name ?: "未命名"
                    methodLabel.text = meta.method.name
                    methodLabel.foreground = getMethodColor(meta.method)

                    buildResponseTabs()

                    val cachedEdit = editCacheService.load(endpoint, currentEndpointKey) as? HttpRequestEditCache
                    if (cachedEdit != null) {
                        loadFromCache(endpoint, cachedEdit)
                    } else {
                        loadFromEndpoint(endpoint)
                    }

                    clearResponse()
                    showResponseDemo()
                    autoSelectTab()

                    loadEndpointScripts()
                }
            }
        } finally {
            isLoading = false
        }
    }

    private fun loadEndpointScripts() {
        val key = currentEndpointKey
        val scope = ScriptScope.Endpoint(key)
        val cache = scriptCacheService.load(scope)
        preRequestScriptArea.text = cache?.preRequestScript ?: ""
        postResponseScriptArea.text = cache?.postResponseScript ?: ""
    }

    private fun saveEndpointScripts() {
        val key = currentEndpointKey
        val scope = ScriptScope.Endpoint(key)
        val cache = ScriptCache(
            preRequestScript = preRequestScriptArea.text.takeIf { it.isNotBlank() },
            postResponseScript = postResponseScriptArea.text.takeIf { it.isNotBlank() }
        )
        scriptCacheService.save(scope, cache)
    }

    private fun showGrpcEndpoint(endpoint: ApiEndpoint, meta: GrpcMetadata) {
        nameLabel.text = endpoint.name ?: "未命名"

        val streamingBadge = when (meta.streamingType) {
            GrpcStreamingType.UNARY -> "一元"
            GrpcStreamingType.SERVER_STREAMING -> "服务端流"
            GrpcStreamingType.CLIENT_STREAMING -> "客户端流"
            GrpcStreamingType.BIDIRECTIONAL -> "双向流"
        }
        methodLabel.text = "gRPC [$streamingBadge]"
        methodLabel.foreground = Color(0x8B5CF6)

        pathField.text = "${meta.packageName}/${meta.methodName}"
        pathField.isEditable = false

        buildGrpcResponseView()

        val cachedEdit = editCacheService.load(endpoint, currentEndpointKey) as? GrpcRequestEditCache
        if (cachedEdit != null) {
            loadGrpcFromCache(meta, cachedEdit)
        } else {
            loadGrpcFromEndpoint(meta)
        }

        rebuildTabsForGrpc(meta)
        clearResponse()
        showResponseDemo()
    }

    private fun loadGrpcFromCache(meta: GrpcMetadata, cache: GrpcRequestEditCache) {
        cache.host?.let { host ->
            val hosts = hostCacheHelper.getHosts()
            hostComboBox.removeAllItems()
            if (hosts.isEmpty()) {
                hostComboBox.addItem(host)
            } else {
                if (host !in hosts) {
                    hostComboBox.addItem(host)
                }
                hosts.forEach { hostComboBox.addItem(it) }
                hostComboBox.selectedItem = host
            }
        } ?: run {
            val hosts = hostCacheHelper.getHosts()
            hostComboBox.removeAllItems()
            if (hosts.isEmpty()) {
                hostComboBox.addItem("localhost:50051")
            } else {
                hosts.forEach { hostComboBox.addItem(it) }
            }
            hostComboBox.selectedIndex = 0
        }

        bodyArea.text = cache.body ?: meta.body?.let { ObjectModelJsonConverter.toJson(it) } ?: ""
    }

    private fun loadGrpcFromEndpoint(meta: GrpcMetadata) {
        val hosts = hostCacheHelper.getHosts()
        hostComboBox.removeAllItems()
        if (hosts.isEmpty()) {
            hostComboBox.addItem("localhost:50051")
        } else {
            hosts.forEach { hostComboBox.addItem(it) }
        }
        hostComboBox.selectedIndex = 0

        bodyArea.text = meta.body?.let { ObjectModelJsonConverter.toJson(it) } ?: ""
    }

    private fun rebuildTabsForGrpc(meta: GrpcMetadata) {
        tabPane.removeAll()
        tabPane.addTab("请求消息", createBodyPanel())
        tabPane.addTab("信息", createGrpcInfoPanel(meta))
    }

    private fun createGrpcInfoPanel(meta: GrpcMetadata): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(8)
        }

        fun addRow(label: String, value: String) {
            val row = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                alignmentX = java.awt.Component.LEFT_ALIGNMENT
                border = JBUI.Borders.emptyBottom(4)
            }
            row.add(JBLabel("$label:").apply {
                preferredSize = Dimension(130, preferredSize.height)
                minimumSize = Dimension(130, minimumSize.height)
                maximumSize = Dimension(130, maximumSize.height)
            })
            row.add(JBLabel(value))
            row.add(Box.createHorizontalGlue())
            panel.add(row)
        }

        addRow("服务", meta.serviceName)
        addRow("包名", meta.packageName)
        addRow("流类型", meta.streamingType.name)
        meta.protoFile?.let { addRow("Proto文件", it) }

        panel.add(Box.createVerticalGlue())
        return panel
    }

    private fun loadFromCache(endpoint: ApiEndpoint, cache: HttpRequestEditCache) {
        val meta = endpoint.httpMetadata
        pathField.text = cache.path ?: meta?.path ?: ""
        val parameters = meta?.parameters ?: emptyList()
        val headers = meta?.headers ?: emptyList()

        cache.host?.let { host ->
            val hosts = hostCacheHelper.getHosts()
            hostComboBox.removeAllItems()
            if (hosts.isEmpty()) {
                hostComboBox.addItem(host)
            } else {
                if (host !in hosts) {
                    hostComboBox.addItem(host)
                }
                hosts.forEach { hostComboBox.addItem(it) }
                hostComboBox.selectedItem = host
            }
        }

        val pathParams = parameters.filter { it.binding == ParameterBinding.Path }
        pathParamsTableModel.rowCount = 0
        pathParamsEnumValues.clear()
        val cachedPathParams = cache.pathParams.associateBy { it.name }
        pathParams.forEachIndexed { index, p ->
            val cachedValue = cachedPathParams[p.name]?.value ?: p.defaultValue ?: p.example ?: ""
            pathParamsTableModel.addRow(arrayOf(p.name, cachedValue, p.description ?: "", ""))
            updatePathParamComboBox(index, p.enumValues)
        }
        ensureEmptyRow(pathParamsTableModel)

        paramsTableModel.rowCount = 0
        val cachedQueryParams = cache.queryParams.associateBy { it.name }
        parameters
            .filter { it.binding == ParameterBinding.Query || it.binding == ParameterBinding.Cookie }
            .forEach { p ->
                val cachedValue = cachedQueryParams[p.name]?.value ?: p.defaultValue ?: p.example ?: ""
                paramsTableModel.addRow(arrayOf(p.name, cachedValue, p.description ?: "", ""))
            }
        ensureEmptyRow(paramsTableModel)

        headersTableModel.rowCount = 0
        val cachedHeaders = cache.headers.associateBy { it.name }
        headers.forEach { h ->
            val cachedValue = cachedHeaders[h.name]?.value ?: h.value ?: ""
            headersTableModel.addRow(arrayOf(h.name, cachedValue, ""))
        }
        parameters.filter { it.binding == ParameterBinding.Header }
            .forEach { p ->
                val cachedValue = cachedHeaders[p.name]?.value ?: p.defaultValue ?: p.example ?: ""
                headersTableModel.addRow(arrayOf(p.name, cachedValue, ""))
            }
        ensureEmptyRow(headersTableModel)

        val formParams = parameters.filter { it.binding == ParameterBinding.Form }
        formTableModel.rowCount = 0
        formFileRows.clear()
        val cachedFormParams = cache.formParams.associateBy { it.name }
        formParams.forEachIndexed { index, p ->
            val cachedValue = cachedFormParams[p.name]?.value ?: p.defaultValue ?: p.example ?: ""
            formTableModel.addRow(arrayOf(p.name, cachedValue, p.description ?: "", ""))
            if (p.type == ParameterType.FILE) formFileRows.add(index)
        }
        ensureEmptyRow(formTableModel)
        setupFormTableFileEditors()

        val contentType = meta?.contentType
        val isFormData = formParams.isNotEmpty() ||
                contentType?.contains("form-urlencoded", ignoreCase = true) == true ||
                contentType?.contains("form-data", ignoreCase = true) == true
        hasFormData = isFormData
        endpointContentType = cache.contentType ?: contentType

        bodyArea.text = if (!isFormData) {
            cache.body ?: meta?.body?.let { ObjectModelJsonConverter.toJson(it) } ?: ""
        } else ""

        rebuildTabs(hasPathParams = pathParams.isNotEmpty(), hasFormParams = isFormData)
    }

    private fun loadFromEndpoint(endpoint: ApiEndpoint) {
        val meta = endpoint.httpMetadata
        pathField.text = meta?.path ?: ""
        val parameters = meta?.parameters ?: emptyList()
        val headers = meta?.headers ?: emptyList()

        val pathParams = parameters.filter { it.binding == ParameterBinding.Path }
        pathParamsTableModel.rowCount = 0
        pathParamsEnumValues.clear()
        pathParams.forEachIndexed { index, p ->
            pathParamsTableModel.addRow(arrayOf(p.name, p.defaultValue ?: p.example ?: "", p.description ?: "", ""))
            updatePathParamComboBox(index, p.enumValues)
        }
        ensureEmptyRow(pathParamsTableModel)

        paramsTableModel.rowCount = 0
        parameters
            .filter { it.binding == ParameterBinding.Query || it.binding == ParameterBinding.Cookie }
            .forEach { p ->
                paramsTableModel.addRow(arrayOf(p.name, p.defaultValue ?: p.example ?: "", p.description ?: "", ""))
            }
        ensureEmptyRow(paramsTableModel)

        headersTableModel.rowCount = 0
        headers.forEach { h ->
            headersTableModel.addRow(arrayOf(h.name, h.value ?: "", ""))
        }
        parameters.filter { it.binding == ParameterBinding.Header }
            .forEach { p ->
                headersTableModel.addRow(arrayOf(p.name, p.defaultValue ?: p.example ?: "", ""))
            }
        ensureEmptyRow(headersTableModel)

        val formParams = parameters.filter { it.binding == ParameterBinding.Form }
        formTableModel.rowCount = 0
        formFileRows.clear()
        formParams.forEachIndexed { index, p ->
            formTableModel.addRow(arrayOf(p.name, p.defaultValue ?: p.example ?: "", p.description ?: "", ""))
            if (p.type == ParameterType.FILE) formFileRows.add(index)
        }
        ensureEmptyRow(formTableModel)
        setupFormTableFileEditors()

        val contentType = meta?.contentType
        val isFormData = formParams.isNotEmpty() ||
                contentType?.contains("form-urlencoded", ignoreCase = true) == true ||
                contentType?.contains("form-data", ignoreCase = true) == true
        hasFormData = isFormData
        endpointContentType = contentType

        bodyArea.text = if (!isFormData) {
            meta?.body?.let { ObjectModelJsonConverter.toJson(it) } ?: ""
        } else ""

        rebuildTabs(hasPathParams = pathParams.isNotEmpty(), hasFormParams = isFormData)
    }

    private fun autoSelectTab() {
        val tabCount = tabPane.tabCount
        val pathParamsCount = pathParamsTableModel.rowCount - 1
        val paramsCount = paramsTableModel.rowCount - 1
        val headersCount = headersTableModel.rowCount - 1

        tabPane.selectedIndex = when {
            pathParamsCount > 0 -> 0
            hasFormData -> (0 until tabCount).firstOrNull { tabPane.getTitleAt(it) == "表单" } ?: 0
            bodyArea.text.isNotBlank() -> (0 until tabCount).firstOrNull { tabPane.getTitleAt(it) == "请求体" } ?: 0
            paramsCount > 0 -> (0 until tabCount).firstOrNull { tabPane.getTitleAt(it) == "参数" } ?: 0
            headersCount > 0 -> (0 until tabCount).firstOrNull { tabPane.getTitleAt(it) == "请求头" } ?: 0
            else -> 0
        }
    }

    private fun clearResponse() {
        currentRequestJob?.cancel()
        currentRequestJob = null
        loadingTimer?.stop()
        loadingTimer = null
        sendButton.isEnabled = true
        sendButton.text = "发送"

        rawResponseBody = ""
        isPrettyJson = true
        prettyToggleBtn.text = "原始"
        responseBodyArea.text = ""
        responseHeadersTableModel.rowCount = 0
        responseStatusLabel.text = ""
        responseStatusLabel.foreground = null
        testResultsTableModel.rowCount = 0
    }

    private fun showResponseDemo() {
        val endpoint = currentEndpoint ?: return
        val responseBody = when (val meta = endpoint.metadata) {
            is HttpMetadata -> meta.responseBody
            is GrpcMetadata -> meta.responseBody
            else -> null
        } ?: return

        val demoJson = ObjectModelJsonConverter.toJson(responseBody)
        if (demoJson.isBlank() || demoJson == "{}") return

        rawResponseBody = demoJson
        isPrettyJson = true
        prettyToggleBtn.text = "原始"
        responseBodyArea.text = formatJson(demoJson)
        responseStatusLabel.text = "示例响应"
        responseStatusLabel.foreground = Color(0x888888)
    }

    fun clear() {
        saveEndpointScripts()
        currentEndpoint = null
        currentEndpointKey = ""
        nameLabel.text = ""
        methodLabel.text = ""
        pathField.text = ""
        pathParamsTableModel.rowCount = 0
        paramsTableModel.rowCount = 0
        headersTableModel.rowCount = 0
        formTableModel.rowCount = 0
        bodyArea.text = ""
        clearResponse()
        hasFormData = false
        endpointContentType = null
        formFileRows.clear()
        rebuildTabs(hasPathParams = false, hasFormParams = false)
        ensureEmptyRow(pathParamsTableModel)
        ensureEmptyRow(paramsTableModel)
        ensureEmptyRow(headersTableModel)
        ensureEmptyRow(formTableModel)
    }

    private fun sendRequest() {
        val endpoint = currentEndpoint ?: return

        if (endpoint.isGrpc) {
            if (!checkGrpcCallReady()) {
                return
            }
        }

        val host = getSelectedHost()
        hostCacheHelper.addHost(host)
        loadHostHistory()

        prepareSendUI(endpoint)

        currentRequestJob = scope.launch {
            val response = if (endpoint.isGrpc) {
                sendGrpcRequestInternal(endpoint, host)
            } else {
                sendHttpRequestInternal(endpoint, host)
            }

            handleResponse(endpoint, response)
        }
    }

    private fun checkGrpcCallReady(): Boolean {
        val settings = com.apimocktle.settings.SettingBinder.getInstance(project).read()

        if (!settings.grpcCallEnabled) {
            val result = Messages.showOkCancelDialog(
                project,
                "gRPC调用未启用。是否现在启用？",
                "gRPC调用未启用",
                "打开设置",
                "取消",
                Messages.getQuestionIcon()
            )
            if (result == Messages.OK) {
                openGrpcSettings()
            }
            return false
        }

        val resolver = com.apimocktle.grpc.GrpcRuntimeResolver.getInstance(project)
        val resolved = resolver.resolve()
        if (resolved == null) {
            val result = Messages.showOkCancelDialog(
                project,
                "gRPC运行时包不可用。是否现在配置？",
                "gRPC运行时不可用",
                "打开设置",
                "取消",
                Messages.getWarningIcon()
            )
            if (result == Messages.OK) {
                openGrpcSettings()
            }
            return false
        }

        return true
    }

    private fun openGrpcSettings() {
        com.apimocktle.settings.ui.ApiMocktleSettingsConfigurable.selectTab(
            com.apimocktle.settings.ui.ApiMocktleSettingsConfigurable.TAB_GRPC
        )
        com.intellij.openapi.options.ShowSettingsUtil.getInstance().showSettingsDialog(
            project,
            com.apimocktle.settings.ui.ApiMocktleSettingsConfigurable::class.java
        )
    }

    private fun prepareSendUI(endpoint: ApiEndpoint) {
        val meta = endpoint.metadata
        val path = when (meta) {
            is HttpMetadata -> meta.path
            is GrpcMetadata -> meta.path
            else -> ""
        }
        LOG.info("Sending ${if (endpoint.isGrpc) "gRPC" else endpoint.httpMetadata?.method?.name ?: "HTTP"} request to $path")
        sendButton.isEnabled = false
        sendButton.text = "发送中..."
        responseBodyArea.text = ""
        responseStatusLabel.text = "发送中..."
        responseStatusLabel.foreground = null

        var loadingDots = 0
        loadingTimer = Timer(300) {
            loadingDots = (loadingDots + 1) % 4
            val dots = ".".repeat(loadingDots)
            sendButton.text = "发送中$dots"
            responseStatusLabel.text = "发送中$dots"
        }.apply { start() }
    }

    private suspend fun sendHttpRequestInternal(endpoint: ApiEndpoint, host: String): RequestResult {
        val pathParams = (0 until pathParamsTableModel.rowCount).map { row ->
            val name = pathParamsTableModel.getValueAt(row, 0)?.toString()?.trim().orEmpty()
            val value = pathParamsTableModel.getValueAt(row, 1)?.toString()?.trim().orEmpty()
            name to value
        }
        val headers = extractKeyValuesFromTable(headersTableModel)
        val query = extractKeyValuesFromTable(paramsTableModel)
        val formParams = if (hasFormData) buildFormParams() else emptyList()
        val body = bodyArea.text.takeIf { it.isNotBlank() }
        val method = endpoint.httpMetadata?.method?.name ?: endpoint.metadata.protocol

        val input = HttpRequestInput(
            host = host,
            path = pathField.text,
            method = method,
            pathParams = pathParams,
            headers = headers,
            query = query,
            body = body,
            formParams = formParams,
            hasFormData = hasFormData,
            contentType = endpointContentType,
            preRequestScript = preRequestScriptArea.text.takeIf { it.isNotBlank() },
            postResponseScript = postResponseScriptArea.text.takeIf { it.isNotBlank() },
            endpointName = endpoint.name ?: "",
            endpointKey = currentEndpointKey,
            scriptScopes = scriptScopesProvider?.invoke(endpoint) ?: emptyList()
        )

        val result = requestExecutor.executeHttp(input)

        result.testResults?.let { testResults ->
            swing {
                displayTestResults(testResults)
            }
        }

        return result
    }

    private suspend fun sendGrpcRequestInternal(endpoint: ApiEndpoint, host: String): RequestResult {
        val meta = endpoint.grpcMetadata ?: return RequestResult(body = "错误：不是gRPC端点", isError = true)

        val input = GrpcRequestInput(
            host = host,
            grpcMetadata = meta,
            body = bodyArea.text.takeIf { it.isNotBlank() },
            endpointName = endpoint.name ?: "",
            sourceMethod = endpoint.sourceMethod
        )

        val result = requestExecutor.executeGrpc(input)

        if (result.requiresGrpcSetup) {
            swing {
                Messages.showInfoMessage(
                    project,
                    "没有可用的gRPC客户端。请在设置中配置gRPC运行时。\n\n" +
                            "插件需要gRPC运行时JAR包。请从gRPC设置面板下载。",
                    "gRPC客户端不可用"
                )
            }
        }

        return result
    }

    private suspend fun handleResponse(endpoint: ApiEndpoint, response: RequestResult) {
        swing {
            if (currentEndpoint != endpoint) {
                LOG.debug("Endpoint changed, discarding response")
                return@swing
            }

            loadingTimer?.stop()
            loadingTimer = null
            sendButton.isEnabled = true
            sendButton.text = "发送"

            rawResponseBody = response.body
            isPrettyJson = true
            prettyToggleBtn.text = "原始"
            responseBodyArea.text = if (response.isError) response.body else formatJson(response.body)

            when {
                response.statusCode != null -> {
                    val isGrpcStatus = response.statusCode in 0..16
                    val statusText = if (isGrpcStatus) {
                        GrpcStatus.formatStatus(response.statusCode)
                    } else {
                        response.statusCode.toString()
                    }
                    responseStatusLabel.text = "状态：$statusText"
                    responseStatusLabel.foreground = when {
                        isGrpcStatus -> {
                            if (response.statusCode == GrpcStatus.OK) Color(0x49cc90) else Color(0xf93e3e)
                        }

                        response.statusCode in 200..299 -> Color(0x49cc90)
                        response.statusCode in 400..499 -> Color(0xfca130)
                        response.statusCode >= 500 -> Color(0xf93e3e)
                        else -> null
                    }
                }

                else -> {
                    responseStatusLabel.text = if (response.isError) "错误" else "成功"
                    responseStatusLabel.foreground = if (response.isError) Color(0xf93e3e) else Color(0x49cc90)
                }
            }

            responseHeadersTableModel.rowCount = 0
            response.headers.forEach { (k, v) ->
                responseHeadersTableModel.addRow(arrayOf(k, v))
            }

            responseTabPane.selectedIndex = 0
        }
    }

    private fun togglePrettyJson() {
        isPrettyJson = !isPrettyJson
        prettyToggleBtn.text = if (isPrettyJson) "原始" else "格式化"
        responseBodyArea.text = if (isPrettyJson) {
            formatJson(rawResponseBody)
        } else {
            rawResponseBody
        }
    }

    private fun copyResponseBody() {
        val text = responseBodyArea.text
        if (text.isNotEmpty()) {
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            val selection = java.awt.datatransfer.StringSelection(text)
            clipboard.setContents(selection, null)
            showCopyNotification()
        }
    }

    private fun showCopyNotification() {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("ApiMocktle Notifications")
            .createNotification("已复制到剪贴板", NotificationType.INFORMATION)
            .notify(project)
    }

    private fun displayTestResults(results: List<TestResult>) {
        testResultsTableModel.rowCount = 0
        for (result in results) {
            val status = if (result.passed) "通过" else "失败"
            testResultsTableModel.addRow(arrayOf(result.name, status, result.error ?: ""))
        }
        if (results.isNotEmpty()) {
            val tabIndex = (0 until responseTabPane.tabCount).firstOrNull {
                responseTabPane.getTitleAt(it) == "测试结果"
            }
            if (tabIndex != null) {
                responseTabPane.selectedIndex = tabIndex
            }
        }
    }

    private fun extractKeyValuesFromTable(model: DefaultTableModel): List<KeyValue> {
        return (0 until model.rowCount)
            .map { row ->
                val name = model.getValueAt(row, 0)?.toString()?.trim().orEmpty()
                val value = model.getValueAt(row, 1)?.toString()?.trim().orEmpty()
                name to value
            }
            .filter { it.name.isNotEmpty() }
    }

    /**
     * Sets up custom renderer and editor for file-type rows in the form table.
     * File rows show a path field with a "Browse..." button; text rows use normal editing.
     */
    private fun setupFormTableFileEditors() {
        val valueCol = formTable.columnModel.getColumn(1)

        // Renderer: show "(file) <path>" for file rows
        valueCol.cellRenderer = object : javax.swing.table.DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
            ): java.awt.Component {
                val text = value?.toString() ?: ""
                val display = if (row in formFileRows) {
                    val fileName = if (text.isNotBlank()) java.io.File(text).name else "<未选择文件>"
                    "📎 $fileName"
                } else text
                return super.getTableCellRendererComponent(table, display, isSelected, hasFocus, row, column)
            }
        }

        // Editor: file rows open a file chooser; text rows use default text field
        valueCol.cellEditor = object : javax.swing.AbstractCellEditor(), javax.swing.table.TableCellEditor {
            private val textField = JTextField()
            private var currentRow = -1

            override fun getCellEditorValue(): Any = textField.text

            override fun getTableCellEditorComponent(
                table: JTable?, value: Any?, isSelected: Boolean, row: Int, column: Int
            ): java.awt.Component {
                currentRow = row
                textField.text = value?.toString() ?: ""
                if (row in formFileRows) {
                    // Trigger file chooser immediately when editing starts
                    SwingUtilities.invokeLater {
                        val chooser = javax.swing.JFileChooser()
                        val current = textField.text
                        if (current.isNotBlank()) chooser.selectedFile = java.io.File(current)
                        if (chooser.showOpenDialog(table) == javax.swing.JFileChooser.APPROVE_OPTION) {
                            textField.text = chooser.selectedFile.absolutePath
                        }
                        stopCellEditing()
                    }
                }
                return textField
            }
        }
    }

    /**
     * Builds the list of [FormParam] from the form table, creating [FormParam.File]
     * entries for file-type rows and [FormParam.Text] for all others.
     */
    private fun buildFormParams(): List<FormParam> {
        val rows = (0 until formTableModel.rowCount).map { row ->
            Triple(
                formTableModel.getValueAt(row, 0)?.toString()?.trim().orEmpty(),
                formTableModel.getValueAt(row, 1)?.toString()?.trim().orEmpty(),
                row in formFileRows
            )
        }
        return EndpointDetailsPanelLogic.buildFormParams(rows)
    }

    private fun formatJson(json: String) = EndpointDetailsPanelLogic.formatJson(json)

    private fun formatRequestBody() {
        val currentText = bodyArea.text
        val formatted = formatJson(currentText)
        if (formatted != currentText) {
            bodyArea.text = formatted
        }
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

    private fun getEndpointColor(endpoint: ApiEndpoint): Color =
        if (endpoint.isGrpc) Color(0x8B5CF6) else getMethodColor(endpoint.httpMetadata?.method ?: HttpMethod.NO_METHOD)

    private fun resetCurrentEndpoint() {
        val endpoint = currentEndpoint ?: return
        editCacheService.delete(currentEndpointKey, endpoint.isGrpc)
        isLoading = true
        try {
            if (endpoint.isGrpc) {
                val meta = endpoint.grpcMetadata
                if (meta != null) {
                    loadGrpcFromEndpoint(meta)
                }
            } else {
                loadFromEndpoint(endpoint)
                autoSelectTab()
            }
            clearResponse()
            showResponseDemo()
        } finally {
            isLoading = false
        }
    }

    fun resetEndpoint(endpoint: ApiEndpoint) {
        if (currentEndpoint == endpoint) {
            resetCurrentEndpoint()
        } else {
            val key = readSync {
                computeCacheKey(endpoint)
            }
            editCacheService.delete(key, endpoint.isGrpc)
        }
    }

    private fun saveCurrentEdit() {
        autoSaveTimer.restart()
    }

    private fun doSaveCurrentEdit() {
        val endpoint = currentEndpoint ?: return

        if (endpoint.isGrpc) {
            val cache = GrpcRequestEditCache(
                key = currentEndpointKey,
                name = endpoint.name,
                host = getSelectedHost(),
                serviceName = endpoint.grpcMetadata?.serviceName,
                methodName = endpoint.grpcMetadata?.methodName,
                packageName = endpoint.grpcMetadata?.packageName,
                body = bodyArea.text.takeIf { it.isNotBlank() }
            )
            editCacheService.save(endpoint, cache, currentEndpointKey)
        } else {
            val headers = (0 until headersTableModel.rowCount)
                .map { row ->
                    val name = headersTableModel.getValueAt(row, 0)?.toString()?.trim().orEmpty()
                    val value = headersTableModel.getValueAt(row, 1)?.toString()?.trim().orEmpty()
                    EditableKeyValue(name, value)
                }
                .filter { it.name.isNotEmpty() }

            val pathParams = (0 until pathParamsTableModel.rowCount)
                .map { row ->
                    val name = pathParamsTableModel.getValueAt(row, 0)?.toString()?.trim().orEmpty()
                    val value = pathParamsTableModel.getValueAt(row, 1)?.toString()?.trim().orEmpty()
                    val desc = pathParamsTableModel.getValueAt(row, 2)?.toString()?.trim().orEmpty()
                    EditableKeyValue(name, value, desc)
                }
                .filter { it.name.isNotEmpty() }

            val queryParams = (0 until paramsTableModel.rowCount)
                .map { row ->
                    val name = paramsTableModel.getValueAt(row, 0)?.toString()?.trim().orEmpty()
                    val value = paramsTableModel.getValueAt(row, 1)?.toString()?.trim().orEmpty()
                    val desc = paramsTableModel.getValueAt(row, 2)?.toString()?.trim().orEmpty()
                    EditableKeyValue(name, value, desc)
                }
                .filter { it.name.isNotEmpty() }

            val formParams = (0 until formTableModel.rowCount)
                .map { row ->
                    val name = formTableModel.getValueAt(row, 0)?.toString()?.trim().orEmpty()
                    val value = formTableModel.getValueAt(row, 1)?.toString()?.trim().orEmpty()
                    val desc = formTableModel.getValueAt(row, 2)?.toString()?.trim().orEmpty()
                    EditableKeyValue(name, value, desc)
                }
                .filter { it.name.isNotEmpty() }

            val cache = HttpRequestEditCache(
                key = currentEndpointKey,
                name = endpoint.name,
                path = pathField.text,
                method = endpoint.httpMetadata?.method?.name ?: endpoint.metadata.protocol,
                host = getSelectedHost(),
                headers = headers,
                pathParams = pathParams,
                queryParams = queryParams,
                formParams = formParams,
                body = bodyArea.text.takeIf { it.isNotBlank() },
                contentType = endpointContentType
            )
            editCacheService.save(endpoint, cache, currentEndpointKey)
        }
    }

    private fun setupAutoSaveListeners() {
        pathField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = saveCurrentEdit()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = saveCurrentEdit()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = saveCurrentEdit()
        })

        hostComboBox.addActionListener { saveCurrentEdit() }

        bodyArea.document.addDocumentListener(object : com.intellij.openapi.editor.event.DocumentListener {
            override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) = saveCurrentEdit()
        }, project)

        val tableModelListener = TableModelListener { e ->
            if (e.type == TableModelEvent.UPDATE) {
                saveCurrentEdit()
            }
        }
        pathParamsTableModel.addTableModelListener(tableModelListener)
        paramsTableModel.addTableModelListener(tableModelListener)
        headersTableModel.addTableModelListener(tableModelListener)
        formTableModel.addTableModelListener(tableModelListener)
    }

    private fun computeCacheKey(endpoint: ApiEndpoint): String {
        val method = endpoint.sourceMethod ?: return ""
        val cls = endpoint.sourceClass ?: method.containingClass ?: return ""

        val className = readSync {
            cls.qualifiedName ?: cls.name ?: ""
        }
        return "$className#${method.name}"
    }

    fun dispose() {
        saveEndpointScripts()
        scope.cancel()
    }
}

/**
 * Pure logic extracted from [EndpointDetailsPanel] for testability.
 * No UI dependencies — all methods are stateless or take plain data as arguments.
 */
internal object EndpointDetailsPanelLogic {

    /**
     * Pretty-prints a JSON string. Returns the original string if it is blank or not valid JSON.
     */
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

    /**
     * Substitutes path template variables (e.g. `{id}`) with URL-encoded values
     * from [pathParams] (a list of name→value pairs).
     */
    fun resolvePath(pathTemplate: String, pathParams: List<Pair<String, String>>): String {
        var path = pathTemplate
        for ((key, value) in pathParams) {
            if (key.isNotEmpty() && value.isNotEmpty()) {
                path = path.replace("{$key}", URLEncoder.encode(value, "UTF-8"))
            }
        }
        return path
    }

    /**
     * Builds a list of [FormParam] from raw row data.
     *
     * @param rows list of (name, value, isFile) triples
     * @param fileLoader reads a file by path, returns null if the file doesn't exist
     */
    fun buildFormParams(
        rows: List<Triple<String, String, Boolean>>,
        fileLoader: (String) -> Pair<String, ByteArray>? = { path ->
            val f = java.io.File(path)
            if (!f.exists()) null
            else f.name to f.readBytes()
        }
    ): List<FormParam> {
        return rows.mapNotNull { (name, value, isFile) ->
            if (name.isEmpty()) return@mapNotNull null
            if (isFile) {
                if (value.isBlank()) return@mapNotNull null
                val (fileName, bytes) = fileLoader(value) ?: return@mapNotNull null
                val mimeType = runCatching {
                    java.nio.file.Files.probeContentType(java.io.File(value).toPath())
                }.getOrNull() ?: "application/octet-stream"
                FormParam.File(name, fileName, mimeType, bytes)
            } else {
                FormParam.Text(name, value)
            }
        }
    }
}
