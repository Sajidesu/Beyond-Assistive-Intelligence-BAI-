package com.example.helloworld.actions

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import java.util.Locale

/**
 * Helper class to encapsulate native Android functions (the "Hands" of the AI).
 * This class handles setting alarms and using Text-to-Speech.
 */
class AssistantActions(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    init {
        // Initialize TextToSpeech engine
        tts = TextToSpeech(context, this)
    }

    /**
     * Called when the TTS engine is initialized.
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Language is not supported")
                isTtsReady = false
            } else {
                Log.d("TTS", "TTS Engine Ready")
                isTtsReady = true
            }
        } else {
            Log.e("TTS", "Initialization Failed!")
            isTtsReady = false
        }
    }
    // TASK 3
    fun setAlarm(time: String, label: String) {
        // Simple validation for HH:mm format
        val parts = time.split(":")
        if (parts.size != 2) {
            Toast.makeText(context, "Alarm format must be HH:mm", Toast.LENGTH_SHORT).show()
            return
        }

        val hour = parts[0].toIntOrNull()
        val minute = parts[1].toIntOrNull()

        if (hour == null || minute == null || hour !in 0..23 || minute !in 0..59) {
            Toast.makeText(context, "Invalid time (HH:mm) format", Toast.LENGTH_SHORT).show()
            return
        }

        // Create the Intent to set the alarm
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            // Ensures the intent starts a new activity outside the app
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        // Check if there is an app that can handle this intent
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            Toast.makeText(context, "Attempting to set alarm for $time: $label", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "No alarm app found.", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Speaks the given text using the Text-to-Speech engine.
     * @param text The string to be spoken.
     */
    fun speak(text: String) {
        if (isTtsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "uniqueId")
            Toast.makeText(context, "Speaking: \"$text\"", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Text-to-Speech engine is not ready.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Cleanup resources when the Activity is destroyed.
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
