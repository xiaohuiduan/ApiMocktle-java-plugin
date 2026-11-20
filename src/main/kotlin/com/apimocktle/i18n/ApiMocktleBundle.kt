package com.apimocktle.i18n

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

/**
 * 插件界面文案资源包。
 *
 * 英文为基线（`ApiMocktleBundle.properties`），中文为覆盖层
 * （`ApiMocktleBundle_zh_CN.properties`）。语言跟随 **IDE 界面语言**。
 *
 * ## 为什么用 `DynamicBundle` 而不是 `AbstractBundle`
 *
 * `AbstractBundle.getMessage(key)` 最终走无参 `ResourceBundle.getBundle(baseName)`，
 * 用的是 **JVM 默认 locale**（`Locale.getDefault()`），而它来自 **操作系统区域设置**，
 * 与「IDE 界面语言」并不同源。真实反例（本项目实测）：
 *
 * | 场景 | JVM 默认 locale | IDE 界面 | `AbstractBundle` 结果 | 期望 |
 * |---|---|---|---|---|
 * | 中文 Windows + 英文 IDE | `zh_CN` | English | 中文（错） | 英文 |
 * | 英文 Windows + 中文 IDE | `en_US` | 中文 | 英文（错） | 中文 |
 *
 * 而且 JDK 查找链是 `请求 locale → JVM 默认 locale → 基线 bundle`，
 * 因此「显式传 `Locale.ENGLISH`」也救不了：JVM 默认是 `zh_CN` 时仍会命中中文层。
 *
 * `DynamicBundle` 通过 `LocalizationUtil` → `LocalizationOrder` 解析
 * **Language Pack 插件实际选择的** locale，这正是跟随 IDE 界面语言的正确来源。
 *
 * ## 语言生效时机
 *
 * 由 IDE 的 Language Pack 插件决定；切换语言后 **需重启 IDE**。
 * 已渲染的 Swing 组件不会自动重绘（与 IntelliJ 自身行为一致）。
 *
 * ## Usage
 * ```kotlin
 * val title = ApiMocktleBundle.message("ui.action.exportToApiMocktle.title")
 * val text = ApiMocktleBundle.message("dashboard.status.endpoints", endpoints.size)
 * ```
 *
 * 注意：带 `{0}` 占位符的文案走 `MessageFormat`，其中的单引号必须写成 `''`；
 * 不带占位符的文案按字面量返回。
 */
object ApiMocktleBundle : DynamicBundle("messages.ApiMocktleBundle") {

    @Nls
    fun message(
        @PropertyKey(resourceBundle = "messages.ApiMocktleBundle") key: String,
        vararg params: Any
    ): String = getMessage(key, *params)
}
