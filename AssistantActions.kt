package com.example.helloworld.actions

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.widget.Toast
import android.util.Log
// --- MERGE: Removed unused imports (TTS, Locale, Log) ---

/**
 * Helper class to encapsulate native Android functions (the "Hands" of the AI).
 * This class handles setting alarms.
 * --- MERGE: Removed TextToSpeech.OnInitListener ---
 */
class AssistantActions(private val context: Context) {

    // --- MERGE: Removed all TTS-related code (tts, isTtsReady, init, onInit) ---

    /**
     * TASK 3: Sets a native system alarm.
     */
    fun setAlarm(timeInHours: Int, timeInMinutes: Int, label: String) {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_HOUR, timeInHours)
            putExtra(AlarmClock.EXTRA_MINUTES, timeInMinutes)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        // --- THIS IS THE CORRECTED CODE ---
        // It catches 'e: Exception', which stops the crash
        try {   1
            context.startActivity(intent)
        } catch (e: Exception) { // <-- CATCHES ALL ERRORS

            // This will log the full crash error for you to see
            Log.e("AssistantActions", "Failed to set alarm", e)

            // This will show a Toast INSTEAD of crashing
            Toast.makeText(
                context,
                "Error: ${e.message}", // This will show the real error
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
