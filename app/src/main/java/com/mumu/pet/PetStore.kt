package com.mumu.pet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/** MuMu 的云端存档快照。 */
data class PetState(
    val name: String = "MuMu",
    val mood: String = "idle",
    val moodValue: Int = 70,
    val energy: Int = 80,
    val intimacy: Int = 10
)

class SupabaseException(message: String) : Exception(message)

/**
 * MuMu 的云同步层。
 *
 * 直接用 HttpURLConnection 打 Supabase 的 Auth + PostgREST 接口，
 * 不引第三方 SDK，不添任何依赖，编译面最小。
 *
 * 约定：所有方法都是阻塞式，必须在工作线程上调用。
 */
object PetStore {

    private const val PREFS = "mumu_session"
    private const val K_TOKEN = "access_token"
    private const val K_REFRESH = "refresh_token"
    private const val K_UID = "user_id"
    private const val K_EMAIL = "email"

    private const val TIMEOUT_MS = 15000

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun apiKey(): String = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    private fun baseUrl(): String = BuildConfig.SUPABASE_URL.trimEnd('/')

    // ---------- 会话 ----------

    fun token(ctx: Context): String? =
        prefs(ctx).getString(K_TOKEN, null)?.takeIf { it.isNotEmpty() }

    fun userId(ctx: Context): String? =
        prefs(ctx).getString(K_UID, null)?.takeIf { it.isNotEmpty() }

    fun email(ctx: Context): String? =
        prefs(ctx).getString(K_EMAIL, null)?.takeIf { it.isNotEmpty() }

    fun isSignedIn(ctx: Context): Boolean = token(ctx) != null && userId(ctx) != null

    fun signOut(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    // ---------- Auth ----------

    fun signUp(ctx: Context, email: String, password: String) {
        val body = JSONObject().put("email", email).put("password", password).toString()
        val raw = call("POST", "/auth/v1/signup", body, null)
        persist(ctx, JSONObject(raw), email)
    }

    fun signIn(ctx: Context, email: String, password: String) {
        val body = JSONObject().put("email", email).put("password", password).toString()
        val raw = call("POST", "/auth/v1/token?grant_type=password", body, null)
        persist(ctx, JSONObject(raw), email)
    }

    private fun persist(ctx: Context, json: JSONObject, email: String) {
        val access = json.optString("access_token")
        if (access.isEmpty()) {
            throw SupabaseException("没有拿到 access_token。返回：$json")
        }
        val uid = json.optJSONObject("user")?.optString("id").orEmpty()
        if (uid.isEmpty()) {
            throw SupabaseException("返回里没有 user.id。返回：$json")
        }
        prefs(ctx).edit()
            .putString(K_TOKEN, access)
            .putString(K_REFRESH, json.optString("refresh_token"))
            .putString(K_UID, uid)
            .putString(K_EMAIL, email)
            .apply()
    }

    // ---------- pet_state ----------

    /** 读云端状态；如果是第一次，自动建一行。 */
    fun loadState(ctx: Context): PetState {
        val uid = requireUser(ctx)
        val path = "/rest/v1/pet_state?select=name,mood,mood_value,energy,intimacy" +
            "&user_id=eq.$uid&limit=1"
        val arr = JSONArray(call("GET", path, null, requireToken(ctx)))
        if (arr.length() > 0) return parseState(arr.getJSONObject(0))

        val created = call(
            "POST",
            "/rest/v1/pet_state",
            JSONObject().put("user_id", uid).toString(),
            requireToken(ctx),
            mapOf("Prefer" to "return=representation")
        )
        return parseState(firstObject(created))
    }

    fun saveState(ctx: Context, state: PetState) {
        val uid = requireUser(ctx)
        val body = JSONObject()
            .put("name", state.name)
            .put("mood", state.mood)
            .put("mood_value", state.moodValue)
            .put("energy", state.energy)
            .put("intimacy", state.intimacy)
            .put("last_seen_at", Instant.now().toString())
            .toString()
        call(
            "PATCH",
            "/rest/v1/pet_state?user_id=eq.$uid",
            body,
            requireToken(ctx),
            mapOf("Prefer" to "return=minimal")
        )
    }

    // ---------- pet_events / pet_memory ----------

    fun appendEvent(ctx: Context, type: String, payload: JSONObject = JSONObject()) {
        val uid = requireUser(ctx)
        val body = JSONObject()
            .put("user_id", uid)
            .put("type", type)
            .put("payload", payload)
            .toString()
        call(
            "POST",
            "/rest/v1/pet_events",
            body,
            requireToken(ctx),
            mapOf("Prefer" to "return=minimal")
        )
    }

    fun remember(ctx: Context, content: String, kind: String = "note", weight: Int = 1) {
        val uid = requireUser(ctx)
        val body = JSONObject()
            .put("user_id", uid)
            .put("kind", kind)
            .put("content", content)
            .put("weight", weight)
            .toString()
        call(
            "POST",
            "/rest/v1/pet_memory",
            body,
            requireToken(ctx),
            mapOf("Prefer" to "return=minimal")
        )
    }

    // ---------- 内部 ----------

    private fun requireToken(ctx: Context): String =
        token(ctx) ?: throw SupabaseException("还没登录")

    private fun requireUser(ctx: Context): String =
        userId(ctx) ?: throw SupabaseException("还没登录")

    private fun parseState(o: JSONObject) = PetState(
        name = o.optString("name", "MuMu"),
        mood = o.optString("mood", "idle"),
        moodValue = o.optInt("mood_value", 70),
        energy = o.optInt("energy", 80),
        intimacy = o.optInt("intimacy", 10)
    )

    private fun firstObject(raw: String): JSONObject {
        val t = raw.trim()
        if (t.isEmpty()) return JSONObject()
        if (t.startsWith("[")) return JSONArray(t).optJSONObject(0) ?: JSONObject()
        return JSONObject(t)
    }

    private fun call(
        method: String,
        path: String,
        body: String?,
        bearer: String?,
        extra: Map<String, String> = emptyMap()
    ): String {
        val conn = (URL(baseUrl() + path).openConnection() as HttpURLConnection)
        try {
            conn.requestMethod = method
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.doInput = true
            conn.setRequestProperty("apikey", apiKey())
            conn.setRequestProperty("Accept", "application/json")
            if (bearer != null) {
                conn.setRequestProperty("Authorization", "Bearer $bearer")
            }
            extra.forEach { (k, v) -> conn.setRequestProperty(k, v) }

            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }

            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.let { readAll(it) }
                .orEmpty()

            if (code !in 200..299) {
                throw SupabaseException("HTTP $code $method $path -> $text")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun readAll(input: InputStream): String =
        BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { br ->
            val sb = StringBuilder()
            var line = br.readLine()
            while (line != null) {
                sb.append(line)
                line = br.readLine()
            }
            sb.toString()
        }
}
