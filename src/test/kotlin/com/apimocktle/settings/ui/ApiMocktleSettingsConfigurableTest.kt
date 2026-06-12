package com.apimocktle.settings.ui

import com.apimocktle.testFramework.ApiMocktleLightCodeInsightFixtureTestCase

class ApiMocktleSettingsConfigurableTest : ApiMocktleLightCodeInsightFixtureTestCase() {

    private lateinit var configurable: ApiMocktleSettingsConfigurable

    override fun setUp() {
        super.setUp()
        configurable = ApiMocktleSettingsConfigurable(project)
    }

    fun testConfigurableExists() {
        assertNotNull("ApiMocktleSettingsConfigurable should be created", configurable)
    }

    fun testDisplayName() {
        assertEquals("Display name should be 'ApiMocktle'", "ApiMocktle", configurable.displayName)
    }

    fun testCreateComponentDoesNotThrow() {
        try {
            val component = configurable.createComponent()
            assertNotNull("Component should be created", component)
        } catch (e: Exception) {
            fail("createComponent should not throw: ${e.message}")
        }
    }

    fun testResetAndApplyCycle() {
        configurable.createComponent()
        configurable.reset()
        configurable.apply()
    }

    fun testResetDoesNotThrow() {
        configurable.createComponent()
        try { configurable.reset() } catch (e: Exception) { fail("reset should not throw: ${e.message}") }
    }

    fun testApplyDoesNotThrow() {
        configurable.createComponent()
        try { configurable.apply() } catch (e: Exception) { fail("apply should not throw: ${e.message}") }
    }

    fun testDisposeUIResourcesDoesNotThrow() {
        configurable.createComponent()
        try { configurable.disposeUIResources() } catch (e: Exception) { fail("disposeUIResources should not throw: ${e.message}") }
    }
}
