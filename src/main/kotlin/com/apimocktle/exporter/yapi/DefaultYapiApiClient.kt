package com.apimocktle.exporter.yapi

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.apimocktle.exporter.yapi.model.YapiApiDoc
import com.apimocktle.exporter.yapi.model.YapiCart
import com.apimocktle.exporter.yapi.model.YapiProjectInfo
import com.apimocktle.exporter.yapi.model.YapiResponse
import com.apimocktle.http.HttpClient
import com.apimocktle.http.HttpRequest
import com.apimocktle.http.HttpResponse
import com.apimocktle.http.KeyValue
import com.apimocktle.util.GsonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * Default implementation of [YapiApiClient] that communicates with a real YAPI server.
 *
 * Authenticates via a personal token paired with an explicit [projectId].
 *
 * @param serverUrl Base URL of the YAPI server
 * @param token Personal token for authentication
 * @param projectId The target YAPI project ID (provided by user selection)
 * @param httpClient HTTP client for all network calls
 */
class DefaultYapiApiClient(
    private val serverUrl: String,
    private val token: String,
    private val projectId: String,
    private val httpClient: HttpClient
) : YapiApiClient {

    // region Project listing

    override suspend fun listProjects(): YapiResponse<List<YapiProjectInfo>> {
        if (serverUrl.isBlank()) return YapiResponse.failure("YAPI服务器URL未配置")
        if (token.isBlank()) return YapiResponse.failure("令牌为空")
        return withContext(Dispatchers.IO) {
            runCatching {
                val url = "$serverUrl$LIST_PROJECTS?token=${enc(token)}"
                val resp = httpClient.execute(HttpRequest(url = url, method = "GET"))
                parseResponse(resp) { json ->
                    json.getAsJsonArray("data")?.map { element ->
                        val obj = element.asJsonObject
                        YapiProjectInfo(
                            id = obj.get("_id").asString,
                            name = obj.get("name").asString,
                            desc = obj.get("desc")?.asString ?: ""
                        )
                    } ?: emptyList()
                }
            }.getOrElse { YapiResponse.failure(it.message ?: "未知错误") }
        }
    }

    // endregion

    // region Carts

    override suspend fun listCarts(): YapiResponse<List<YapiCart>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val url = "$serverUrl$GET_CAT_MENU?project_id=${enc(projectId)}&token=${enc(token)}"
                val resp = httpClient.execute(HttpRequest(url = url, method = "GET"))
                parseResponse(resp) { json ->
                    json.getAsJsonArray("data")?.map { element ->
                        val obj = element.asJsonObject
                        YapiCart(id = obj.get("_id").asString, name = obj.get("name").asString)
                    } ?: emptyList()
                }
            }.getOrElse { YapiResponse.failure(it.message ?: "未知错误") }
        }
    }

    override suspend fun createCart(name: String, desc: String): YapiResponse<YapiCart> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val body = GsonUtils.toJson(
                    linkedMapOf("desc" to desc, "project_id" to projectId, "name" to name, "token" to token)
                )
                val resp = httpClient.execute(
                    HttpRequest(
                        url = "$serverUrl$ADD_CAT",
                        method = "POST",
                        headers = listOf(KeyValue("Content-Type", "application/json")),
                        body = body
                    )
                )
                parseResponse(resp) { json ->
                    val data = json.getAsJsonObject("data")
                    YapiCart(id = data.get("_id").asString, name = data.get("name").asString)
                }
            }.getOrElse { YapiResponse.failure(it.message ?: "未知错误") }
        }
    }

    override suspend fun findOrCreateCart(name: String, desc: String): YapiResponse<String> {
        val cartsResult = listCarts()
        val carts = cartsResult.getOrNull()
            ?: return YapiResponse.failure(cartsResult.errorMessage() ?: "获取分类列表失败")
        val existing = carts.firstOrNull { it.name == name }
        if (existing != null) return YapiResponse.success(existing.id)
        return createCart(name, desc).let { result ->
            result.getOrNull()?.let { YapiResponse.success(it.id) }
                ?: YapiResponse.failure(result.errorMessage() ?: "创建分类 '$name' 失败")
        }
    }

    // endregion

    // region API listing & deduplication

    override suspend fun listApis(catId: String): YapiResponse<JsonArray> = listApis(catId, apiPageLimit)

    private suspend fun listApis(catId: String, limit: Int): YapiResponse<JsonArray> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val url = "$serverUrl$LIST_CAT?token=${enc(token)}&project_id=${enc(projectId)}&catid=${enc(catId)}&limit=$limit"
                val resp = httpClient.execute(HttpRequest(url = url, method = "GET"))
                val result = parseResponse(resp) { json ->
                    json.getAsJsonObject("data")?.getAsJsonArray("list") ?: JsonArray()
                }
                val list = result.getOrNull() ?: return@runCatching result
                if (list.size() == limit && limit < 5000) {
                    apiPageLimit = (limit * 1.4).toInt()
                    return@runCatching listApis(catId, apiPageLimit)
                }
                result
            }.getOrElse { YapiResponse.failure(it.message ?: "未知错误") }
        }
    }

    override suspend fun findExistingApi(catId: String, path: String, method: String): String? {
        return findExistingApiInfo(catId, path, method)?.id
    }

    override suspend fun findExistingApiInfo(catId: String, path: String, method: String): ExistingApiInfo? {
        val apis = listApis(catId).getOrNull() ?: return null
        return apis.firstOrNull { element ->
            val obj = element.asJsonObject
            obj.get("path")?.asString == path &&
                    obj.get("method")?.asString?.equals(method, ignoreCase = true) == true
        }?.asJsonObject?.let { obj ->
            ExistingApiInfo(
                id = obj.get("_id")?.asString ?: return null,
                title = obj.get("title")?.asString
            )
        }
    }

    override suspend fun findExistingApiData(catId: String, path: String, method: String): JsonObject? {
        val apis = listApis(catId).getOrNull() ?: return null
        return apis.firstOrNull { element ->
            val obj = element.asJsonObject
            obj.get("path")?.asString == path &&
                    obj.get("method")?.asString?.equals(method, ignoreCase = true) == true
        }?.asJsonObject
    }

    // endregion

    // region API upload

    override suspend fun uploadApi(doc: YapiApiDoc, catId: String): YapiResponse<Unit> {
        if (serverUrl.isBlank() || token.isBlank()) return YapiResponse.success(Unit)
        return withContext(Dispatchers.IO) {
            runCatching {
                val existingId = findExistingApi(catId, doc.path, doc.method)
                val resp = httpClient.execute(
                    HttpRequest(
                        url = "$serverUrl$SAVE_API",
                        method = "POST",
                        headers = listOf("Content-Type" to "application/json"),
                        body = buildApiDocBody(doc, catId, existingId)
                    )
                )
                parseResponse(resp) { Unit }
            }.getOrElse { YapiResponse.failure(it.message ?: "未知错误") }
        }
    }

    override suspend fun uploadApi(
        doc: YapiApiDoc,
        catId: String,
        updateConfirmation: UpdateConfirmation
    ): YapiResponse<Unit> {
        if (serverUrl.isBlank() || token.isBlank()) return YapiResponse.success(Unit)
        if (!updateConfirmation.confirm(doc, catId)) {
            return YapiResponse.success(Unit)
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val existingId = findExistingApi(catId, doc.path, doc.method)
                val resp = httpClient.execute(
                    HttpRequest(
                        url = "$serverUrl$SAVE_API",
                        method = "POST",
                        headers = listOf("Content-Type" to "application/json"),
                        body = buildApiDocBody(doc, catId, existingId)
                    )
                )
                parseResponse(resp) { Unit }
            }.getOrElse { YapiResponse.failure(it.message ?: "未知错误") }
        }
    }

    // endregion

    private fun <T> parseResponse(res: HttpResponse, handle: (JsonObject) -> T?): YapiResponse<T> {
        if (res.code != 200) return YapiResponse.failure("HTTP ${res.code}")
        val json = runCatching { GsonUtils.fromJson<JsonObject>(res.body ?: "") }.getOrNull()
            ?: return YapiResponse.failure("无效的JSON响应")
        val errcode = json.get("errcode")?.asInt ?: json.get("code")?.asInt
        val errmsg = json.get("errmsg")?.asString ?: json.get("message")?.asString
        val isSuccess = errmsg in SUCCESS_MESSAGES || errcode in SUCCESS_CODES
        if (!isSuccess) {
            return YapiResponse.failure(errmsg ?: "未知错误（错误码：$errcode）")
        }
        return handle(json)?.let { YapiResponse.success(it) }
            ?: YapiResponse.failure("响应中数据为空")
    }

    companion object {
        private const val LIST_PROJECTS = "/api/project/list"
        private const val SAVE_API = "/api/interface/save"
        private const val GET_CAT_MENU = "/api/interface/getCatMenu"
        private const val ADD_CAT = "/api/interface/add_cat"
        private const val LIST_CAT = "/api/interface/list_cat"

        private val SUCCESS_MESSAGES = setOf("成功", "成功！")
        private val SUCCESS_CODES = setOf(0, 200)

        @Volatile
        private var apiPageLimit: Int = 1000
    }

    private fun buildApiDocBody(doc: YapiApiDoc, catId: String, existingId: String? = null): String {
        val map = mutableMapOf<String, Any?>(
            "token" to token,
            "project_id" to projectId,
            "catid" to catId,
            "title" to doc.title,
            "path" to doc.path,
            "method" to doc.method,
            "desc" to doc.desc,
            "markdown" to doc.markdown,
            "status" to (doc.status ?: "done"),
            "req_body_is_json_schema" to doc.reqBodyIsJsonSchema,
            "res_body_is_json_schema" to doc.resBodyIsJsonSchema,
            "req_headers" to (doc.reqHeaders?.map {
                linkedMapOf(
                    "name" to it.name, "value" to (it.value ?: ""), "desc" to (it.desc ?: ""),
                    "example" to (it.example ?: it.value ?: ""), "required" to it.required
                )
            } ?: emptyList<Any>()),
            "req_query" to (doc.reqQuery?.map {
                linkedMapOf(
                    "name" to it.name, "value" to (it.value ?: ""), "example" to (it.example ?: it.value ?: ""),
                    "desc" to (it.desc ?: ""), "required" to it.required
                )
            } ?: emptyList<Any>()),
            "req_params" to (doc.reqParams?.map {
                linkedMapOf("name" to it.name, "example" to (it.example ?: ""), "desc" to (it.desc ?: ""))
            } ?: emptyList<Any>()),
            "req_body_other" to (doc.reqBodyOther ?: ""),
            "res_body" to (doc.resBody ?: ""),
            "tags" to (doc.tags ?: emptyList<String>()),
            "tag" to (doc.tag ?: emptyList<String>()),
            "req_body_form" to (doc.reqBodyForm?.map {
                linkedMapOf(
                    "name" to it.name, "example" to (it.example ?: ""), "type" to it.type,
                    "required" to it.required, "desc" to (it.desc ?: "")
                )
            } ?: emptyList<Any>())
        )
        doc.reqBodyType?.let { map["req_body_type"] = it }
        doc.resBodyType?.let { map["res_body_type"] = it }
        doc.open?.let { map["api_opened"] = it }
        existingId?.let { map["id"] = it }
        return GsonUtils.toJson(map)
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
