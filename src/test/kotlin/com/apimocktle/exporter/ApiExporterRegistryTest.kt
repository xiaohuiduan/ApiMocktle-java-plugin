package com.apimocktle.exporter

import com.apimocktle.exporter.model.ExportFormat
import com.apimocktle.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.*

class ApiExporterRegistryTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var registry: ApiExporterRegistry

    override fun setUp() {
        super.setUp()
        registry = ApiExporterRegistry.getInstance(project)
    }

    fun testGetInstance() {
        assertNotNull(registry)
        assertSame(registry, ApiExporterRegistry.getInstance(project))
    }

    fun testGetYapiExporter() {
        val exporter = registry.getExporter(ExportFormat.YAPI)
        assertNotNull(exporter)
        assertTrue(exporter is com.apimocktle.exporter.yapi.YapiExporter)
    }

    fun testGetAllExporters() {
        val exporters = registry.getAllExporters()
        assertEquals(1, exporters.size)

        val exporterTypes = exporters.map { it::class.simpleName }.toSet()
        assertTrue(exporterTypes.contains("YapiExporter"))
    }

    fun testAllExportersAreUnique() {
        val exporters = registry.getAllExporters()
        val uniqueExporters = exporters.toSet()
        assertEquals(exporters.size, uniqueExporters.size)
    }
}
