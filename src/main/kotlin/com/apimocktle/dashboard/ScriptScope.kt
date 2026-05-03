package com.apimocktle.dashboard

sealed class ScriptScope {
    abstract val key: String
    abstract fun displayLabel(): String

    data class Module(val name: String) : ScriptScope() {
        override val key: String = "module:$name"
        override fun displayLabel(): String = "模块：$name"
    }

    data class Class(val qualifiedName: String) : ScriptScope() {
        override val key: String = "class:$qualifiedName"
        override fun displayLabel(): String = "类：${qualifiedName.substringAfterLast('.')}"
    }

    data class Endpoint(val endpointKey: String) : ScriptScope() {
        override val key: String = "endpoint:$endpointKey"
        override fun displayLabel(): String = "端点：${endpointKey.substringAfterLast('#')}"
    }
}

data class ScriptCache(
    val preRequestScript: String? = null,
    val postResponseScript: String? = null
)
