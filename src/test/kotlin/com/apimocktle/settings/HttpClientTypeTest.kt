package com.apimocktle.settings

import org.junit.Assert.*
import org.junit.Test

class HttpClientTypeTest {

    @Test
    fun testValues() {
        val values = HttpClientType.values()
        assertEquals(1, values.size)
    }

    @Test
    fun testValue() {
        assertEquals("Apache", HttpClientType.APACHE.value)
    }

    @Test
    fun testName() {
        assertEquals("APACHE", HttpClientType.APACHE.name)
    }

    @Test
    fun testValueOf() {
        assertEquals(HttpClientType.APACHE, HttpClientType.valueOf("APACHE"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testValueOf_invalid() {
        HttpClientType.valueOf("INVALID")
    }
}
