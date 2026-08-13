package com.apimocktle.ide.dialog

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.apimocktle.exporter.model.ApiEndpoint
import com.apimocktle.exporter.model.ExportFormat
import com.apimocktle.exporter.model.OutputConfig
import com.apimocktle.exporter.model.httpMetadata
import com.apimocktle.exporter.model.path
import com.apimocktle.ide.ui.HttpMethodColors
import java.awt.*
import java.awt.event.ItemEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

/**
 * API export configuration dialog.
 *
 * Allows users to select which endpoints to export and configure
 * ApiMocktle export options.
 */
class ExportDialog(
    private val project: Project,
    endpointCount: Int,
    private val endpoints: List<ApiEndpoint> = emptyList()
) : DialogWrapper(project) {

    private val preferencesPersistence = ExportDialogPreferencesPersistence(project)

    // --- Endpoint table ---
    private val endpointTableModel = EndpointTableModel(endpoints)
    private val endpointTable = JBTable(endpointTableModel)
    private val selectAllBtn = JButton("全选")
    private val deselectAllBtn = JButton("取消全选")
    private val searchField = JTextField().apply {
        putClientProperty("JTextField.placeholder", "搜索路径/名称/方法")
        preferredSize = Dimension(180, preferredSize.height)
    }
    private val countLabel = JLabel("")

    var selectedFormat: ExportFormat = ExportFormat.YAPI
        private set

    var outputConfig: OutputConfig = OutputConfig.DEFAULT
        private set

    init {
        title = "导出API端点（$endpointCount 个端点）"

        endpointTableModel.onDataChanged = { updateCount() }

        selectAllBtn.addActionListener { endpointTableModel.selectAll() }
        deselectAllBtn.addActionListener { endpointTableModel.deselectAll() }
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = onSearchChanged()
            override fun removeUpdate(e: DocumentEvent?) = onSearchChanged()
            override fun changedUpdate(e: DocumentEvent?) = onSearchChanged()
        })

        init()
        setupEndpointTable()
        updateCount()
    }

    private fun onSearchChanged() {
        endpointTableModel.setFilter(searchField.text.trim())
    }

    private fun updateCount() {
        val selected = endpointTableModel.rows.count { it.selected }
        countLabel.text = "已选 $selected / ${endpointTableModel.rows.size}"
    }

    private fun setupEndpointTable() {
        endpointTable.autoResizeMode = JBTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS

        val selectCol = endpointTable.columnModel.getColumn(COL_SELECT)
        selectCol.maxWidth = 50
        selectCol.minWidth = 40
        selectCol.preferredWidth = 40
        selectCol.resizable = false
        selectCol.cellRenderer = CheckboxRenderer()
        selectCol.cellEditor = CheckboxEditor()

        val methodCol = endpointTable.columnModel.getColumn(COL_METHOD)
        methodCol.maxWidth = 80
        methodCol.minWidth = 60
        methodCol.preferredWidth = 70
        methodCol.resizable = false
        methodCol.cellRenderer = MethodCellRenderer()

        val pathCol = endpointTable.columnModel.getColumn(COL_PATH)
        pathCol.preferredWidth = 250
        pathCol.minWidth = 100

        val nameCol = endpointTable.columnModel.getColumn(COL_NAME)
        nameCol.preferredWidth = 180
        nameCol.minWidth = 80
    }

    override fun createCenterPanel(): JComponent {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(createEndpointPanel())
        }
    }

    private fun createEndpointPanel(): JPanel {
        val headerPanel = JPanel(BorderLayout(8, 0)).apply {
            add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                add(JLabel("API端点："))
                add(countLabel)
            }, BorderLayout.WEST)
            add(searchField, BorderLayout.CENTER)
            add(JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                add(selectAllBtn)
                add(Box.createHorizontalStrut(4))
                add(deselectAllBtn)
            }, BorderLayout.EAST)
        }

        val scrollPane = JScrollPane(endpointTable).apply {
            preferredSize = Dimension(0, 300)
            minimumSize = Dimension(0, 150)
        }

        return JPanel(BorderLayout()).apply {
            add(headerPanel, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
        }
    }

    override fun doOKAction() {
        selectedFormat = ExportFormat.YAPI
        outputConfig = OutputConfig()
        saveDialogState()
        super.doOKAction()
    }

    private fun saveDialogState() {
        val prefs = ExportDialogPreferences(lastExportFormat = selectedFormat.name)
        preferencesPersistence.save(prefs)
    }

    companion object {
        fun show(
            project: Project,
            endpointCount: Int,
            endpoints: List<ApiEndpoint> = emptyList()
        ): ExportDialogResult? {
            val dialog = ExportDialog(project, endpointCount, endpoints)
            return if (dialog.showAndGet()) {
                val selectedEndpoints = dialog.endpointTableModel.getSelectedEndpoints()
                ExportDialogResult(
                    format = dialog.selectedFormat,
                    outputConfig = dialog.outputConfig,
                    selectedEndpoints = selectedEndpoints
                )
            } else {
                null
            }
        }
    }
}

