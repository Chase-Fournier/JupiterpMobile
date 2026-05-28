package com.jupiterp.jupiterpmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
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
import com.jupiterp.jupiterpmobile.domain.model.OtherScheduleItem
import com.jupiterp.jupiterpmobile.domain.model.ScheduleBlock
import com.jupiterp.ui.theme.JupiterpTheme

/**
 * Weekly schedule grid view
 */
@Composable
fun WeeklyScheduleView(
    scheduleBlocks: List<ScheduleBlock>,
    onBlockClick: (ScheduleBlock) -> Unit,
    onRemoveBlock: (ScheduleBlock) -> Unit,
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
    val timeColumnWidth = 42.dp
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
        // Day headers
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight),
            color = Color.Transparent
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
                                modifier = Modifier.padding(end = 6.dp, top = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = JupiterpTheme.extendedColors.textSecondary
                            )
                        }
                    }
                }

                // Days columns with schedule blocks
                days.forEach { day ->
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .height(hourHeight * (actualEndHour - actualStartHour))
                    ) {
                        val columnWidth = maxWidth

                        // Grid lines
                        ScheduleGridLines(
                            hourHeight = hourHeight,
                            hourCount = actualEndHour - actualStartHour
                        )

                        // Assign lanes to overlapping blocks
                        val dayBlocks = scheduleBlocks.filter { it.day == day }
                        val lanedBlocks = assignLanes(dayBlocks)

                        lanedBlocks.forEach { (block, lane, totalLanes) ->
                            ScheduleBlockView(
                                block = block,
                                startHour = actualStartHour,
                                hourHeight = hourHeight,
                                laneIndex = lane,
                                totalLanes = totalLanes,
                                columnWidth = columnWidth,
                                onClick = { onBlockClick(block) },
                                onRemove = { onRemoveBlock(block) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class LanedBlock(val block: ScheduleBlock, val lane: Int, val totalLanes: Int)

private fun assignLanes(blocks: List<ScheduleBlock>): List<LanedBlock> {
    if (blocks.isEmpty()) return emptyList()
    val sorted = blocks.sortedBy { it.startTime }
    val laneEndTimes = mutableListOf<Float>()
    val assigned = mutableListOf<Pair<ScheduleBlock, Int>>()
    for (block in sorted) {
        val lane = laneEndTimes.indexOfFirst { it <= block.startTime }
        if (lane == -1) {
            laneEndTimes.add(block.endTime)
            assigned.add(block to laneEndTimes.lastIndex)
        } else {
            laneEndTimes[lane] = block.endTime
            assigned.add(block to lane)
        }
    }
    val totalLanes = laneEndTimes.size
    return assigned.map { (block, lane) -> LanedBlock(block, lane, totalLanes) }
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
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    laneIndex: Int = 0,
    totalLanes: Int = 1,
    columnWidth: Dp = 0.dp
) {
    val density = LocalDensity.current
    val topOffset = with(density) {
        ((block.startTime - startHour) * hourHeight.toPx()).toDp()
    }
    val blockHeight = with(density) {
        (block.duration * hourHeight.toPx()).toDp()
    }
    val blockWidth = if (totalLanes > 1) columnWidth / totalLanes else columnWidth
    val xOffset = if (totalLanes > 1) blockWidth * laneIndex else 0.dp

    val scheduleColors = JupiterpTheme.extendedColors.scheduleColors
    val backgroundColor = scheduleColors[block.colorIndex % scheduleColors.size]
    val textColor = getContrastColor(backgroundColor)

    var showInfoPopup by remember { mutableStateOf(false) }

    // Parse course code into department and number (e.g., "CMSC132" -> "CMSC", "132")
    val courseCode = block.selection.course.courseCode
    val (department, courseNumber) = parseCourseCode(courseCode)

    Box(
        modifier = modifier
            .then(if (totalLanes > 1) Modifier.width(blockWidth) else Modifier.fillMaxWidth())
            .padding(horizontal = 2.dp)
            .offset(x = xOffset, y = topOffset)
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
            verticalArrangement = Arrangement.Top
        ) {
            // Department (e.g., CMSC)
            Text(
                text = department,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = textColor,
                overflow = TextOverflow.Clip
            )

            // Course number (e.g., 132)
            Text(
                text = courseNumber,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor.copy(alpha = 0.9f),
                overflow = TextOverflow.Clip
            )

            // Location (if space permits)
            if (block.duration >= 1.1f) {
                Text(
                    text = block.meeting.location.display,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f),
                    overflow = TextOverflow.Clip
                )
            }
        }
    }

    // Info popup dialog
    if (showInfoPopup) {
        ScheduleBlockInfoDialog(
            block = block,
            onDismiss = { showInfoPopup = false },
            onRemove = {
                showInfoPopup = false
                onRemove()
            }
        )
    }
}

/**
 * Info dialog for a schedule block
 */
@Composable
private fun ScheduleBlockInfoDialog(
    block: ScheduleBlock,
    onDismiss: () -> Unit,
    onRemove: () -> Unit
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
                    value = if (block.selection.section.sectionCode == "---") "No section" else block.selection.section.sectionCode
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
        dismissButton = {
            TextButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Remove", color = MaterialTheme.colorScheme.error)
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

/**
 * Parse course code into department and number (e.g., "CMSC132" -> Pair("CMSC", "132"))
 */
private fun parseCourseCode(courseCode: String): Pair<String, String> {
    val regex = Regex("^([A-Za-z]+)(\\d+.*)$")
    val match = regex.find(courseCode)
    return if (match != null) {
        Pair(match.groupValues[1].uppercase(), match.groupValues[2])
    } else {
        Pair(courseCode, "")
    }
}

/**
 * Section displaying "Other" classes (async, weekend, TBA, no meetings)
 */
@Composable
fun OtherClassesSection(
    items: List<OtherScheduleItem>,
    onRemoveItem: (OtherScheduleItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreHoriz,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = JupiterpTheme.extendedColors.textSecondary
                )
                Text(
                    text = "Other",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = JupiterpTheme.extendedColors.textSecondary
                )
            }

            // Group items by selection to avoid duplicates
            val groupedItems = items.groupBy { it.selection }

            groupedItems.forEach { (selection, selectionItems) ->
                OtherClassCard(
                    selection = selection,
                    items = selectionItems,
                    onRemove = { onRemoveItem(selectionItems.first()) }
                )
            }
        }
    }
}

@Composable
private fun OtherClassCard(
    selection: com.jupiterp.jupiterpmobile.domain.model.ScheduleSelection,
    items: List<OtherScheduleItem>,
    onRemove: () -> Unit
) {
    val scheduleColors = JupiterpTheme.extendedColors.scheduleColors
    val backgroundColor = scheduleColors[selection.colorIndex % scheduleColors.size]

    var showDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { showDialog = true })
            },
        shape = RoundedCornerShape(10.dp),
        color = backgroundColor.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selection.course.courseCode,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (selection.section.sectionCode == "---") "No section" else selection.section.sectionCode,
                    style = MaterialTheme.typography.labelSmall,
                    color = JupiterpTheme.extendedColors.textSecondary
                )
            }

            // Show type badges
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items.distinctBy { it::class }.forEach { item ->
                    val (label, icon) = when (item) {
                        is OtherScheduleItem.OnlineAsync -> "Async" to Icons.Outlined.CloudQueue
                        is OtherScheduleItem.Weekend -> item.day.short to Icons.Outlined.Weekend
                        is OtherScheduleItem.TBA -> "TBA" to Icons.AutoMirrored.Outlined.HelpOutline
                        is OtherScheduleItem.NoMeetings -> "No meetings" to Icons.Outlined.EventBusy
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = JupiterpTheme.extendedColors.textSecondary
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = JupiterpTheme.extendedColors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        OtherClassInfoDialog(
            selection = selection,
            items = items,
            onDismiss = { showDialog = false },
            onRemove = {
                showDialog = false
                onRemove()
            }
        )
    }
}

