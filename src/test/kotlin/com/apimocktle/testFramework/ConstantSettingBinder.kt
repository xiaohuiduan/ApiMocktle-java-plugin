package com.apimocktle.testFramework

import com.apimocktle.settings.SettingBinder
import com.apimocktle.settings.Settings

class ConstantSettingBinder(
    private var settings: Settings = Settings()
) : SettingBinder {

    override fun read(): Settings = settings

    override fun tryRead(): Settings = settings

    override fun save(settings: Settings) {
        this.settings = settings
    }
}
