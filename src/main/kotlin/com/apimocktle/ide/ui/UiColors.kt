package com.apimocktle.ide.ui

import com.apimocktle.exporter.model.HttpMethod
import java.awt.Color

/**
 * 统一的 UI 颜色常量。
 *
 * HTTP 方法色板遵循常见 API 文档约定（与 Swagger 一致）：
 * GET 蓝 / POST 绿 / PUT 橙 / DELETE 红 / PATCH 青 / HEAD 紫 / OPTIONS 深蓝。
 * 树徽章、列表、表格、对话框等所有组件共用同一色板，避免多处硬编码导致漂移。
 */
object HttpMethodColors {

    val GET = Color(0x61affe)
    val POST = Color(0x49cc90)
    val PUT = Color(0xfca130)
    val DELETE = Color(0xf93e3e)
    val PATCH = Color(0x50e3c2)
    val HEAD = Color(0x9012fe)
    val OPTIONS = Color(0x0d5aa7)
    val UNKNOWN = Color(0x888888)

    /** 按 HTTP 方法返回对应颜色，未知方法返回灰色。 */
    fun colorFor(method: HttpMethod): Color = when (method) {
        HttpMethod.GET -> GET
        HttpMethod.POST -> POST
        HttpMethod.PUT -> PUT
        HttpMethod.DELETE -> DELETE
        HttpMethod.PATCH -> PATCH
        HttpMethod.HEAD -> HEAD
        HttpMethod.OPTIONS -> OPTIONS
        HttpMethod.NO_METHOD -> UNKNOWN
    }

    /** 按方法名字符串（如 "GET"）返回颜色，用于表格列/文本渲染。 */
    fun colorForName(method: String): Color = when (method.uppercase()) {
        "GET" -> GET
        "POST" -> POST
        "PUT" -> PUT
        "DELETE" -> DELETE
        "PATCH" -> PATCH
        "HEAD" -> HEAD
        "OPTIONS" -> OPTIONS
        else -> UNKNOWN
    }
}

/** 语义状态色：成功（绿）/ 失败（红）提示。 */
object StatusColors {
    val Success = Color(0x2da44e)
    val Error = Color(0xcf222e)
}

/** Agent 状态指示色：就绪（绿）/ 已暂停（黄）/ 未连接（红）/ 辅助灰。 */
object AgentStatusColors {
    val Ready = StatusColors.Success
    val Paused = Color(0xD4A017)
    val Offline = Color(0xCF6A4C)
    val Muted = Color(0x999999)
}
