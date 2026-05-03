package com.apimocktle.settings.ui

import com.apimocktle.testFramework.EasyApiLightCodeInsightFixtureTestCase

class EasyApiSettingsConfigurableTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var configurable: EasyApiSettingsConfigurable

    override fun setUp() {
        super.setUp()
        configurable = EasyApiSettingsConfigurable(project)
    }

    fun testConfigurableExists() {
        assertNotNull("EasyApiSettingsConfigurable should be created", configurable)
    }

    fun testDisplayName() {
        assertEquals("Display name should be 'EasyApi'", "EasyApi", configurable.displayName)
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
        try {
            configurable.reset()
        } catch (e: Exception) {
            fail("reset should not throw: ${e.message}")
        }
    }

    fun testApplyDoesNotThrow() {
        configurable.createComponent()
        try {
            configurable.apply()
        } catch (e: Exception) {
            fail("apply should not throw: ${e.message}")
        }
    }

    fun testDisposeUIResourcesDoesNotThrow() {
        configurable.createComponent()
        try {
            configurable.disposeUIResources()
        } catch (e: Exception) {
            fail("disposeUIResources should not throw: ${e.message}")
        }
    }

    fun testSelectTabConstant() {
        assertEquals("TAB_GENERAL should be '通用'", "通用", EasyApiSettingsConfigurable.TAB_GENERAL)
        assertEquals("TAB_HTTP should be 'HTTP'", "HTTP", EasyApiSettingsConfigurable.TAB_HTTP)
        assertEquals("TAB_INTELLIGENT should be '智能'", "智能", EasyApiSettingsConfigurable.TAB_INTELLIGENT)
        assertEquals("TAB_EXTENSIONS should be '扩展'", "扩展", EasyApiSettingsConfigurable.TAB_EXTENSIONS)
        assertEquals("TAB_REMOTE should be '远程'", "远程", EasyApiSettingsConfigurable.TAB_REMOTE)
        assertEquals("TAB_BUILT_IN should be '内置'", "内置", EasyApiSettingsConfigurable.TAB_BUILT_IN)
        assertEquals("TAB_OTHER should be '其他'", "其他", EasyApiSettingsConfigurable.TAB_OTHER)
        assertEquals("TAB_GRPC should be 'gRPC'", "gRPC", EasyApiSettingsConfigurable.TAB_GRPC)
        assertEquals("TAB_ENVIRONMENT should be '环境'", "环境", EasyApiSettingsConfigurable.TAB_ENVIRONMENT)
    }
}
