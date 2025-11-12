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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.platform.LocalContext

import androidx.compose.runtime.mutableStateListOf
import com.google.gson.Gson

// --- NEW PLAN: This key MUST match the permanent one in MainActivity ---
private const val PERMANENT_CONTEXT_KEY = "PermanentContexts"

@OptIn(ExperimentalMaterial3Api::class)
class SavedContextsActivity : ComponentActivity() {

    private var contextsStateList = mutableStateListOf<String>()
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This line is unchanged, it still reads "CONTEXT_LIST" from the intent
        val savedContexts = intent.getStringArrayListExtra("CONTEXT_LIST") ?: arrayListOf()
        contextsStateList.addAll(savedContexts)

        setContent {
            HelloWorldTheme {
                val context = LocalContext.current as Activity

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Edit Saved") }, // Your "Edit Saved" title
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

        val prefs = getSharedPreferences("ChatAppPreferences", Context.MODE_PRIVATE)
        val json = gson.toJson(contextsStateList)

        // --- NEW PLAN: This is the one-line logic change ---
        // Save the edits to the PERMANENT key, not the old key
        prefs.edit().putString(PERMANENT_CONTEXT_KEY, json).apply()
    }
}
