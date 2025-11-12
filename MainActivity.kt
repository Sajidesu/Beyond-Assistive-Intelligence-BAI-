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
// --- BACKEND SYNC: Import new data classes ---
import com.example.helloworld.network.ChatRequest
import com.example.helloworld.network.HistoryMessage
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

// --- BACKEND SYNC: We MUST re-import AssistantActions to handle the "alarm" tool ---
import com.example.helloworld.actions.AssistantActions

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

    // --- BACKEND SYNC: We need this again ---
    private lateinit var actions: AssistantActions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- BACKEND SYNC: We need to initialize this again ---
        actions = AssistantActions(applicationContext)

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
                        },
                        // --- BACKEND SYNC: Pass the actions down ---
                        onSetAlarm = { time, label ->
                            actions.setAlarm(time, label)
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
    onSpeak: (String) -> Unit,
    // --- BACKEND SYNC: A function to call the alarm tool ---
    onSetAlarm: (String, String) -> Unit
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

    // --- BACKEND SYNC: Helper function to SAVE chat history ---
    // We'll be calling this a lot
    fun saveChatHistory(history: List<String>) {
        val json = gson.toJson(history)
        prefs.edit().putString(CHAT_HISTORY_KEY, json).apply()
    }

    // --- BACKEND SYNC: Helper function to SAVE permanent context ---
    fun savePermanentContexts(contexts: List<String>) {
        val json = gson.toJson(contexts)
        prefs.edit().putString(PERMANENT_CONTEXT_KEY, json).apply()
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
            // --- BACKEND SYNC: New placeholder to encourage saving facts ---
            placeholder = { Text("e.g., 'Remember my anniversary is Oct 12'") },
            enabled = !isLoading, // Disable while loading
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        // --- BACKEND SYNC: Removed the "Save as Fact" button.
        // The AI will handle this automatically now.

        // --- NEW PLAN: Button Row for memory management ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
                            putStringArrayListExtra("CONTEXT_LIST", ArrayList(savedList))
                        }
                        context.startActivity(intent)
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("View Contexts") // Renamed for clarity
            }

            // --- 2. Chat History (Temporary, Read-only) ---
            Button(
                onClick = {
                    val savedList = loadChatHistory()
                    if (savedList.isEmpty()) {
                        Toast.makeText(context, "No chat history.", Toast.LENGTH_SHORT).show()
                    } else {
                        val intent = Intent(context, ChatHistoryActivity::class.java).apply {
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

                // --- BACKEND SYNC: Completely new "Send" logic ---

                // 1. Load both memory lists
                val permanentContexts = loadPermanentContexts()
                val chatHistory = loadChatHistory()

                // 2. Add the user's new message to the chat history
                val userMessage = "User: $inputText"
                chatHistory.add(userMessage)

                // 3. Save the *updated* chat history
                saveChatHistory(chatHistory)

                // 4. Clear the input box
                inputText = ""

                // 5. Convert app's List<String> into backend's List<HistoryMessage>
                val geminiHistory = chatHistory.map {
                    if (it.startsWith("User: ")) {
                        HistoryMessage(role = "user", text = it.removePrefix("User: "))
                    } else { // Assumes "AI: "
                        HistoryMessage(role = "model", text = it.removePrefix("AI: "))
                    }
                }

                // 6. Create the new ChatRequest
                val request = ChatRequest(
                    permanentContext = permanentContexts.joinToString("\n"),
                    chatHistory = geminiHistory
                )
                // --- END OF NEW "Send" LOGIC ---

                coroutineScope.launch {
                    try {
                        val response = ChatApi.service.chat(request)

                        // --- BACKEND SYNC: Handle the 3+ response types ---
                        when (response.type) {
                            "text" -> {
                                val aiReply = response.content ?: "I have no reply."
                                // Save the AI's response to chat history
                                chatHistory.add("AI: $aiReply")
                                saveChatHistory(chatHistory)
                                // Show the AI response in the reply box
                                responseText = aiReply
                            }
                            "alarm" -> {
                                val time = response.time ?: "00:00"
                                val label = response.label ?: "Alarm"
                                // Call the native function
                                onSetAlarm(time, label)
                                // Set a user-friendly response
                                responseText = "Okay, I'm setting an alarm for $time with the label '$label'."
                                // We don't save this to chat history, as it's an action
                            }
                            "context_update" -> {
                                val newContextContent = response.content ?: ""
                                // The AI sent us the new, complete list of facts
                                val newFactList = newContextContent.split("\n")
                                savePermanentContexts(newFactList)

                                responseText = "Okay, I've updated your permanent facts."
                                Toast.makeText(context, "Permanent facts updated by AI!", Toast.LENGTH_LONG).show()
                            }
                            else -> {
                                responseText = "Received an unknown response type: ${response.type}"
                            }
                        }

                        Log.d("ChatApp", "API Success: ${response.type}")

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
            onSpeak = {},
            // --- BACKEND SYNC: Add new param to preview ---
            onSetAlarm = { _, _ -> }
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
