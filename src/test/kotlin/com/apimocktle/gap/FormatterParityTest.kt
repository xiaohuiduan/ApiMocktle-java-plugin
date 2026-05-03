package com.apimocktle.gap

import com.apimocktle.exporter.curl.CurlFormatter
import com.apimocktle.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.apimocktle.testFramework.TestConfigReader
import org.junit.Assert.*

class FormatterParityTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun createConfigReader() = TestConfigReader.empty(project)

    fun testCurlFormatterExists() {
        assertNotNull("CurlFormatter should exist", CurlFormatter)
    }
}
