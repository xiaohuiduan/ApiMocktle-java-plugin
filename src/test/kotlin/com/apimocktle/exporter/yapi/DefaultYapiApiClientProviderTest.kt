package com.apimocktle.exporter.yapi

import com.apimocktle.http.HttpClient
import com.apimocktle.http.HttpResponse
import com.apimocktle.testFramework.ApiMocktleLightCodeInsightFixtureTestCase
import com.apimocktle.testFramework.wrap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.mockito.kotlin.*

/**
 * Unit tests for [DefaultYapiApiClientProvider].
 *
 * Verifies that the provider correctly initializes by resolving the server URL
 * and personal token, and creates clients with the given project ID.
 *
 * Extends [ApiMocktleLightCodeInsightFixtureTestCase] (JUnit 3 style) — tests are
 * discovered by the `test` method name prefix, not by annotation.
 */
class DefaultYapiApiClientProviderTest : ApiMocktleLightCodeInsightFixtureTestCase() {

    private lateinit var settingsHelper: YapiSettingsHelper

    override fun setUp() {
        super.setUp()
        settingsHelper = mock()
    }

    private fun buildProvider(serverUrl: String? = "http://yapi.example.com"): DefaultYapiApiClientProvider {
        runBlocking {
            whenever(settingsHelper.resolveServerUrl(any())).thenReturn(serverUrl)
            whenever(settingsHelper.resolvePersonalToken()).thenReturn("test-token")
        }
        val wrappedProject = wrap(project) {
            replaceService(YapiSettingsHelper::class, settingsHelper)
        }
        return DefaultYapiApiClientProvider(wrappedProject)
    }

    fun testInitThrowsWhenServerUrlNotConfigured() {
        val provider = buildProvider(serverUrl = null)
        try {
            runBlocking { provider.init() }
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("server URL", ignoreCase = true))
        }
    }

    fun testInitSucceedsAndExposesServerUrl() = runBlocking {
        val provider = buildProvider("http://yapi.example.com")
        val result = provider.init()
        assertTrue(result)
        assertEquals("http://yapi.example.com", provider.serverUrl)
    }

    fun testInitReturnsFalseWhenTokenCannotBeResolved() = runBlocking {
        val provider = buildProvider()
        whenever(settingsHelper.resolvePersonalToken()).thenReturn(null)
        val result = provider.init()
        assertFalse(result)
    }

    fun testGetClientReturnsClientWithGivenProjectId() = runBlocking {
        val provider = buildProvider()
        provider.init()

        val client = provider.getClient("project-123")
        assertNotNull(client)
    }
}
