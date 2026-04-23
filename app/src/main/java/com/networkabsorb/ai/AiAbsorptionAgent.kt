package com.networkabsorb.ai

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class AgentStatus(
    val message: String = "מוכן לפקודה",
    val thinking: Boolean = false,
    val hasKey: Boolean = false
)

/**
 * AI agent powered by Claude that plans and narrates the absorption process.
 *
 * With a valid API key → calls claude-haiku-4-5 for intelligent planning.
 * Without a key → falls back to smart built-in rules (still useful).
 *
 * The agent:
 *  1. Plans what to absorb before starting (prioritizes for emergency scenarios)
 *  2. Narrates progress mid-absorption
 *  3. Generates an inventory report in SERVE mode
 *  4. Answers questions about what's cached
 */
@Singleton
class AiAbsorptionAgent @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AiAgent"
        private const val PREFS = "ai_agent_prefs"
        private const val KEY_API_KEY = "claude_api_key"
        private const val CLAUDE_API = "https://api.anthropic.com/v1/messages"
        private const val MODEL = "claude-haiku-4-5-20251001"
    }

    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    private val _status = MutableStateFlow(AgentStatus())
    val status: StateFlow<AgentStatus> = _status.asStateFlow()

    var apiKey: String?
        get() = prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY_API_KEY, value?.trim()).apply()
            _status.value = _status.value.copy(hasKey = !value.isNullOrBlank())
        }

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    init {
        _status.value = AgentStatus(
            message  = if (apiKey != null) "סוכן מוכן — מפתח API מוגדר" else "הגדר מפתח Claude API בהגדרות",
            hasKey   = apiKey != null
        )
    }

    // ─── Public actions ───────────────────────────────────────────────────────

    /** Called when user presses Start Absorbing. Returns strategic plan. */
    suspend fun planAbsorption(quotaMb: Int): String {
        _status.value = _status.value.copy(thinking = true, message = "מתכנן אסטרטגיית שאיבה...")
        val result = callClaude(
            systemPrompt = """
                אתה סוכן AI המנהל אפליקציית Virtual SIM לאנדרואיד.
                תפקידך: לתכנן שאיבת אינטרנט לשימוש חירום ללא רשת.
                ענה בעברית, ב-2-3 משפטים קצרים ומעשיים בלבד.
            """.trimIndent(),
            userMessage = "המשתמש רוצה לשאוב ${quotaMb}MB לשימוש חירום בחו\"ל. " +
                         "תכנן: מה לשאוב קודם, מה החשוב ביותר."
        ) ?: defaultPlan(quotaMb)

        _status.value = AgentStatus(message = result, thinking = false, hasKey = apiKey != null)
        return result
    }

    /** Called when absorption is active with live stats. */
    suspend fun reportProgress(absorbedMb: Float, quotaMb: Int, topDomains: List<String>) {
        if (absorbedMb < 1f) return
        val pct = (absorbedMb / quotaMb * 100).toInt()
        if (pct % 20 != 0 && pct != 5) return  // update every 20%

        _status.value = _status.value.copy(thinking = true)
        val domains = topDomains.take(4).joinToString(", ")
        val result = callClaude(
            systemPrompt = "אתה סוכן Virtual SIM. ענה בעברית, משפט אחד קצר.",
            userMessage  = "נשאב ${absorbedMb.toInt()}MB מתוך ${quotaMb}MB ($pct%). " +
                          "אתרים: $domains. מה הסטטוס?"
        ) ?: "נשאב ${"%.0f".format(absorbedMb)}MB — ממשיך..."

        _status.value = AgentStatus(message = result, thinking = false, hasKey = apiKey != null)
    }

    /** Called when Virtual SIM activates. Returns inventory summary. */
    suspend fun describeInventory(cachedMb: Float, entryCount: Int, topDomains: List<String>) {
        _status.value = _status.value.copy(thinking = true, message = "בודק מה זמין במטמון...")
        val domains = topDomains.take(5).joinToString(", ")
        val result = callClaude(
            systemPrompt = "אתה סוכן Virtual SIM. ענה בעברית, 2 משפטים, מה זמין offline.",
            userMessage  = "מטמון: ${"%.0f".format(cachedMb)}MB, $entryCount כתובות. " +
                          "אתרים: $domains. תאר מה המשתמש יכול לגשת אליו."
        ) ?: "יש ${"%.0f".format(cachedMb)}MB זמינים ($entryCount עמודים). תוכל לגלוש בתוכן השמור."

        _status.value = AgentStatus(message = result, thinking = false, hasKey = apiKey != null)
    }

    /** Returns an instant local message without API call (for when agent is idle). */
    fun setIdleMessage(msg: String) {
        _status.value = AgentStatus(message = msg, thinking = false, hasKey = apiKey != null)
    }

    // ─── Claude API call ──────────────────────────────────────────────────────

    private suspend fun callClaude(systemPrompt: String, userMessage: String): String? =
        withContext(Dispatchers.IO) {
            val key = apiKey ?: return@withContext null
            try {
                val payload = JSONObject().apply {
                    put("model", MODEL)
                    put("max_tokens", 200)
                    put("system", systemPrompt)
                    put("messages", JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", userMessage)
                        }
                    ))
                }.toString()

                val req = Request.Builder()
                    .url(CLAUDE_API)
                    .header("x-api-key", key)
                    .header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()

                val resp = http.newCall(req).execute()
                val body = resp.body?.string() ?: return@withContext null

                if (!resp.isSuccessful) {
                    Log.w(TAG, "Claude API error ${resp.code}: $body")
                    return@withContext null
                }

                JSONObject(body)
                    .getJSONArray("content")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()

            } catch (e: Exception) {
                Log.w(TAG, "Claude call failed: ${e.message}")
                null
            }
        }

    private fun defaultPlan(quotaMb: Int): String {
        return when {
            quotaMb >= 2048 -> "מתכנן שאיבת ${quotaMb}MB: ויקיפדיה, חדשות בינלאומיות, OpenStreetMap, APIs שימושיים, ומשאבי CDN נפוצים."
            quotaMb >= 500  -> "מתכנן ${quotaMb}MB: ויקיפדיה, BBC, חדשות, APIs חיוניים ועמודים נפוצים."
            else            -> "מתכנן ${quotaMb}MB: תוכן חיוני — ויקיפדיה, חדשות ו-APIs בסיסיים."
        }
    }
}
