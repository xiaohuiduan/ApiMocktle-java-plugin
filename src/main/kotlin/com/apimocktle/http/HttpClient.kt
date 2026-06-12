package com.apimocktle.http

/**
 * HTTP client interface for making HTTP requests.
 *
 * Uses [ApacheHttpClient] as the implementation.
 *
 * ## Usage
 * ```kotlin
 * val client = HttpClientProvider.getInstance(project).getClient()
 * val response = client.execute(HttpRequest(
 *     method = "GET",
 *     url = "https://api.example.com/users"
 * ))
 * ```
 *
 * @see HttpClientProvider for client creation
 * @see HttpRequest for request model
 * @see HttpResponse for response model
 */
interface HttpClient : AutoCloseable {
    /**
     * Executes an HTTP request and returns the response.
     *
     * @param request The request to execute
     * @return The response
     */
    suspend fun execute(request: HttpRequest): HttpResponse
    
    /**
     * Closes the client and releases resources.
     */
    override fun close()
}
