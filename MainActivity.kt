package com.example.helloworld
// Ayaw ipang wala ang comments kay ako ning guide </333
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.helloworld.ui.theme.HelloWorldTheme
// --- REQUIRED IMPORTS FOR NETWORKING ---
import com.example.helloworld.network.ChatApi
import com.example.helloworld.network.ChatRequest
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

// --- MERGE: Imports from Teammate's file (Task 1 & 2) ---
import android.Manifest
import android.app.Activity
import android.content.Context // <-- MEMORY TASK: Needed for SharedPreferences
import android.content.Intent // <-- LIST LOGIC: Needed to launch new activity
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech // Task 2
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.* // For Locale and TTS

// --- LIST LOGIC: Imports for saving a list ---
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.ArrayList

// --- NEW PLAN: We now have TWO separate keys for memory ---
private const val PERMANENT_CONTEXT_KEY = "PermanentContexts" // For editable, permanent facts
private const val CHAT_HISTORY_KEY = "ChatHistory" // For the temporary chat log
// -------------------------------------

// The main entry point for the Activity
class MainActivity : ComponentActivity() {

    // --- MERGE: Task 2 (TextToSpeech engine) ---
    private var textToSpeech: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- MERGE: Task 2 (TextToSpeech) Initialization ---
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.getDefault())
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Language not supported by TTS")
                    Toast.makeText(this, "Language not supported by TTS", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e("TTS", "TTS Initialization failed")
                Toast.makeText(this, "TTS Initialization failed", Toast.LENGTH_SHORT).show()
            }
        }
        // --- End of MERGE ---

        setContent {
            HelloWorldTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChatUI(
                        onSpeak = { text ->
                            speak(text)
                        }
                    )
                }
            }
        }
    }

    // --- MERGE: Task 2 (TextToSpeech) speak function ---
    /**
     * Task 2: Simple function that says a string out loud.
     */
    private fun speak(text: String) {
        if (text.isNotBlank()) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }
    // --- End of MERGE ---

    // --- TASK 4: Add onDestroy to clean up the TextToSpeech engine ---
    override fun onDestroy() {
        super.onDestroy()

        // --- MERGE: Shutdown teammate's TTS (Task 2) ---
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        // --- End of MERGE ---
    }
}