@Composable
private fun OtherClassInfoDialog(
    selection: com.jupiterp.jupiterpmobile.domain.model.ScheduleSelection,
    items: List<OtherScheduleItem>,
    onDismiss: () -> Unit,
    onRemove: () -> Unit
) {
    val scheduleColors = JupiterpTheme.extendedColors.scheduleColors
    val accentColor = scheduleColors[selection.colorIndex % scheduleColors.size]

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accentColor)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = selection.course.courseCode,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = selection.course.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = JupiterpTheme.extendedColors.textSecondary
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoRow(
                    icon = Icons.Outlined.Tag,
                    label = "Section",
                    value = if (selection.section.sectionCode == "---") "No section" else selection.section.sectionCode
                )

                // Show meeting type info
                items.distinctBy { it::class }.forEach { item ->
                    when (item) {
                        is OtherScheduleItem.OnlineAsync -> {
                            InfoRow(
                                icon = Icons.Outlined.CloudQueue,
                                label = "Format",
                                value = "Online Asynchronous"
                            )
                        }
                        is OtherScheduleItem.Weekend -> {
                            InfoRow(
                                icon = Icons.Outlined.Weekend,
                                label = "${item.day.displayName}",
                                value = item.timeRange
                            )
                        }
                        is OtherScheduleItem.TBA -> {
                            InfoRow(
                                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                                label = "Schedule",
                                value = "To Be Announced"
                            )
                        }
                        is OtherScheduleItem.NoMeetings -> {
                            InfoRow(
                                icon = Icons.Outlined.EventBusy,
                                label = "Meetings",
                                value = "No scheduled meetings"
                            )
                        }
                    }
                }

                val instructors = selection.section.instructors.joinToString(", ")
                if (instructors.isNotBlank()) {
                    InfoRow(
                        icon = Icons.Outlined.Person,
                        label = "Instructor",
                        value = instructors
                    )
                }

                InfoRow(
                    icon = Icons.Outlined.School,
                    label = "Credits",
                    value = selection.course.credits
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = JupiterpTheme.extendedColors.orange)
            }
        }
    )
}