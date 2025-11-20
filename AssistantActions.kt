package com.example.helloworld.actions

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import android.widget.Toast
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AssistantActions(private val context: Context) {

    fun setAlarm(time: String, label: String) {
        val alarmDetails = parseTime(time)

        if (alarmDetails == null) {
            Toast.makeText(context, "Invalid time: $time", Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_HOUR, alarmDetails.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, alarmDetails.minute)
            if (alarmDetails.day != null) {
                putExtra(AlarmClock.EXTRA_DAYS, arrayListOf(alarmDetails.day))
            }
            putExtra(AlarmClock.EXTRA_SKIP_UI, false) // Kept as false per your OG file
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(intent)
            // Optional: Add a Toast here if you want to see what time it set
            // Toast.makeText(context, "Setting alarm for ${alarmDetails.hour}:${alarmDetails.minute}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error setting alarm", Toast.LENGTH_SHORT).show()
        }
    }

    private data class AlarmDetails(val hour: Int, val minute: Int, val day: Int?)

    private fun parseTime(timeString: String): AlarmDetails? {
        // 1. Try ISO Format with FORCED Manila Timezone (The "OG" Logic)
        try {
            // Parse the time from the server (usually UTC)
            val offsetDt = OffsetDateTime.parse(timeString, DateTimeFormatter.ISO_OFFSET_DATE_TIME)

            // FORCE it to Philippines time, even if the emulator is in US/UK time
            val localDt = offsetDt.atZoneSameInstant(ZoneId.of("Asia/Manila"))

            val dayOfWeek = localDt.dayOfWeek.value // 1=Mon, 7=Sun
            // Convert to Calendar day (1=Sun, 2=Mon)
            val calendarDay = if (dayOfWeek == 7) Calendar.SUNDAY else dayOfWeek + 1

            return AlarmDetails(localDt.hour, localDt.minute, calendarDay)
        } catch (e: Exception) {
            // Log error if needed
        }

        // 2. Fallback for simple formats (e.g. "3:00 PM")
        try {
            val parser12 = SimpleDateFormat("h:mm a", Locale.getDefault())
            val cal = Calendar.getInstance()
            cal.time = parser12.parse(timeString)!!
            return AlarmDetails(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), null)
        } catch (e: Exception) {}

        return null
    }
}
