package com.apimocktle.dashboard

import com.apimocktle.testFramework.EasyApiLightCodeInsightFixtureTestCase

class ApiDashboardToolWindowFactoryTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testFactoryExists() {
        val factory = ApiDashboardToolWindowFactory()
        assertNotNull("Factory should be instantiable", factory)
    }
}
