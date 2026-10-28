package ir.matincode.matinai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("matin_ai", MODE_PRIVATE)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF9B8CFF),
                    background = Color(0xFF10111A),
                    surface = Color(0xFF191B28)
                )
            ) {
                MatinAiScreen(
                    initialKey = prefs.getString("api_key", "") ?: "",
                    initialBaseUrl = prefs.getString("base_url", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL,
                    initialModel = prefs.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL,
                    onSave = { key, url, model ->
                        prefs.edit()
                            .putString("api_key", key)
                            .putString("base_url", url)
                            .putString("model", model)
                            .apply()
                    }
                )
            }
        }
    }
}

private const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
private const val DEFAULT_MODEL = "gpt-4.1-mini"

private data class ChatMessage(val role: String, val text: String)

/**
 * Builds the chat-completions endpoint from a user-supplied base URL.
 * Accepts forms like:
 *   https://api.openai.com/v1            -> .../v1/chat/completions
 *   https://api.openai.com/v1/           -> .../v1/chat/completions
 *   https://openrouter.ai/api/v1         -> .../v1/chat/completions
 *   https://host/v1/chat/completions     -> kept as-is
 *   https://host                         -> .../host/v1/chat/completions
 */
private fun chatEndpoint(base: String): String {
    val b = base.trim().trimEnd('/')
    return when {
        b.endsWith("/chat/completions") -> b
        b.endsWith("/v1") -> "$b/chat/completions"
        else -> "$b/v1/chat/completions"
    }
}

@Composable
private fun MatinAiScreen(
    initialKey: String,
    initialBaseUrl: String,
    initialModel: String,
    onSave: (key: String, baseUrl: String, model: String) -> Unit
) {
    var apiKey by remember { mutableStateOf(initialKey) }
    var baseUrl by remember { mutableStateOf(initialBaseUrl) }
    var model by remember { mutableStateOf(initialModel) }
    var showSettings by remember { mutableStateOf(initialKey.isBlank()) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                "assistant",
                "سلام! من متین AI هستم، دستیار شخصی فارسی‌زبان. سوالت رو بپرس تا شروع کنیم.\n" +
                    "Hi! I'm Matin AI, your Persian personal assistant. Ask me anything!"
            )
        )
    }
    val scope = rememberCoroutineScope()
    val client = remember {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
    val bg = Color(0xFF10111A)
    val panel = Color(0xFF191B28)
    val accent = Color(0xFF9B8CFF)

    Column(
        modifier = Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(panel).padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Matin AI", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("دستیار هوشمند فارسی · Persian AI Assistant", fontSize = 12.sp, color = Color.LightGray)
            }
            TextButton(onClick = { showSettings = !showSettings }) {
                Text(if (showSettings) "بازگشت به چت · Chat" else "تنظیمات · Settings")
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
                    "کلید API یک سرویس سازگار با OpenAI را وارد کن. استفاده از API ممکن است هزینه داشته باشد.\n" +
                        "Enter the API key of an OpenAI-compatible service. API usage may incur charges.",
                    color = Color.LightGray
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key — کلید API") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
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
                    "مثال: https://api.openai.com/v1 یا https://openrouter.ai/api/v1\n" +
                        "e.g. https://api.openai.com/v1 or https://openrouter.ai/api/v1",
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
                    "مثال: gpt-4.1-mini · google/gemini-2.0-flash-exp:free\n" +
                        "e.g. gpt-4.1-mini (OpenAI) or a free OpenRouter model",
                    color = Color(0xFF8F96B3), fontSize = 11.sp
                )
                Button(
                    onClick = {
                        val k = apiKey.trim()
                        val u = baseUrl.trim().ifBlank { DEFAULT_BASE_URL }
                        val m = model.trim().ifBlank { DEFAULT_MODEL }
                        onSave(k, u, m)
                        apiKey = k; baseUrl = u; model = m
                        showSettings = false
                        error = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("ذخیره · Save") }
                Text(
                    "کلید روی همین گوشی ذخیره می‌شود. آن را با کسی به اشتراک نگذار و داخل مخزن عمومی GitHub قرار نده.\n" +
                        "Your key is stored only on this device. Never share it or commit it to GitHub.",
                    color = Color(0xFFFFD180),
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(
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
                                color = accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(message.text, color = Color.White, fontSize = 15.sp)
                        }
                    }
                }
                if (busy) item {
                    Text("در حال دریافت پاسخ... · Thinking", color = Color.LightGray, modifier = Modifier.padding(8.dp))
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
                    placeholder = { Text("پیامت رو بنویس... · Type your message") },
                    enabled = !busy,
                    maxLines = 4
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = !busy && input.isNotBlank(),
                    onClick = {
                        val prompt = input.trim()
                        input = ""
                        messages.add(ChatMessage("user", prompt))
                        error = ""
                        if (apiKey.isBlank()) {
                            error = "اول از بخش تنظیمات، کلید API را وارد کن. · Add your API key in Settings first."
                            showSettings = true
                        } else {
                            busy = true
                            scope.launch {
                                try {
                                    val reply = withContext(Dispatchers.IO) {
                                        val history = JSONArray()
                                        history.put(JSONObject().put("role", "system").put("content",
                                            "تو Matin AI هستی، دستیار شخصی فارسی‌زبان. دقیق، محترمانه و کاربردی جواب بده. " +
                                                "You are Matin AI, a Persian-speaking personal assistant; answer precisely and politely."))
                                        messages.takeLast(20).forEach {
                                            history.put(JSONObject().put("role", it.role).put("content", it.text))
                                        }
                                        val body = JSONObject()
                                            .put("model", model.trim().ifBlank { DEFAULT_MODEL })
                                            .put("messages", history)
                                            .toString()
                                            .toRequestBody("application/json; charset=utf-8".toMediaType())
                                        val request = Request.Builder()
                                            .url(chatEndpoint(baseUrl))
                                            .header("Authorization", "Bearer ${apiKey.trim()}")
                                            .post(body)
                                            .build()
                                        client.newCall(request).execute().use { response ->
                                            val text = response.body?.string().orEmpty()
                                            if (!response.isSuccessful) {
                                                val detail = try {
                                                    JSONObject(text).optJSONObject("error")?.optString("message")
                                                } catch (_: Exception) { null }
                                                throw IllegalStateException(detail?.takeIf { it.isNotBlank() }
                                                    ?: "خطای سرویس · HTTP ${response.code}")
                                            }
                                            JSONObject(text).optJSONArray("choices")
                                                ?.optJSONObject(0)?.optJSONObject("message")
                                                ?.optString("content")?.takeIf { it.isNotBlank() }
                                                ?: "پاسخی دریافت نشد. · No response received."
                                        }
                                    }
                                    messages.add(ChatMessage("assistant", reply))
                                } catch (e: Exception) {
                                    error = e.message ?: "ارتباط با سرویس برقرار نشد. · Could not reach the service."
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    }
                ) { Text("ارسال") }
            }
        }
    }
}
