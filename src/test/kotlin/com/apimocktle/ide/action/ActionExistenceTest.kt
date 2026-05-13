package com.apimocktle.ide.action

import org.junit.Assert.*
import org.junit.Test

class ActionExistenceTest {

    @Test
    fun testApiMocktleActionClassExists() {
        val clazz = Class.forName("com.apimocktle.ide.action.ApiMocktleAction")
        assertNotNull("ApiMocktleAction class should exist", clazz)
    }

    @Test
    fun testFieldsToJsonActionTitle() {
        val action = FieldsToJsonAction()
        assertNotNull("FieldsToJsonAction should be created", action)
    }

    @Test
    fun testFieldsToJson5ActionTitle() {
        val action = FieldsToJson5Action()
        assertNotNull("FieldsToJson5Action should be created", action)
    }

    @Test
    fun testFieldsToPropertiesActionTitle() {
        val action = FieldsToPropertiesAction()
        assertNotNull("FieldsToPropertiesAction should be created", action)
    }

    @Test
    fun testOpenApiDashboardActionExists() {
        val action = OpenApiDashboardAction()
        assertNotNull("OpenApiDashboardAction should be created", action)
    }

    @Test
    fun testScriptExecutorActionExists() {
        val action = ScriptExecutorAction()
        assertNotNull("ScriptExecutorAction should be created", action)
    }

    @Test
    fun testOpenScriptExecutorActionExists() {
        val action = OpenScriptExecutorAction()
        assertNotNull("OpenScriptExecutorAction should be created", action)
    }

    @Test
    fun testApiCallActionExists() {
        val action = ApiCallAction()
        assertNotNull("ApiCallAction should be created", action)
    }
}
