package com.apimocktle.exporter.yapi

import org.junit.Assert.assertEquals
import org.junit.Test

class YapiUrlsTest {

    @Test
    fun `normalizeBaseUrl trims spaces and trailing slash`() {
        assertEquals("http://localhost:3000", YapiUrls.normalizeBaseUrl(" http://localhost:3000/ "))
    }

    @Test
    fun `cartUrl builds project home url from normalized base url`() {
        assertEquals(
            "http://localhost:3000/projects/12/home",
            YapiUrls.cartUrl(" http://localhost:3000/ ", "12", "34")
        )
    }
}
