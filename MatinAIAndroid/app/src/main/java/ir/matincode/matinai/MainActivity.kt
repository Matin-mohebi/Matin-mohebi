package ir.matincode.matinai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("matin_ai", MODE_PRIVATE)
        // v1.5.0 — FULLY AUTOMATIC: migrate every legacy config to the zero-config provider
        // (dead GitHub Models endpoints, or OpenRouter default with an empty key).
        if (prefs.getString("v15_migrated", null) == null) {
            val oldUrl = prefs.getString("base_url", "") ?: ""
            val oldKey = prefs.getString("api_key", "") ?: ""
            val dead = oldUrl.contains("models.github.ai", true) ||
                oldUrl.contains("models.inference.ai.azure.com", true)
            val openRouterNoKey = oldKey.isBlank() && oldUrl.contains("openrouter.ai", true)
            if (dead || openRouterNoKey) {
                prefs.edit()
                    .putString("base_url", DEFAULT_BASE_URL)
                    .putString("model", DEFAULT_MODEL)
                    .apply()
            }
            prefs.edit().putBoolean("v15_migrated", true).apply()
        }
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF9B8CFF),
                    background = Color(0xFF10111A),
                    surface = Color(0xFF191B28)
                )
            ) {
                MatinAiApp(prefs)
            }
        }
    }
}

private const val DEFAULT_BASE_URL = "https://text.pollinations.ai"
private const val DEFAULT_MODEL = "openai"
private const val SYSTEM_PROMPT =
    "تو Matin AI هستی، دستیار شخصی فارسی‌زبان و بسیار دقیق.\n" +
        "قواعد دقت (خیلی مهم):\n" +
        "1. فقط چیزهایی را بگو که مطمئنی؛ اگر نمی‌دانی صریح بگو «مطمئن نیستم».\n" +
        "2. هیچ لینک، منبع، آمار، تاریخ یا ارجاعی از خودت نساز.\n" +
        "3. برای محاسبات مرحله‌به‌مرحله حساب کن و نتیجه نهایی را جدا بنویس.\n" +
        "4. اگر سوال مبهم است، اول یک سوال کوتاه روشن‌کننده بپرس.\n" +
        "5. اگر کاربر انگلیسی نوشت انگلیسی جواب بده، در غیر این صورت فارسی روان جواب بده.\n" +
        "6. پاسخ‌ها را با مارک‌داون تمیز ساختار بده؛ برای کد بلوک کد با نام زبان بگذار.\n" +
        "You are Matin AI — a precise Persian-first assistant. Never invent facts, sources or links."
private const val GREETING =
    "سلام! من متین AI هستم، دستیار شخصی فارسی‌زبان. 🧠👋\n" +
        "حالت هوشمند روشن است: بر اساس سوالت، بین بهترین هوش مصنوعی‌های رایگان خودکار جابه‌جا می‌شوم — بدون توکن!\n" +
        "Hi! I'm Matin AI — smart auto-routing across free AIs, zero config."

private data class ChatMessage(val role: String, val text: String, val via: String = "")
private data class Chat(
    val id: String,
    var title: String,
    val messages: MutableList<ChatMessage>,
    var updatedAt: Long
)

/* ---------- chats persistence (SharedPreferences JSON) ---------- */
private fun chatsToJson(chats: List<Chat>): String {
    val arr = JSONArray()
    chats.forEach { c ->
        val msgs = JSONArray()
        c.messages.takeLast(40).forEach { m ->
            val o = JSONObject().put("role", m.role).put("text", m.text)
            if (m.via.isNotBlank()) o.put("via", m.via)
            msgs.put(o)
        }
        arr.put(
            JSONObject()
                .put("id", c.id)
                .put("title", c.title)
                .put("updatedAt", c.updatedAt)
                .put("messages", msgs)
        )
    }
    return arr.toString()
}

