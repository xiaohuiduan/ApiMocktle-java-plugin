package com.apimocktle.settings

import com.intellij.util.messages.Topic

interface SettingsChangeListener {

    fun settingsChanged()

    companion object {
        val TOPIC: Topic<SettingsChangeListener> = Topic.create(
            "ApiMocktle Settings Changed",
            SettingsChangeListener::class.java
        )
    }
}
