package com.example.helloworld

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.helloworld.ui.theme.HelloWorldTheme
import com.google.gson.Gson

// This key MUST match the one in MainActivity
private const val PERMANENT_CONTEXT_KEY = "PermanentContexts"

@OptIn(ExperimentalMaterial3Api::class)
class SavedContextsActivity : ComponentActivity() {

    private var contextsStateList = mutableStateListOf<String>()
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load data passed from MainActivity
        val savedContexts = intent.getStringArrayListExtra("CONTEXT_LIST") ?: arrayListOf()
        contextsStateList.addAll(savedContexts)

        setContent {
            HelloWorldTheme {
                val context = LocalContext.current as Activity

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("View Contexts") },
                            navigationIcon = {
                                IconButton(onClick = {
                                    context.finish()
                                }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        LazyColumn(modifier = Modifier.padding(16.dp)) {
                            itemsIndexed(contextsStateList) { index, itemText ->
                                OutlinedTextField(
                                    value = itemText,
                                    onValueChange = { newText ->
                                        if (newText.isEmpty()) {
                                            contextsStateList.removeAt(index)
                                        } else {
                                            contextsStateList[index] = newText
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            contextsStateList.removeAt(index)
                                        }) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = "Delete item"
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Save changes to SharedPreferences when user leaves this screen
        val prefs = getSharedPreferences("ChatAppPreferences", Context.MODE_PRIVATE)
        val json = gson.toJson(contextsStateList)
        prefs.edit().putString(PERMANENT_CONTEXT_KEY, json).apply()
    }
}
