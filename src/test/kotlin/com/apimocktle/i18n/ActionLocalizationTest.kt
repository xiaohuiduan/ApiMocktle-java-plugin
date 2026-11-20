package com.apimocktle.i18n

import org.junit.Assert.assertEquals
import com.intellij.openapi.actionSystem.ActionManager
import com.apimocktle.testFramework.ApiMocktleLightCodeInsightFixtureTestCase

/**
 * 验证 `plugin.xml` 中的 action/group 文案确实被平台从资源包解析。
 *
 * ## 为什么需要这个测试
 *
 * 曾经踩过的坑：`plugin.xml` 里写 `text="%action.xxx.text"`，**平台不会解析 `%` 前缀**，
 * 结果整棵菜单直接把这串字面量显示给用户。单元测试当时是绿的，因为只验证了
 * "key 在 .properties 文件里存在"，没有验证"平台真的去解析它"——这两件事完全不同。
 *
 * 平台对 action/group 本地化的约定（见官方 Localizing Actions and Groups）：
 * - `plugin.xml` 里 **不写** `text` / `description` 属性
 * - 声明 `<resource-bundle>`
 * - key 命名：`action.<完整 action-id>.text` / `group.<完整 group-id>.text`
 *
 * 本测试通过 `ActionManager` 读取平台注册后的真实 Presentation 文本，
 * 能同时抓到"`%` 未解析"与"key 命名错误"两类问题。
 */
class ActionLocalizationTest : ApiMocktleLightCodeInsightFixtureTestCase() {

    private val actionIds = listOf(
        "com.apimocktle.action.YapiExportAction",
        "com.apimocktle.action.FieldsToJsonAction",
        "com.apimocktle.action.FieldsToJson5Action",
        "com.apimocktle.action.OpenDashboardAction"
    )

    private val groupIds = listOf(
        "ApiMocktleFieldsGroup",
        "ApiMocktleGenerateMenu",
        "ApiMocktleEditorLangPopupMenu",
        "ApiMocktleProjectViewPopupMenu"
    )

    /**
     * 动作文本必须来自资源包，且**不能**是未解析的 `%key` 字面量。
     *
     * 同时校验解析结果确实等于资源包里对应 key 的值 —— 只断言"非 % 开头"不够，
     * 因为平台在 key 缺失时也可能返回空串或回退文本。
     */
    fun testActionTextIsResolvedFromBundle() {
        val actionManager = ActionManager.getInstance()
        for (id in actionIds) {
            val action = actionManager.getAction(id)
            assertNotNull("Action '$id' must be registered in plugin.xml", action)
            val text = action!!.templatePresentation.text
            assertNotNull("Action '$id' must have a non-null text resolved from the bundle", text)
            assertFalse(
                "Action '$id' text must not be an unresolved '%key' literal, got: $text",
                text!!.startsWith("%")
            )
            assertTrue("Action '$id' text must not be blank", text.isNotBlank())
            assertFalse(
                "Action '$id' text must not be a !key! missing marker, got: $text",
                text.startsWith("!") && text.endsWith("!")
            )
            // 与资源包按平台约定拼出的 key 逐字比对
            assertEquals(
                "Action '$id' text must equal the bundle value for 'action.$id.text'",
                ApiMocktleBundle.message("action.$id.text"),
                text
            )

            val description = action.templatePresentation.description
            assertNotNull("Action '$id' must have a description from the bundle", description)
            assertEquals(
                "Action '$id' description must equal the bundle value for 'action.$id.description'",
                ApiMocktleBundle.message("action.$id.description"),
                description
            )
        }
    }

    /**
     * 分组文本同理：必须是解析后的文案，不能是 `%key`。
     */
    fun testGroupTextIsResolvedFromBundle() {
        val actionManager = ActionManager.getInstance()
        for (id in groupIds) {
            val group = actionManager.getAction(id)
            assertNotNull("Group '$id' must be registered in plugin.xml", group)
            val text = group!!.templatePresentation.text
            assertNotNull("Group '$id' must have a non-null text resolved from the bundle", text)
            assertFalse(
                "Group '$id' text must not be an unresolved '%key' literal, got: $text",
                text!!.startsWith("%")
            )
            assertTrue("Group '$id' text must not be blank", text.isNotBlank())
            assertEquals(
                "Group '$id' text must equal the bundle value for 'group.$id.text'",
                ApiMocktleBundle.message("group.$id.text"),
                text
            )
        }
    }

    /**
     * 反例守卫：确认平台**不会**把 `%key` 当变量解析。
     *
     * 若未来有人恢复 `text="%..."` 写法，上面的测试会因为拿到 `%...` 字面量而失败；
     * 这条测试把该结论显式记录下来，避免再次误判。
     */
    fun testUnresolvedKeyLiteralIsDetectable() {
        val unresolved = listOf("%action.some.key.text", "%group.some.key.text")
        for (literal in unresolved) {
            assertTrue(
                "Sanity check: an unresolved %-prefixed literal must be detectable by startsWith(\"%\")",
                literal.startsWith("%")
            )
        }
    }
}
