package com.jupiterp.jupiterpmobile.ui.screens.generator

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jupiterp.jupiterpmobile.data.repository.ScheduleRepository
import com.jupiterp.jupiterpmobile.domain.model.DayOfWeek
import com.jupiterp.jupiterpmobile.domain.model.ScheduleBlock
import com.jupiterp.jupiterpmobile.domain.scheduler.GeneratedSchedule
import com.jupiterp.jupiterpmobile.domain.scheduler.HardConstraints
import com.jupiterp.jupiterpmobile.domain.scheduler.RelaxationKind
import com.jupiterp.jupiterpmobile.domain.scheduler.SortCriterion
import com.jupiterp.jupiterpmobile.domain.scheduler.sortedByCriterion
import com.jupiterp.jupiterpmobile.ui.components.FilterChip
import com.jupiterp.jupiterpmobile.ui.components.MeetingInfo
import com.jupiterp.jupiterpmobile.ui.components.RatingChip
import com.jupiterp.jupiterpmobile.ui.components.SolarSystemLoader
import com.jupiterp.jupiterpmobile.ui.components.WeeklyScheduleView
import com.jupiterp.ui.theme.JupiterpTheme
import kotlin.math.roundToInt

/**
 * Schedule generator: define course requirements and constraints, then browse
 * every conflict-free schedule sorted by what matters to you.
 */
