package com.apimocktle.gap

import com.apimocktle.exporter.curl.CurlFormatter
import com.apimocktle.testFramework.ApiMocktleLightCodeInsightFixtureTestCase
import com.apimocktle.testFramework.TestConfigReader
import org.junit.Assert.*

class FormatterParityTest : ApiMocktleLightCodeInsightFixtureTestCase() {

    override fun createConfigReader() = TestConfigReader.empty(project)

    fun testCurlFormatterExists() {
        assertNotNull("CurlFormatter should exist", CurlFormatter)
    }
}
