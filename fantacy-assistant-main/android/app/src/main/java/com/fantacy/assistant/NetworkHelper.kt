package com.fantacy.assistant

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object NetworkHelper {
    val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS) // 稍微放宽一点连接时间
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * 统一的 POST 请求工具
     */
    fun makePostRequest(url: String, jsonBody: JSONObject, token: String? = null): Request {
        val builder = Request.Builder().url(url)
        token?.let {
            builder.addHeader("Authorization", "Bearer $it")
        }
        return builder.post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE)).build()
    }

    /**
     * 🌟 新增：统一的 GET 请求工具
     */
    fun makeGetRequest(url: String, token: String? = null): Request {
        val builder = Request.Builder().url(url)
        token?.let {
            builder.addHeader("Authorization", "Bearer $it")
        }
        // GET 请求不需要 Body
        return builder.get().build()
    }
}