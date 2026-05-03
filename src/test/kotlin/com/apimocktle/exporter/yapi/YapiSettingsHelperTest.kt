package com.apimocktle.exporter.yapi

import com.apimocktle.settings.SettingBinder
import com.apimocktle.settings.Settings
import com.apimocktle.settings.update
import com.apimocktle.testFramework.ConstantSettingBinder
import com.apimocktle.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.apimocktle.testFramework.wrap
import kotlinx.coroutines.runBlocking

class YapiSettingsHelperTest : EasyApiLightCodeInsightFixtureTestCase() {

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
    fun `test resolveToken returns module token from settings when validator accepts it`() {
        testSettingBinder.update {
            yapiTokens = """
                module-a=token-a
                module-b=token-b
            """.trimIndent()
        }
        val token = runBlocking {
            helper.resolveToken("module-b") { it == "token-b" }
        }
        assertEquals("token-b", token)
    }

    @org.junit.Test
    fun `test resolveToken ignores comments and blank token entries`() {
        testSettingBinder.update {
            yapiTokens = """
                # comment
                module-a=token-a
                invalid-line
                module-b=
            """.trimIndent()
        }
        val token = runBlocking {
            helper.resolveToken("module-a") { it == "token-a" }
        }
        assertEquals("token-a", token)
    }
}
