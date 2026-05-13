package com.apimocktle.ide.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.apimocktle.ide.support.SelectedHelper
import com.apimocktle.testFramework.ApiMocktleLightCodeInsightFixtureTestCase

class ApiMocktleActionTest : ApiMocktleLightCodeInsightFixtureTestCase() {

    private lateinit var userCtrl: PsiClass
    private lateinit var greetingMethod: PsiMethod

    override fun setUp() {
        super.setUp()
        loadFile("spring/RestController.java")
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("api/UserCtrl.java")

        userCtrl = findClass("com.itangcent.api.UserCtrl")!!
        greetingMethod = userCtrl.findMethodsByName("greeting", false).first()
    }

    fun testGetActionUpdateThreadReturnsBGT() {
        val action = TestableApiMocktleAction()
        assertEquals(
            "ApiMocktleAction should use BGT thread for updates",
            ActionUpdateThread.BGT,
            action.actionUpdateThread
        )
    }

    fun testUpdateEnabledWithClassInEditor() {
        val action = TestableApiMocktleAction()
        val presentation = Presentation()
        val event = createEvent(
            psiElement = userCtrl,
            psiFile = userCtrl.containingFile
        )

        action.update(event)

        assertTrue(
            "Action should be enabled and visible when a class is selected in editor",
            event.presentation.isEnabledAndVisible
        )
    }

    fun testUpdateEnabledWithMethodInEditor() {
        val action = TestableApiMocktleAction()
        val event = createEvent(
            psiElement = greetingMethod,
            psiFile = userCtrl.containingFile
        )

        action.update(event)

        assertTrue(
            "Action should be enabled and visible when a method is selected in editor",
            event.presentation.isEnabledAndVisible
        )
    }

    fun testUpdateEnabledWithClassInProjectTree() {
        val action = TestableApiMocktleAction()
        val event = createEvent(
            navigatables = arrayOf(userCtrl as Navigatable)
        )

        action.update(event)

        assertTrue(
            "Action should be enabled and visible when a class is selected in project tree",
            event.presentation.isEnabledAndVisible
        )
    }

    fun testUpdateEnabledWithFileContext() {
        val action = TestableApiMocktleAction()
        val event = createEvent(
            psiFile = userCtrl.containingFile
        )

        action.update(event)

        assertTrue(
            "Action should be enabled and visible when a file is available",
            event.presentation.isEnabledAndVisible
        )
    }

    fun testUpdateDisabledWithNoContext() {
        val action = TestableApiMocktleAction()
        val event = createEvent()

        action.update(event)

        assertFalse(
            "Action should be disabled when no selection context is available",
            event.presentation.isEnabledAndVisible
        )
    }

    fun testResolveScopeDelegatesToSelectedHelper() {
        val event = createEvent(
            psiElement = userCtrl,
            psiFile = userCtrl.containingFile
        )

        val actionScope = TestableApiMocktleAction().callResolveScope(event)
        val helperScope = SelectedHelper.resolveSelection(event)

        assertNotNull("Action resolveScope should return non-null for valid context", actionScope)
        assertNotNull("SelectedHelper should return non-null for valid context", helperScope)
        assertEquals(
            "Action resolveScope should match SelectedHelper result",
            helperScope!!.psiClass(),
            actionScope!!.psiClass()
        )
    }

    fun testResolveScopeReturnsNullForNoContext() {
        val event = createEvent()

        val scope = TestableApiMocktleAction().callResolveScope(event)

        assertNull("resolveScope should return null when no selection context", scope)
    }

    private fun createEvent(
        psiElement: PsiElement? = null,
        navigatables: Array<Navigatable>? = null,
        psiFile: com.intellij.psi.PsiFile? = null
    ): AnActionEvent {
        val data = mutableMapOf<String, Any?>()
        if (psiElement != null) data[CommonDataKeys.PSI_ELEMENT.name] = psiElement
        if (navigatables != null) data[CommonDataKeys.NAVIGATABLE_ARRAY.name] = navigatables
        if (psiFile != null) data[CommonDataKeys.PSI_FILE.name] = psiFile
        return AnActionEvent.createFromDataContext("test", Presentation(), MapDataContext(data))
    }

    private class MapDataContext(private val data: Map<String, Any?>) : com.intellij.openapi.actionSystem.DataContext {
        override fun getData(dataId: String): Any? = data[dataId]
    }

    private class TestableApiMocktleAction : ApiMocktleAction() {
        override fun actionPerformed(e: AnActionEvent) {}

        fun callResolveScope(e: AnActionEvent) = resolveScope(e)
    }
}
