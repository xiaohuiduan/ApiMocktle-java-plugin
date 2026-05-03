package com.apimocktle.ide.action

import org.junit.Assert.*
import org.junit.Test

class FieldsToPropertiesActionTest {

    @Test
    fun testActionCreation() {
        val action = FieldsToPropertiesAction()
        assertNotNull("FieldsToPropertiesAction should be created", action)
    }

    @Test
    fun testActionIsAnAction() {
        val action = FieldsToPropertiesAction()
        assertTrue("Should be an AnAction", action is com.intellij.openapi.actionSystem.AnAction)
    }

    @Test
    fun testActionExtendsFieldFormatAction() {
        val action = FieldsToPropertiesAction()
        assertTrue("Should extend FieldFormatAction", action is FieldFormatAction)
    }
}
