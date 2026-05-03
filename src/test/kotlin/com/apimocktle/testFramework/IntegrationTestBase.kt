package com.apimocktle.testFramework

import com.apimocktle.settings.Settings
import org.junit.Before

abstract class IntegrationTestBase {

    protected open fun createSettings(): Settings = ApiFixtures.createSettings()

    @Before
    open fun setUp() {
    }
}
