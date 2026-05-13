package com.apimocktle.core

import com.intellij.openapi.application.ApplicationManager

/**
 * Application-level service for ApiMocktle plugin.
 *
 * This service provides application-scoped functionality and serves as
 * a central point for application-level plugin components.
 *
 * @see ApiMocktleProjectService for project-level service
 */
class ApiMocktleApplicationService {
    companion object {
        /**
         * Gets the application service instance.
         *
         * @return The service instance
         */
        fun getInstance(): ApiMocktleApplicationService =
            ApplicationManager.getApplication().getService(ApiMocktleApplicationService::class.java)
    }
}