data class ExportDialogResult(
    val format: ExportFormat,
    val outputConfig: OutputConfig,
    val selectedEndpoints: List<EndpointSelection> = emptyList()
)

data class EndpointSelection(
    val endpoint: ApiEndpoint
)

private const val COL_SELECT = 0
private const val COL_METHOD = 1
private const val COL_PATH = 2
private const val COL_NAME = 3

private class EndpointTableModel(
    endpoints: List<ApiEndpoint>
) : AbstractTableModel() {

    data class Row(
        val endpoint: ApiEndpoint,
        var selected: Boolean = true
    )

    val rows = endpoints.map { Row(it, true) }
    private var visibleRows: List<Row> = rows
    private var filter: String = ""

    /** Called after any change that affects selection count. */
    var onDataChanged: (() -> Unit)? = null

    fun setFilter(text: String) {
        filter = text.lowercase()
        visibleRows = if (filter.isEmpty()) {
            rows
        } else {
            rows.filter { row ->
                val endpoint = row.endpoint
                endpoint.path.lowercase().contains(filter) ||
                        (endpoint.name?.lowercase()?.contains(filter) == true) ||
                        (endpoint.httpMetadata?.method?.name?.lowercase()?.contains(filter) == true)
            }
        }
        fireTableDataChanged()
    }

    override fun getRowCount(): Int = visibleRows.size
    override fun getColumnCount(): Int = 4

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? = when (columnIndex) {
        COL_SELECT -> visibleRows[rowIndex].selected
        COL_METHOD -> visibleRows[rowIndex].endpoint.httpMetadata?.method?.name
            ?: visibleRows[rowIndex].endpoint.metadata.protocol
        COL_PATH -> visibleRows[rowIndex].endpoint.path
        COL_NAME -> visibleRows[rowIndex].endpoint.name ?: ""
        else -> null
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        if (columnIndex == COL_SELECT) {
            visibleRows[rowIndex].selected = aValue as Boolean
            onDataChanged?.invoke()
        }
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean =
        columnIndex == COL_SELECT

    override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
        COL_SELECT -> Boolean::class.java
        else -> String::class.java
    }

    override fun getColumnName(column: Int): String = when (column) {
        COL_SELECT -> ""
        COL_METHOD -> "方法"
        COL_PATH -> "路径"
        COL_NAME -> "名称"
        else -> ""
    }

    fun selectAll() {
        visibleRows.forEach { it.selected = true }
        fireTableDataChanged()
        onDataChanged?.invoke()
    }

    fun deselectAll() {
        visibleRows.forEach { it.selected = false }
        fireTableDataChanged()
        onDataChanged?.invoke()
    }

    fun getSelectedEndpoints(): List<EndpointSelection> {
        return rows.filter { it.selected }.map { EndpointSelection(it.endpoint) }
    }
}

private class MethodCellRenderer : DefaultTableCellRenderer() {

    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
        if (c is JLabel && value is String) {
            c.text = value.padEnd(6)
            if (!isSelected) {
                c.foreground = HttpMethodColors.colorForName(value)
            }
        }
        return c
    }
}

private class CheckboxRenderer : JCheckBox(), TableCellRenderer {

    init {
        horizontalAlignment = SwingConstants.CENTER
        isOpaque = true
    }

    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        this.isSelected = value as? Boolean ?: false
        if (isSelected) {
            foreground = table?.selectionForeground
            background = table?.selectionBackground
        } else {
            foreground = table?.foreground
            background = table?.background
        }
        return this
    }
}

private class CheckboxEditor : DefaultCellEditor(JCheckBox().apply {
    horizontalAlignment = SwingConstants.CENTER
})