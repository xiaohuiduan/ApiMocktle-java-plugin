package com.apimocktle.dashboard

import com.apimocktle.exporter.model.ApiEndpoint
import com.apimocktle.exporter.model.HttpMetadata
import com.apimocktle.exporter.model.HttpMethod
import com.apimocktle.ide.ui.HttpMethodColors
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer

/**
 * Custom tree cell renderer for API endpoint nodes in the dashboard tree.
 *
 * - Endpoint nodes: colored HTTP method badge + endpoint name + gray path
 * - Folder/class nodes: default IntelliJ tree rendering
 *
 * Badge keeps high contrast on both light/dark themes and selected rows.
 */
class ApiTreeCellRenderer : DefaultTreeCellRenderer() {

    private val badge = JLabel().apply {
        isOpaque = true
        font = font.deriveFont(Font.BOLD, font.size2D - 1f)
        border = BorderFactory.createEmptyBorder(1, 4, 1, 4)
    }
    private val nameLabel = JLabel()
    private val pathLabel = JLabel()
    private val cellPanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
        isOpaque = true
        add(badge)
        add(nameLabel)
        add(pathLabel)
    }

    override fun getTreeCellRendererComponent(
        tree: JTree?,
        value: Any?,
        sel: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): Component {
        val endpoint = when (value) {
            is DefaultMutableTreeNode -> value.userObject as? ApiEndpoint
            is ApiEndpoint -> value
            else -> null
        }

        if (endpoint != null && tree != null) {
            val method = when (val meta = endpoint.metadata) {
                is HttpMetadata -> meta.method
                else -> null
            }
            val path = when (val meta = endpoint.metadata) {
                is HttpMetadata -> meta.path
                else -> ""
            }
            val methodName = method?.name ?: endpoint.metadata.protocol

            badge.text = methodName
            badge.background = method?.let { HttpMethodColors.colorFor(it) } ?: HttpMethodColors.UNKNOWN
            badge.foreground = Color.WHITE

            nameLabel.text = endpoint.name ?: path
            nameLabel.foreground = if (sel) UIUtil.getTreeSelectionForeground() else tree.foreground
            pathLabel.text = if (endpoint.name != null && endpoint.name != path) "  $path" else ""
            pathLabel.foreground = if (sel) UIUtil.getTreeSelectionForeground() else UIUtil.getContextHelpForeground()

            cellPanel.background = if (sel) UIUtil.getTreeSelectionBackground() else tree.background
            cellPanel.border = null
            return cellPanel
        }

        return super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
    }
}