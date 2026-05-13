package com.apimocktle.exporter.jaxrs

import com.apimocktle.psi.helper.UnifiedAnnotationHelper
import com.apimocktle.testFramework.ApiMocktleLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking

class JaxRsParameterResolverTest : ApiMocktleLightCodeInsightFixtureTestCase() {

    private lateinit var resolver: JaxRsParameterResolver

    override fun setUp() {
        super.setUp()
        val annotationHelper = UnifiedAnnotationHelper()
        resolver = JaxRsParameterResolver(annotationHelper)
    }

    fun testResolvePlainParameterReturnsBodyBinding() = runBlocking {
        loadFile("jaxrs/PlainParamClass.java", """
            package com.test.jaxrs;
            public class PlainParamClass {
                public void doSomething(String input) {}
            }
        """.trimIndent())
        val psiClass = findClass("com.test.jaxrs.PlainParamClass")!!
        val method = psiClass.findMethodsByName("doSomething", false).firstOrNull() ?: return@runBlocking
        val param = method.parameterList.parameters.firstOrNull() ?: return@runBlocking
        val result = resolver.resolve(param)
        assertTrue("Should return at least one parameter", result.isNotEmpty())
        assertEquals("Plain param should have Body binding",
            com.apimocktle.exporter.model.ParameterBinding.Body, result.first().binding)
    }
}
