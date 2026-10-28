package ir.matincode.matinai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
        // v1.4.0 — GitHub Models retired (2026-07-30): auto-migrate saved settings to OpenRouter
        if (prefs.getString("gh_migrated", null) == null) {
            val old = prefs.getString("base_url", "") ?: ""
            if (old.contains("models.github.ai", true) || old.contains("models.inference.ai.azure.com", true)) {
                prefs.edit()
                    .putString("base_url", DEFAULT_BASE_URL)
                    .putString("model", DEFAULT_MODEL)
                    .apply()
            }
            prefs.edit().putBoolean("gh_migrated", true).apply()
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

private const val DEFAULT_BASE_URL = "https://openrouter.ai/api/v1"
private const val DEFAULT_MODEL = "openrouter/free"
private const val SYSTEM_PROMPT =
    "تو Matin AI هستی، دستیار شخصی فارسی‌زبان. دقیق، محترمانه و کاربردی جواب بده. " +
        "اگر کاربر انگلیسی پرسید، انگلیسی جواب بده. پاسخ‌ها را با مارک‌داون مرتب کن. " +
        "You are Matin AI, a Persian-speaking personal assistant. Be precise, polite and practical."
private const val GREETING =
    "سلام! من متین AI هستم، دستیار شخصی فارسی‌زبان. سوالت رو بپرس تا شروع کنیم. 👋\n" +
        "Hi! I'm Matin AI, your Persian personal assistant — now powered by GitHub Models."

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
        b.endsWith("/inference") -> "$b/chat/completions"
        b.endsWith("/v1") -> "$b/chat/completions"
        b.endsWith("models.github.ai", ignoreCase = true) -> "$b/inference/chat/completions"
        b.endsWith("models.inference.ai.azure.com", ignoreCase = true) -> "$b/chat/completions"
        else -> "$b/v1/chat/completions"
    }
}

private fun providerName(base: String): String {
    val b = base.trim().lowercase()
    return when {
        b.isBlank(), b.contains("openrouter.ai") -> "OpenRouter"
        b.contains("models.github.ai") || b.contains("models.inference.ai.azure.com") -> "GitHub Models (retired)"
        else -> try {
            java.net.URI(base.trim()).host?.removePrefix("www.") ?: "OpenRouter"
        } catch (_: Exception) { "OpenRouter" }
    }
}

@Composable
private fun MatinAiApp(prefs: android.content.SharedPreferences) {
    var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }
    var baseUrl by remember { mutableStateOf(prefs.getString("base_url", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL) }
    var model by remember { mutableStateOf(prefs.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL) }
    var showSettings by remember { mutableStateOf((prefs.getString("api_key", "") ?: "").isBlank()) }

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
                    val request = Request.Builder()
                        .url(chatEndpoint(baseUrl))
                        .header("Authorization", "Bearer ${apiKey.trim()}")
                        .post(body)
                        .build()
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
        if (apiKey.isBlank()) {
            error = "اول از بخش تنظیمات، کلید API/توکن گیت‌هاب را وارد کن. · Add your API key / GitHub token first."
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
                        "با یک کلید رایگان از OpenRouter شروع کن، یا کلید هر سرویس سازگار با OpenAI را بگذار. (سرویس GitHub Models از ۳۰ ژوئیه ۲۰۲۶ تعطیل شده است.)",
                        color = Color.LightGray
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API Key — OpenRouter") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    Text(
                        "تنها چیزی که لازم داری: کلید رایگان از openrouter.ai/keys — ثبت‌نام با گوگل/گیت‌هاب، بعد Create Key · Get a free key at openrouter.ai/keys",
                        color = Color(0xFF8F96B3), fontSize = 11.sp
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Base URL — آدرس سرور") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )
                    Text(
                        "پیش‌فرض: https://openrouter.ai/api/v1 (رایگان) — سازگار با OpenAI، Gemini، Groq و… دست نزن مگر سرویس دیگری بخواهی.",
                        color = Color(0xFF8F96B3), fontSize = 11.sp
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Model — نام مدل") },
                        singleLine = true
                    )
                    Text(
                        "مثال: openrouter/free (خودکار، رایگان) · google/gemma-4-31b-it:free · nvidia/nemotron-3-ultra-550b-a55b:free",
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
                        "کلید فقط روی همین گوشی ذخیره می‌شود. آن را با کسی به اشتراک نگذار و داخل مخزن عمومی GitHub قرار نده.\n" +
                            "Your key is stored only on this device. Never share or commit it.",
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
