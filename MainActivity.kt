package com.example.helloworld

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import com.example.helloworld.ui.theme.HelloWorldTheme
import com.example.helloworld.network.ChatApi
import com.example.helloworld.network.ChatRequest
import com.example.helloworld.network.HistoryMessage
import com.example.helloworld.actions.AssistantActions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.util.*

private const val PERMANENT_CONTEXT_KEY = "PermanentContexts"
private const val CHAT_HISTORY_KEY = "ChatHistory"

class MainActivity : ComponentActivity() {

    private var textToSpeech: TextToSpeech? = null
    private lateinit var actions: AssistantActions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        actions = AssistantActions(applicationContext)

        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.setLanguage(Locale.getDefault())
            }
        }

        setContent {
            HelloWorldTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChatUI(
                        onSpeak = { text -> speak(text) },
                        onSetAlarm = { time, label -> actions.setAlarm(time, label) }
                    )
                }
            }
        }
    }

    private fun speak(text: String) {
        if (text.isNotBlank()) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}

@Composable
fun ChatUI(
    onSpeak: (String) -> Unit,
    onSetAlarm: (String, String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ChatAppPreferences", Context.MODE_PRIVATE) }
    val gson = remember { Gson() }

    var inputText by remember { mutableStateOf("") }
    // Default text
    var responseText by remember { mutableStateOf("Server reply will appear here.") }
    var isLoading by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }

    // --- HELPER FUNCTIONS ---
    fun loadPermanentContexts(): MutableList<String> {
        val json = prefs.getString(PERMANENT_CONTEXT_KEY, null)
        return if (json == null) mutableListOf() else gson.fromJson(json, object : TypeToken<MutableList<String>>() {}.type)
    }

    fun loadChatHistory(): MutableList<String> {
        val json = prefs.getString(CHAT_HISTORY_KEY, null)
        return if (json == null) mutableListOf() else gson.fromJson(json, object : TypeToken<MutableList<String>>() {}.type)
    }

    fun saveChatHistory(history: List<String>) {
        val json = gson.toJson(history)
        prefs.edit().putString(CHAT_HISTORY_KEY, json).apply()
    }

    fun savePermanentContexts(contexts: List<String>) {
        val json = gson.toJson(contexts)
        prefs.edit().putString(PERMANENT_CONTEXT_KEY, json).apply()
    }

    // --- SPEECH RECOGNITION ---
    fun startSpeechRecognition(launcher: ActivityResultLauncher<Intent>) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Toast.makeText(context, "Speech not available", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
        }
        launcher.launch(intent)
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0) ?: ""
            inputText = spokenText
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) startSpeechRecognition(speechLauncher)
    }

    // --- UI LAYOUT (Original Column Style) ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // INPUT FIELD
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Type your message...") },
            placeholder = { Text("e.g., 'Remind me to call mom'") },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )

        // BUTTON ROW
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val savedList = loadPermanentContexts()
                    val intent = Intent(context, SavedContextsActivity::class.java).apply {
                        putStringArrayListExtra("CONTEXT_LIST", ArrayList(savedList))
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.weight(1f)
            ) { Text("View Contexts") }

            Button(
                onClick = {
                    val savedList = loadChatHistory()
                    val intent = Intent(context, ChatHistoryActivity::class.java).apply {
                        putStringArrayListExtra("CHAT_HISTORY_LIST", ArrayList(savedList))
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.weight(1f)
            ) { Text("Chat History") }
        }

        // NEW CHAT BUTTON
        Button(
            onClick = {
                prefs.edit().remove(CHAT_HISTORY_KEY).apply()
                responseText = "New chat started."
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text("New Chat") }

        Spacer(modifier = Modifier.height(16.dp))

        // SEND BUTTON
        Button(
            onClick = {
                if (isLoading || inputText.isBlank()) return@Button
                isLoading = true

                // We won't update responseText to "Sending..." to avoid flickering if it errors immediately,
                // or you can keep it if you prefer visual feedback.
                // responseText = "Sending..."

                val chatHistory = loadChatHistory()
                val contexts = loadPermanentContexts()

                // 1. Add User Message
                chatHistory.add("User: $inputText")
                saveChatHistory(chatHistory)

                // Clear input
                inputText = ""

                // 2. Prepare API
                val apiHistory = chatHistory.map {
                    if (it.startsWith("User: ")) HistoryMessage("user", it.removePrefix("User: "))
                    else HistoryMessage("model", it.removePrefix("AI: "))
                }

                val request = ChatRequest(
                    permanentContext = contexts.joinToString("\n"),
                    chatHistory = apiHistory
                )

                coroutineScope.launch {
                    try {
                        // Optional: clear text or show "..."
                        // responseText = "..."

                        val response = ChatApi.service.chat(request)

                        // CHECK FOR ERRORS FIRST
                        if (response.type == "error") {
                            // ERROR: Show Toast only. Do NOT update responseText.
                            if (response.errorType == "model_overloaded") {
                                Toast.makeText(context, "AI is overloaded. Please try again.", Toast.LENGTH_LONG).show()
                            } else {
                                val errorMsg = response.message ?: "Unknown error"
                                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                            }
                        }
                        else {
                            // SUCCESS: Now it is safe to update the UI

                            // 1. Handle Text
                            val aiText = response.message ?: response.content ?: ""
                            if (aiText.isNotBlank()) {
                                chatHistory.add("AI: $aiText")
                                saveChatHistory(chatHistory)
                                responseText = aiText // <--- Only update here on success
                                onSpeak(aiText)
                            }

                            // 2. Handle Tools
                            if ((response.type == "multi_tool_result" || response.type == "text") && response.results != null) {
                                response.results.forEach { tool ->
                                    when(tool.type) {
                                        "alarm" -> {
                                            onSetAlarm(tool.time ?: "00:00", tool.label ?: "Alarm")
                                        }
                                        "alarm_exists" -> {
                                            Toast.makeText(context, tool.message ?: "Alarm exists.", Toast.LENGTH_SHORT).show()
                                        }
                                        "context_update" -> {
                                            val newContext = tool.content ?: ""
                                            if (newContext.isNotBlank()) {
                                                val currentList = loadPermanentContexts()
                                                currentList.add(newContext)
                                                savePermanentContexts(currentList)
                                                Toast.makeText(context, "Memory updated.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        "task_create_success" -> {
                                            Toast.makeText(context, "Task created: ${tool.taskTitle}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // NETWORK ERROR: Toast only.
                        Log.e("ChatApp", "Error", e)
                        Toast.makeText(context, "Connection Failed.", Toast.LENGTH_SHORT).show()
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Send")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // MIC BUTTON
        Button(
            onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    isListening = true
                    startSpeechRecognition(speechLauncher)
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Filled.Mic, "Mic", modifier = Modifier.padding(end = 8.dp))
            Text(if (isListening) "Listening..." else "Tap to Speak")
        }

        // SERVER REPLY AREA
        Spacer(modifier = Modifier.height(24.dp))
        Text("Server Reply:", style = MaterialTheme.typography.titleMedium)
        Text(
            text = responseText,
            modifier = Modifier.fillMaxSize().padding(8.dp)
        )
    }
}
