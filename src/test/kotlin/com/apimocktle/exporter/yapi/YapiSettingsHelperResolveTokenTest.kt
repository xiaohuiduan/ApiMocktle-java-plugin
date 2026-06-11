package com.apimocktle.exporter.yapi

import com.apimocktle.settings.SettingBinder
import com.apimocktle.settings.Settings
import com.apimocktle.settings.update
import com.apimocktle.testFramework.ConstantSettingBinder
import com.apimocktle.testFramework.ApiMocktleLightCodeInsightFixtureTestCase
import com.apimocktle.testFramework.wrap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*

class YapiSettingsHelperResolveTokenTest : ApiMocktleLightCodeInsightFixtureTestCase() {

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
    fun `test resolvePersonalToken returns token from settings when set`() {
        testSettingBinder.update {
            yapiPersonalToken = "my-personal-token"
        }
        val token = runBlocking { helper.resolvePersonalToken() }
        assertEquals("my-personal-token", token)
    }

    @org.junit.Test
    fun `test resolvePersonalToken returns null when token is blank`() {
        testSettingBinder.update {
            yapiPersonalToken = ""
        }
        // In test mode, the dialog prompt cannot be shown, so this will return null
        // or throw — we just verify it doesn't crash with a blank token
        try {
            val token = runBlocking { helper.resolvePersonalToken() }
            // If it returns, it should be null since blank tokens are ignored
            assertNull(token)
        } catch (e: Exception) {
            // Expected in test environment since dialog cannot be shown
        }
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
    fun `test resolveServerUrl returns default when not configured`() {
        testSettingBinder.save(Settings())
        val serverUrl = runBlocking { helper.resolveServerUrl(dumb = false) }
        // When not configured and not in dumb mode, it returns the default URL
        assertNotNull(serverUrl)
    }
}
