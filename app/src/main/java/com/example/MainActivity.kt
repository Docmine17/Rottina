package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.OnboardingDialog
import com.example.ui.components.TaskEditBottomSheet
import com.example.ui.components.TaskListSection
import com.example.ui.components.VisualClockCanvas
import com.example.ui.components.parseHexColor
import com.example.ui.theme.VisualClockTheme
import com.example.ui.viewmodel.VisualClockViewModel
import com.example.ui.viewmodel.VisualClockViewModelFactory
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: VisualClockViewModel by viewModels {
        VisualClockViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VisualClockTheme(darkTheme = isSystemInDarkTheme()) {
                VisualClockAppScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualClockAppScreen(viewModel: VisualClockViewModel) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val currentTime by viewModel.currentTime.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val is12HourDial by viewModel.is12HourDial.collectAsStateWithLifecycle()
    val showTutorial by viewModel.showTutorial.collectAsStateWithLifecycle()
    val editingTask by viewModel.editingTask.collectAsStateWithLifecycle()
    val isSheetOpen by viewModel.isSheetOpen.collectAsStateWithLifecycle()
    val activeTasks by viewModel.activeTasks.collectAsStateWithLifecycle()
    val draggedTaskId by viewModel.draggedTaskId.collectAsStateWithLifecycle()
    val draggedStartMinute by viewModel.draggedStartMinute.collectAsStateWithLifecycle()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openAddTask() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier.padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Adicionar Tarefa",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Top Header Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Relógio de Rotina",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    val timeFormat = if (is12HourDial) "hh:mm:ss a" else "HH:mm:ss"
                    val formattedLiveTime = currentTime.format(DateTimeFormatter.ofPattern(timeFormat, Locale.getDefault()))
                    Text(
                        text = "Horário atual: $formattedLiveTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 12h / 24h Toggle Button
                    Surface(
                        onClick = { viewModel.toggleClockDialFormat() },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(
                            text = if (is12HourDial) "AM/PM" else "24h",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Tutorial / Info Help Button
                    IconButton(
                        onClick = { viewModel.openTutorial() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Abrir Tutorial",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 2. Active Task Banners (if any tasks are running right now)
            AnimatedVisibility(
                visible = activeTasks.isNotEmpty(),
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    activeTasks.forEach { currentTask ->
                        val taskColor = parseHexColor(currentTask.colorHex)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = taskColor,
                            shadowElevation = 6.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = currentTask.icon, fontSize = 24.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "EM ANDAMENTO AGORA",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.sp,
                                        color = Color.Black.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = currentTask.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                                Surface(
                                    color = Color.Black.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Até ${currentTask.formatEndTime(is12HourDial == false)}",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Central Interactive Visual Clock Canvas
            VisualClockCanvas(
                tasks = tasks,
                currentTime = currentTime,
                is12HourDial = is12HourDial,
                draggedTaskId = draggedTaskId,
                draggedStartMinute = draggedStartMinute,
                onTaskTap = { task -> viewModel.openEditTask(task) },
                onTaskDragStart = { task -> viewModel.onTaskDragStart(task) },
                onTaskDragUpdate = { task, angle -> viewModel.onTaskDragUpdate(task, angle) },
                onTaskDragEnd = { task -> viewModel.onTaskDragEnd(task) },
                modifier = Modifier.fillMaxWidth()
            )

            // 4. Task List & Reordering Section
            TaskListSection(
                tasks = tasks,
                currentTime = currentTime,
                is24Hour = !is12HourDial,
                onTaskClick = { task -> viewModel.openEditTask(task) },
                onToggleTask = { task -> viewModel.toggleTaskEnabled(task) },
                onAddTaskClick = { viewModel.openAddTask() }
            )

            Spacer(modifier = Modifier.height(80.dp)) // Space for FAB
        }
    }

    // Task Edit Bottom Sheet
    if (isSheetOpen && editingTask != null) {
        TaskEditBottomSheet(
            task = editingTask,
            sheetState = sheetState,
            onDismiss = { viewModel.closeSheet() },
            onSave = { updated -> viewModel.saveTask(updated) },
            onDelete = { deleted -> viewModel.deleteTask(deleted) }
        )
    }

    // Tutorial Onboarding Dialog
    if (showTutorial) {
        OnboardingDialog(
            onDismiss = { viewModel.dismissTutorial() }
        )
    }
}
