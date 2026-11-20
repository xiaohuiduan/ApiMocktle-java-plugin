package com.apimocktle.ide.dialog

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.apimocktle.exporter.yapi.UpdateDecision
import com.apimocktle.exporter.yapi.model.YapiApiDoc
import com.apimocktle.i18n.ApiMocktleBundle
import com.apimocktle.ide.ui.HttpMethodColors
import java.awt.Component
import javax.swing.*

class YapiUpdateConfirmationDialog(
    project: Project?,
    private val doc: YapiApiDoc,
    private val existingApiTitle: String?
) : DialogWrapper(project) {

    private val applyForAllCheckBox = JCheckBox(ApiMocktleBundle.message("dialog.update.applyForAll")).apply {
        toolTipText = ApiMocktleBundle.message("dialog.update.applyForAll.tooltip")
    }

    var shouldApplyForAll: Boolean = false
        private set

    var userDecision: UpdateDecision = UpdateDecision.Skip
        private set

    init {
        title = ApiMocktleBundle.message("dialog.update.title")
        setOKButtonText(ApiMocktleBundle.message("dialog.yes"))
        setCancelButtonText(ApiMocktleBundle.message("dialog.no"))
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = JBUI.Borders.empty(JBUI.scale(12))

        val questionLabel = JBLabel(ApiMocktleBundle.message("dialog.update.question")).apply {
            font = font.deriveFont(font.size2D + 2)
            setCopyable(true)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        panel.add(questionLabel)
        panel.add(Box.createVerticalStrut(JBUI.scale(12)))

        val method = doc.method.uppercase()
        val methodPathLabel = createMethodPathLabel(method, doc.path)
        methodPathLabel.alignmentX = Component.LEFT_ALIGNMENT
        panel.add(methodPathLabel)
        panel.add(Box.createVerticalStrut(JBUI.scale(8)))

        val apiTitle = existingApiTitle ?: doc.title ?: ApiMocktleBundle.message("dialog.update.unknownApi")
        val apiNameLabel = JBLabel(apiTitle).apply {
            font = font.deriveFont(font.size2D + 1)
            setCopyable(true)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        panel.add(apiNameLabel)
        panel.add(Box.createVerticalStrut(JBUI.scale(16)))

        applyForAllCheckBox.alignmentX = Component.LEFT_ALIGNMENT
        panel.add(applyForAllCheckBox)

        return panel
    }

    private fun createMethodPathLabel(method: String, path: String): JBLabel {
        val color = HttpMethodColors.colorForName(method)
        return JBLabel("[$method] $path").apply {
            foreground = color
            font = font.deriveFont(java.awt.Font.BOLD, font.size2D + 1)
            setCopyable(true)
        }
    }

    override fun getOKAction(): Action {
        return object : AbstractAction(ApiMocktleBundle.message("dialog.yes")) {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                shouldApplyForAll = applyForAllCheckBox.isSelected
                userDecision = if (shouldApplyForAll) {
                    UpdateDecision.ApplyAll(UpdateDecision.Update)
                } else {
                    UpdateDecision.Update
                }
                close(OK_EXIT_CODE)
            }
        }
    }

    override fun getCancelAction(): Action {
        return object : AbstractAction(ApiMocktleBundle.message("dialog.no")) {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                shouldApplyForAll = applyForAllCheckBox.isSelected
                userDecision = if (shouldApplyForAll) {
                    UpdateDecision.ApplyAll(UpdateDecision.Skip)
                } else {
                    UpdateDecision.Skip
                }
                close(CANCEL_EXIT_CODE)
            }
        }
    }

    override fun doCancelAction() {
        shouldApplyForAll = applyForAllCheckBox.isSelected
        userDecision = if (shouldApplyForAll) {
            UpdateDecision.ApplyAll(UpdateDecision.Skip)
        } else {
            UpdateDecision.Skip
        }
        super.doCancelAction()
    }

    companion object {
        fun show(
            project: Project?,
            doc: YapiApiDoc,
            existingApiTitle: String?
        ): UpdateDecision {
            val dialog = YapiUpdateConfirmationDialog(project, doc, existingApiTitle)
            dialog.show()
            return dialog.userDecision
        }
    }
}
