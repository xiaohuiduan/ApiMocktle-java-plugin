package com.apimocktle.dashboard

import org.junit.Assert.*
import org.junit.Test

class RequestEditCacheTest {

    @Test
    fun testHttpRequestEditCacheDefaults() {
        val cache = HttpRequestEditCache()
        assertNull("Key should default to null", cache.key)
        assertNull("Name should default to null", cache.name)
        assertNull("Path should default to null", cache.path)
        assertNull("Method should default to null", cache.method)
        assertNull("Body should default to null", cache.body)
        assertTrue("Headers should default to empty", cache.headers.isEmpty())
        assertTrue("QueryParams should default to empty", cache.queryParams.isEmpty())
    }

    @Test
    fun testHttpRequestEditCacheWithValues() {
        val cache = HttpRequestEditCache(
            key = "test-key",
            name = "Get Users",
            path = "/api/users",
            method = "GET",
            host = "localhost:8080",
            headers = listOf(EditableKeyValue("Content-Type", "application/json")),
            body = "{}"
        )
        assertEquals("test-key", cache.key)
        assertEquals("Get Users", cache.name)
        assertEquals("/api/users", cache.path)
        assertEquals("GET", cache.method)
        assertEquals("localhost:8080", cache.host)
        assertEquals(1, cache.headers.size)
        assertEquals("{}", cache.body)
    }

    @Test
    fun testGrpcRequestEditCacheDefaults() {
        // gRPC module has been removed — test deleted
    }

    @Test
    fun testGrpcRequestEditCacheWithValues() {
        // gRPC module has been removed — test deleted
    }

    @Test
    fun testCacheKeyReturnsKey() {
        val cache = HttpRequestEditCache(key = "my-key")
        assertEquals("my-key", cache.cacheKey())
    }

    @Test
    fun testCacheKeyNullReturnsEmpty() {
        val cache = HttpRequestEditCache(key = null)
        assertEquals("", cache.cacheKey())
    }

    @Test
    fun testEditableKeyValue() {
        val kv = EditableKeyValue("name", "value", "description")
        assertEquals("name", kv.name)
        assertEquals("value", kv.value)
        assertEquals("description", kv.description)
    }

    @Test
    fun testEditableKeyValueDefaults() {
        val kv = EditableKeyValue("name")
        assertEquals("name", kv.name)
        assertNull(kv.value)
        assertNull(kv.description)
    }
}
