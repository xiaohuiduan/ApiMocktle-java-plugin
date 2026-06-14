package com.apimocktle.agent

/**
 * 单个 Agent 实例的信息。
 *
 * @param name          Run Configuration 名称，作为服务名展示
 * @param port          agent 监听端口
 * @param active        是否激活（接受 mock 规则）
 * @param connected     agent HTTP 服务器是否可达
 * @param runConfigId   关联的 Run Configuration ID，用于定位进程
 */
data class AgentInfo(
    val name: String,
    val port: Int,
    val active: Boolean = true,
    val connected: Boolean = false,
    val runConfigId: String? = null,
) {
    val address: String get() = "http://localhost:$port"
}
