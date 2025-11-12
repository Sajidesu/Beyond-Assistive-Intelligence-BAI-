package com.example.helloworld

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.helloworld.ui.theme.HelloWorldTheme

@OptIn(ExperimentalMaterial3Api::class)
class ChatHistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Get the list of chat messages from the Intent
        val chatHistory = intent.getStringArrayListExtra("CHAT_HISTORY_LIST") ?: arrayListOf("No chat history.")

        setContent {
            HelloWorldTheme {
                val context = LocalContext.current as Activity

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Chat History") },
                            navigationIcon = {
                                // 2. The back button
                                IconButton(onClick = {
                                    context.finish() // Closes this screen
                                }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back to main screen"
                                    )
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding), // Apply padding
                        color = MaterialTheme.colorScheme.background
                    ) {
                        // 3. A simple, read-only list
                        LazyColumn(modifier = Modifier.padding(16.dp)) {
                            items(chatHistory) { message ->
                                // 4. Show each chat message
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