@Composable
fun GeneratorScreen(
    viewModel: GeneratorViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.generationState.collectAsState()
    val requirements by viewModel.requirements.collectAsState()
    val constraints by viewModel.constraints.collectAsState()
    val courseQuery by viewModel.courseQuery.collectAsState()
    val courseSuggestions by viewModel.courseSuggestions.collectAsState()
    val sortCriterion by viewModel.sortCriterion.collectAsState()
    val currentScheduleSize by viewModel.currentScheduleSize.collectAsState()

    // The schedule whose full detail is being viewed; only meaningful while
    // results are showing.
    var detailSchedule by remember { mutableStateOf<GeneratedSchedule?>(null) }
    val doneState = state as? GeneratorViewModel.GenerationState.Done
    // Drop a stale detail selection if generation is rerun
    LaunchedEffect(doneState) { if (doneState == null) detailSchedule = null }
    val viewingDetail = doneState != null && detailSchedule != null

    val title = when {
        viewingDetail -> "Schedule Details"
        doneState != null -> "Generated Schedules"
        else -> "Schedule Generator"
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    when {
                        viewingDetail -> detailSchedule = null
                        state is GeneratorViewModel.GenerationState.Done ||
                            state is GeneratorViewModel.GenerationState.Loading ->
                            viewModel.backToRequirements()
                        else -> onClose()
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            when (val s = state) {
                is GeneratorViewModel.GenerationState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        SolarSystemLoader(size = 120.dp, color = JupiterpTheme.extendedColors.orange)
                    }
                }

                is GeneratorViewModel.GenerationState.Done -> {
                    val detail = detailSchedule
                    if (detail != null) {
                        ScheduleDetailView(
                            schedule = detail,
                            rank = remember(s.schedules, sortCriterion, detail) {
                                s.schedules.sortedByCriterion(sortCriterion).indexOf(detail) + 1
                            },
                            instructorRatings = s.instructorRatings,
                            currentScheduleSize = currentScheduleSize,
                            onApply = {
                                viewModel.applySchedule(detail)
                                onClose()
                            },
                            onSave = { name -> viewModel.saveSchedule(detail, name) }
                        )
                    } else {
                        ResultsContent(
                            schedules = s.schedules,
                            truncated = s.truncated,
                            sortCriterion = sortCriterion,
                            onSortChange = viewModel::setSortCriterion,
                            currentScheduleSize = currentScheduleSize,
                            onOpenDetail = { detailSchedule = it },
                            onApply = { schedule ->
                                viewModel.applySchedule(schedule)
                                onClose()
                            },
                            onSave = viewModel::saveSchedule
                        )
                    }
                }

                else -> {
                    RequirementsContent(
                        requirements = requirements,
                        constraints = constraints,
                        courseQuery = courseQuery,
                        courseSuggestions = courseSuggestions,
                        currentScheduleSize = currentScheduleSize,
                        noSchedules = s as? GeneratorViewModel.GenerationState.NoSchedules,
                        failure = s as? GeneratorViewModel.GenerationState.Failed,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Requirements page
// ---------------------------------------------------------------------------

@Composable
private fun RequirementsContent(
    requirements: List<GeneratorViewModel.RequirementItem>,
    constraints: HardConstraints,
    courseQuery: String,
    courseSuggestions: List<GeneratorViewModel.CourseSuggestion>,
    currentScheduleSize: Int,
    noSchedules: GeneratorViewModel.GenerationState.NoSchedules?,
    failure: GeneratorViewModel.GenerationState.Failed?,
    viewModel: GeneratorViewModel
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // No-results explanation, shown right at the top after a failed run
            if (noSchedules != null) {
                NoSchedulesCard(
                    noSchedules = noSchedules,
                    constraints = constraints,
                    onApplyHint = viewModel::applyRelaxation
                )
            }
            if (failure != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.ErrorOutline, null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(failure.message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Courses
            Text(
                "Courses (${requirements.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (requirements.isEmpty()) {
                Text(
                    "Add the courses you need to take. The generator finds every section combination with no time conflicts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = JupiterpTheme.extendedColors.textSecondary
                )
            }

            requirements.forEach { requirement ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            requirement.courseCode,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = JupiterpTheme.extendedColors.orange
                        )
                        Text(
                            requirement.courseName,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                        )
                        IconButton(
                            onClick = { viewModel.removeCourse(requirement.courseCode) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close, "Remove",
                                modifier = Modifier.size(18.dp),
                                tint = JupiterpTheme.extendedColors.textSecondary
                            )
                        }
                    }
                }
            }

            if (currentScheduleSize > 0) {
                TextButton(onClick = viewModel::seedFromCurrentSchedule) {
                    Icon(
                        Icons.Outlined.School, null,
                        modifier = Modifier.size(18.dp),
                        tint = JupiterpTheme.extendedColors.orange
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Use my current schedule ($currentScheduleSize courses)",
                        color = JupiterpTheme.extendedColors.orange
                    )
                }
            }

            // Add-course search
            OutlinedTextField(
                value = courseQuery,
                onValueChange = viewModel::onCourseQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Add a course (e.g., CMSC132)") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JupiterpTheme.extendedColors.orange,
                    cursorColor = JupiterpTheme.extendedColors.orange
                )
            )

            if (courseSuggestions.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    Column {
                        courseSuggestions.take(6).forEachIndexed { index, suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.addCourse(suggestion) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    suggestion.courseCode,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = JupiterpTheme.extendedColors.orange
                                )
                                Text(
                                    suggestion.courseName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (index < courseSuggestions.take(6).lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = JupiterpTheme.extendedColors.divider
                                )
                            }
                        }
                    }
                }
            }

            // Constraints
            Text(
                "Requirements",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp)
            )

            ConstraintsCard(constraints = constraints, viewModel = viewModel)

            Spacer(Modifier.height(8.dp))
        }

        // Generate button pinned at the bottom
        Surface(color = MaterialTheme.colorScheme.background) {
            Button(
                onClick = viewModel::generate,
                enabled = requirements.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = JupiterpTheme.extendedColors.orange
                )
            ) {
                Text(
                    "Generate schedules",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ConstraintsCard(
    constraints: HardConstraints,
    viewModel: GeneratorViewModel
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Earliest start
            TimeConstraintRow(
                label = "No classes before",
                minutes = constraints.earliestStartMinutes,
                defaultMinutes = 9 * 60,
                range = 7f..12f,
                onChange = viewModel::setEarliestStart
            )

            // Latest end
            TimeConstraintRow(
                label = "No classes after",
                minutes = constraints.latestEndMinutes,
                defaultMinutes = 17 * 60,
                range = 12f..22f,
                onChange = viewModel::setLatestEnd
            )

            // Days off
            Text(
                "Days off",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        label = day.short,
                        selected = day in constraints.daysOff,
                        onClick = { viewModel.toggleDayOff(day) }
                    )
                }
            }

            // Open seats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Open seats only",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Skip sections that are already full",
                        style = MaterialTheme.typography.bodySmall,
                        color = JupiterpTheme.extendedColors.textSecondary
                    )
                }
                Switch(
                    checked = constraints.onlyOpenSeats,
                    onCheckedChange = viewModel::setOnlyOpenSeats,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = JupiterpTheme.extendedColors.orange
                    )
                )
            }

            // Minimum gap
            Text(
                "Break between classes",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 10, 20, 30).forEach { gap ->
                    FilterChip(
                        label = if (gap == 0) "None" else "$gap min",
                        selected = constraints.minGapMinutes == gap,
                        onClick = { viewModel.setMinGap(gap) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeConstraintRow(
    label: String,
    minutes: Int?,
    defaultMinutes: Int,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Int?) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (minutes != null) "$label ${formatMinutes(minutes)}" else label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Switch(
                checked = minutes != null,
                onCheckedChange = { enabled ->
                    onChange(if (enabled) defaultMinutes else null)
                },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = JupiterpTheme.extendedColors.orange
                )
            )
        }
        if (minutes != null) {
            Slider(
                value = minutes / 60f,
                onValueChange = { hours -> onChange(((hours * 2).roundToInt() * 30)) },
                valueRange = range,
                steps = ((range.endInclusive - range.start) * 2).toInt() - 1,
                colors = SliderDefaults.colors(
                    thumbColor = JupiterpTheme.extendedColors.orange,
                    activeTrackColor = JupiterpTheme.extendedColors.orange
                )
            )
        }
    }
}

