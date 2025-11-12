package com.example.helloworld

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.helloworld.ui.theme.HelloWorldTheme

import android.app.Activity
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.platform.LocalContext

import androidx.compose.runtime.mutableStateListOf
import com.google.gson.Gson

private const val CONTEXT_LIST_KEY = "ContextList"

@OptIn(ExperimentalMaterial3Api::class)
class SavedContextsActivity : ComponentActivity() {

    private var contextsStateList = mutableStateListOf<String>()
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val savedContexts = intent.getStringArrayListExtra("CONTEXT_LIST") ?: arrayListOf()
        contextsStateList.addAll(savedContexts)

        setContent {
            HelloWorldTheme {
                val context = LocalContext.current as Activity

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Saved Contexts") },
                            navigationIcon = {
                                IconButton(onClick = {
                                    context.finish()
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
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        LazyColumn(modifier = Modifier.padding(16.dp)) {
                            itemsIndexed(contextsStateList) { index, itemText ->
                                OutlinedTextField(
                                    value = itemText,
                                    onValueChange = { newText ->
                                        // --- THIS IS THE FIX ---
                                        if (newText.isEmpty()) {
                                            // If the text is deleted, remove the item
                                            contextsStateList.removeAt(index)
                                        } else {
                                            // Otherwise, just update it
                                            contextsStateList[index] = newText
                                        }
                                        // --- END OF FIX ---
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
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

        // This save logic is now even more important!
        val prefs = getSharedPreferences("ChatAppPreferences", Context.MODE_PRIVATE)
        val json = gson.toJson(contextsStateList)
        prefs.edit().putString(CONTEXT_LIST_KEY, json).apply()
    }
}
