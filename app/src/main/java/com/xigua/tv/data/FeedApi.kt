package com.xigua.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 抖音移动 Feed 接口封装 —— 2026 实测可用的主链路。
 * 免签名 / 免 Cookie。西瓜视频 ID 与抖音 ID 互通。
 */
class FeedApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        // 抖音 App 移动 UA —— 走免签名的核心 Feed 网关
        private const val MOBILE_UA =
            "com.ss.android.ugc.aweme/290101 (Linux; U; Android 10; zh_CN; Pixel 4; " +
            "Build/QQ3A.200805.001; Cronet/TTNetVersion:5f9037be 2023-01-13 " +
            "QuicVersion:4668bb42 2022-11-21)"

        private val ENDPOINTS = listOf(
            "https://api5-normal-c-hl.amemv.com/aweme/v1/feed/",
            "https://aweme.snssdk.com/aweme/v1/feed/",
        )
    }

    /** 解析单个视频：传 aweme_id */
    suspend fun resolveVideo(itemId: String): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val data = requestFeed(mapOf("aweme_id" to itemId, "count" to "1"))
                ?: return@withContext Result.failure(Exception("Feed 接口无响应"))

            val list = data["aweme_list"]?.jsonArray ?: jsonArrayOf()
            // 精确匹配优先
            val target = list.firstOrNull {
                str(it.jsonObject, "aweme_id") == itemId
            } ?: list.firstOrNull()

            if (target == null) {
                Result.failure(Exception("未找到视频: $itemId"))
            } else {
                Result.success(parseAweme(target.jsonObject))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 获取推荐信息流 */
    suspend fun getFeed(count: Int = 5): Result<List<VideoInfo>> = withContext(Dispatchers.IO) {
        try {
            val data = requestFeed(mapOf("count" to count.toString(), "refresh_type" to "1"))
                ?: return@withContext Result.failure(Exception("Feed 接口无响应"))

            val list = data["aweme_list"]?.jsonArray ?: jsonArrayOf()
            val videos = list.map { parseAweme(it.jsonObject) }
                .filter { !it.playUrl.isNullOrBlank() }
            Result.success(videos)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 请求 Feed 接口（多端点容灾） */
    private fun requestFeed(params: Map<String, String>): Map<String, JsonElement>? {
        val query = params.entries.joinToString("&") { (k, v) ->
            "$k=${java.net.URLEncoder.encode(v, "UTF-8")}"
        }
        for (endpoint in ENDPOINTS) {
            try {
                val request = Request.Builder()
                    .url("$endpoint?$query")
                    .header("User-Agent", MOBILE_UA)
                    .header("Accept", "application/json")
                    .build()
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string()
                    if (resp.isSuccessful && body != null && body.startsWith("{")) {
                        return json.parseToJsonElement(body).jsonObject
                    }
                }
            } catch (e: Exception) {
                // 尝试下一个端点
            }
        }
        return null
    }

    /** 把 aweme JSON 对象解析为 VideoInfo */
    private fun parseAweme(obj: kotlinx.serialization.json.JsonObject): VideoInfo {
        val video = obj["video"]?.jsonObject
        val playAddr = video?.get("play_addr")?.jsonObject
        val coverObj = video?.get("cover")?.jsonObject
        val author = obj["author"]?.jsonObject

        val playUrls = playAddr?.get("url_list")?.jsonArray
            ?.map { it.jsonPrimitive.content }
            ?: emptyList()

        val coverUrls = coverObj?.get("url_list")?.jsonArray
            ?.map { it.jsonPrimitive.content }
            ?: emptyList()

        // bit_rate 里的多档清晰度
        val qualities = video?.get("bit_rate")?.jsonArray?.mapNotNull { brEl ->
            val br = brEl.jsonObject
            val qa = br["play_addr"]?.jsonObject ?: return@mapNotNull null
            val url = qa["url_list"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
                ?: return@mapNotNull null
            Quality(
                quality = br["quality_type"]?.jsonPrimitive?.content?.toIntOrNull(),
                width = qa["width"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                height = qa["height"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                url = url,
            )
        } ?: emptyList()

        return VideoInfo(
            awemeId = str(obj, "aweme_id"),
            title = str(obj, "desc"),
            author = author?.let { str(it, "nickname") } ?: "",
            durationMs = video?.get("duration")?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            width = video?.get("width")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            height = video?.get("height")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            cover = coverUrls.firstOrNull(),
            playUrl = playUrls.firstOrNull(),
            backupUrls = playUrls.drop(1),
            qualities = qualities,
            source = "douyin_feed",
        )
    }

    /** 安全取字符串字段 */
    private fun str(obj: kotlinx.serialization.json.JsonObject, key: String): String =
        obj[key]?.jsonPrimitive?.content ?: ""

    private fun jsonArrayOf(): JsonArray = JsonArray(emptyList())
}