@Composable
private fun NoSchedulesCard(
    noSchedules: GeneratorViewModel.GenerationState.NoSchedules,
    constraints: HardConstraints,
    onApplyHint: (GeneratorViewModel.RelaxationHint) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = JupiterpTheme.extendedColors.warning.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "No conflict-free schedules found",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (noSchedules.coursesWithoutSections.isNotEmpty()) {
                Text(
                    "No sections match your filters for: ${noSchedules.coursesWithoutSections.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = JupiterpTheme.extendedColors.textSecondary
                )
            }
            if (noSchedules.hints.isNotEmpty()) {
                Text(
                    "Loosening one requirement would help — tap to try:",
                    style = MaterialTheme.typography.bodySmall,
                    color = JupiterpTheme.extendedColors.textSecondary
                )
                noSchedules.hints.forEach { hint ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onApplyHint(hint) },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Lightbulb, null,
                                modifier = Modifier.size(16.dp),
                                tint = JupiterpTheme.extendedColors.orange
                            )
                            Text(
                                hintLabel(hint, constraints),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${hint.scheduleCount}${if (hint.countIsLowerBound) "+" else ""}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = JupiterpTheme.extendedColors.orange
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun hintLabel(
    hint: GeneratorViewModel.RelaxationHint,
    constraints: HardConstraints
): String = when (hint.relaxation.kind) {
    RelaxationKind.EARLIEST_START ->
        constraints.earliestStartMinutes
            ?.let { "Allow classes before ${formatMinutes(it)}" }
            ?: "Remove the earliest-start limit"

    RelaxationKind.LATEST_END ->
        constraints.latestEndMinutes
            ?.let { "Allow classes after ${formatMinutes(it)}" }
            ?: "Remove the latest-end limit"

    RelaxationKind.DAY_OFF ->
        "Allow ${hint.relaxation.day?.full ?: "that day's"} classes"

    RelaxationKind.OPEN_SEATS -> "Include full sections"

    RelaxationKind.MIN_GAP -> "Remove the break-between-classes requirement"
}

// ---------------------------------------------------------------------------
// Results list
// ---------------------------------------------------------------------------

@Composable
private fun ResultsContent(
    schedules: List<GeneratedSchedule>,
    truncated: Boolean,
    sortCriterion: SortCriterion,
    onSortChange: (SortCriterion) -> Unit,
    currentScheduleSize: Int,
    onOpenDetail: (GeneratedSchedule) -> Unit,
    onApply: (GeneratedSchedule) -> Unit,
    onSave: (GeneratedSchedule, String) -> Unit
) {
    val sorted = remember(schedules, sortCriterion) { schedules.sortedByCriterion(sortCriterion) }

    var confirmApply by remember { mutableStateOf<GeneratedSchedule?>(null) }
    var saveTarget by remember { mutableStateOf<GeneratedSchedule?>(null) }

    confirmApply?.let { schedule ->
        ApplyConfirmDialog(
            currentScheduleSize = currentScheduleSize,
            onConfirm = { confirmApply = null; onApply(schedule) },
            onDismiss = { confirmApply = null }
        )
    }
    saveTarget?.let { schedule ->
        SaveScheduleDialog(
            onSave = { name -> onSave(schedule, name); saveTarget = null },
            onDismiss = { saveTarget = null }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            if (truncated) "Showing the first ${sorted.size} schedules"
            else "${sorted.size} schedule${if (sorted.size != 1) "s" else ""} found",
            style = MaterialTheme.typography.bodyMedium,
            color = JupiterpTheme.extendedColors.textSecondary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        LazyRow(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(SortCriterion.entries) { criterion ->
                FilterChip(
                    label = criterion.label,
                    selected = criterion == sortCriterion,
                    onClick = { onSortChange(criterion) }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(sorted) { index, schedule ->
                ScheduleResultCard(
                    rank = index + 1,
                    schedule = schedule,
                    onClick = { onOpenDetail(schedule) },
                    onApply = {
                        if (currentScheduleSize > 0) confirmApply = schedule
                        else onApply(schedule)
                    },
                    onSave = { saveTarget = schedule }
                )
            }
        }
    }
}

@Composable
private fun ScheduleResultCard(
    rank: Int,
    schedule: GeneratedSchedule,
    onClick: () -> Unit,
    onApply: () -> Unit,
    onSave: () -> Unit
) {
    val metrics = schedule.metrics
    val blocks = remember(schedule) { ScheduleRepository.getScheduleBlocks(schedule.selections) }

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header: rank + title on the left, rating featured on the right
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RankBadge(rank)
                Text(
                    "Schedule #$rank",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                RatingSummary(metrics)
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "View details",
                    tint = JupiterpTheme.extendedColors.textSecondary
                )
            }

            HorizontalDivider(color = JupiterpTheme.extendedColors.divider)

            // Body: preview beside details on wide cards, stacked on narrow ones
            BoxWithConstraints {
                val stack = maxWidth < 360.dp
                if (stack) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (blocks.isNotEmpty()) {
                            MiniSchedulePreview(
                                blocks = blocks,
                                modifier = Modifier.fillMaxWidth().height(112.dp)
                            )
                        }
                        ScheduleCardDetails(schedule.selections, metrics)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (blocks.isNotEmpty()) {
                            MiniSchedulePreview(
                                blocks = blocks,
                                modifier = Modifier.width(128.dp).height(132.dp)
                            )
                        }
                        ScheduleCardDetails(
                            schedule.selections, metrics,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            HorizontalDivider(color = JupiterpTheme.extendedColors.divider)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onSave) {
                    Text("Save", color = JupiterpTheme.extendedColors.textSecondary)
                }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = onApply,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JupiterpTheme.extendedColors.orange
                    )
                ) {
                    Text("Apply", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Adaptive metric chips + a per-course line with section and instructor names. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleCardDetails(
    selections: List<com.jupiterp.jupiterpmobile.domain.model.ScheduleSelection>,
    metrics: com.jupiterp.jupiterpmobile.domain.scheduler.ScheduleMetrics,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MetricBadge(creditsText(metrics))
            MetricBadge("${metrics.daysWithClasses} day${if (metrics.daysWithClasses != 1) "s" else ""}")
            MetricBadge(gapText(metrics.totalGapMinutes))
            MetricBadge(classWindowText(metrics))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            selections.forEach { selection ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        selection.course.courseCode,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        selection.section.sectionCode,
                        style = MaterialTheme.typography.labelSmall,
                        color = JupiterpTheme.extendedColors.sectionCodes
                    )
                    Text(
                        selection.section.instructorsDisplay,
                        style = MaterialTheme.typography.bodySmall,
                        color = JupiterpTheme.extendedColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** Rating chip plus coverage line, or a muted "Unrated" pill when no ratings exist. */
@Composable
private fun RatingSummary(metrics: com.jupiterp.jupiterpmobile.domain.scheduler.ScheduleMetrics) {
    if (metrics.avgInstructorRating == null) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                "Unrated",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = JupiterpTheme.extendedColors.textSecondary
            )
        }
    } else {
        Column(horizontalAlignment = Alignment.End) {
            RatingChip(rating = metrics.avgInstructorRating)
            Text(
                "${metrics.ratedSectionCount}/${metrics.sectionCount} rated",
                style = MaterialTheme.typography.labelSmall,
                color = JupiterpTheme.extendedColors.textSecondary
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Detail view
// ---------------------------------------------------------------------------

@Composable
private fun ScheduleDetailView(
    schedule: GeneratedSchedule,
    rank: Int,
    instructorRatings: Map<String, Float>,
    currentScheduleSize: Int,
    onApply: () -> Unit,
    onSave: (String) -> Unit
) {
    val metrics = schedule.metrics
    val blocks = remember(schedule) { ScheduleRepository.getScheduleBlocks(schedule.selections) }

    var confirmApply by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    if (confirmApply) {
        ApplyConfirmDialog(
            currentScheduleSize = currentScheduleSize,
            onConfirm = { confirmApply = false; onApply() },
            onDismiss = { confirmApply = false }
        )
    }
    if (saving) {
        SaveScheduleDialog(
            onSave = { name -> onSave(name); saving = false },
            onDismiss = { saving = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Full week grid (read-only — tapping a block still shows its info)
        if (blocks.isNotEmpty()) {
            WeeklyScheduleView(
                scheduleBlocks = blocks,
                onBlockClick = { },
                modifier = Modifier.fillMaxWidth().height(320.dp)
            )
            HorizontalDivider(color = JupiterpTheme.extendedColors.divider)
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RankBadge(rank)
                    Spacer(Modifier.width(10.dp))
                    RatingChip(rating = metrics.avgInstructorRating)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (metrics.avgInstructorRating == null) "No instructor ratings"
                        else "avg • ${metrics.ratedSectionCount}/${metrics.sectionCount} sections rated",
                        style = MaterialTheme.typography.labelMedium,
                        color = JupiterpTheme.extendedColors.textSecondary
                    )
                }
            }

            item { MetricsGrid(metrics) }

            item {
                Text(
                    "Courses",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            items(schedule.selections) { selection ->
                CourseDetailCard(selection = selection, instructorRatings = instructorRatings)
            }
        }

        // Action bar
        Surface(tonalElevation = 3.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { saving = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save")
                }
                Button(
                    onClick = { if (currentScheduleSize > 0) confirmApply = true else onApply() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JupiterpTheme.extendedColors.orange
                    )
                ) {
                    Text("Apply to planner", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun CourseDetailCard(
    selection: com.jupiterp.jupiterpmobile.domain.model.ScheduleSelection,
    instructorRatings: Map<String, Float>
) {
    val palette = JupiterpTheme.extendedColors.scheduleColors
    val accent = palette[selection.colorIndex % palette.size]

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Color spine matching the grid block
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(accent)
            )
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        selection.course.courseCode,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = JupiterpTheme.extendedColors.orange
                    )
                    Text(
                        "Section ${selection.section.sectionCode}",
                        style = MaterialTheme.typography.labelMedium,
                        color = JupiterpTheme.extendedColors.sectionCodes
                    )
                    Text(
                        "${selection.course.credits} cr",
                        style = MaterialTheme.typography.labelMedium,
                        color = JupiterpTheme.extendedColors.textSecondary
                    )
                }
                Text(
                    selection.course.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                // Per-instructor ratings
                if (selection.section.instructors.isEmpty()) {
                    Text(
                        "Instructor: TBA",
                        style = MaterialTheme.typography.bodySmall,
                        color = JupiterpTheme.extendedColors.textSecondary
                    )
                } else {
                    selection.section.instructors.forEach { name ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            val rating = instructorRatings[name]
                            if (rating != null) {
                                RatingChip(rating = rating)
                            } else {
                                Text(
                                    "unrated",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JupiterpTheme.extendedColors.textSecondary
                                )
                            }
                        }
                    }
                }

                // Meetings
                selection.section.meetings.forEach { meeting ->
                    MeetingInfo(meeting = meeting)
                }
                if (selection.section.meetings.isEmpty()) {
                    Text(
                        "No scheduled meetings",
                        style = MaterialTheme.typography.bodySmall,
                        color = JupiterpTheme.extendedColors.textSecondary
                    )
                }

                // Seats
                Text(
                    "${selection.section.openSeats} of ${selection.section.totalSeats} seats open" +
                        if (selection.section.waitlist > 0) " • ${selection.section.waitlist} waitlisted" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = JupiterpTheme.extendedColors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun MetricsGrid(metrics: com.jupiterp.jupiterpmobile.domain.scheduler.ScheduleMetrics) {
    val stats = buildList {
        add("Credits" to creditsText(metrics))
        add("Days on campus" to "${metrics.daysWithClasses}")
        add("Gaps / week" to gapDurationText(metrics.totalGapMinutes))
        metrics.earliestStartMinutes?.let { add("First class" to formatMinutes(it)) }
        metrics.latestEndMinutes?.let { add("Last class" to formatMinutes(it)) }
        add("Tightest seats" to "${metrics.minOpenSeats} open")
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            stats.chunked(2).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEach { (label, value) ->
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = JupiterpTheme.extendedColors.textSecondary
                            )
                            Text(
                                value,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------

@Composable
private fun RankBadge(rank: Int) {
    Surface(
        shape = CircleShape,
        color = JupiterpTheme.extendedColors.orangeContainer,
        modifier = Modifier.size(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "$rank",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = JupiterpTheme.extendedColors.orange
            )
        }
    }
}

@Composable
private fun MetricBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ApplyConfirmDialog(
    currentScheduleSize: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Replace current schedule?", fontWeight = FontWeight.Bold) },
        text = {
            Text("Applying this will replace the $currentScheduleSize course${if (currentScheduleSize != 1) "s" else ""} currently in your planner.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Apply", color = JupiterpTheme.extendedColors.orange)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SaveScheduleDialog(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Save Schedule", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("e.g., Fall Plan A") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JupiterpTheme.extendedColors.orange,
                    cursorColor = JupiterpTheme.extendedColors.orange
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text("Save", color = JupiterpTheme.extendedColors.orange)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Lightweight week-grid thumbnail: 5 weekday columns, blocks placed
 * proportionally within an 8am-8pm window.
 */
@Composable
private fun MiniSchedulePreview(
    blocks: List<ScheduleBlock>,
    modifier: Modifier = Modifier
) {
    val palette = JupiterpTheme.extendedColors.scheduleColors
    val gridColor = JupiterpTheme.extendedColors.divider

    Canvas(modifier = modifier) {
        val dayWidth = size.width / 5f
        val startHour = 8f
        val endHour = 20f
        val span = endHour - startHour

        for (i in 1 until 5) {
            drawLine(
                color = gridColor,
                start = Offset(dayWidth * i, 0f),
                end = Offset(dayWidth * i, size.height),
                strokeWidth = 1f
            )
        }

        blocks.forEach { block ->
            val column = block.day.column
            if (column > 4) return@forEach
            val top = ((block.startTime - startHour) / span).coerceIn(0f, 1f) * size.height
            val bottom = ((block.endTime - startHour) / span).coerceIn(0f, 1f) * size.height
            drawRoundRect(
                color = palette[block.colorIndex % palette.size],
                topLeft = Offset(column * dayWidth + 2f, top),
                size = Size(dayWidth - 4f, (bottom - top).coerceAtLeast(4f)),
                cornerRadius = CornerRadius(6f, 6f)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Formatting helpers
// ---------------------------------------------------------------------------

private fun formatMinutes(totalMinutes: Int): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    val period = if (h >= 12) "PM" else "AM"
    val displayHour = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return if (m == 0) "$displayHour $period"
    else "$displayHour:${m.toString().padStart(2, '0')} $period"
}

private fun creditsText(metrics: com.jupiterp.jupiterpmobile.domain.scheduler.ScheduleMetrics): String =
    if (metrics.minCredits == metrics.maxCredits) "${metrics.minCredits} cr"
    else "${metrics.minCredits}–${metrics.maxCredits} cr"

private fun classWindowText(metrics: com.jupiterp.jupiterpmobile.domain.scheduler.ScheduleMetrics): String {
    val start = metrics.earliestStartMinutes
    val end = metrics.latestEndMinutes
    return if (start == null || end == null) "No fixed times"
    else "${formatMinutes(start)} – ${formatMinutes(end)}"
}

// Bare duration, e.g. "none", "45m", "2h", "1h 30m".
private fun gapDurationText(gapMinutes: Int): String = when {
    gapMinutes == 0 -> "none"
    gapMinutes < 60 -> "${gapMinutes}m"
    gapMinutes % 60 == 0 -> "${gapMinutes / 60}h"
    else -> "${gapMinutes / 60}h ${gapMinutes % 60}m"
}

// Gap totals are summed across the whole week, so the card chip says "/wk"
// explicitly to avoid reading a weekly figure as a single-day one.
private fun gapText(gapMinutes: Int): String =
    if (gapMinutes == 0) "no gaps" else "${gapDurationText(gapMinutes)} gaps/wk"
