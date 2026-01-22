package com.jupiterp.jupiterpmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jupiterp.jupiterpmobile.domain.model.DayOfWeek
import com.jupiterp.jupiterpmobile.domain.model.ScheduleBlock
import com.jupiterp.ui.theme.JupiterpTheme

/**
 * Weekly schedule grid view
 */
@Composable
fun WeeklyScheduleView(
    scheduleBlocks: List<ScheduleBlock>,
    onBlockClick: (ScheduleBlock) -> Unit,
    modifier: Modifier = Modifier,
    startHour: Int = 8,
    endHour: Int = 22
) {
    val days = listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY
    )

    val hourHeight = 60.dp
    val dayWidth = 0.dp // Will be calculated dynamically
    val timeColumnWidth = 48.dp
    val headerHeight = 40.dp

    val scrollState = rememberScrollState()

    // Calculate dynamic hour range based on classes
    val actualStartHour = remember(scheduleBlocks) {
        val earliestClass = scheduleBlocks.minOfOrNull { it.startTime }?.toInt() ?: startHour
        minOf(startHour, earliestClass)
    }

    val actualEndHour = remember(scheduleBlocks) {
        val latestClass = scheduleBlocks.maxOfOrNull { it.endTime }?.toInt()?.plus(1) ?: endHour
        maxOf(endHour, latestClass)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Day headers with rounded corners
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                // Empty corner for time column
                Spacer(modifier = Modifier.width(timeColumnWidth))

                // Day headers
                days.forEach { day ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = day.short,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Schedule grid
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                // Time column
                Column(
                    modifier = Modifier.width(timeColumnWidth)
                ) {
                    for (hour in actualStartHour until actualEndHour) {
                        Box(
                            modifier = Modifier
                                .height(hourHeight)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            Text(
                                text = formatHour(hour),
                                modifier = Modifier.padding(end = 8.dp, top = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = JupiterpTheme.extendedColors.textSecondary
                            )
                        }
                    }
                }

                // Days columns with schedule blocks
                days.forEach { day ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(hourHeight * (actualEndHour - actualStartHour))
                    ) {
                        // Grid lines
                        ScheduleGridLines(
                            hourHeight = hourHeight,
                            hourCount = actualEndHour - actualStartHour
                        )

                        // Schedule blocks for this day
                        scheduleBlocks
                            .filter { it.day == day }
                            .forEach { block ->
                                ScheduleBlockView(
                                    block = block,
                                    startHour = actualStartHour,
                                    hourHeight = hourHeight,
                                    onClick = { onBlockClick(block) }
                                )
                            }
                    }
                }
            }
        }
    }
}

/**
 * Grid lines for the schedule
 */
@Composable
private fun ScheduleGridLines(
    hourHeight: Dp,
    hourCount: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        repeat(hourCount) { index ->
            Box(
                modifier = Modifier
                    .height(hourHeight)
                    .fillMaxWidth()
                    .border(
                        width = 0.5.dp,
                        color = JupiterpTheme.extendedColors.divider,
                        shape = RoundedCornerShape(0.dp)
                    )
            )
        }
    }
}

/**
 * Individual schedule block view with expandable info popup
 */
@Composable
private fun ScheduleBlockView(
    block: ScheduleBlock,
    startHour: Int,
    hourHeight: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val topOffset = with(density) {
        ((block.startTime - startHour) * hourHeight.toPx()).toDp()
    }
    val blockHeight = with(density) {
        (block.duration * hourHeight.toPx()).toDp()
    }

    val scheduleColors = JupiterpTheme.extendedColors.scheduleColors
    val backgroundColor = scheduleColors[block.colorIndex % scheduleColors.size]
    val textColor = getContrastColor(backgroundColor)

    var showInfoPopup by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .offset(y = topOffset)
            .height(blockHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { showInfoPopup = true })
            }
            .padding(6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Course code
            Text(
                text = block.selection.course.courseCode,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Location (if space permits)
            if (block.duration >= 1f) {
                Text(
                    text = block.meeting.location.display,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Time
            Text(
                text = "${block.meeting.classtime.startFormatted} - ${block.meeting.classtime.endFormatted}",
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    // Info popup dialog
    if (showInfoPopup) {
        ScheduleBlockInfoDialog(
            block = block,
            onDismiss = { showInfoPopup = false }
        )
    }
}

/**
 * Info dialog for a schedule block
 */
@Composable
private fun ScheduleBlockInfoDialog(
    block: ScheduleBlock,
    onDismiss: () -> Unit
) {
    val scheduleColors = JupiterpTheme.extendedColors.scheduleColors
    val accentColor = scheduleColors[block.colorIndex % scheduleColors.size]

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column {
                // Color bar accent
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accentColor)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = block.selection.course.courseCode,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = block.selection.course.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = JupiterpTheme.extendedColors.textSecondary
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section
                InfoRow(
                    icon = Icons.Outlined.Tag,
                    label = "Section",
                    value = block.selection.section.sectionCode
                )

                // Time
                InfoRow(
                    icon = Icons.Outlined.Schedule,
                    label = "Time",
                    value = "${block.meeting.classtime.startFormatted} - ${block.meeting.classtime.endFormatted}"
                )

                // Day
                InfoRow(
                    icon = Icons.Outlined.CalendarToday,
                    label = "Day",
                    value = block.day.displayName
                )

                // Location
                InfoRow(
                    icon = Icons.Outlined.LocationOn,
                    label = "Location",
                    value = block.meeting.location.display
                )

                // Instructors
                val instructors = block.selection.section.instructors.joinToString(", ")
                if (instructors.isNotBlank()) {
                    InfoRow(
                        icon = Icons.Outlined.Person,
                        label = "Instructor",
                        value = instructors
                    )
                }

                // Credits
                InfoRow(
                    icon = Icons.Outlined.School,
                    label = "Credits",
                    value = block.selection.course.credits
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = JupiterpTheme.extendedColors.orange)
            }
        }
    )
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = JupiterpTheme.extendedColors.textSecondary
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = JupiterpTheme.extendedColors.textSecondary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Format hour to 12-hour format
 */
private fun formatHour(hour: Int): String {
    return when {
        hour == 0 -> "12 AM"
        hour < 12 -> "$hour AM"
        hour == 12 -> "12 PM"
        else -> "${hour - 12} PM"
    }
}

/**
 * Get contrasting text color for schedule blocks
 */
private fun getContrastColor(backgroundColor: Color): Color {
    // Calculate relative luminance
    val r = backgroundColor.red
    val g = backgroundColor.green
    val b = backgroundColor.blue
    val luminance = 0.299 * r + 0.587 * g + 0.114 * b

    // Use dark text for light backgrounds
    return if (luminance > 0.6) {
        Color(0xFF1F2937)
    } else {
        Color.White
    }
}