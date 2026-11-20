package com.apimocktle.settings

import com.apimocktle.i18n.ApiMocktleBundle

/**
 * ApiMocktle 导出模式，决定如何处理已有的 API。
 *
 * 显示文案按枚举 `name` 从资源包动态求值；
 * **不得**覆写 `toString()` 返回译文 —— `Settings.yapiExportMode` 用枚举名持久化。
 */
enum class YapiExportMode {
    /** 始终更新已有 API */
    ALWAYS_UPDATE,
    /** 不更新已有 API */
    NEVER_UPDATE,
    /** 每次弹窗询问是否覆盖 */
    ALWAYS_ASK,
    /** 仅在 API 内容变更时更新 */
    UPDATE_IF_CHANGED;

    /** 面向用户的说明文案，按当前 IDE 语言动态求值。 */
    val desc: String
        get() = ApiMocktleBundle.message("enum.yapiExportMode.${name.lowercase().replace("_", "")}")
}
