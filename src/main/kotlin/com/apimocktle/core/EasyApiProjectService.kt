package com.apimocktle.core

import com.intellij.openapi.project.Project

/**
 * Project-level service for ApiMocktle plugin.
 *
 * This service provides project-scoped functionality and serves as
 * a central point for project-level plugin components.
 *
 * @see ApiMocktleApplicationService for application-level service
 */
class ApiMocktleProjectService(private val project: Project) {
    companion object {
        /**
         * Gets the project service instance.
         *
         * @param project The project
         * @return The service instance
         */
        fun getInstance(project: Project): ApiMocktleProjectService = project.getService(ApiMocktleProjectService::class.java)
    }
}
