package com.apimocktle.settings

/**
 * HTTP client implementation type.
 *
 * Currently only Apache HttpClient is supported.
 */
enum class HttpClientType(
    val value: String
) {
    /** Apache HttpClient - feature-rich, widely used */
    APACHE("Apache")
}
