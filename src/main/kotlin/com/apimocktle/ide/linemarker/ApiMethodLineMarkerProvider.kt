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

        // 顺序很关键：先查 O(1) 索引，命中即返回；未命中再做本地注解名匹配。
        // 只有方法确实携带 API 注解名时才进入 classpath 可用性检查（有缓存，极少触发阻塞查询）。
        if (!isIndexedMethod(parent) && !hasApiAnnotation(parent)) return null

        return LineMarkerInfo(
            element,
            element.textRange,
            AllIcons.Toolwindows.Documentation,
            { "在API仪表盘中打开" },
            ApiMethodNavigationHandler,
            GutterIconRenderer.Alignment.LEFT,
            { "在API仪表盘中打开" }
        )
    }

    private fun isIndexedMethod(method: PsiMethod): Boolean {
        return ApiIndex.getInstance(method.project).containsMethod(method)
    }

    /**
     * 判断方法是否带 API 注解。
     * 先做纯 PSI 的注解短名预筛（无协程派发、无阻塞）：方法没有携带任何 API 注解名时直接返回 false；
     * 仅对真正带 API 注解的方法才进入 runBlocking 做 FQN 确认与 classpath 可用性检查（有 TTL 缓存），
     * 保证对文件里绝大多数普通方法零阻塞。
     */
    private fun hasApiAnnotation(method: PsiMethod): Boolean {
        val modifierList = method.modifierList ?: return false
        val presentNames = modifierList.annotations
            .mapNotNull { it.qualifiedName?.substringAfterLast('.')?.lowercase() }
            .toSet()
        if (presentNames.isEmpty()) return false

        val matchedAnnotations = allApiAnnotations.filter { fqn ->
            fqn.substringAfterLast('.').lowercase() in presentNames
        }
        if (matchedAnnotations.isEmpty()) return false

        val availabilityService = ProjectClassAvailabilityService.getInstance(method.project)
        return runBlocking {
            matchedAnnotations.any { annotationFqn ->
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
        "org.springframework.web.bind.annotation.PatchMapping"
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
