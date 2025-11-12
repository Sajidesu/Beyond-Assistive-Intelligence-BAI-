package com.example.helloworld.actions

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AssistantActions(private val context: Context) {

    /**
     * Sets a native system alarm.
     * This new version parses a time string (e.g., "7:00 AM" or "18:30").
     */
    fun setAlarm(time: String, label: String) {
        val (hour, minute) = parseTime(time)

        if (hour == -1) {
            // If parsing fails, show an error but don't crash
            Toast.makeText(context, "Could not set alarm: Invalid time format '$time'", Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("AssistantActions", "Failed to set alarm", e)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Helper to parse "7:00 AM" or "18:30" into (hour, minute).
     * Returns (-1, -1) on failure.
     */
    private fun parseTime(timeString: String): Pair<Int, Int> {
        return try {
            // Try parsing "h:mm a" (e.g., "7:00 AM")
            val parser12 = SimpleDateFormat("h:mm a", Locale.getDefault())
            val cal = Calendar.getInstance()
            cal.time = parser12.parse(timeString)!!
            Pair(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        } catch (e: Exception) {
            try {
                // Try parsing "HH:mm" (e.g., "18:30")
                val parser24 = SimpleDateFormat("HH:mm", Locale.getDefault())
                val cal = Calendar.getInstance()
                cal.time = parser24.parse(timeString)!!
                Pair(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            } catch (e2: Exception) {
                // Both failed
                Log.e("AssistantActions", "Could not parse time string: $timeString")
                Pair(-1, -1)
            }
        }
    }
}
