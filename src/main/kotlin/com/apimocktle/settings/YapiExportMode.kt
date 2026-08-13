package com.apimocktle.settings

/**
 * ApiMocktle 导出模式，决定如何处理已有的 API。
 */
enum class YapiExportMode(val desc: String) {
    /** 始终更新已有 API */
    ALWAYS_UPDATE("始终更新已有API"),
    /** 不更新已有 API */
    NEVER_UPDATE("不更新已有API"),
    /** 每次弹窗询问是否覆盖 */
    ALWAYS_ASK("每次弹窗询问是否覆盖"),
    /** 仅在 API 内容变更时更新 */
    UPDATE_IF_CHANGED("仅在API内容变更时更新")
}
