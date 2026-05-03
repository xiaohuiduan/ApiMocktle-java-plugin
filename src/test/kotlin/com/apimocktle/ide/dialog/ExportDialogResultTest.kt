package com.apimocktle.ide.dialog

import com.apimocktle.exporter.model.*
import org.junit.Assert.*
import org.junit.Test

class ExportDialogResultTest {

    @Test
    fun testProperties() {
        val config = OutputConfig()
        val result = ExportDialogResult(ExportFormat.YAPI, config)
        assertEquals(ExportFormat.YAPI, result.format)
        assertSame(config, result.outputConfig)
        assertTrue(result.selectedEndpoints.isEmpty())
    }

    @Test
    fun testEquality() {
        val config = OutputConfig()
        val r1 = ExportDialogResult(ExportFormat.YAPI, config)
        val r2 = ExportDialogResult(ExportFormat.YAPI, config)
        assertEquals(r1, r2)
    }

    @Test
    fun testWithSelectedEndpoints() {
        val config = OutputConfig()
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(path = "/api/users", method = HttpMethod.GET)
        )
        val selection = EndpointSelection(endpoint)
        val result = ExportDialogResult(ExportFormat.YAPI, config, listOf(selection))

        assertEquals(1, result.selectedEndpoints.size)
        assertSame(endpoint, result.selectedEndpoints[0].endpoint)
    }

    @Test
    fun testEndpointSelectionProperties() {
        val endpoint = ApiEndpoint(
            name = "Create User",
            metadata = httpMetadata(path = "/api/users", method = HttpMethod.POST)
        )
        val selection = EndpointSelection(endpoint)
        assertSame(endpoint, selection.endpoint)
    }

    @Test
    fun testEndpointSelectionEquality() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(path = "/api/users", method = HttpMethod.GET)
        )
        val s1 = EndpointSelection(endpoint)
        val s2 = EndpointSelection(endpoint)
        assertEquals(s1, s2)
    }
}
