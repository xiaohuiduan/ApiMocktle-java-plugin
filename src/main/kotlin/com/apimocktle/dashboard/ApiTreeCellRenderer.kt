package com.apimocktle.dashboard

import com.apimocktle.exporter.model.ApiEndpoint
import com.apimocktle.exporter.model.HttpMetadata
import com.apimocktle.exporter.model.HttpMethod
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
            val path = when (val meta = endpoint.metadata) {
                is HttpMetadata -> meta.path
                else -> ""
            }
            val methodName = when (val meta = endpoint.metadata) {
                is HttpMetadata -> meta.method.name
                else -> endpoint.metadata.protocol
            }

            badge.text = methodName
            badge.background = getMethodColor(endpoint)
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

    /**
     * Returns the color for an API endpoint based on its HTTP method.
     * Follows common API documentation conventions:
     * - GET: Blue, POST: Green, PUT: Orange, DELETE: Red
     * - PATCH: Cyan, HEAD: Purple, OPTIONS: Dark blue
     */
    private fun getMethodColor(endpoint: ApiEndpoint): Color {
        return when (val meta = endpoint.metadata) {
            is HttpMetadata -> when (meta.method) {
                HttpMethod.GET -> Color(0x61affe)
                HttpMethod.POST -> Color(0x49cc90)
                HttpMethod.PUT -> Color(0xfca130)
                HttpMethod.DELETE -> Color(0xf93e3e)
                HttpMethod.PATCH -> Color(0x50e3c2)
                HttpMethod.HEAD -> Color(0x9012fe)
                HttpMethod.OPTIONS -> Color(0x0d5aa7)
                HttpMethod.NO_METHOD -> Color(0x888888)
            }
            else -> Color(0x888888)
        }
    }
}