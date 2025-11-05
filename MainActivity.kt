package com.example.helloworld

import android.os.Bundle
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

// The main entry point for the Activity
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Applies the default app theme
            HelloWorldTheme {
                // Surface is like a background container
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Call our custom UI function
                    ChatUI()
                }
            }
        }
    }
}

// Our main composable function for the UI layout
@Composable
fun ChatUI() {
    // State to hold the content of the Context TextField
    var contextText by remember { mutableStateOf("") }
    // State to hold the content of the Message TextField
    var messageText by remember { mutableStateOf("") }
    // State to hold the response from the server/model
    var responseText by remember { mutableStateOf("Server reply will appear here.") }

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
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- 3. SEND BUTTON ---
        Button(
            onClick = {
                // TODO: Implement the actual API call logic here in the next step
                // For now, simulate a response
                responseText = "Processing message: '$messageText' with context: '$contextText'..."
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send", fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- 4. SERVER REPLY TEXT AREA ---
        // Use a Scrollable Column if the response might be long
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Takes up remaining space
                .padding(top = 8.dp)
        ) {
            Text(
                text = "Server Reply:",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            // Display the response text
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
        ChatUI()
    }
}
