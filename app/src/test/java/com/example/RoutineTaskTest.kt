package com.example

import com.example.data.model.RoutineTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineTaskTest {

    @Test
    fun testTaskTimeFormatting() {
        val task = RoutineTask(
            id = 1,
            title = "Almoço",
            startMinute = 750, // 12:30
            durationMinutes = 60
        )

        assertEquals("12:30", task.formatStartTime(is24Hour = true))
        assertEquals("13:30", task.formatEndTime(is24Hour = true))
        assertEquals("12:30 PM", task.formatStartTime(is24Hour = false))
        assertEquals("01:30 PM", task.formatEndTime(is24Hour = false))
    }

    @Test
    fun testIsCurrent() {
        val task = RoutineTask(
            id = 1,
            title = "Trabalho",
            startMinute = 540, // 09:00
            durationMinutes = 120 // até 11:00
        )

        assertTrue(task.isCurrent(currentMinuteOfDay = 540))
        assertTrue(task.isCurrent(currentMinuteOfDay = 600))
        assertFalse(task.isCurrent(currentMinuteOfDay = 660)) // 11:00 is end
        assertFalse(task.isCurrent(currentMinuteOfDay = 500))
    }

    @Test
    fun testMidnightWrap() {
        val sleepTask = RoutineTask(
            id = 2,
            title = "Dormir",
            startMinute = 1380, // 23:00
            durationMinutes = 480 // 8 horas -> até 07:00 (420)
        )

        assertTrue(sleepTask.isCurrent(currentMinuteOfDay = 1400)) // 23:20
        assertTrue(sleepTask.isCurrent(currentMinuteOfDay = 60))   // 01:00
        assertFalse(sleepTask.isCurrent(currentMinuteOfDay = 450)) // 07:30
    }
}
