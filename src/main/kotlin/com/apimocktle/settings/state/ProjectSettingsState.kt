package com.apimocktle.settings.state

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * 项目级设置状态，存储在项目的 `apimocktle.xml` 中。
 */
@State(name = "ApiMocktleProjectSettings", storages = [Storage("apimocktle.xml")])
class ProjectSettingsState : PersistentStateComponent<ProjectSettingsState.State> {
    /**
     * 项目级设置数据类。
     */
    data class State(
        override var yapiPersonalToken: String? = null,
        var builtInConfig: Boolean = true,
        var remoteConfig: String? = null,
        var recommendConfig: String? = null,
        var yapiServer: String? = null,
        var yapiToken: String? = null
    ) : ProjectSettingsSupport

    private var state: State = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }
}
