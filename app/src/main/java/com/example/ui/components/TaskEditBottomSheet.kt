package com.example.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.emoji2.emojipicker.EmojiPickerView
import com.example.data.model.RoutineTask
import java.time.LocalTime
import java.util.Locale

private val PRESET_ICONS = listOf(
    "🥪", "🍽️", "🏖️", "🍝", "🎮", "🎉", "☕", "❤️",
    "🏃", "😴", "💼", "📚", "💊", "🎨", "🎵", "🚿",
    "🛒", "✈️", "🙏", "🚴"
)

private val PRESET_COLORS = listOf(
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

private val DURATION_PRESETS = listOf(
    15 to "15 min",
    30 to "30 min",
    45 to "45 min",
    60 to "1 hora",
    90 to "1.5h",
    120 to "2 horas",
    180 to "3 horas",
    240 to "4 horas",
    360 to "6 horas",
    480 to "8 horas"
)

private fun formatDurationText(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> if (h == 1) "1 hora" else "$h horas"
        else -> "${h}h ${m}min"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditBottomSheet(
    task: RoutineTask?,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSave: (RoutineTask) -> Unit,
    onDelete: (RoutineTask) -> Unit
) {
    if (task == null) return

    var title by remember(task) { mutableStateOf(task.title) }
    var selectedIcon by remember(task) { 
        mutableStateOf(if (task.id == 0 && (task.icon == "⭐" || task.icon == "⏱️")) PRESET_ICONS.first() else task.icon) 
    }
    var selectedColorHex by remember(task) {
        mutableStateOf(if (task.id == 0 && task.colorHex.isEmpty()) PRESET_COLORS.random() else task.colorHex)
    }
    var startMinute by remember(task) { mutableIntStateOf(task.startMinute) }
    var durationMinutes by remember(task) { mutableIntStateOf(task.durationMinutes) }
    var isCustomDuration by remember(task) {
        mutableStateOf(DURATION_PRESETS.none { it.first == task.durationMinutes })
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Title & Delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (task.id == 0) "Nova Tarefa de Rotina" else "Editar Tarefa",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (task.id != 0) {
                    IconButton(
                        onClick = { onDelete(task) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Excluir Tarefa",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Title TextField
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Nome da Tarefa (ex: Almoço, Exercício)") },
                placeholder = { Text("Digite o nome...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            // Icon Selector
            Text(
                text = "Ícone / Emoji",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            var showEmojiPicker by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PRESET_ICONS.forEach { icon ->
                    val isSelected = icon == selectedIcon
                    Surface(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { selectedIcon = icon },
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = icon, fontSize = 22.sp)
                        }
                    }
                }

                val isCustomEmojiSelected = selectedIcon !in PRESET_ICONS
                Surface(
                    modifier = Modifier
                        .height(48.dp)
                        .defaultMinSize(minWidth = 48.dp)
                        .clip(CircleShape)
                        .clickable { showEmojiPicker = true },
                    shape = CircleShape,
                    color = if (isCustomEmojiSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = if (isCustomEmojiSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = if (isCustomEmojiSelected) 12.dp else 0.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isCustomEmojiSelected) {
                            Text(text = selectedIcon, fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Mais Emojis",
                            tint = if (isCustomEmojiSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (showEmojiPicker) {
                val isDark = androidx.compose.foundation.isSystemInDarkTheme()
                Dialog(
                    onDismissRequest = { showEmojiPicker = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(450.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        AndroidView(
                            factory = { context ->
                                val themeRes = if (isDark) android.R.style.Theme_DeviceDefault else android.R.style.Theme_DeviceDefault_Light
                                val wrapper = android.view.ContextThemeWrapper(context, themeRes)
                                EmojiPickerView(wrapper).apply {
                                    setOnEmojiPickedListener { item ->
                                        selectedIcon = item.emoji
                                        showEmojiPicker = false
                                    }
                                }
                            },
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }

            // Color Selector
            Text(
                text = "Cor Visual da Tarefa",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PRESET_COLORS.forEach { hex ->
                    val color = parseHexColor(hex)
                    val isSelected = hex.equals(selectedColorHex, ignoreCase = true)
                    
                    // Choose white or black checkmark depending on luminance for clear visibility
                    val isLightColor = hex.equals("#FFEB3B", ignoreCase = true) || 
                                       hex.equals("#FFC107", ignoreCase = true) || 
                                       hex.equals("#CDDC39", ignoreCase = true) ||
                                       hex.equals("#8BC34A", ignoreCase = true) ||
                                       hex.equals("#00BCD4", ignoreCase = true) ||
                                       hex.equals("#03A9F4", ignoreCase = true)

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { selectedColorHex = hex }
                            .then(
                                if (isSelected) Modifier.border(3.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Cor Selecionada",
                                tint = if (isLightColor) Color.Black else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // Start Time Selector with Material 3 TimePicker Dialog
            val context = LocalContext.current
            val is24HourFormat = remember { DateFormat.is24HourFormat(context) }
            val currentHour = (startMinute / 60) % 24
            val currentMinute = startMinute % 60
            var showTimePickerDialog by remember { mutableStateOf(false) }

            val displayFormattedTime = remember(startMinute, is24HourFormat) {
                if (is24HourFormat) {
                    String.format(Locale.getDefault(), "%02d:%02d", currentHour, currentMinute)
                } else {
                    val h12 = if (currentHour % 12 == 0) 12 else currentHour % 12
                    val amPm = if (currentHour < 12) "AM" else "PM"
                    String.format(Locale.getDefault(), "%02d:%02d %s", h12, currentMinute, amPm)
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Horário de Início",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Interactive Card to open Material 3 TimePicker Dialog
                Surface(
                    onClick = { showTimePickerDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = "Início Selecionado",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = displayFormattedTime,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Editar Horário",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Alterar",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            if (showTimePickerDialog) {
                Material3TimePickerDialog(
                    initialHour = currentHour,
                    initialMinute = currentMinute,
                    is24Hour = is24HourFormat,
                    onDismiss = { showTimePickerDialog = false },
                    onConfirm = { selectedHour, selectedMinute ->
                        startMinute = ((selectedHour * 60) + selectedMinute) % 1440
                        showTimePickerDialog = false
                    }
                )
            }

            // Duration Selection (Atalhos Rápidos + Personalizado)
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Duração da Tarefa",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = formatDurationText(durationMinutes),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DURATION_PRESETS.forEach { (mins, label) ->
                        val isSelected = !isCustomDuration && durationMinutes == mins
                        if (isSelected) {
                            Button(
                                onClick = {
                                    isCustomDuration = false
                                    durationMinutes = mins
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text(label, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    isCustomDuration = false
                                    durationMinutes = mins
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(label)
                            }
                        }
                    }

                    if (isCustomDuration) {
                        Button(
                            onClick = { isCustomDuration = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Personalizado", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { isCustomDuration = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Personalizado")
                        }
                    }
                }

                if (isCustomDuration) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Ajuste Personalizado de Duração",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatDurationText(durationMinutes),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Slider(
                                value = durationMinutes.coerceIn(5, 720).toFloat(),
                                onValueChange = { durationMinutes = ((it / 5).toInt() * 5).coerceIn(5, 720) },
                                valueRange = 5f..720f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                            ) {
                                OutlinedButton(
                                    onClick = { durationMinutes = (durationMinutes - 60).coerceAtLeast(5) },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("-1h")
                                }
                                OutlinedButton(
                                    onClick = { durationMinutes = (durationMinutes - 15).coerceAtLeast(5) },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("-15m")
                                }
                                OutlinedButton(
                                    onClick = { durationMinutes = (durationMinutes + 15).coerceAtMost(1440) },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("+15m")
                                }
                                OutlinedButton(
                                    onClick = { durationMinutes = (durationMinutes + 60).coerceAtMost(1440) },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("+1h")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Save Action Button
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(
                            task.copy(
                                title = title.trim(),
                                icon = selectedIcon,
                                colorHex = selectedColorHex,
                                startMinute = startMinute,
                                durationMinutes = durationMinutes
                            )
                        )
                    }
                },
                enabled = title.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = "Salvar Tarefa na Rotina",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Material3TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = is24Hour
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Selecionar Horário de Início",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                TimePicker(
                    state = timePickerState
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConfirm(timePickerState.hour, timePickerState.minute)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Confirmar")
                    }
                }
            }
        }
    }
}