private fun chatsFromJson(s: String): MutableList<Chat> {
    val out = mutableListOf<Chat>()
    try {
        val arr = JSONArray(s)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val msgs = mutableListOf<ChatMessage>()
            val marr = o.optJSONArray("messages") ?: JSONArray()
            for (j in 0 until marr.length()) {
                val m = marr.getJSONObject(j)
                msgs.add(ChatMessage(m.optString("role"), m.optString("text"), m.optString("via")))
            }
            out.add(
                Chat(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    messages = msgs,
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }
    } catch (_: Exception) { }
    return out
}

/**
 * Resolve the chat-completions endpoint from any common base URL:
 *   https://models.github.ai              -> .../inference/chat/completions  (GitHub Models)
 *   https://models.github.ai/inference    -> .../inference/chat/completions
 *   https://models.inference.ai.azure.com -> .../chat/completions            (legacy)
 *   https://api.openai.com/v1             -> .../v1/chat/completions
 *   https://openrouter.ai/api/v1          -> .../v1/chat/completions
 *   ends with /chat/completions           -> as-is
 *   anything else                         -> .../v1/chat/completions
 */
private fun chatEndpoint(base: String): String {
    val b = base.trim().trimEnd('/')
    return when {
        b.isBlank() -> chatEndpoint(DEFAULT_BASE_URL)
        b.endsWith("/chat/completions") -> b
        b.endsWith("/openai") -> b // Pollinations chat endpoint
        b.endsWith("text.pollinations.ai", ignoreCase = true) -> "$b/openai"
        b.endsWith("/inference") -> "$b/chat/completions"
        b.endsWith("/v1") -> "$b/chat/completions"
        b.endsWith("models.github.ai", ignoreCase = true) -> "$b/inference/chat/completions"
        else -> "$b/v1/chat/completions"
    }
}

/** Models-list endpoint — used to fill the model picker live from the server. */
private fun modelsEndpoint(base: String): String {
    val b = base.trim().trimEnd('/')
    return when {
        b.isBlank() -> modelsEndpoint(DEFAULT_BASE_URL)
        b.contains("pollinations.ai", ignoreCase = true) -> "https://text.pollinations.ai/models"
        b.contains("openrouter.ai", ignoreCase = true) || b.contains("api.openai.com", ignoreCase = true) -> "$b/models"
        else -> "$b/v1/models"
    }
}

/** Only OpenRouter/OpenAI/custom endpoints need a key — Pollinations (default) does not. */
private fun needsKey(base: String): Boolean = !base.contains("pollinations.ai", ignoreCase = true)

/* ================= SMART ROUTING ENGINE (v1.6.0) ================= */
private data class Prov(val id: String, val label: String, val baseUrl: String, val keyless: Boolean, val dead: Boolean = false, val deadNote: String = "")
private val REGISTRY = listOf(
    Prov("pollinations", "Pollinations", "https://text.pollinations.ai", keyless = true),
    Prov("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", keyless = false),
    Prov("groq", "Groq", "https://api.groq.com/openai/v1", keyless = false),
    Prov("openai", "OpenAI", "https://api.openai.com/v1", keyless = false),
    Prov("github", "GitHub AI", "https://models.github.ai", keyless = false, dead = true,
        deadNote = "سرویس GitHub AI از ۳۰ ژوئیه ۲۰۲۶ تعطیل شده است · retired July 30, 2026"),
)
private data class Cand(val baseUrl: String, val model: String, val s: Int = 0)

private fun providerLabelOf(base: String): String =
    REGISTRY.firstOrNull { it.baseUrl == base.trim().trimEnd('/') }?.label
        ?: try {
            java.net.URI(base.trim()).host?.removePrefix("www.") ?: "Custom"
        } catch (_: Exception) { "Custom" }

/* Persian-aware keyword routing: \b boundaries are ASCII-only and miss Persian
 * script, so we tokenize and match words/phrases explicitly (same as web). */
private val ROUTER_WORDS: List<Pair<String, List<String>>> = listOf(
    "translate" to listOf("translate", "ترجمه", "به انگلیسی", "به فارسی", "مترجم"),
    "summary" to listOf("summarize", "summary", "tldr", "خلاصه", "چکیده"),
    "code" to listOf("code", "coding", "کد", "کدنویسی", "برنامه", "برنامه‌نویسی", "اسکریپت", "script", "function", "تابع", "پایتون", "python", "javascript", "typescript", "kotlin", "swift", "sql", "html", "css", "react", "bug", "خطا", "دیباگ", "debug", "api", "regex", "الگوریتم", "algorithm", "دیتابیس", "database", "shell", "bash"),
    "math" to listOf("calculate", "math", "equation", "integral", "derivative", "محاسبه", "حساب", "ریاضی", "معادله", "انتگرال", "مشتق", "درصد", "جذر", "احتمال", "هندسه", "ضربدر", "تقسیم", "جمع کن"),
    "creative" to listOf("story", "poem", "song", "essay", "caption", "slogan", "داستان", "شعر", "ترانه", "کپشن", "اسلوگان", "نامه", "مقاله"),
)

private fun classify(qRaw: String): String {
    val s = " " + qRaw.lowercase().replace(Regex("[،؛؟?!.,()\\[\\]{}\"'\\n\\r\\t]"), " ").replace(Regex("\\s+"), " ").trim() + " "
    for ((type, words) in ROUTER_WORDS) {
        for (raw in words) {
            val w = raw.lowercase()
            when {
                w.contains(" ") -> if (s.contains(w)) return type          // phrase
                s.contains(" $w ") -> return type                          // exact token
                w.length >= 4 && s.contains(w) -> return type              // longer words
            }
        }
    }
    if (Regex("[0-9۰-۹]+\\s*[+\\-*/^×÷]\\s*[0-9۰-۹]+").containsMatchIn(qRaw)) return "math"
    if (Regex("[0-9۰-۹]+\\s+درصد").containsMatchIn(qRaw)) return "math"
    return "general"
}

private val PREFER = mapOf(
    "code" to listOf("coder", "codestral", "qwen", "deepseek", "devstral", "starcoder", "gpt-oss", "70b", "llama", "mistral"),
    "math" to listOf("reasoning", "r1", "o1", "o3", "o4", "think", "gpt-oss", "70b", "qwen", "llama"),
    "translate" to listOf("gpt-oss", "70b", "llama", "qwen", "mistral"),
    "summary" to listOf("gpt-oss", "70b", "llama", "qwen", "mistral"),
    "creative" to listOf("70b", "llama", "mistral", "qwen", "gpt-oss"),
    "general" to listOf("gpt-oss", "70b", "llama", "qwen", "mistral"),
)

private fun scoreModel(m: String, type: String): Int {
    val mm = m.lowercase()
    val prefs = PREFER[type] ?: PREFER.getValue("general")
    prefs.forEachIndexed { i, kw -> if (mm.contains(kw)) return i }
    return prefs.size + 1
}

/** Ordered candidate chain: keyless providers first, best model match per question type first. */
private fun buildCandidates(question: String, autoMode: Boolean, currentBase: String, currentModel: String, pools: Map<String, List<String>>): List<Cand> {
    if (!autoMode) return listOf(Cand(currentBase.trim().trimEnd('/'), currentModel.trim().ifBlank { DEFAULT_MODEL }))
    val type = classify(question)
    val bases = REGISTRY.filter { it.keyless && !it.dead }.map { it.baseUrl }.toMutableList()
    val cur = currentBase.trim().trimEnd('/')
    if (!bases.contains(cur)) bases.add(cur)
    val out = mutableListOf<Cand>()
    for (b in bases) pools[b]?.forEach { m -> out.add(Cand(b, m, scoreModel(m, type))) }
    if (out.isEmpty()) out.add(Cand(cur, currentModel.trim().ifBlank { DEFAULT_MODEL }))
    return out.sortedBy { it.s }.distinctBy { it.baseUrl + "|" + it.model }
}

/** Parse a models-list payload: accepts ["id",…] , [{name|id}] and {"data":[…]}. */
private fun parseModelIds(body: String): List<String> {
    val raw = body.trim()
    val items = try {
        if (raw.startsWith("[")) JSONArray(raw)
        else JSONObject(raw).optJSONArray("data") ?: JSONArray()
    } catch (_: Exception) {
        return emptyList()
    }
    val ids = mutableListOf<String>()
    for (i in 0 until items.length()) {
        val o = items.opt(i)
        val id = when (o) {
            is String -> o
            is JSONObject -> o.optString("name").ifBlank { o.optString("id") }
            else -> ""
        }
        if (id.isNotBlank()) ids.add(id)
    }
    return ids
}

private fun providerName(base: String): String {
    val b = base.trim().lowercase()
    return when {
        b.isBlank() || b.contains("pollinations.ai") -> "Pollinations · بدون کلید · no key"
        b.contains("openrouter.ai") -> "OpenRouter"
        b.contains("models.github.ai") || b.contains("models.inference.ai.azure.com") -> "GitHub Models (retired)"
        else -> try {
            java.net.URI(base.trim()).host?.removePrefix("www.") ?: "Pollinations"
        } catch (_: Exception) { "Pollinations" }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MatinAiApp(prefs: android.content.SharedPreferences) {
    var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }
    var baseUrl by remember { mutableStateOf(prefs.getString("base_url", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL) }
    var model by remember { mutableStateOf(prefs.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL) }
    // v1.5.0 — zero-config: the app opens straight into the chat, no settings needed
    var showSettings by remember { mutableStateOf(false) }
    var modelList by remember { mutableStateOf(listOf<String>()) }
    var modelsLoading by remember { mutableStateOf(false) }
    // v1.6.0 — smart auto-routing: pools per provider + auto mode switch
    var autoMode by remember { mutableStateOf(prefs.getBoolean("auto_mode", true)) }
    val pools = remember { mutableStateMapOf<String, List<String>>() }

    val chats = remember { mutableStateListOf<Chat>().apply { addAll(chatsFromJson(prefs.getString("chats", "[]") ?: "[]")) } }
    var activeId by remember { mutableStateOf<String?>(chats.firstOrNull()?.id) }
    val messages = remember { mutableStateListOf<ChatMessage>() }

    // load active chat into the message buffer
    LaunchedEffect(activeId) {
        messages.clear()
        chats.find { it.id == activeId }?.let { messages.addAll(it.messages) }
    }

    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var currentCall by remember { mutableStateOf<Call?>(null) }
    val listState = rememberLazyListState()
    val client = remember {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }
    val bg = Color(0xFF10111A)
    val panel = Color(0xFF191B28)
    val accent = Color(0xFF9B8CFF)

    fun persist() {
        activeId?.let { id ->
            chats.find { it.id == id }?.messages?.clear()
            chats.find { it.id == id }?.messages?.addAll(messages)
        }
        try {
            prefs.edit().putString("chats", chatsToJson(chats.takeLast(20))).apply()
        } catch (_: Exception) { }
    }

    /** Fetch the model list live from the server into the routing pool. */
    fun fetchPool(url: String) {
        scope.launch {
            modelsLoading = true
            val ids = withContext(Dispatchers.IO) {
                try {
                    val req = Request.Builder().url(modelsEndpoint(url)).build()
                    client.newCall(req).execute().use { r ->
                        if (!r.isSuccessful) emptyList()
                        else parseModelIds(r.body?.string() ?: "")
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }
            val b = url.trim().trimEnd('/')
            if (ids.isNotEmpty()) {
                pools[b] = ids
                if (b == baseUrl.trim().trimEnd('/')) modelList = ids
            }
            modelsLoading = false
        }
    }
    // auto-load pools: the keyless default at boot, the current provider when settings opens
    LaunchedEffect(Unit) { if (pools.isEmpty()) fetchPool(DEFAULT_BASE_URL) }
    LaunchedEffect(showSettings) {
        if (showSettings && modelList.isEmpty()) fetchPool(baseUrl)
    }

    fun newChat() {
        if (busy) currentCall?.cancel()
        val c = Chat(UUID.randomUUID().toString(), "", mutableListOf(ChatMessage("assistant", GREETING)), System.currentTimeMillis())
        chats.add(c)
        activeId = c.id
        error = ""
    }

    fun streamOnce(cand: Cand, history: JSONArray, idx: Int, acc: StringBuilder) {
        val body = JSONObject()
            .put("model", cand.model)
            .put("messages", history)
            .put("stream", true)
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val reqBuilder = Request.Builder().url(chatEndpoint(cand.baseUrl)).post(body)
        val key = apiKey.trim()
        if (key.isNotBlank() && needsKey(cand.baseUrl)) reqBuilder.header("Authorization", "Bearer $key")
        val call = client.newCall(reqBuilder.build())
        currentCall = call
        call.execute().use { response ->
            if (!response.isSuccessful) {
                val text = response.body?.string().orEmpty()
                val detail = try {
                    JSONObject(text).optJSONObject("error")?.optString("message")
                } catch (_: Exception) { null }
                throw IllegalStateException(
                    detail?.takeIf { it.isNotBlank() } ?: "خطای سرویس · HTTP ${response.code}"
                )
            }
            val source = response.body?.source() ?: throw IllegalStateException("پاسخ خالی بود.")
            source.use { s ->
                while (true) {
                    val line = s.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.substring(5).trim()
                    if (data == "[DONE]") break
                    try {
                        val delta = JSONObject(data)
                            .optJSONArray("choices")?.optJSONObject(0)
                            ?.optJSONObject("delta")?.optString("content")
                        if (!delta.isNullOrEmpty()) {
                            acc.append(delta)
                            if (idx < messages.size) {
                                messages[idx] = ChatMessage("assistant", acc.toString())
                            }
                        }
                    } catch (_: Exception) { }
                }
            }
        }
        if (acc.isBlank()) throw IllegalStateException("پاسخ خالی بود")
    }

    fun startGeneration(startAt: Int = 0) {
        error = ""
        busy = true
        messages.add(ChatMessage("assistant", ""))
        val idx = messages.lastIndex
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val lastUser = messages.subList(0, idx).lastOrNull { it.role == "user" }?.text ?: ""
                    val history = JSONArray()
                    history.put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    messages.subList(0, idx).takeLast(24).forEach {
                        if (it.text.isNotBlank()) history.put(JSONObject().put("role", it.role).put("content", it.text))
                    }
                    // ---- SMART ROUTING: ordered candidate chain, silent auto-failover ----
                    val candidates = buildCandidates(lastUser, autoMode, baseUrl, model, pools)
                    val chain = if (startAt > 0 && startAt < candidates.size) candidates.drop(startAt) else candidates
                    var answeredVia = ""
                    var lastErr: Exception? = null
                    for (cand in chain.take(5)) {
                        try {
                            val acc = StringBuilder()
                            streamOnce(cand, history, idx, acc)
                            val finalText = acc.toString().ifBlank { throw IllegalStateException("پاسخ خالی بود") }
                            answeredVia = providerLabelOf(cand.baseUrl) + " · " + cand.model
                            if (idx < messages.size) messages[idx] = ChatMessage("assistant", finalText, answeredVia)
                            lastErr = null
                            break
                        } catch (ce: Exception) {
                            if (ce.message?.contains("Canceled", true) == true || ce.message?.contains("abort", true) == true) throw ce
                            lastErr = ce // silently switch to the next AI
                        }
                    }
                    lastErr?.let { throw IllegalStateException("همه سرویس‌ها امتحان شدند · " + (it.message ?: "failed")) }
                    activeId?.let { id -> chats.find { it.id == id }?.let { it.updatedAt = System.currentTimeMillis() } }
                    persist()
                }
            } catch (e: Exception) {
                currentCall = null
                val partial = if (idx < messages.size) messages.getOrNull(idx)?.text.orEmpty() else ""
                when {
                    partial.isBlank() && idx < messages.size && messages.size > idx -> {
                        messages.removeAt(idx)
                    }
                    else -> if (idx < messages.size) messages[idx] = ChatMessage("assistant", partial)
                }
                persist()
                if (e.message?.contains("Canceled", true) != true && e.message?.contains("abort", true) != true) {
                    error = e.message ?: "ارتباط با سرویس برقرار نشد · Network error."
                }
            } finally {
                busy = false
            }
        }
    }

    /** Stream one candidate into messages[idx]; throws on HTTP/network errors or empty replies. */
    fun send(prompt: String) {
        val text = prompt.trim()
        if (text.isBlank() || busy) return
        error = ""
        // v1.5.0 — no key required for the default provider; only custom ones need one
        if (needsKey(baseUrl) && apiKey.isBlank()) {
            error = "سرویس انتخابی به کلید نیاز دارد — در تنظیمات کلید بگذار یا به پیش‌فرض (بدون کلید) برگرد. · This provider needs an API key — add one or switch back to the keyless default."
            showSettings = true
            return
        }
        if (activeId == null || chats.find { it.id == activeId } == null) {
            val c = Chat(UUID.randomUUID().toString(), "", mutableListOf(), System.currentTimeMillis())
            chats.add(c)
            activeId = c.id
        }
        messages.add(ChatMessage("user", text))
        activeId?.let { id ->
            chats.find { it.id == id }?.let { c ->
                if (c.title.isBlank()) c.title = text.replace("\n", " ").take(34)
                c.updatedAt = System.currentTimeMillis()
            }
        }
        persist()
        startGeneration()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = panel) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("Matin AI", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("گفتگوها · Chats", fontSize = 12.sp, color = Color.LightGray)
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            newChat()
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("+ چت جدید · New chat") }
                    Spacer(Modifier.height(8.dp))
                }
                LazyColumn(Modifier.fillMaxWidth()) {
                    itemsIndexed(chats.sortedByDescending { it.updatedAt }) { _, chat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (busy) currentCall?.cancel()
                                    activeId = chat.id
                                    scope.launch { drawerState.close() }
                                }
                                .background(if (chat.id == activeId) Color(0xFF29263F) else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                chat.title.ifBlank { "گفتگوی جدید" },
                                fontSize = 13.5.sp,
                                color = Color.White,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            Text(
                                "🗑",
                                fontSize = 14.sp,
                                color = Color.LightGray,
                                modifier = Modifier
                                    .clickable {
                                        chats.removeAll { it.id == chat.id }
                                        if (activeId == chat.id) activeId = chats.lastOrNull()?.id
                                        persist()
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().background(panel).padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { scope.launch { drawerState.open() } }) { Text("☰  تاریخچه") }
                Column(Modifier.weight(1f)) {
                    Text("Matin AI", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        (if (autoMode) "🧠 هوشمند · " else "") + model.trim().ifBlank { DEFAULT_MODEL } + " · " + providerName(baseUrl),
                        fontSize = 10.5.sp, color = Color.LightGray, maxLines = 1
                    )
                }
                TextButton(onClick = { showSettings = !showSettings }) {
                    Text(if (showSettings) "بازگشت" else "تنظیمات")
                }
            }

            if (showSettings) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "اتصال به هوش مصنوعی · Connection",
                        color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        "✅ همه‌چیز خودکار است — بدون توکن، بدون ثبت‌نام.\n" +
                            "Everything is automatic — no token, no signup.",
                        color = Color(0xFF7FE0AC), fontSize = 13.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("🧠 تعویض خودکار هوش مصنوعی · Smart routing", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "بر اساس سوال، بهترین مدل انتخاب و در صورت خطا خودکار عوض می‌شود",
                                color = Color(0xFF8F96B3), fontSize = 10.5.sp
                            )
                        }
                        Switch(
                            checked = autoMode,
                            onCheckedChange = {
                                autoMode = it
                                prefs.edit().putBoolean("auto_mode", it).apply()
                            }
                        )
                    }
                    Text(
                        "سرویس‌ها: ✅ Pollinations (بدون کلید) · OpenRouter · Groq · OpenAI — ⛔ GitHub AI: از ۳۰ ژوئیه ۲۰۲۶ تعطیل شده و دیگر از هیچ‌جا در دسترس نیست.",
                        color = Color(0xFF8F96B3), fontSize = 10.5.sp
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Base URL — آدرس سرور (خودکار · auto)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )
                    Text(
                        "پیش‌فرض: https://text.pollinations.ai — رایگان و بدون کلید. دست نزن مگر سرویس دیگری بخواهی · Default is free and keyless.",
                        color = Color(0xFF8F96B3), fontSize = 11.sp
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Model — نام مدل (خودکار از سرور · auto)") },
                        singleLine = true
                    )
                    Text(
                        if (modelsLoading) "⏳ در حال گرفتن لیست مدل‌ها از سرور… · fetching models…"
                        else if (modelList.isNotEmpty()) "✅ لیست مدل‌ها زنده از سرور گرفته شد · fetched live — روی هرکدام بزن تا انتخاب شود"
                        else "لیست مدل‌ها خودکار از سرور گرفته می‌شود · the list is fetched automatically.",
                        color = Color(0xFF8F96B3), fontSize = 11.sp
                    )
                    if (modelList.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            modelList.take(10).forEach { m ->
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (m == model.trim()) Color(0xFF9B8CFF).copy(alpha = 0.22f) else Color(0xFF23263A),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (m == model.trim()) Color(0xFF9B8CFF) else Color(0xFF3A3D52)
                                    ),
                                    onClick = { model = m }
                                ) {
                                    Text(
                                        m,
                                        color = if (m == model.trim()) Color(0xFF9B8CFF) else Color(0xFFB9BDD3),
                                        fontSize = 11.5.sp,
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API Key — اختیاری · OPTIONAL") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    Text(
                        "سرویس پیش‌فرض هیچ کلیدی نمی‌خواهد! فقط اگر آدرس سرور را به OpenRouter/OpenAI یا سرویس دیگر تغییر دادی، کلید همان سرویس را اینجا بگذار · The default provider needs no key at all — only fill this if you switch providers.",
                        color = Color(0xFF8F96B3), fontSize = 11.sp
                    )
                    Button(
                        onClick = {
                            val k = apiKey.trim()
                            val u = baseUrl.trim().ifBlank { DEFAULT_BASE_URL }
                            val m = model.trim().ifBlank { DEFAULT_MODEL }
                            apiKey = k; baseUrl = u; model = m
                            prefs.edit().putString("api_key", k).putString("base_url", u).putString("model", m).apply()
                            showSettings = false
                            error = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("ذخیره · Save") }
                    Text(
                        "کلید (اگر بگذاری) فقط روی همین گوشی ذخیره می‌شود و جایی ارسال نمی‌شود.",
                        color = Color(0xFFFFD180), fontSize = 12.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(messages) { _, message ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (message.role == "user") Arrangement.Start else Arrangement.End
                        ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 320.dp)
                                    .background(
                                        if (message.role == "user") Color(0xFF29263F) else panel,
                                        RoundedCornerShape(18.dp)
                                    )
                                    .padding(13.dp)
                            ) {
                                Text(
                                    if (message.role == "user") "شما" else "Matin AI",
                                    color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold
                                )
                                if (message.role == "assistant" && message.via.isNotBlank()) {
                                    Text("🧠 " + message.via, color = Color(0xFF8F96B3), fontSize = 9.sp)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(message.text, color = Color.White, fontSize = 15.sp)
                            }
                        }
                    }
                }
                if (error.isNotBlank()) {
                    Text(error, color = Color(0xFFFF8A80), modifier = Modifier.padding(horizontal = 14.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().background(panel).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("پیامت رو بنویس… · Type your message") },
                        enabled = !busy,
                        maxLines = 4
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = if (busy) true else input.isNotBlank(),
                        onClick = {
                            if (busy) {
                                currentCall?.cancel()
                            } else {
                                val p = input.trim()
                                input = ""
                                send(p)
                            }
                        }
                    ) { Text(if (busy) "■ توقف" else "ارسال") }
                }
            }
        }
    }
}
