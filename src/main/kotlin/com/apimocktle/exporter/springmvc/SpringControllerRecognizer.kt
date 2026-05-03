package com.apimocktle.exporter.springmvc

import com.intellij.psi.PsiClass
import com.apimocktle.exporter.core.ApiClassRecognizer
import com.apimocktle.exporter.core.MetaAnnotationResolver
import com.apimocktle.rule.RuleKeys
import com.apimocktle.rule.engine.RuleEngine

/**
 * Recognizes Spring MVC controller classes.
 *
 * Supports both standard annotations (@Controller, @RestController) and
 * custom meta-annotations (e.g., @CustomRestController annotated with @RestController).
 */
class SpringControllerRecognizer(
    private val ruleEngine: RuleEngine? = null
) : ApiClassRecognizer {

    override val frameworkName: String = "SpringMVC"

    override val targetAnnotations: Set<String> = CONTROLLER_ANNOTATIONS

    override suspend fun isApiClass(psiClass: PsiClass): Boolean {
        if (ruleEngine?.evaluate(RuleKeys.CLASS_IS_CTRL, psiClass) == true) return true
        return MetaAnnotationResolver.hasMetaAnnotation(psiClass, CONTROLLER_ANNOTATIONS)
    }

    suspend fun isController(psiClass: PsiClass): Boolean = isApiClass(psiClass)

    companion object {
        val CONTROLLER_ANNOTATIONS = setOf(
            "org.springframework.stereotype.Controller",
            "org.springframework.web.bind.annotation.RestController"
        )
    }
}