// Our main composable function for the UI layout
@Composable
fun ChatUI(
    onSpeak: (String) -> Unit
) {
    // Allows us to launch network tasks asynchronously
    val coroutineScope = rememberCoroutineScope()


    // --- LIST LOGIC: Setup for saving/loading a list ---
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("ChatAppPreferences", Context.MODE_PRIVATE)
    }
    val gson = remember { Gson() }

    // --- NEW PLAN: Helper function to load PERMANENT contexts ---
    fun loadPermanentContexts(): MutableList<String> {
        val json = prefs.getString(PERMANENT_CONTEXT_KEY, null)
        return if (json == null) {
            mutableListOf()
        } else {
            val type = object : TypeToken<MutableList<String>>() {}.type
            gson.fromJson(json, type)
        }
    }

    // --- NEW PLAN: Helper function to load CHAT history ---
    fun loadChatHistory(): MutableList<String> {
        val json = prefs.getString(CHAT_HISTORY_KEY, null)
        return if (json == null) {
            mutableListOf()
        } else {
            val type = object : TypeToken<MutableList<String>>() {}.type
            gson.fromJson(json, type)
        }
    }
    // --- END OF NEW PLAN ---


    // --- CHAT LOGIC: This is now your one and only input field ---
    var inputText by remember { mutableStateOf("") }

    // State to hold the response from the server/model
    var responseText by remember { mutableStateOf("Server reply will appear here.") }
    // State to handle the loading state
    var isLoading by remember { mutableStateOf(false) }

    // --- MERGE: Task 1 (SpeechRecognizer) Logic from VoiceApp ---
    var isListening by remember { mutableStateOf(false) }

    // ... (All Speech Recognizer code remains unchanged) ...
    fun startSpeechRecognition(launcher: ActivityResultLauncher<Intent>) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Toast.makeText(context, "Speech recognition not available", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
        }
        launcher.launch(intent)
    }
    val speechRecognitionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.get(0) ?: ""
            inputText = spokenText
        } else {
            Toast.makeText(context, "Speech recognition failed", Toast.LENGTH_SHORT).show()
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isListening = true
            startSpeechRecognition(speechRecognitionLauncher)
        } else {
            Toast.makeText(context, "Microphone permission denied", Toast.LENGTH_SHORT).show()
        }
    }


    // Column arranges items vertically
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- CHAT LOGIC: This is now your main text field ---
        OutlinedTextField(
            value = inputText,
            onValueChange = { newText ->
                inputText = newText
            },
            label = { Text("Type your message...") },
            placeholder = { Text("e.g., What is 34324325 / 412421?") },
            enabled = !isLoading, // Disable while loading
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        // --- NEW PLAN: Button Row for memory management ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- 1. View Contexts (Permanent, Editable) ---
            Button(
                onClick = {
                    val savedList = loadPermanentContexts()
                    if (savedList.isEmpty()) {
                        Toast.makeText(context, "No saved contexts.", Toast.LENGTH_SHORT).show()
                    } else {
                        val intent = Intent(context, SavedContextsActivity::class.java).apply {
                            // We pass the permanent list to the editable screen
                            putStringArrayListExtra("CONTEXT_LIST", ArrayList(savedList))
                        }
                        context.startActivity(intent)
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("View Contexts")
            }

            // --- 2. Chat History (Temporary, Read-only) ---
            Button(
                onClick = {
                    val savedList = loadChatHistory()
                    if (savedList.isEmpty()) {
                        Toast.makeText(context, "No chat history.", Toast.LENGTH_SHORT).show()
                    } else {
                        val intent = Intent(context, ChatHistoryActivity::class.java).apply {
                            // We pass the chat history list to the read-only screen
                            putStringArrayListExtra("CHAT_HISTORY_LIST", ArrayList(savedList))
                        }
                        context.startActivity(intent)
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Chat History")
            }
        }

        // --- 3. New Chat Button ---
        Button(
            onClick = {
                // This button clears ONLY the chat history
                prefs.edit().remove(CHAT_HISTORY_KEY).apply()
                // Clear the server reply box as well
                responseText = "New chat started. Chat history cleared."
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text("New Chat")
        }
        // --- END OF NEW PLAN ---


        Spacer(modifier = Modifier.height(16.dp))

        // --- 3. SEND BUTTON (Your UI) ---
        Button(
            onClick = {
                if (isLoading) return@Button
                if (inputText.isBlank()) {
                    Toast.makeText(context, "Message is empty", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                isLoading = true
                responseText = "Sending request..."

                // --- NEW PLAN: Updated Send Logic ---
                // 1. Load BOTH memory lists
                val permanentContexts = loadPermanentContexts()
                val chatHistory = loadChatHistory()

                // 2. Add the user's new message to the chat history
                val userMessage = "User: $inputText"
                chatHistory.add(userMessage)

                // 3. Save the *updated* chat history
                val chatJson = gson.toJson(chatHistory)
                prefs.edit().putString(CHAT_HISTORY_KEY, chatJson).apply()

                // 4. Clear the input box
                val currentInput = inputText // Save current input for the request
                inputText = ""
                // --- END OF NEW "Send" LOGIC ---

                coroutineScope.launch {
                    try {
                        // --- NEW PLAN: Combine both lists to create the full context ---
                        val permanentFacts = permanentContexts.joinToString("\n")
                        val currentChat = chatHistory.joinToString("\n")

                        // We send permanent facts first, then the chat history
                        val fullContext = "$permanentFacts\n\n$currentChat"

                        val request = ChatRequest(message = currentInput, context = fullContext)
                        val response = ChatApi.service.chat(request)

                        // --- NEW PLAN: Save the AI's response to chat history ---
                        val aiResponse = "AI: ${response.content}"
                        chatHistory.add(aiResponse) // Add the AI's reply
                        val newChatJson = gson.toJson(chatHistory) // Re-save the list
                        prefs.edit().putString(CHAT_HISTORY_KEY, newChatJson).apply()

                        // Show the AI response in the reply box
                        responseText = response.content
                        Log.d("ChatApp", "API Success: ${response.content}")

                    } catch (e: IOException) {
                        responseText = "Error: Could not connect to server. Check IP and Wi-Fi."
                        Log.e("ChatApp", "Network Error (IOException): ${e.message}")
                    } catch (e: HttpException) {
                        responseText = "Error: Server returned HTTP status ${e.code()}"
                        Log.e("ChatApp", "HTTP Error: ${e.code()}")
                    } catch (e: Exception) {
                        responseText = "An unknown error occurred: ${e.message}"
                        Log.e("ChatApp", "General Error: ${e.message}")
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading, // Button is disabled when loading
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Send", fontSize = 18.sp)
            }
        }

        // --- MERGE: Added Teammate's UI (Task 1 & 2 Buttons) ---
        Spacer(modifier = Modifier.height(16.dp))

        // Task 1: Microphone Button
        Button(
            onClick = {
                when {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED -> {
                        isListening = true
                        startSpeechRecognition(speechRecognitionLauncher)
                    }
                    else -> {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "Microphone Icon",
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(text = if (isListening) "Listening..." else "Tap to Speak")
        }

        Spacer(modifier = Modifier.height(16.dp)) // Spacer from teammate's code

        // Task 2: Speak Button
        Button(
            onClick = {
                if (inputText.isNotBlank()) {
                    onSpeak(inputText)
                } else {
                    Toast.makeText(context, "Text box is empty", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Speak Message")
        }
        // --- End of MERGE ---

        Spacer(modifier = Modifier.height(24.dp))

        // --- 4. SERVER REPLY TEXT AREA (Your UI) ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 8.dp)
        ) {
            Text(
                text = "Server Reply:",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = responseText,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            )
        }
    }
}

// Preview Composable to see the UI in Android Studio's design tab
@Preview(showBackground = true)
@Composable
fun ChatUIPreview() {
    HelloWorldTheme {
        ChatUI(
            onSpeak = {}
        )
    }
}

// --- MERGE: Teammate's Preview (Kept to not leave out code) ---
@Preview(showBackground = true)
@Composable
fun VoiceAppPreview() {
    HelloWorldTheme { // Use your theme
        Column {
            Button(onClick = {}) { Text("Tap to Speak") }
            Button(onClick = {}) { Text("Speak Message") }
        }
    }
}
