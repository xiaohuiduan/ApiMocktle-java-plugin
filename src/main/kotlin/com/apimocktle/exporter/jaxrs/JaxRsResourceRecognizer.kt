package com.apimocktle.exporter.jaxrs

import com.intellij.psi.PsiClass
import com.apimocktle.exporter.core.ApiClassRecognizer
import com.apimocktle.exporter.core.MetaAnnotationResolver
import com.apimocktle.rule.RuleKeys
import com.apimocktle.rule.engine.RuleEngine

/**
 * Recognizes JAX-RS resource classes.
 *
 * Supports both standard @Path annotations (javax and jakarta) and
 * custom meta-annotations annotated with @Path.
 */
class JaxRsResourceRecognizer(
    private val ruleEngine: RuleEngine? = null,
    private val enabled: Boolean = true
) : ApiClassRecognizer {

    override val frameworkName: String = "JAX-RS"

    override val targetAnnotations: Set<String> = PATH_ANNOTATIONS

    override suspend fun isApiClass(psiClass: PsiClass): Boolean {
        if (!enabled) return false
        if (ruleEngine?.evaluate(RuleKeys.CLASS_IS_JAXRS_CTRL, psiClass) == true) return true
        if (ruleEngine?.evaluate(RuleKeys.CLASS_IS_QUARKUS_CTRL, psiClass) == true) return true
        return MetaAnnotationResolver.hasMetaAnnotation(psiClass, PATH_ANNOTATIONS)
    }

    suspend fun isResource(psiClass: PsiClass): Boolean = isApiClass(psiClass)

    companion object {
        val PATH_ANNOTATIONS = setOf(
            "javax.ws.rs.Path",
            "jakarta.ws.rs.Path"
        )
    }
}
