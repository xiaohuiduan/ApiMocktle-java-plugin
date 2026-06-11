package com.apimocktle.ide.linemarker

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiMethod
import com.apimocktle.cache.ApiIndex
import com.apimocktle.cache.ApiIndexManager
import com.apimocktle.dashboard.ApiDashboardService
import com.apimocktle.core.threading.IdeDispatchers
import com.apimocktle.core.threading.backgroundAsync
import com.apimocktle.core.threading.swing
import com.apimocktle.logging.IdeaLog
import com.apimocktle.psi.helper.UnifiedAnnotationHelper
import com.apimocktle.util.ide.ProjectClassAvailabilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import java.awt.event.MouseEvent

/**
 * Line marker provider for API methods.
 *
 * Adds a gutter icon to methods annotated with API annotations
 * (Spring MVC, JAX-RS, etc.) that allows quick navigation
 * to the API Dashboard.
 *
 * When the endpoint is not found in the current index (e.g., after a branch
 * switch or new file), clicking the gutter icon triggers a re-scan of the
 * containing file before navigating.
 *
 * ## Supported Annotations
 * - Spring MVC: @RequestMapping, @GetMapping, @PostMapping, etc.
 * - JAX-RS: @GET, @POST, @PUT, @DELETE, @PATCH, @Path
 *
 * @see ApiDashboardService for navigation target
 */
class ApiMethodLineMarkerProvider : LineMarkerProvider {

    private val annotationHelper = UnifiedAnnotationHelper()

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is PsiIdentifier) return null
        val parent = element.parent as? PsiMethod ?: return null

        if (!isApiMethod(parent) && !isIndexedMethod(parent)) return null

        return LineMarkerInfo(
            element,
            element.textRange,
            AllIcons.Actions.Execute,
            { "在API仪表盘中打开" },
            ApiMethodNavigationHandler,
            GutterIconRenderer.Alignment.LEFT,
            { "在API仪表盘中打开" }
        )
    }

    private fun isIndexedMethod(method: PsiMethod): Boolean {
        return ApiIndex.getInstance(method.project).containsMethod(method)
    }

    private fun isApiMethod(method: PsiMethod): Boolean {
        val availabilityService = ProjectClassAvailabilityService.getInstance(method.project)

        return runBlocking {
            allApiAnnotations.any { annotationFqn ->
                availabilityService.hasClassInProject(annotationFqn) &&
                    annotationHelper.hasAnn(method, annotationFqn)
            }
        }
    }

    /**
     * All possible API method annotations across supported frameworks.
     */
    private val allApiAnnotations: List<String> = listOf(
        "org.springframework.web.bind.annotation.RequestMapping",
        "org.springframework.web.bind.annotation.GetMapping",
        "org.springframework.web.bind.annotation.PostMapping",
        "org.springframework.web.bind.annotation.PutMapping",
        "org.springframework.web.bind.annotation.DeleteMapping",
        "org.springframework.web.bind.annotation.PatchMapping",
        "javax.ws.rs.GET",
        "javax.ws.rs.POST",
        "javax.ws.rs.PUT",
        "javax.ws.rs.DELETE",
        "javax.ws.rs.PATCH",
        "javax.ws.rs.Path"
    )

    private object ApiMethodNavigationHandler : GutterIconNavigationHandler<PsiElement>, IdeaLog {

        override fun navigate(e: MouseEvent, element: PsiElement) {
            val method = element.parent as? PsiMethod ?: return
            val project = element.project

            IdeDispatchers.backgroundAsync {
                val service = ApiDashboardService.getInstance(project)
                val found = service.navigateToMethod(method)

                swing {
                    com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
                        .getToolWindow("API Dashboard")
                        ?.activate(null)
                }

                if (!found) {
                    val filePath = method.containingFile?.virtualFile?.path ?: return@backgroundAsync
                    LOG.info("Endpoint not found in index, re-scanning file: $filePath")

                    ApiIndexManager.getInstance(project).reIndex(listOf(filePath))

                    swing {
                        service.navigateToMethod(method)
                    }
                }
            }
        }
    }
}
