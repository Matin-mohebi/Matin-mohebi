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
    "تو Matin AI هستی، دستیار شخصی فارسی‌زبان. دقیق، محترمانه و کاربردی جواب بده. " +
        "اگر کاربر انگلیسی پرسید، انگلیسی جواب بده. پاسخ‌ها را با مارک‌داون مرتب کن. " +
        "You are Matin AI, a Persian-speaking personal assistant. Be precise, polite and practical."
private const val GREETING =
    "سلام! من متین AI هستم، دستیار شخصی فارسی‌زبان. 👋\n" +
        "همه‌چیز خودکار وصل است — بدون توکن و بدون ثبت‌نام؛ فقط سوالت رو بپرس!\n" +
        "Hi! I'm Matin AI — zero config, no token needed. Just ask!"

private data class ChatMessage(val role: String, val text: String)
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
            msgs.put(JSONObject().put("role", m.role).put("text", m.text))
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
                msgs.add(ChatMessage(m.optString("role"), m.optString("text")))
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

    /** Fetch the model list live from the server (zero-config: no key needed for the default). */
    fun fetchModels(url: String) {
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
            if (ids.isNotEmpty()) modelList = ids
            modelsLoading = false
        }
    }
    // auto-load the model list when the settings screen opens
    LaunchedEffect(showSettings) {
        if (showSettings && modelList.isEmpty()) fetchModels(baseUrl)
    }

    fun newChat() {
        if (busy) currentCall?.cancel()
        val c = Chat(UUID.randomUUID().toString(), "", mutableListOf(ChatMessage("assistant", GREETING)), System.currentTimeMillis())
        chats.add(c)
        activeId = c.id
        error = ""
    }

    fun startGeneration() {
        error = ""
        busy = true
        messages.add(ChatMessage("assistant", ""))
        val idx = messages.lastIndex
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val history = JSONArray()
                    history.put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    messages.subList(0, idx).takeLast(24).forEach {
                        if (it.text.isNotBlank()) history.put(JSONObject().put("role", it.role).put("content", it.text))
                    }
                    val body = JSONObject()
                        .put("model", model.trim().ifBlank { DEFAULT_MODEL })
                        .put("messages", history)
                        .put("stream", true)
                        .toString()
                        .toRequestBody("application/json; charset=utf-8".toMediaType())
                    // v1.5.0 — Authorization only when a key exists (default provider needs none)
                    val reqBuilder = Request.Builder().url(chatEndpoint(baseUrl)).post(body)
                    val key = apiKey.trim()
                    if (key.isNotBlank()) reqBuilder.header("Authorization", "Bearer $key")
                    val request = reqBuilder.build()
                    val call = client.newCall(request)
                    currentCall = call
                    val acc = StringBuilder()
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
                    currentCall = null
                    val finalText = acc.toString().ifBlank { "پاسخی دریافت نشد · No response." }
                    if (idx < messages.size) messages[idx] = ChatMessage("assistant", finalText)
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
                        model.trim().ifBlank { DEFAULT_MODEL } + " · " + providerName(baseUrl),
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
                        "✅ همه‌چیز خودکار است — بدون توکن، بدون ثبت‌نام. فقط برگرد و بنویس!\n" +
                            "Everything is automatic — no token, no signup. This screen is only for changing the AI service.",
                        color = Color(0xFF7FE0AC), fontSize = 13.sp
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
