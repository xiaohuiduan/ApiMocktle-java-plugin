package com.apimocktle.exporter.model

import org.junit.Assert.*
import org.junit.Test

class ExportFormatTest {

    @Test
    fun testYapiDisplayName() {
        assertEquals("YAPI", ExportFormat.YAPI.displayName)
    }

    @Test
    fun testAllValues() {
        val values = ExportFormat.values()
        assertEquals(1, values.size)
        assertTrue(values.contains(ExportFormat.YAPI))
    }

    @Test
    fun testValueOf() {
        assertEquals(ExportFormat.YAPI, ExportFormat.valueOf("YAPI"))
    }
}
