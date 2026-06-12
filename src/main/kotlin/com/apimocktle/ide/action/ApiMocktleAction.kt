package com.apimocktle.ide.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.apimocktle.ide.support.SelectedHelper
import com.apimocktle.ide.support.SelectionScope

/**
 * Base class for ApiMocktle actions with selection-based visibility.
 *
 * Actions extending this class are only visible when a valid selection
 * (class, method, or file) is available in the editor.
 *
 * @see SelectedHelper for selection resolution
 * @see SelectionScope for the selection model
 */
abstract class ApiMocktleAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val scope = resolveScope(e)
        val p: Presentation = e.presentation
        p.isEnabledAndVisible = scope != null
    }

    protected open fun resolveScope(e: AnActionEvent): SelectionScope? = SelectedHelper.resolveSelection(e)
}
