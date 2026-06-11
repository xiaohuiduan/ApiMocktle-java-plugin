package com.apimocktle.exporter.yapi

import com.apimocktle.settings.SettingBinder
import com.apimocktle.settings.Settings
import com.apimocktle.settings.update
import com.apimocktle.testFramework.ConstantSettingBinder
import com.apimocktle.testFramework.ApiMocktleLightCodeInsightFixtureTestCase
import com.apimocktle.testFramework.wrap
import kotlinx.coroutines.runBlocking

class YapiSettingsHelperTest : ApiMocktleLightCodeInsightFixtureTestCase() {

    private lateinit var helper: DefaultYapiSettingsHelper
    private lateinit var testSettingBinder: ConstantSettingBinder

    override fun setUp() {
        super.setUp()
        testSettingBinder = ConstantSettingBinder()
        val wrappedProject = wrap(project) {
            replaceService(SettingBinder::class, testSettingBinder)
        }
        helper = DefaultYapiSettingsHelper(wrappedProject)
    }

    @org.junit.Test
    fun `test resolveServerUrl returns normalized configured server`() {
        testSettingBinder.update { yapiServer = " http://localhost:3000/ " }
        val serverUrl = runBlocking { helper.resolveServerUrl() }
        assertEquals("http://localhost:3000", serverUrl)
    }

    @org.junit.Test
    fun `test resolveServerUrl in dumb mode returns null when server is missing`() {
        testSettingBinder.save(Settings())
        val serverUrl = runBlocking { helper.resolveServerUrl(dumb = true) }
        assertNull(serverUrl)
    }

    @org.junit.Test
    fun `test resolveServerUrl returns default when not configured and not dumb`() {
        testSettingBinder.save(Settings())
        val serverUrl = runBlocking { helper.resolveServerUrl(dumb = false) }
        // When not configured and not in dumb mode, returns the default URL
        assertNotNull(serverUrl)
    }

    @org.junit.Test
    fun `test resolvePersonalToken returns configured token`() {
        testSettingBinder.update { yapiPersonalToken = "test-token-value" }
        val token = runBlocking { helper.resolvePersonalToken() }
        assertEquals("test-token-value", token)
    }
}
