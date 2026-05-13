package com.apimocktle.dashboard

import com.apimocktle.testFramework.ApiMocktleLightCodeInsightFixtureTestCase

class ApiDashboardToolWindowFactoryTest : ApiMocktleLightCodeInsightFixtureTestCase() {

    fun testFactoryExists() {
        val factory = ApiDashboardToolWindowFactory()
        assertNotNull("Factory should be instantiable", factory)
    }
}
