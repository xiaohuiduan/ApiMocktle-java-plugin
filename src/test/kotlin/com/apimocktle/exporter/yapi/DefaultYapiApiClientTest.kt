package com.apimocktle.exporter.yapi

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.apimocktle.exporter.yapi.model.YapiApiDoc
import com.apimocktle.exporter.yapi.model.YapiHeader
import com.apimocktle.exporter.yapi.model.YapiQuery
import com.apimocktle.http.HttpClient
import com.apimocktle.http.HttpRequest
import com.apimocktle.http.HttpResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

/**
 * Unit tests for [DefaultYapiApiClient].
 *
 * All HTTP calls are intercepted via a mock [HttpClient] so no real server is needed.
 * Each test configures the mock to return a specific JSON response and then asserts
 * the client's behaviour (caching, deduplication, error propagation, etc.).
 */
class DefaultYapiApiClientTest {

    private lateinit var httpClient: HttpClient
    private lateinit var client: DefaultYapiApiClient

    private val serverUrl = "http://yapi.example.com"
    private val token = "test-token-abc"
    private val projectId = "42"

    @Before
    fun setUp() {
        httpClient = mock()
        client = DefaultYapiApiClient(serverUrl, token, projectId, httpClient)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun mockResponse(body: String, code: Int = 200): HttpResponse =
        HttpResponse(code = code, body = body)

    private fun successJson(data: Any? = null): String {
        val obj = JsonObject().apply {
            addProperty("errcode", 0)
            addProperty("errmsg", "成功！")
            if (data is JsonObject) add("data", data)
            else if (data is JsonArray) add("data", data)
        }
        return obj.toString()
    }

    private fun errorJson(errcode: Int = 40011, errmsg: String = "error"): String =
        """{"errcode":$errcode,"errmsg":"$errmsg"}"""

    private fun projectDataJson(id: String = "42"): JsonObject =
        JsonObject().apply { addProperty("_id", id) }

    private fun cartJson(id: String, name: String): JsonObject =
        JsonObject().apply {
            addProperty("_id", id)
            addProperty("name", name)
        }

    private fun apiJson(id: String, path: String, method: String): JsonObject =
        JsonObject().apply {
            addProperty("_id", id)
            addProperty("path", path)
            addProperty("method", method)
        }

    private fun apiListJson(vararg apis: JsonObject): String {
        val list = JsonArray().apply { apis.forEach { add(it) } }
        val data = JsonObject().apply { add("list", list) }
        return successJson(data)
    }

    // -------------------------------------------------------------------------
    // Helpers for fork-style responses (code/message instead of errcode/errmsg)
    // -------------------------------------------------------------------------

    /** Builds a fork-style success JSON: {"code":0,"message":"成功！",...} */
    private fun forkSuccessJson(data: Any? = null): String {
        val obj = JsonObject().apply {
            addProperty("code", 0)
            addProperty("message", "成功！")
            if (data is JsonObject) add("data", data)
            else if (data is JsonArray) add("data", data)
        }
        return obj.toString()
    }

    private fun forkErrorJson(code: Int = 401, message: String = "token无效"): String =
        """{"code":$code,"message":"$message"}"""

    // -------------------------------------------------------------------------
    // listProjects
    // -------------------------------------------------------------------------

    @Test
    fun `listProjects returns failure when serverUrl is blank`() = runBlocking {
        val c = DefaultYapiApiClient("", token, projectId, httpClient)
        val result = c.listProjects()
        assertFalse(result.isSuccess)
        assertTrue(result.errorMessage()!!.contains("YAPI服务器URL未配置"))
    }

    @Test
    fun `listProjects returns failure when token is blank`() = runBlocking {
        val c = DefaultYapiApiClient(serverUrl, "", projectId, httpClient)
        val result = c.listProjects()
        assertFalse(result.isSuccess)
        assertTrue(result.errorMessage()!!.contains("令牌为空"))
    }

    @Test
    fun `listProjects returns parsed projects`() = runBlocking {
        val projArray = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("_id", "1")
                addProperty("name", "Project A")
                addProperty("desc", "desc A")
            })
        }
        whenever(httpClient.execute(argThat { url.contains("/api/project/list") }))
            .thenReturn(mockResponse(successJson(projArray)))

        val result = client.listProjects()
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()!!.size)
        assertEquals("Project A", result.getOrNull()!![0].name)
    }

    @Test
    fun `listProjects returns failure when server returns error`() = runBlocking {
        whenever(httpClient.execute(any())).thenReturn(mockResponse(errorJson(), code = 500))
        val result = client.listProjects()
        assertFalse(result.isSuccess)
    }

    // -------------------------------------------------------------------------
    // Fork-format response handling (code/message instead of errcode/errmsg)
    // -------------------------------------------------------------------------

    @Test
    fun `listProjects handles fork-format responses`() = runBlocking {
        val projArray = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("_id", "1")
                addProperty("name", "Fork Project")
                addProperty("desc", "desc")
            })
        }
        whenever(httpClient.execute(argThat { url.contains("/api/project/list") }))
            .thenReturn(mockResponse(forkSuccessJson(projArray)))

        val result = client.listProjects()
        assertTrue(result.isSuccess)
        assertEquals("Fork Project", result.getOrNull()!![0].name)
    }

    @Test
    fun `listCarts works with fork-format responses`() = runBlocking {
        val cartsArray = JsonArray().apply { add(cartJson("30", "Fork Cart")) }
        whenever(httpClient.execute(argThat { url.contains("/api/interface/getCatMenu") }))
            .thenReturn(mockResponse(forkSuccessJson(cartsArray)))

        val result = client.listCarts()
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()!!.size)
        assertEquals("Fork Cart", result.getOrNull()!![0].name)
    }

    @Test
    fun `uploadApi succeeds with fork-format responses`() = runBlocking {
        val emptyList = JsonObject().apply { add("list", JsonArray()) }
        whenever(httpClient.execute(argThat { url.contains("/api/interface/list_cat") }))
            .thenReturn(mockResponse(forkSuccessJson(emptyList)))

        whenever(httpClient.execute(argThat { url.contains("/api/interface/save") }))
            .thenReturn(mockResponse(forkSuccessJson(JsonObject())))

        val result = client.uploadApi(testDoc(), "cat1")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `uploadApi fails when fork server returns error code`() = runBlocking {
        val emptyList = JsonObject().apply { add("list", JsonArray()) }
        whenever(httpClient.execute(argThat { url.contains("/api/interface/list_cat") }))
            .thenReturn(mockResponse(forkSuccessJson(emptyList)))

        whenever(httpClient.execute(argThat { url.contains("/api/interface/save") }))
            .thenReturn(mockResponse(forkErrorJson(500, "save failed")))

        val result = client.uploadApi(testDoc(), "cat1")
        assertFalse(result.isSuccess)
        assertEquals("save failed", result.errorMessage())
    }

    // -------------------------------------------------------------------------
    // listCarts
    // -------------------------------------------------------------------------

    @Test
    fun `listCarts returns parsed carts`() = runBlocking {
        val cartsArray = JsonArray().apply {
            add(cartJson("10", "Cart A"))
            add(cartJson("20", "Cart B"))
        }
        whenever(httpClient.execute(argThat { url.contains("/api/interface/getCatMenu") }))
            .thenReturn(mockResponse(successJson(cartsArray)))

        val result = client.listCarts()
        assertTrue(result.isSuccess)
        val carts = result.getOrNull()!!
        assertEquals(2, carts.size)
        assertEquals("Cart A", carts[0].name)
        assertEquals("10", carts[0].id)
        assertEquals("Cart B", carts[1].name)
    }

    // -------------------------------------------------------------------------
    // createCart
    // -------------------------------------------------------------------------

    @Test
    fun `createCart returns created cart`() = runBlocking {
        val data = JsonObject().apply {
            addProperty("_id", "99")
            addProperty("name", "New Cart")
        }
        whenever(httpClient.execute(argThat { url.contains("/api/interface/add_cat") }))
            .thenReturn(mockResponse(successJson(data)))

        val result = client.createCart("New Cart", "desc")
        assertTrue(result.isSuccess)
        assertEquals("New Cart", result.getOrNull()!!.name)
        assertEquals("99", result.getOrNull()!!.id)
    }

    // -------------------------------------------------------------------------
    // findOrCreateCart
    // -------------------------------------------------------------------------

    @Test
    fun `findOrCreateCart returns existing cart id without creating`() = runBlocking {
        val cartsArray = JsonArray().apply { add(cartJson("55", "Existing")) }
        whenever(httpClient.execute(argThat { url.contains("/api/interface/getCatMenu") }))
            .thenReturn(mockResponse(successJson(cartsArray)))

        val result = client.findOrCreateCart("Existing")
        assertTrue(result.isSuccess)
        assertEquals("55", result.getOrNull())
        // add_cat should NOT be called
        verify(httpClient, never()).execute(argThat { url.contains("/api/interface/add_cat") })
        Unit
    }

    @Test
    fun `findOrCreateCart creates cart when not found`() = runBlocking {
        // listCarts returns empty
        whenever(httpClient.execute(argThat { url.contains("/api/interface/getCatMenu") }))
            .thenReturn(mockResponse(successJson(JsonArray())))

        val newCartData = JsonObject().apply {
            addProperty("_id", "88")
            addProperty("name", "New")
        }
        whenever(httpClient.execute(argThat { url.contains("/api/interface/add_cat") }))
            .thenReturn(mockResponse(successJson(newCartData)))

        val result = client.findOrCreateCart("New")
        assertTrue(result.isSuccess)
        assertEquals("88", result.getOrNull())
    }

    // -------------------------------------------------------------------------
    // listApis
    // -------------------------------------------------------------------------

    @Test
    fun `listApis returns api array`() = runBlocking {
        val body = apiListJson(apiJson("1", "/api/users", "GET"))
        whenever(httpClient.execute(argThat { url.contains("/api/interface/list_cat") }))
            .thenReturn(mockResponse(body))

        val result = client.listApis("cat1")
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()!!.size())
    }

    @Test
    fun `listApis retries with larger limit when response hits limit`() = runBlocking {
        // First call returns exactly 1000 items (hits limit)
        val fullPage = JsonArray().apply { repeat(1000) { add(apiJson(it.toString(), "/p/$it", "GET")) } }
        val fullData = JsonObject().apply { add("list", fullPage) }
        whenever(httpClient.execute(argThat { url.contains("limit=1000") }))
            .thenReturn(mockResponse(successJson(fullData)))

        // Second call (with bumped limit) returns fewer items
        val partialPage = JsonArray().apply { add(apiJson("x", "/p/x", "GET")) }
        val partialData = JsonObject().apply { add("list", partialPage) }
        whenever(httpClient.execute(argThat { !url.contains("limit=1000") && url.contains("limit=") }))
            .thenReturn(mockResponse(successJson(partialData)))

        val result = client.listApis("cat1")
        assertTrue(result.isSuccess)
        // Should have retried and returned the partial page
        assertEquals(1, result.getOrNull()!!.size())
    }

    // -------------------------------------------------------------------------
    // findExistingApi
    // -------------------------------------------------------------------------

    @Test
    fun `findExistingApi returns id when match found`() = runBlocking {
        val body = apiListJson(
            apiJson("id-1", "/api/users", "GET"),
            apiJson("id-2", "/api/users", "POST")
        )
        whenever(httpClient.execute(argThat { url.contains("/api/interface/list_cat") }))
            .thenReturn(mockResponse(body))

        val id = client.findExistingApi("cat1", "/api/users", "GET")
        assertEquals("id-1", id)
    }

    @Test
    fun `findExistingApi is case-insensitive on method`() = runBlocking {
        val body = apiListJson(apiJson("id-1", "/api/users", "get"))
        whenever(httpClient.execute(argThat { url.contains("/api/interface/list_cat") }))
            .thenReturn(mockResponse(body))

        val id = client.findExistingApi("cat1", "/api/users", "GET")
        assertEquals("id-1", id)
    }

    @Test
    fun `findExistingApi returns null when no match`() = runBlocking {
        val body = apiListJson(apiJson("id-1", "/api/other", "GET"))
        whenever(httpClient.execute(argThat { url.contains("/api/interface/list_cat") }))
            .thenReturn(mockResponse(body))

        val id = client.findExistingApi("cat1", "/api/users", "GET")
        assertNull(id)
    }

    // -------------------------------------------------------------------------
    // uploadApi
    // -------------------------------------------------------------------------

    @Test
    fun `uploadApi returns success in mock mode (blank serverUrl)`() = runBlocking {
        val c = DefaultYapiApiClient("", token, projectId, httpClient)
        val result = c.uploadApi(testDoc(), "cat1")
        assertTrue(result.isSuccess)
        verifyNoInteractions(httpClient)
    }

    @Test
    fun `uploadApi creates new api when no duplicate exists`() = runBlocking {
        // listApis returns empty — no duplicate
        val emptyList = JsonObject().apply { add("list", JsonArray()) }
        whenever(httpClient.execute(argThat { url.contains("/api/interface/list_cat") }))
            .thenReturn(mockResponse(successJson(emptyList)))

        whenever(httpClient.execute(argThat { url.contains("/api/interface/save") }))
            .thenReturn(mockResponse(successJson(JsonObject())))

        val result = client.uploadApi(testDoc("/api/users", "GET"), "cat1")
        assertTrue(result.isSuccess)

        // save should be called once, without an "id" field (new insert, not update)
        val captor = argumentCaptor<HttpRequest>()
        verify(httpClient, atLeastOnce()).execute(captor.capture())
        val saveCall = captor.allValues.first { it.url.contains("/api/interface/save") }
        assertFalse("Should not contain existing id", saveCall.body?.contains("\"id\":") == true)
    }

    @Test
    fun `uploadApi updates existing api when duplicate found`() = runBlocking {
        // listApis returns one matching api
        val body = apiListJson(apiJson("existing-id", "/api/users", "GET"))
        whenever(httpClient.execute(argThat { url.contains("/api/interface/list_cat") }))
            .thenReturn(mockResponse(body))

        whenever(httpClient.execute(argThat { url.contains("/api/interface/save") }))
            .thenReturn(mockResponse(successJson(JsonObject())))

        val result = client.uploadApi(testDoc("/api/users", "GET"), "cat1")
        assertTrue(result.isSuccess)

        // save body should contain the existing id
        val captor = argumentCaptor<HttpRequest>()
        verify(httpClient, atLeastOnce()).execute(captor.capture())
        val saveCall = captor.allValues.first { it.url.contains("/api/interface/save") }
        assertTrue("Should contain existing id", saveCall.body?.contains("existing-id") == true)
    }

    @Test
    fun `uploadApi returns failure when save endpoint returns error`() = runBlocking {
        whenever(httpClient.execute(argThat { url.contains("/api/interface/list_cat") }))
            .thenReturn(mockResponse(apiListJson()))

        whenever(httpClient.execute(argThat { url.contains("/api/interface/save") }))
            .thenReturn(mockResponse(errorJson(500, "save failed")))

        val result = client.uploadApi(testDoc(), "cat1")
        assertFalse(result.isSuccess)
        assertEquals("save failed", result.errorMessage())
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun testDoc(path: String = "/api/test", method: String = "GET") = YapiApiDoc(
        title = "Test API",
        path = path,
        method = method,
        desc = "desc",
        reqHeaders = listOf(YapiHeader("Content-Type", "application/json")),
        reqQuery = listOf(YapiQuery("id", "1"))
    )
}
