package com.example.helloworldpackage com.example.helloworld
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
// --- TASK 4: Import your AssistantActions class ---
import com.example.helloworld.actions.AssistantActions

// --- MERGE: Imports from Teammate's file (Task 1 & 2) ---
import android.Manifest
import android.app.Activity
import android.content.Context // <-- MEMORY TASK: Needed for SharedPreferences
import android.content.Intent
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
// -------------------------------------

// The main entry point for the Activity
class MainActivity : ComponentActivity() {

    // --- TASK 4: Create a variable for your AssistantActions ---
    private lateinit var actions: AssistantActions

    // --- MERGE: Task 2 (TextToSpeech engine) ---
    private var textToSpeech: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- TASK 4: Initialize AssistantActions with the context ---
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
                    // --- TASK 4: Pass the action functions down to the UI ---
                    ChatUI(
                        onTestAlarm = {
                            // Define what the "Test Alarm" button will do
                            actions.setAlarm(9, 30, "Test Alarm") // Using new Int params
                        },

                        // --- MERGE: Pass the REAL speak function (Task 2) ---
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
    // --- TASK 4: Add parameters to receive the functions ---
    onTestAlarm: () -> Unit,
    // --- MERGE: Add parameter for Task 2 speak function ---
    onSpeak: (String) -> Unit
) {
    // Allows us to launch network tasks asynchronously
    val coroutineScope = rememberCoroutineScope()


    // --- MEMORY TASK: Task 1 (The "Memory") ---
    val context = LocalContext.current
    // 1. Get the SharedPreferences file (or create it if it doesn't exist)
    val prefs = remember {
        context.getSharedPreferences("ChatAppPreferences", Context.MODE_PRIVATE)
    }

    // 2. Load the saved text from memory. Default to "" if nothing is saved.
    var contextText by remember {
        mutableStateOf(prefs.getString("ContextMemory", "") ?: "")
    }
    // --- END OF MEMORY TASK ---


    // State to hold the content of the Message TextField
    var messageText by remember { mutableStateOf("") }
    // State to hold the response from the server/model
    var responseText by remember { mutableStateOf("Server reply will appear here.") }
    // State to handle the loading state
    var isLoading by remember { mutableStateOf(false) }

    // --- MERGE: Task 1 (SpeechRecognizer) Logic from VoiceApp ---
    // val context = LocalContext.current // <-- Already defined above for Memory Task
    var isListening by remember { mutableStateOf(false) }

    // Helper function to start speech recognition
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

    // Launcher for getting the speech-to-text result
    val speechRecognitionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.get(0) ?: ""
            // --- MERGE HOOK: Put spoken text into YOUR messageText state ---
            messageText = spokenText
        } else {
            Toast.makeText(context, "Speech recognition failed", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher for requesting the RECORD_AUDIO permission
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
    // --- End of MERGE (Task 1 Logic) ---


    // Column arranges items vertically
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 1. CONTEXT TEXT FIELD (Your UI) ---
        OutlinedTextField(
            value = contextText,
            onValueChange = { newText ->
                // Update the state
                contextText = newText

                // --- MEMORY TASK: Task 1 (Save) ---
                // 3. Save the new text to SharedPreferences on every change.
                prefs.edit().putString("ContextMemory", newText).apply()
                // --- END OF MEMORY TASK ---
            },
            label = { Text("Context (System Instruction)") },
            placeholder = { Text("e.g., Act as a friendly tutor...") },
            enabled = !isLoading, // Disable while loading
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        // --- 2. MESSAGE TEXT FIELD (Your UI) ---
        OutlinedTextField(
            value = messageText,
            onValueChange = { messageText = it },
            label = { Text("Message (User Query)") },
            placeholder = { Text("e.g., What are the capital cities of Europe?") },
            enabled = !isLoading, // Disable while loading
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- 3. SEND BUTTON (Your UI) ---
        Button(
            onClick = {
                // ... (Your networking logic remains unchanged) ...
                if (isLoading) return@Button
                isLoading = true
                responseText = "Sending request..."
                coroutineScope.launch {
                    try {
                        if (messageText.isBlank()) {
                            responseText = "Error: Message cannot be empty."
                            return@launch
                        }
                        // --- MEMORY TASK: Pass the persistent context to the request ---
                        val request = ChatRequest(message = messageText, context = contextText)
                        val response = ChatApi.service.chat(request)
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

        // --- TASK 4: Add temporary test buttons (Your UI) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = onTestAlarm) {
                Text("Test Alarm")
            }

        }
        // --- End of TASK 4 buttons ---

        // --- MERGE: Added Teammate's UI (Task 1 & 2 Buttons) ---

        Spacer(modifier = Modifier.height(16.dp)) // Spacer from teammate's code

        // Task 1: Microphone Button
        Button(
            onClick = {
                // Check for permission and start listening
                when {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED -> {
                        isListening = true
                        startSpeechRecognition(speechRecognitionLauncher)
                    }
                    else -> {
                        // Request the permission
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            // Change color while listening
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
                // --- MERGE HOOK: Use YOUR messageText state ---
                if (messageText.isNotBlank()) {
                    onSpeak(messageText)
                } else {
                    Toast.makeText(context, "Message box is empty", Toast.LENGTH_SHORT).show()
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
            // Display the response text (now from the server)
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
        // --- Add test button parameters here ---
        ChatUI(
            onTestAlarm = {},
            onSpeak = {}
        )
    }
}

// --- MERGE: Teammate's Preview (Kept to not leave out code) ---
// Note: This preview will show *only* the teammate's buttons,
// because it's not part of the ChatUI.
@Preview(showBackground = true)
@Composable
fun VoiceAppPreview() {
    HelloWorldTheme { // Use your theme
        // This is a simple preview of *just* the buttons
        Column {
            Button(onClick = {}) { Text("Tap to Speak") }
            Button(onClick = {}) { Text("Speak Message") }
        }
    }
}
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
// --- TASK 4: Import your AssistantActions class ---
import com.example.helloworld.actions.AssistantActions
// -------------------------------------

// The main entry point for the Activity
class MainActivity : ComponentActivity() {

    // --- TASK 4: Create a variable for your AssistantActions ---
    private lateinit var actions: AssistantActions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- TASK 4: Initialize AssistantActions with the context ---
        actions = AssistantActions(applicationContext)

        setContent {
            HelloWorldTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // --- TASK 4: Pass the action functions down to the UI ---
                    ChatUI(
                        onTestAlarm = {
                            // Define what the "Test Alarm" button will do
                            actions.setAlarm("09:30", "Test Alarm")
                        },
                        onTestSpeak = {
                            // Define what the "Test Speak" button will do
                            actions.speak("Hello, Serge loves Jhon and He is a Homosexual, our Homosexual friend.")
                        }
                    )
                }
            }
        }
    }
    // --- TASK 4: Add onDestroy to clean up the TextToSpeech engine ---
    override fun onDestroy() {
        super.onDestroy()
        actions.shutdown()
    }
}

// Our main composable function for the UI layout
@Composable
fun ChatUI(
    // --- TASK 4: Add parameters to receive the functions ---
    onTestAlarm: () -> Unit,
    onTestSpeak: () -> Unit
) {
    // Allows us to launch network tasks asynchronously
    val coroutineScope = rememberCoroutineScope()

    // State to hold the content of the Context TextField
    var contextText by remember { mutableStateOf("") }
    // State to hold the content of the Message TextField
    var messageText by remember { mutableStateOf("") }
    // State to hold the response from the server/model
    var responseText by remember { mutableStateOf("Server reply will appear here.") }
    // State to handle the loading state
    var isLoading by remember { mutableStateOf(false) }


    // Column arranges items vertically
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 1. CONTEXT TEXT FIELD ---
        OutlinedTextField(
            value = contextText,
            onValueChange = { contextText = it },
            label = { Text("Context (System Instruction)") },
            placeholder = { Text("e.g., Act as a friendly tutor...") },
            enabled = !isLoading, // Disable while loading
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        // --- 2. MESSAGE TEXT FIELD ---
        OutlinedTextField(
            value = messageText,
            onValueChange = { messageText = it },
            label = { Text("Message (User Query)") },
            placeholder = { Text("e.g., What are the capital cities of Europe?") },
            enabled = !isLoading, // Disable while loading
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- 3. SEND BUTTON (Now with API Logic) ---
        Button(
            onClick = {
                // Prevent duplicate calls
                if (isLoading) return@Button

                // 1. Set loading state and feedback
                isLoading = true
                responseText = "Sending request..."

                // 2. Launch the network call
                coroutineScope.launch {
                    try {
                        // Ensure message is not empty before sending
                        if (messageText.isBlank()) {
                            responseText = "Error: Message cannot be empty."
                            return@launch
                        }

                        val request = ChatRequest(message = messageText, context = contextText)

                        // Call the server
                        val response = ChatApi.service.chat(request)

                        // 3. Update UI with the server's response content
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
                        // 4. Always reset loading state
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading, // Button is disabled when loading
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                // Show a loading indicator in the button
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Send", fontSize = 18.sp)
            }
        }

        // --- TASK 4: Add temporary test buttons ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = onTestAlarm) {
                Text("Test Alarm")
            }
            Button(onClick = onTestSpeak) {
                Text("Test Speak")
            }
        }
        // --- End of TASK 4 buttons ---

        Spacer(modifier = Modifier.height(24.dp))

        // --- 4. SERVER REPLY TEXT AREA ---
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
            // Display the response text (now from the server)
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
        // --- Add test button parameters here ---
        ChatUI(onTestAlarm = {}, onTestSpeak = {})
    }
}
