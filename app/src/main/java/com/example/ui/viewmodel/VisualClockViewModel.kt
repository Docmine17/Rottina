package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.RoutineTask
import com.example.data.repository.TaskRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.roundToInt

private val PALETTE_COLORS = listOf(
    "#F44336", // Red 500
    "#E91E63", // Pink 500
    "#9C27B0", // Purple 500
    "#673AB7", // Deep Purple 500
    "#3F51B5", // Indigo 500
    "#2196F3", // Blue 500
    "#03A9F4", // Light Blue 500
    "#00BCD4", // Cyan 500
    "#009688", // Teal 500
    "#4CAF50", // Green 500
    "#8BC34A", // Light Green 500
    "#CDDC39", // Lime 500
    "#FFEB3B", // Yellow 500
    "#FFC107", // Amber 500
    "#FF9800", // Orange 500
    "#FF5722", // Deep Orange 500
    "#795548", // Brown 500
    "#9E9E9E", // Grey 500
    "#607D8B"  // Blue Grey 500
)

class VisualClockViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("visual_clock_prefs", Context.MODE_PRIVATE)
    private val db = AppDatabase.getDatabase(application)
    private val repository = TaskRepository(db.taskDao())

    val tasks: StateFlow<List<RoutineTask>> = repository.allTasks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _currentTime = MutableStateFlow(LocalTime.now())
    val currentTime: StateFlow<LocalTime> = _currentTime.asStateFlow()

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("dark_mode", true)) // Default dark mode as requested
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _is12HourDial = MutableStateFlow(prefs.getBoolean("is_12h_dial", true))
    val is12HourDial: StateFlow<Boolean> = _is12HourDial.asStateFlow()

    private val _showTutorial = MutableStateFlow(prefs.getBoolean("show_tutorial", true))
    val showTutorial: StateFlow<Boolean> = _showTutorial.asStateFlow()

    // Task currently being edited or created
    private val _editingTask = MutableStateFlow<RoutineTask?>(null)
    val editingTask: StateFlow<RoutineTask?> = _editingTask.asStateFlow()

    private val _isSheetOpen = MutableStateFlow(false)
    val isSheetOpen: StateFlow<Boolean> = _isSheetOpen.asStateFlow()

    // Temporary drag preview state for smooth drag-and-drop on clock face
    private val _draggedTaskId = MutableStateFlow<Int?>(null)
    val draggedTaskId: StateFlow<Int?> = _draggedTaskId.asStateFlow()

    private val _draggedStartMinute = MutableStateFlow<Int?>(null)
    val draggedStartMinute: StateFlow<Int?> = _draggedStartMinute.asStateFlow()

    // Active tasks calculated live
    val activeTasks: StateFlow<List<RoutineTask>> = combine(tasks, currentTime) { list, time ->
        val currentMinuteOfDay = time.hour * 60 + time.minute
        list.filter { it.isCurrent(currentMinuteOfDay) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.ensureDefaultTasks()
        }

        // Live ticker loop updating time every second
        viewModelScope.launch {
            while (true) {
                _currentTime.value = LocalTime.now()
                delay(1000)
            }
        }
    }

    fun toggleDarkMode() {
        val newValue = !_isDarkMode.value
        _isDarkMode.value = newValue
        prefs.edit().putBoolean("dark_mode", newValue).apply()
    }

    fun toggleClockDialFormat() {
        val newValue = !_is12HourDial.value
        _is12HourDial.value = newValue
        prefs.edit().putBoolean("is_12h_dial", newValue).apply()
    }

    fun dismissTutorial() {
        _showTutorial.value = false
        prefs.edit().putBoolean("show_tutorial", false).apply()
    }

    fun openTutorial() {
        _showTutorial.value = true
    }

    fun openAddTask() {
        val nowMinute = LocalTime.now().hour * 60 + LocalTime.now().minute
        // Snap to nearest 15 mins
        val snappedMinute = ((nowMinute + 7) / 15 * 15) % 1440
        _editingTask.value = RoutineTask(
            id = 0,
            title = "",
            icon = "⭐",
            colorHex = PALETTE_COLORS.random(),
            startMinute = snappedMinute,
            durationMinutes = 60,
            isEnabled = true
        )
        _isSheetOpen.value = true
    }

    fun openEditTask(task: RoutineTask) {
        _editingTask.value = task
        _isSheetOpen.value = true
    }

    fun closeSheet() {
        _isSheetOpen.value = false
        _editingTask.value = null
    }

    fun saveTask(task: RoutineTask) {
        viewModelScope.launch {
            if (task.id == 0) {
                repository.insert(task)
            } else {
                repository.update(task)
            }
            closeSheet()
        }
    }

    fun deleteTask(task: RoutineTask) {
        viewModelScope.launch {
            repository.delete(task)
            if (_editingTask.value?.id == task.id) {
                closeSheet()
            }
        }
    }

    fun toggleTaskEnabled(task: RoutineTask) {
        viewModelScope.launch {
            repository.update(task.copy(isEnabled = !task.isEnabled))
        }
    }

    // Drag-and-drop rotation interaction handler on the visual clock dial
    fun onTaskDragStart(task: RoutineTask) {
        _draggedTaskId.value = task.id
        _draggedStartMinute.value = task.startMinute
    }

    fun onTaskDragUpdate(task: RoutineTask, targetStartMinute: Int) {
        var cleanMin = ((targetStartMinute % 1440) + 1440) % 1440
        // Snap to nearest 5 minutes for clean time alignment
        cleanMin = (cleanMin / 5) * 5
        _draggedStartMinute.value = cleanMin
    }

    fun onTaskDragEnd(task: RoutineTask) {
        val newStart = _draggedStartMinute.value
        viewModelScope.launch {
            if (newStart != null && newStart != task.startMinute) {
                repository.update(task.copy(startMinute = newStart))
                // Add a small delay to allow Room database flow to emit the updated list 
                // to the UI before clearing the temporary drag state. This prevents visual 
                // snapping back to the old position momentarily.
                delay(150)
            }
            _draggedTaskId.value = null
            _draggedStartMinute.value = null
        }
    }
}

class VisualClockViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VisualClockViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VisualClockViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
