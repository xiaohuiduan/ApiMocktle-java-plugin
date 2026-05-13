package com.apimocktle.exporter.jaxrs

import com.apimocktle.testFramework.ApiMocktleLightCodeInsightFixtureTestCase
import com.apimocktle.testFramework.TestConfigReader
import com.apimocktle.exporter.model.HttpMethod
import com.apimocktle.exporter.model.httpMetadata
import com.apimocktle.exporter.model.path
import com.apimocktle.psi.helper.DocHelper
import com.apimocktle.psi.helper.UnifiedDocHelper

class JaxRsClassExporterTest : ApiMocktleLightCodeInsightFixtureTestCase() {

    private lateinit var exporter: JaxRsClassExporter

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        exporter = JaxRsClassExporter(project, jaxrsEnable = true)
    }

    private fun loadTestFiles() {
        loadFile("jaxrs/Path.java")
        loadFile("jaxrs/GET.java")
        loadFile("jaxrs/POST.java")
        loadFile("jaxrs/PUT.java")
        loadFile("jaxrs/DELETE.java")
        loadFile("jaxrs/PathParam.java")
        loadFile("jaxrs/QueryParam.java")
        loadFile("jaxrs/FormParam.java")
        loadFile("jaxrs/HeaderParam.java")
        loadFile("jaxrs/CookieParam.java")
        loadFile("jaxrs/BeanParam.java")
        loadFile("jaxrs/DefaultValue.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("constant/UserType.java")
        loadFile("api/jaxrs/UserResource.java")
        loadFile("api/jaxrs/UserDTO.java")
    }

    override fun createConfigReader() = TestConfigReader.empty(project)


    fun testExportJaxRsResource() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue(endpoints.isNotEmpty())
    }

    fun testExportGetMethod() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val getEndpoints = endpoints.filter { it.httpMetadata?.method == HttpMethod.GET }
        assertTrue(getEndpoints.isNotEmpty())
    }

    fun testExportPostMethod() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val postEndpoints = endpoints.filter { it.httpMetadata?.method == HttpMethod.POST }
        assertTrue(postEndpoints.isNotEmpty())
    }

    fun testExportPutMethod() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val putEndpoints = endpoints.filter { it.httpMetadata?.method == HttpMethod.PUT }
        assertTrue(putEndpoints.isNotEmpty())
    }

    fun testExportDeleteMethod() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val deleteEndpoints = endpoints.filter { it.httpMetadata?.method == HttpMethod.DELETE }
        assertTrue(deleteEndpoints.isNotEmpty())
    }

    fun testExportWithPathParam() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        val endpointWithPathParam = endpoints.find { it.path.contains("{id}") }
        assertNotNull(endpointWithPathParam)
    }

    fun testExportNonResource() = runTest {
        val psiClass = findClass("com.itangcent.model.UserInfo")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue(endpoints.isEmpty())
    }
}
