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
