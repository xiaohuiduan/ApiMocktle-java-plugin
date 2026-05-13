package com.apimocktle.ide.support

import com.apimocktle.testFramework.ApiMocktleLightCodeInsightFixtureTestCase

class NotificationUtilsTest : ApiMocktleLightCodeInsightFixtureTestCase() {

    fun testNotifyInfoDoesNotThrow() {
        NotificationUtils.notifyInfo(project, "Test Title", "Test Content")
    }

    fun testNotifyWarningDoesNotThrow() {
        NotificationUtils.notifyWarning(project, "Test Warning", "Warning Content")
    }

    fun testNotifyErrorDoesNotThrow() {
        NotificationUtils.notifyError(project, "Test Error", "Error Content")
    }

    fun testNotifyInfoWithLinksDoesNotThrow() {
        NotificationUtils.notifyInfoWithLinks(
            project,
            "Test Links",
            "Click <a href=\"https://example.com\">here</a>"
        )
    }
}
