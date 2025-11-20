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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.helloworld.actions.AssistantActions
import com.example.helloworld.network.ChatApi
import com.example.helloworld.network.ChatRequest
import com.example.helloworld.network.HistoryMessage
import com.example.helloworld.ui.theme.HelloWorldTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.util.*


val BaiYellow = Color(0xFFFFE066)
val BaiBubbleYellow = Color(0xFFFFE082)
val BaiWhite = Color(0xFFFFFFFF)

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
                    color = BaiYellow 
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
    val keyboardController = LocalSoftwareKeyboardController.current
    val prefs = remember { context.getSharedPreferences("ChatAppPreferences", Context.MODE_PRIVATE) }
    val gson = remember { Gson() }

    
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }

   
    var showTools by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var isChatting by remember { mutableStateOf(false) }

    
    var chatHistory by remember { mutableStateOf<MutableList<String>>(mutableListOf()) }

    
    fun loadPermanentContexts(): MutableList<String> {
        val json = prefs.getString(PERMANENT_CONTEXT_KEY, null)
        return if (json == null) mutableListOf() else gson.fromJson(json, object : TypeToken<MutableList<String>>() {}.type)
    }

    fun loadChatHistoryList(): MutableList<String> {
        val json = prefs.getString(CHAT_HISTORY_KEY, null)
        return if (json == null) mutableListOf() else gson.fromJson(json, object : TypeToken<MutableList<String>>() {}.type)
    }

    fun saveChatHistory(history: List<String>) {
        val json = gson.toJson(history)
        prefs.edit().putString(CHAT_HISTORY_KEY, json).apply()
        chatHistory = history.toMutableList() 
    }

    fun savePermanentContexts(contexts: List<String>) {
        val json = gson.toJson(contexts)
        prefs.edit().putString(PERMANENT_CONTEXT_KEY, json).apply()
    }

    
    LaunchedEffect(Unit) {
        chatHistory = loadChatHistoryList()
        if (chatHistory.isNotEmpty()) isChatting = true
    }

    
    fun sendMessage() {
        if (isLoading || inputText.isBlank()) return
        isLoading = true
        val msgText = inputText
        inputText = "" 
        keyboardController?.hide()

        
        isChatting = true

        val currentHistory = loadChatHistoryList()
        val contexts = loadPermanentContexts()

        
        currentHistory.add("User: $msgText")
        saveChatHistory(currentHistory)

        
        val apiHistory = currentHistory.map {
            if (it.startsWith("User: ")) HistoryMessage("user", it.removePrefix("User: "))
            else HistoryMessage("model", it.removePrefix("AI: "))
        }

        val request = ChatRequest(
            permanentContext = contexts.joinToString("\n"),
            chatHistory = apiHistory
        )

        coroutineScope.launch {
            try {
                val response = ChatApi.service.chat(request)

               
                if (response.type == "error") {
                    if (response.errorType == "model_overloaded") {
                        Toast.makeText(context, "AI is overloaded. Please try again.", Toast.LENGTH_LONG).show()
                    } else {
                        val errorMsg = response.message ?: "Unknown error"
                        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                       
                    }
                } else {
                  

                    
                    val aiText = response.message ?: response.content ?: ""
                    if (aiText.isNotBlank()) {
                        currentHistory.add("AI: $aiText")
                        saveChatHistory(currentHistory)
                        onSpeak(aiText)
                    }

                    
                    if ((response.type == "multi_tool_result" || response.type == "text") && response.results != null) {
                        response.results.forEach { tool ->
                            when (tool.type) {
                                "alarm" -> {
                                    onSetAlarm(tool.time ?: "00:00", tool.label ?: "Alarm")
                                }
                                "alarm_exists" -> {
                                    Toast.makeText(context, tool.message ?: "Alarm exists.", Toast.LENGTH_SHORT).show()
                                }
                                "context_update" -> {
                                    val newContext = tool.content ?: ""
                                    if (newContext.isNotBlank()) {
                                        val currentContexts = loadPermanentContexts()
                                        currentContexts.add(newContext)
                                        savePermanentContexts(currentContexts)
                                        Toast.makeText(context, "Memory updated.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                "task_create_success" -> {
                                    Toast.makeText(context, "Task created: ${tool.taskTitle}", Toast.LENGTH_SHORT).show()
                                }
                                "task_create_failed", "task_create_error" -> {
                                    Toast.makeText(context, "Task failed: ${tool.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatApp", "Error", e)
                Toast.makeText(context, "Connection Failed.", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    
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

    
    Column(modifier = Modifier.fillMaxSize()) {

        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box {
                IconButton(onClick = { showMenu = !showMenu }) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(BaiWhite)
                ) {
                    DropdownMenuItem(
                        text = { Text("Chat History") },
                        onClick = {
                            showMenu = false
                            val savedList = loadChatHistoryList()
                            val intent = Intent(context, ChatHistoryActivity::class.java).apply {
                                putStringArrayListExtra("CHAT_HISTORY_LIST", ArrayList(savedList))
                            }
                            context.startActivity(intent)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("New Chat") },
                        onClick = {
                            showMenu = false
                            prefs.edit().remove(CHAT_HISTORY_KEY).apply()
                            chatHistory.clear()
                            isChatting = false
                            Toast.makeText(context, "New Chat Started", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            Text(
                text = "bai",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (!isChatting && chatHistory.isEmpty()) {
                
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clickable { isChatting = true },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Canvas(modifier = Modifier.size(150.dp)) {
                        drawCircle(color = BaiWhite)
                        
                        drawCircle(color = Color(0xFF4E342E), radius = 20f, center = center.copy(x = center.x - 40f, y = center.y - 10f))
                        drawCircle(color = Color(0xFF4E342E), radius = 20f, center = center.copy(x = center.x + 40f, y = center.y - 10f))
                        
                        drawArc(
                            color = Color(0xFF4E342E),
                            startAngle = 0f,
                            sweepAngle = 180f,
                            useCenter = false,
                            topLeft = center.copy(x = center.x - 30f, y = center.y + 10f),
                            size = androidx.compose.ui.geometry.Size(60f, 30f),
                            style = Stroke(width = 8f)
                        )
                    }
                    Text("?", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4E342E), modifier = Modifier.offset(x = 60.dp, y = (-140).dp))
                    Text("Tap to start chatting", color = Color.White, modifier = Modifier.padding(top = 16.dp))
                }
            } else {
                
                ChatScreen(chatHistory = chatHistory, isLoading = isLoading)
            }
        }

        
        AnimatedVisibility(visible = showTools) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                ToolIcon(Icons.Outlined.ListAlt, "Contexts") {
                    val savedList = loadPermanentContexts()
                    val intent = Intent(context, SavedContextsActivity::class.java).apply {
                        putStringArrayListExtra("CONTEXT_LIST", ArrayList(savedList))
                    }
                    context.startActivity(intent)
                }
                
                ToolIcon(Icons.Outlined.CameraAlt, "Camera") {}
                ToolIcon(Icons.Outlined.Image, "Gallery") {}
            }
        }

        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { showTools = !showTools },
                modifier = Modifier
                    .size(48.dp)
                    .background(BaiWhite, CircleShape)
                    .border(1.dp, Color.LightGray, CircleShape)
            ) {
                Icon(
                    imageVector = if (showTools) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = "Tools",
                    tint = Color.Gray
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .background(BaiWhite, RoundedCornerShape(25.dp))
                    .border(1.dp, Color.LightGray, RoundedCornerShape(25.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontSize = 16.sp, color = Color.Black),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { sendMessage() }
                    ),
                    decorationBox = { innerTextField ->
                        if (inputText.isEmpty()) {
                            Text("Type a message...", color = Color.Gray)
                        }
                        innerTextField()
                    }
                )

                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    if (inputText.isBlank()) {
                        IconButton(onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                isListening = true
                                startSpeechRecognition(speechLauncher)
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }) {
                            Icon(Icons.Filled.Mic, "Voice", tint = Color(0xFFFFC107))
                        }
                    } else {
                        IconButton(onClick = { sendMessage() }) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color(0xFFFFC107))
                        }
                    }
                }
            }
        }
    }
}



@Composable
fun ChatScreen(chatHistory: List<String>, isLoading: Boolean) {
    val listState = rememberLazyListState()

    LaunchedEffect(chatHistory.size, isLoading) {
        val total = chatHistory.size + if(isLoading) 1 else 0
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        state = listState
    ) {
        items(chatHistory) { message ->
            val isUser = message.startsWith("User: ")
            val text = if (isUser) message.removePrefix("User: ") else message.removePrefix("AI: ")
            ChatBubble(text, isUser)
        }
        if (isLoading) {
            item { ChatBubble("...", false) }
        }
    }
}

@Composable
fun ToolIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = Color.DarkGray, modifier = Modifier.size(24.dp))
        Text(label, fontSize = 10.sp, color = Color.DarkGray)
    }
}

@Composable
fun ChatBubble(text: String, isUser: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) BaiWhite else BaiBubbleYellow,
            shape = if (isUser) RoundedCornerShape(20.dp, 20.dp, 0.dp, 20.dp)
            else RoundedCornerShape(20.dp, 20.dp, 20.dp, 0.dp),
            border = if (isUser) BorderStroke(1.dp, Color.Black) else null,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(12.dp),
                color = Color.Black,
                fontSize = 16.sp
            )
        }
    }
}
