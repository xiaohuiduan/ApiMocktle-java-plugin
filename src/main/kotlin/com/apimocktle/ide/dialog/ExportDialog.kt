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
import java.awt.*
import java.awt.event.ItemEvent
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

/**
 * API export configuration dialog.
 *
 * Allows users to select which endpoints to export and configure
 * YAPI export options.
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

    var selectedFormat: ExportFormat = ExportFormat.YAPI
        private set

    var outputConfig: OutputConfig = OutputConfig.DEFAULT
        private set

    init {
        title = "导出API端点（$endpointCount 个端点）"

        selectAllBtn.addActionListener { endpointTableModel.selectAll() }
        deselectAllBtn.addActionListener { endpointTableModel.deselectAll() }

        init()
        setupEndpointTable()
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
        val headerPanel = JPanel(BorderLayout()).apply {
            add(JLabel("API端点："), BorderLayout.WEST)
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

    override fun getRowCount(): Int = rows.size
    override fun getColumnCount(): Int = 4

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? = when (columnIndex) {
        COL_SELECT -> rows[rowIndex].selected
        COL_METHOD -> rows[rowIndex].endpoint.httpMetadata?.method?.name
            ?: rows[rowIndex].endpoint.metadata.protocol
        COL_PATH -> rows[rowIndex].endpoint.path
        COL_NAME -> rows[rowIndex].endpoint.name ?: ""
        else -> null
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        if (columnIndex == COL_SELECT) {
            rows[rowIndex].selected = aValue as Boolean
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
        rows.forEach { it.selected = true }
        fireTableDataChanged()
    }

    fun deselectAll() {
        rows.forEach { it.selected = false }
        fireTableDataChanged()
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
                c.foreground = getMethodColor(value)
            }
        }
        return c
    }

    private fun getMethodColor(method: String): Color = when (method) {
        "GET" -> Color(0x61affe)
        "POST" -> Color(0x49cc90)
        "PUT" -> Color(0xfca130)
        "DELETE" -> Color(0xf93e3e)
        "PATCH" -> Color(0x50e3c2)
        "HEAD" -> Color(0x9012fe)
        "OPTIONS" -> Color(0x0d5aa7)
        else -> Color(0x999999)
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
