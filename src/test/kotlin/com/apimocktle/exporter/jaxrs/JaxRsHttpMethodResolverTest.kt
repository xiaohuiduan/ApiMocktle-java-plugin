package com.apimocktle.exporter.jaxrs

import com.apimocktle.psi.helper.UnifiedAnnotationHelper
import com.apimocktle.testFramework.ApiMocktleLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking

class JaxRsHttpMethodResolverTest : ApiMocktleLightCodeInsightFixtureTestCase() {

    private lateinit var resolver: JaxRsHttpMethodResolver

    override fun setUp() {
        super.setUp()
        val annotationHelper = UnifiedAnnotationHelper()
        resolver = JaxRsHttpMethodResolver(annotationHelper)
    }

    fun testResolveNoAnnotationReturnsNull() = runBlocking {
        loadFile("jaxrs/PlainMethodClass.java", """
            package com.test.jaxrs;
            public class PlainMethodClass {
                public void doSomething() {}
            }
        """.trimIndent())
        val psiClass = findClass("com.test.jaxrs.PlainMethodClass")!!
        val method = psiClass.findMethodsByName("doSomething", false).firstOrNull() ?: return@runBlocking
        val result = resolver.resolve(method)
        assertNull("Should return null for method without HTTP annotation", result)
    }
}
