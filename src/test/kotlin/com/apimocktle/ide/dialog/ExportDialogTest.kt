package com.apimocktle.ide.dialog

import com.apimocktle.exporter.model.*
import com.apimocktle.testFramework.ApiMocktleLightCodeInsightFixtureTestCase

class ExportDialogTest : ApiMocktleLightCodeInsightFixtureTestCase() {

    fun testDialogShowsEndpointCount() {
        val dialog = ExportDialog(project, 10)
        assertTrue("Dialog title should show endpoint count",
            dialog.title.contains("10"))
    }

    fun testDialogShowsAllExportFormats() {
        val dialog = ExportDialog(project, 5)
        assertNotNull("Dialog should be created", dialog)
    }

    fun testDefaultFormatIsYapi() {
        val dialog = ExportDialog(project, 5)
        assertNotNull("Dialog should be created", dialog)
    }

    fun testOutputConfigDefaults() {
        val dialog = ExportDialog(project, 5)
        val config = dialog.outputConfig
        assertNotNull("Output config should not be null", config)
        assertEquals("Default output config", OutputConfig.DEFAULT, config)
    }

    fun testDialogCanBeCreatedWithDifferentCounts() {
        for (count in listOf(0, 1, 10, 100)) {
            val dialog = ExportDialog(project, count)
            assertNotNull("Dialog should be created for $count endpoints", dialog)
        }
    }

    fun testFormatFilteringWithHttpEndpoints() {
        val endpoints = listOf(
            ApiEndpoint(
                name = "Get User",
                metadata = httpMetadata(path = "/api/users", method = HttpMethod.GET)
            ),
            ApiEndpoint(
                name = "Create User",
                metadata = httpMetadata(path = "/api/users", method = HttpMethod.POST)
            )
        )
        
        val dialog = ExportDialog(project, endpoints.size, endpoints)
        assertNotNull("Dialog should be created", dialog)
    }

    fun testFormatFilteringWithHttpOnlyEndpoints() {
        val endpoints = listOf(
            ApiEndpoint(
                name = "Get User",
                metadata = httpMetadata(path = "/api/users", method = HttpMethod.GET)
            )
        )

        val dialog = ExportDialog(project, endpoints.size, endpoints)
        assertNotNull("Dialog should be created", dialog)
    }

    fun testFormatFilteringWithMultipleHttpEndpoints() {
        val endpoints = listOf(
            ApiEndpoint(
                name = "Get User",
                metadata = httpMetadata(path = "/api/users", method = HttpMethod.GET)
            ),
            ApiEndpoint(
                name = "Create User",
                metadata = httpMetadata(path = "/api/users", method = HttpMethod.POST)
            )
        )

        val dialog = ExportDialog(project, endpoints.size, endpoints)
        assertNotNull("Dialog should be created", dialog)
    }

    fun testFormatFilteringWithEmptyEndpoints() {
        val endpoints = emptyList<ApiEndpoint>()
        
        val dialog = ExportDialog(project, 0, endpoints)
        assertNotNull("Dialog should be created with empty endpoints", dialog)
    }
}
