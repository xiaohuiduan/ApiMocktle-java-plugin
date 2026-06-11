package com.apimocktle.exporter.curl

import com.apimocktle.exporter.model.ApiEndpoint
import com.apimocktle.exporter.model.ApiHeader
import com.apimocktle.exporter.model.ApiParameter
import com.apimocktle.exporter.model.HttpMetadata
import com.apimocktle.exporter.model.HttpMethod
import com.apimocktle.exporter.model.ParameterBinding
import com.apimocktle.exporter.model.httpMetadata
import org.junit.Assert.*
import org.junit.Test

class CurlFormatterTest {

    @Test
    fun testFormatSimpleGet() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(
                path = "/api/users/1",
                method = HttpMethod.GET
            )
        )

        val result = CurlFormatter.format(endpoint, "http://localhost:8080")

        assertTrue(result.contains("curl"))
        assertTrue(result.contains("-X GET"))
        assertTrue(result.contains("http://localhost:8080/api/users/1"))
    }

    @Test
    fun testFormatPostWithJsonBody() {
        val endpoint = ApiEndpoint(
            name = "Create User",
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.POST,
                contentType = "application/json",
                parameters = listOf(
                    ApiParameter(
                        name = "name",
                        binding = ParameterBinding.Body,
                        example = "John"
                    ),
                    ApiParameter(
                        name = "age",
                        binding = ParameterBinding.Body,
                        example = "25"
                    )
                )
            )
        )

        val result = CurlFormatter.format(endpoint, "http://localhost:8080")

        assertTrue(result.contains("-X POST"))
        assertTrue(result.contains("Content-Type"))
        assertTrue(result.contains("-d"))
        assertTrue(result.contains("name"))
        assertTrue(result.contains("John"))
    }

    @Test
    fun testFormatWithQueryParams() {
        val endpoint = ApiEndpoint(
            name = "List Users",
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.GET,
                parameters = listOf(
                    ApiParameter(
                        name = "page",
                        binding = ParameterBinding.Query,
                        example = "1"
                    ),
                    ApiParameter(
                        name = "size",
                        binding = ParameterBinding.Query,
                        example = "10"
                    )
                )
            )
        )

        val result = CurlFormatter.format(endpoint, "http://localhost:8080")

        assertTrue(result.contains("page=1"))
        assertTrue(result.contains("size=10"))
    }

    @Test
    fun testFormatWithHeaders() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(
                path = "/api/users/1",
                method = HttpMethod.GET,
                headers = listOf(
                    ApiHeader("Authorization", "Bearer token123"),
                    ApiHeader("X-Request-Id", "abc123")
                )
            )
        )

        val result = CurlFormatter.format(endpoint, "http://localhost:8080")

        assertTrue(result.contains("-H 'Authorization: Bearer token123'"))
        assertTrue(result.contains("-H 'X-Request-Id: abc123'"))
    }

    @Test
    fun testFormatWithFormUrlencoded() {
        val endpoint = ApiEndpoint(
            name = "Login",
            metadata = httpMetadata(
                path = "/api/login",
                method = HttpMethod.POST,
                contentType = "application/x-www-form-urlencoded",
                parameters = listOf(
                    ApiParameter(
                        name = "username",
                        binding = ParameterBinding.Form,
                        example = "admin"
                    ),
                    ApiParameter(
                        name = "password",
                        binding = ParameterBinding.Form,
                        example = "secret"
                    )
                )
            )
        )

        val result = CurlFormatter.format(endpoint, "http://localhost:8080")

        assertTrue(result.contains("-X POST"))
        assertTrue(result.contains("--data-urlencode"))
        assertTrue(result.contains("username=admin"))
        assertTrue(result.contains("password=secret"))
    }

    @Test
    fun testFormatWithMultipartFormData() {
        val endpoint = ApiEndpoint(
            name = "Upload File",
            metadata = httpMetadata(
                path = "/api/upload",
                method = HttpMethod.POST,
                contentType = "multipart/form-data",
                parameters = listOf(
                    ApiParameter(
                        name = "file",
                        binding = ParameterBinding.Form,
                        example = "@/path/to/file"
                    ),
                    ApiParameter(
                        name = "description",
                        binding = ParameterBinding.Form,
                        example = "My file"
                    )
                )
            )
        )

        val result = CurlFormatter.format(endpoint, "http://localhost:8080")

        assertTrue(result.contains("-F"))
        assertTrue(result.contains("file=@/path/to/file"))
        assertTrue(result.contains("description=My file"))
    }

    @Test
    fun testEscapeShell() {
        val input = "it's a test"
        val result = CurlFormatter.escapeShell(input)
        assertEquals("it'\\''s a test", result)
    }

    @Test
    fun testFormatWithSpecialCharsInPath() {
        val endpoint = ApiEndpoint(
            name = "Search",
            metadata = httpMetadata(
                path = "/api/search/test's query",
                method = HttpMethod.GET
            )
        )

        val result = CurlFormatter.format(endpoint, "http://localhost:8080")

        assertTrue(result.contains("test'\\''s"))
    }

    @Test
    fun testFormatWithPathVariable() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.GET,
                parameters = listOf(
                    ApiParameter(
                        name = "id",
                        binding = ParameterBinding.Path,
                        example = "123"
                    )
                )
            )
        )

        val result = CurlFormatter.format(endpoint, "http://localhost:8080")

        assertTrue(result.contains("/api/users/{id}"))
    }

    @Test
    fun testFormatPutRequest() {
        val endpoint = ApiEndpoint(
            name = "Update User",
            metadata = httpMetadata(
                path = "/api/users/1",
                method = HttpMethod.PUT,
                contentType = "application/json",
                parameters = listOf(
                    ApiParameter(
                        name = "name",
                        binding = ParameterBinding.Body,
                        example = "Updated Name"
                    )
                )
            )
        )

        val result = CurlFormatter.format(endpoint, "http://localhost:8080")

        assertTrue(result.contains("-X PUT"))
        assertTrue(result.contains("Updated Name"))
    }

    @Test
    fun testFormatDeleteRequest() {
        val endpoint = ApiEndpoint(
            name = "Delete User",
            metadata = httpMetadata(
                path = "/api/users/1",
                method = HttpMethod.DELETE
            )
        )

        val result = CurlFormatter.format(endpoint, "http://localhost:8080")

        assertTrue(result.contains("-X DELETE"))
    }

    @Test
    fun testFormatGrpcBasic() {
        // gRPC module has been removed — test deleted
    }

    @Test
    fun testFormatGrpcWithBody() {
        // gRPC module has been removed — test deleted
    }

    @Test
    fun testFormatGrpcStripsHttpPrefix() {
        // gRPC module has been removed — test deleted
    }

    @Test
    fun testFormatGrpcDefaultHost() {
        // gRPC module has been removed — test deleted
    }

    @Test
    fun testFormatGrpcNoBody() {
        // gRPC module has been removed — test deleted
    }

    @Test
    fun testFormatAllWithMixedEndpoints() {
        // gRPC module has been removed — test deleted
    }
}
