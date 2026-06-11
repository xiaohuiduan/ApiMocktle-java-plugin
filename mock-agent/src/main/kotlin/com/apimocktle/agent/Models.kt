package com.apimocktle.agent

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Mock rule definition - pushed from ApiMocktle to Agent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MockRule(
    val id: String,
    val className: String,
    val methodName: String,
    val paramTypes: List<String>? = null,
    val responseTemplate: String,
    val responseDelay: Long? = null,
    val maxTimes: Int? = null,
    @JsonProperty("returnType")
    val returnTypeStr: String? = null,
) {
    @get:JsonIgnore
    val returnType: java.lang.reflect.Type? by lazy {
        returnTypeStr?.let { name ->
            try { Class.forName(name) } catch (_: ClassNotFoundException) { null }
        }
    }
}

/**
 * Mock 调用日志 —— Agent 收集后返回给 ApiMocktle
 */
data class MockCallLog(
    val className: String,
    val methodName: String,
    val args: List<Any?>,
    val response: Any?,
    val matchedRuleId: String,
    val timestamp: Long,
    val durationMs: Long,
)

/**
 * Agent 发现的类信息
 */
data class DiscoverClassInfo(
    val className: String,
    val displayName: String,
    val methods: List<DiscoverMethodInfo>,
)

data class DiscoverMethodInfo(
    val name: String,
    val paramTypes: List<String>,
    val returnType: String,
    val displayName: String,
)

/**
 * Agent 发现结果
 */
data class DiscoverResult(
    val feignClients: List<DiscoverClassInfo>,
    val mappers: List<DiscoverClassInfo>,
    val status: String = "connected",
    val version: String = "1.0.0",
)

/**
 * Agent 状态
 */
data class AgentStatus(
    val connected: Boolean,
    val version: String = "1.0.0",
    val pid: Long? = null,
)
