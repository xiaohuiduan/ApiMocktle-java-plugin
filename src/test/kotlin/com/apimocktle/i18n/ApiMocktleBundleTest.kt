package com.apimocktle.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 资源包一致性测试 —— 整个 i18n 改造的安全网。
 *
 * 24 个文件、260+ 条文案，人工核对 key 对齐不现实；这里用机器断言兜住：
 * - 中英文 key 集合完全相等（否则缺失 key 会静默回退英文）
 * - 每个 key 的 `{n}` 占位符集合在中英文之间相等（否则 `MessageFormat` 抛异常或吞参数）
 * - 两份文件都没有重复 key（后者会静默覆盖前者）
 * - 已知 key 可解析且不是 `!key!` 缺失标记
 * - 缺失 key 返回 `!key!` 形式（验证降级行为符合预期）
 */
class ApiMocktleBundleTest {

    private companion object {
        const val BASE_NAME = "messages/ApiMocktleBundle"

        /** 用于验证"已知 key 可解析"的代表性 key（覆盖 action/settings/dashboard/error/enum）。 */
        val KNOWN_KEYS = listOf(
            "action.com.apimocktle.action.YapiExportAction.text",
            "group.ApiMocktleFieldsGroup.text",
            "ui.action.exportToApiMocktle.title",
            "settings.section.frameworks",
            "dashboard.tree.loading",
            "notification.copied",
            "error.yapi.tokenEmpty",
            "enum.pathSelector.all",
            "enum.repositoryType.custom"
        )

        /** 读取资源包原文（UTF-8，无 BOM）。用原文而非 ResourceBundle，才能看到重复 key。 */
        fun readRaw(resourceName: String): String {
            val stream = ApiMocktleBundle::class.java.getResourceAsStream("/$resourceName")
            assertNotNull("Resource '$resourceName' must exist on the classpath", stream)
            return stream!!.use { it.readBytes().toString(Charsets.UTF_8) }
        }

        /**
         * 解析 .properties 原文为 `key -> raw value` 的有序列表。
         *
         * 只做测试需要的最小解析：跳过空行与 `#`/`!` 注释，按第一个未转义的 `=`/`:` 切分，
         * 并拼接以 `\` 结尾的续行。刻意不使用 [java.util.Properties]：
         * 它会静默丢弃重复 key，而重复 key 正是本测试要抓的问题之一。
         */
        fun parseEntries(raw: String): List<Pair<String, String>> {
            val entries = mutableListOf<Pair<String, String>>()
            var pendingKey: String? = null
            var pendingValue = StringBuilder()

            for (line in raw.lines()) {
                val trimmed = line.trim()
                if (pendingKey == null && (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!"))) {
                    continue
                }
                val text = pendingKey?.let { line } ?: trimmed
                val continues = text.endsWith("\\") && !text.endsWith("\\\\")

                if (pendingKey == null) {
                    val separator = text.indexOfFirst { it == '=' || it == ':' }
                    assertTrue("Invalid properties line (no '=' or ':'): $line", separator > 0)
                    val key = text.substring(0, separator).trim()
                    val value = text.substring(separator + 1)
                    if (continues) {
                        pendingKey = key
                        pendingValue = StringBuilder(value.dropLast(1))
                    } else {
                        entries += key to value.trim()
                    }
                } else {
                    if (continues) {
                        pendingValue.append(text.dropLast(1))
                    } else {
                        pendingValue.append(text)
                        entries += pendingKey to pendingValue.toString().trim()
                        pendingKey = null
                        pendingValue = StringBuilder()
                    }
                }
            }
            return entries
        }

        fun placeholderSets(entries: List<Pair<String, String>>): Map<String, Set<Int>> =
            entries.associate { (key, value) ->
                key to Regex("""\{(\d+)}""").findAll(value).map { it.groupValues[1].toInt() }.toSet()
            }
    }

    private val english = parseEntries(readRaw("$BASE_NAME.properties"))
    private val chinese = parseEntries(readRaw("${BASE_NAME}_zh_CN.properties"))

    @Test
    fun `english and chinese key sets are exactly equal`() {
        val englishKeys = english.map { it.first }.toSet()
        val chineseKeys = chinese.map { it.first }.toSet()

        val missingInChinese = (englishKeys - chineseKeys).sorted()
        val missingInEnglish = (chineseKeys - englishKeys).sorted()

        assertTrue(
            "Keys present in English but MISSING in zh_CN (would silently fall back to English): $missingInChinese",
            missingInChinese.isEmpty()
        )
        assertTrue(
            "Keys present in zh_CN but MISSING in English (fallback bundle incomplete): $missingInEnglish",
            missingInEnglish.isEmpty()
        )
        assertEquals(
            "English and zh_CN must define the same number of keys",
            englishKeys.size,
            chineseKeys.size
        )
    }

    @Test
    fun `placeholder sets match per key across both bundles`() {
        val englishPlaceholders = placeholderSets(english)
        val chinesePlaceholders = placeholderSets(chinese)

        val mismatches = englishPlaceholders.keys.intersect(chinesePlaceholders.keys)
            .mapNotNull { key ->
                val en = englishPlaceholders.getValue(key)
                val zh = chinesePlaceholders.getValue(key)
                if (en == zh) null else "$key: en=$en zh=$zh"
            }
            .sorted()

        assertTrue(
            "Placeholder sets must match across bundles (MessageFormat would throw or drop params): $mismatches",
            mismatches.isEmpty()
        )
    }

    @Test
    fun `no duplicate keys in either bundle`() {
        val duplicateEnglish = english.groupingBy { it.first }.eachCount().filter { it.value > 1 }.keys.sorted()
        val duplicateChinese = chinese.groupingBy { it.first }.eachCount().filter { it.value > 1 }.keys.sorted()

        assertTrue("Duplicate keys in English bundle (last one silently wins): $duplicateEnglish", duplicateEnglish.isEmpty())
        assertTrue("Duplicate keys in zh_CN bundle (last one silently wins): $duplicateChinese", duplicateChinese.isEmpty())
    }

    @Test
    fun `known keys resolve and are not missing markers`() {
        for (key in KNOWN_KEYS) {
            val value = ApiMocktleBundle.message(key)
            assertFalse("Key '$key' must not resolve to the !key! missing marker", value.startsWith("!") && value.endsWith("!"))
            assertTrue("Key '$key' must resolve to a non-blank message", value.isNotBlank())
        }
    }

    @Test
    fun `every enum display name resolves from the bundle`() {
        // 枚举显示名按 name 拼 key；缺 key 时平台会 Logger.error（测试框架会直接判失败），
        // 所以这等价于"每个枚举项都有对应文案"，是本文件最有价值的一条守卫。
        for (entry in com.apimocktle.exporter.model.PathSelector.entries) {
            val text = entry.displayName
            assertTrue("PathSelector.${entry.name} display name must be non-blank", text.isNotBlank())
            assertFalse("PathSelector.${entry.name} must not be a missing marker: $text", text.startsWith("!"))
        }
        for (entry in com.apimocktle.settings.YapiExportMode.entries) {
            val text = entry.desc
            assertTrue("YapiExportMode.${entry.name} desc must be non-blank", text.isNotBlank())
            assertFalse("YapiExportMode.${entry.name} must not be a missing marker: $text", text.startsWith("!"))
        }
        for (entry in com.apimocktle.settings.ui.VerbosityLevel.entries) {
            val text = entry.displayName
            assertTrue("VerbosityLevel.${entry.name} display name must be non-blank", text.isNotBlank())
            assertFalse("VerbosityLevel.${entry.name} must not be a missing marker: $text", text.startsWith("!"))
        }
        for (entry in com.apimocktle.repository.RepositoryType.entries) {
            val text = entry.displayName()
            assertTrue("RepositoryType.${entry.name} display name must be non-blank", text.isNotBlank())
            assertFalse("RepositoryType.${entry.name} must not be a missing marker: $text", text.startsWith("!"))
        }
    }

    /**
     * 持久化契约回归：`Settings.pathMulti` / `Settings.yapiExportMode` 存的是枚举 `name`。
     * 一旦给这两个枚举覆写 `toString()` 返回译文，设置会写入译文，重启后 `valueOf` 失败、
     * 用户配置静默丢失。这里锁定 `toString()` 仍是枚举名且可往返。
     */
    @Test
    fun `persisted enums keep name-based toString and round-trip`() {
        for (entry in com.apimocktle.exporter.model.PathSelector.entries) {
            assertEquals("PathSelector.toString() must stay the enum name", entry.name, entry.toString())
            assertEquals(entry, com.apimocktle.exporter.model.PathSelector.valueOf(entry.name))
        }
        for (entry in com.apimocktle.settings.YapiExportMode.entries) {
            assertEquals("YapiExportMode.toString() must stay the enum name", entry.name, entry.toString())
            assertEquals(entry, com.apimocktle.settings.YapiExportMode.valueOf(entry.name))
        }
    }

    @Test
    fun `message substitutes positional parameters`() {
        val rendered = ApiMocktleBundle.message("dashboard.status.endpoints", 42)
        assertTrue("Placeholder {0} must be substituted, got: $rendered", rendered.contains("42"))
        assertFalse("Rendered message must not keep a raw placeholder, got: $rendered", rendered.contains("{0}"))
    }

    @Test
    fun `apostrophe in parameterised message renders as a single quote`() {
        // 两份文件里该 key 的单引号都写成 ''，MessageFormat 会还原成一个 '。
        val rendered = ApiMocktleBundle.message("error.yapi.createCartFailed", "Demo")
        assertTrue("Doubled apostrophes must render as one quote, got: $rendered", rendered.contains("'Demo'"))
        assertFalse("Rendered message must not keep doubled apostrophes, got: $rendered", rendered.contains("''"))
    }

    @Test
    fun `literal braces are preserved when no parameters are passed`() {
        // 无参数时 DynamicBundle 不走 MessageFormat，{id} 这类字面量必须原样保留。
        val rendered = ApiMocktleBundle.message("settings.scan.urlTemplate.label")
        assertTrue("Literal {id} must survive, got: $rendered", rendered.contains("{id}"))
    }

    @Test
    fun `bundles contain no byte order mark`() {
        assertFalse(
            "English bundle must be UTF-8 without BOM",
            readRaw("$BASE_NAME.properties").startsWith('\uFEFF')
        )
        assertFalse(
            "zh_CN bundle must be UTF-8 without BOM",
            readRaw("${BASE_NAME}_zh_CN.properties").startsWith('\uFEFF')
        )
    }

    /**
     * `java.util.Properties` 会吞掉分隔符后的首个未转义空格，因此需要前导空格的文案必须写成
     * `\ `。这里断言**原文**（而非解析后的 bundle），因为 bundle 实际解析到哪一层取决于
     * JVM 默认 locale —— 断 bundle 会变成 locale 相关测试。
     */
    @Test
    fun `english bundle escapes the leading space it needs`() {
        val raw = readRaw("$BASE_NAME.properties")
        val line = raw.lines().firstOrNull { it.startsWith("settings.extensions.defaultEnabled=") }
        assertNotNull("English bundle must define settings.extensions.defaultEnabled", line)
        assertTrue(
            "Value needs a leading space, so it must be escaped as '\\ ' (Properties drops an unescaped one): $line",
            line!!.startsWith("settings.extensions.defaultEnabled=\\ ")
        )
    }
}
