package com.apimocktle.exporter.model

import com.apimocktle.i18n.ApiMocktleBundle

/**
 * Strategy for selecting which mappings to export when a method
 * has multiple paths (e.g., `@PostMapping({"/report-pb", "/events"})`).
 *
 * Configured via the `path.multi` rule.
 *
 * 显示名按枚举 `name` 从资源包动态求值，因此语言切换后重新渲染即可生效；
 * **不得**覆写 `toString()` 返回译文 —— `Settings.pathMulti` 用枚举名持久化。
 */
enum class PathSelector {
    /** Export all paths */
    ALL {
        override fun <T> select(items: List<T>, pathOf: (T) -> String): List<T> = items
    },

    /** Export only the first path */
    FIRST {
        override fun <T> select(items: List<T>, pathOf: (T) -> String): List<T> = items.take(1)
    },

    /** Export only the last path */
    LAST {
        override fun <T> select(items: List<T>, pathOf: (T) -> String): List<T> = items.takeLast(1)
    },

    /** Export only the shortest path */
    SHORTEST {
        override fun <T> select(items: List<T>, pathOf: (T) -> String): List<T> =
            items.sortedBy { pathOf(it).length }.take(1)
    },

    /** Export only the longest path */
    LONGEST {
        override fun <T> select(items: List<T>, pathOf: (T) -> String): List<T> =
            items.sortedByDescending { pathOf(it).length }.take(1)
    };

    /** 面向用户的显示名，按当前 IDE 语言动态求值。 */
    val displayName: String
        get() = ApiMocktleBundle.message("enum.pathSelector.${name.lowercase()}")

    /**
     * Selects items from the list based on this strategy.
     *
     * @param items The full list of items to select from
     * @param pathOf Extracts the path string from an item
     * @return The selected subset
     */
    abstract fun <T> select(items: List<T>, pathOf: (T) -> String): List<T>

    companion object {
        /**
         * Parses a rule string into a [PathSelector].
         * Returns [ALL] for null/blank/unrecognized values.
         */
        fun fromRule(value: String?): PathSelector {
            if (value.isNullOrBlank()) return ALL
            return when (value.trim().uppercase()) {
                "FIRST" -> FIRST
                "LAST" -> LAST
                "SHORTEST" -> SHORTEST
                "LONGEST" -> LONGEST
                "ALL" -> ALL
                else -> ALL
            }
        }
    }
}
