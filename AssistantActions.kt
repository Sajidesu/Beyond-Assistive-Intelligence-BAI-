package com.example.helloworld.actions

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class AssistantActions(private val context: Context) {

    fun setAlarm(time: String, label: String) {
        val alarmDetails = parseTime(time)

        if (alarmDetails == null) {
            Toast.makeText(context, "Could not parse time: $time", Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_HOUR, alarmDetails.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, alarmDetails.minute)
            // Calendar.SUNDAY = 1, which matches what AlarmClock expects
            if (alarmDetails.day != null) {
                putExtra(AlarmClock.EXTRA_DAYS, arrayListOf(alarmDetails.day))
            }
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(intent)

            // Formatted Toast to confirm accuracy to the user
            val amPm = if (alarmDetails.hour >= 12) "PM" else "AM"
            val hour12 = if (alarmDetails.hour > 12) alarmDetails.hour - 12 else if (alarmDetails.hour == 0) 12 else alarmDetails.hour
            val minStr = String.format("%02d", alarmDetails.minute)

            Toast.makeText(context, "Alarm set for $hour12:$minStr $amPm", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error launching Alarm Clock", Toast.LENGTH_SHORT).show()
        }
    }

    private data class AlarmDetails(val hour: Int, val minute: Int, val day: Int?)

    private fun parseTime(timeString: String): AlarmDetails? {
        // PROBLEM: Server sends "2023-11-21T11:00:00+00:00" (UTC)
        // GOAL: 7:00 PM Philippines

        try {
            // 1. STRIP TIMEZONE INFO MANUALLY
            // We only want the first 19 characters: "2023-11-21T11:00:00"
            // This removes potential issues with "+00:00" or "Z" parsing on older Androids
            val cleanTime = if (timeString.length >= 19) timeString.substring(0, 19) else timeString

            // 2. FORCE UTC PARSING
            // We tell the parser: "This string is definitely in UTC"
            val utcParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            utcParser.timeZone = TimeZone.getTimeZone("UTC")

            val date = utcParser.parse(cleanTime)

            if (date != null) {
                // 3. CONVERT TO LOCAL DEVICE TIME
                // Calendar.getInstance() gets the phone's current timezone (Asia/Manila)
                val localCalendar = Calendar.getInstance()
                localCalendar.time = date // This automatically adds +8 hours (or whatever local offset is)

                val hour = localCalendar.get(Calendar.HOUR_OF_DAY) // Should be 19 (7 PM)
                val minute = localCalendar.get(Calendar.MINUTE)
                val day = localCalendar.get(Calendar.DAY_OF_WEEK)

                return AlarmDetails(hour, minute, day)
            }
        } catch (e: Exception) {
            Log.e("AssistantActions", "UTC Force Parse Failed: ${e.message}")
        }

        // Fallback for non-standard strings (e.g. "7:00 PM" text only)
        try {
            val simpleParser = SimpleDateFormat("h:mm a", Locale.US)
            val date = simpleParser.parse(timeString)
            if (date != null) {
                val cal = Calendar.getInstance()
                cal.time = date
                return AlarmDetails(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), null)
            }
        } catch (e: Exception) {
            // Ignore
        }

        return null
    }
}
