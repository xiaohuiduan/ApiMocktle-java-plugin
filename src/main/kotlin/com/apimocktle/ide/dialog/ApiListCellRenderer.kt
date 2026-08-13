package com.apimocktle.ide.dialog

import com.apimocktle.exporter.model.ApiEndpoint
import com.apimocktle.exporter.model.httpMetadata
import com.apimocktle.exporter.model.path
import com.apimocktle.ide.ui.HttpMethodColors
import java.awt.Color
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer

/**
 * List cell renderer for displaying API endpoints with color-coded HTTP methods.
 *
 * Renders each endpoint as "METHOD path - name" with the method name colored
 * according to standard HTTP method conventions (see [HttpMethodColors]).
 *
 * @see ApiEndpoint for the data model
 */
class ApiListCellRenderer : JLabel(), ListCellRenderer<ApiEndpoint> {

    init {
        isOpaque = true
    }

    override fun getListCellRendererComponent(
        list: JList<out ApiEndpoint>?,
        value: ApiEndpoint?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        if (value == null) {
            text = ""
            return this
        }

        val method = (value.httpMetadata?.method?.name ?: value.metadata.protocol).padEnd(6)
        val path = value.path
        val name = value.name?.let { " - $it" } ?: ""

        text = "$method $path$name"
        foreground = if (isSelected) {
            list?.selectionForeground ?: Color.WHITE
        } else {
            val httpMeta = value.httpMetadata
            if (httpMeta != null) HttpMethodColors.colorFor(httpMeta.method) else HttpMethodColors.UNKNOWN
        }
        background = if (isSelected) {
            list?.selectionBackground ?: Color.BLUE
        } else {
            list?.background ?: Color.WHITE
        }

        return this
    }
}
