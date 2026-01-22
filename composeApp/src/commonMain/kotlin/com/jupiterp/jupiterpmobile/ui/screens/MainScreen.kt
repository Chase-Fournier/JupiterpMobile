package com.jupiterp.jupiterpmobile.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jupiterp.jupiterpmobile.data.api.ApiState
import com.jupiterp.jupiterpmobile.domain.model.Course
import com.jupiterp.jupiterpmobile.domain.model.ScheduleBlock
import com.jupiterp.jupiterpmobile.domain.model.ScheduleSelection
import com.jupiterp.jupiterpmobile.domain.model.Section
import com.jupiterp.jupiterpmobile.domain.model.StoredSchedule
import com.jupiterp.jupiterpmobile.ui.components.CompactCourseCard
import com.jupiterp.jupiterpmobile.ui.components.CourseCard
import com.jupiterp.jupiterpmobile.ui.components.SolarSystemLoader
import com.jupiterp.jupiterpmobile.ui.components.WeeklyScheduleView
import com.jupiterp.ui.theme.JupiterpTheme
import jupiterpmobile.composeapp.generated.resources.Res
import jupiterpmobile.composeapp.generated.resources.logo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/**
 * Main screen with bottom search sheet
 * - Search bar always at bottom of sheet
 * - Results appear above search bar as sheet expands upward
 * - Drag to expand/collapse
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedDepartment by viewModel.selectedDepartment.collectAsState()
    val selectedGenEds by viewModel.selectedGenEds.collectAsState()
    val coursesState by viewModel.coursesState.collectAsState()
    val departmentsState by viewModel.departmentsState.collectAsState()
    val expandedCourseCode by viewModel.expandedCourseCode.collectAsState()
    val currentSelections by viewModel.currentSelections.collectAsState()
    val savedSchedules by viewModel.savedSchedules.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val searchFocusRequester = remember { FocusRequester() }

    var showSettings by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showCoursesExpanded by remember { mutableStateOf(false) }
    var containerHeight by remember { mutableStateOf(0f) }

    // Sheet dimensions - increased to account for navigation bar padding
    val collapsedHeightDp = 125.dp
    val collapsedHeightPx = with(density) { collapsedHeightDp.toPx() }
    val maxExpandedRatio = 0.85f
    val maxExpandedHeightPx = containerHeight * maxExpandedRatio

    // Sheet height animation - starts at collapsed
    val sheetHeightPx = remember { Animatable(0f) }

    // Initialize sheet height when container is measured
    LaunchedEffect(containerHeight) {
        if (containerHeight > 0 && sheetHeightPx.value == 0f) {
            sheetHeightPx.snapTo(collapsedHeightPx)
        }
    }

    // Expansion progress: 0 = collapsed, 1 = fully expanded
    val expansionProgress by remember {
        derivedStateOf {
            if (maxExpandedHeightPx > collapsedHeightPx && sheetHeightPx.value >= collapsedHeightPx) {
                ((sheetHeightPx.value - collapsedHeightPx) / (maxExpandedHeightPx - collapsedHeightPx)).coerceIn(0f, 1f)
            } else 0f
        }
    }

    val isExpanded by remember { derivedStateOf { expansionProgress > 0.3f } }

    val sheetColor = MaterialTheme.colorScheme.surfaceContainerHigh

    // Track if we should be expanded based on having content to show
    val hasSearchContent = coursesState is ApiState.Loading ||
            coursesState is ApiState.Success ||
            coursesState is ApiState.Error ||
            (coursesState is ApiState.Empty && (searchQuery.isNotEmpty() || selectedDepartment != null || selectedGenEds.isNotEmpty()))

    // Snackbar handling
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissSnackbar()
        }
    }

    // Auto-expand when there's search content to show
    LaunchedEffect(hasSearchContent, maxExpandedHeightPx) {
        if (hasSearchContent && maxExpandedHeightPx > 0 && sheetHeightPx.value < maxExpandedHeightPx * 0.8f) {
            sheetHeightPx.animateTo(
                maxExpandedHeightPx,
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            )
        }
    }

    // Expand and focus search
    fun expand() {
        scope.launch {
            sheetHeightPx.animateTo(
                maxExpandedHeightPx,
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            )
            delay(100)
            try { searchFocusRequester.requestFocus() } catch (_: Exception) { }
        }
    }

    // Collapse sheet
    fun collapse() {
        scope.launch {
            sheetHeightPx.animateTo(
                collapsedHeightPx,
                animationSpec = tween(250, easing = FastOutSlowInEasing)
            )
        }
    }

    // Save schedule dialog
    if (showSaveDialog) {
        SaveScheduleDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                viewModel.saveSchedule(name)
                showSaveDialog = false
            }
        )
    }

    // Settings sheet
    if (showSettings) {
        SettingsBottomSheet(
            isDarkMode = isDarkMode,
            onToggleDarkMode = onToggleDarkMode,
            savedSchedules = savedSchedules,
            hasCurrentSchedule = currentSelections.isNotEmpty(),
            onSaveSchedule = {
                showSettings = false
                showSaveDialog = true
            },
            onLoadSchedule = { scheduleId ->
                viewModel.loadSchedule(scheduleId)
                showSettings = false
            },
            onDeleteSchedule = { scheduleId ->
                viewModel.deleteSchedule(scheduleId)
            },
            onDismiss = { showSettings = false },
            onClearSchedule = {
                viewModel.clearSchedule()
                showSettings = false
            }
        )
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(12.dp),
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        }
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    val newHeight = coords.size.height.toFloat()
                    if (newHeight > 0 && containerHeight != newHeight) {
                        containerHeight = newHeight
                    }
                }
        ) {
            // Background: Schedule view
            Column(modifier = Modifier.fillMaxSize()) {
                CompactHeader(
                    selectedCount = currentSelections.size,
                    totalCredits = viewModel.getTotalCredits(),
                    onSettingsClick = { showSettings = true }
                )

                ScheduleContent(
                    selections = currentSelections,
                    totalCredits = viewModel.getTotalCredits(),
                    scheduleBlocks = viewModel.getScheduleBlocks(),
                    showCoursesExpanded = showCoursesExpanded,
                    onToggleCoursesExpanded = { showCoursesExpanded = !showCoursesExpanded },
                    onRemoveSection = { code, section -> viewModel.removeSection(code, section) },
                    modifier = Modifier.weight(1f)
                )

                // Reserve space for collapsed sheet
                Spacer(modifier = Modifier.height(collapsedHeightDp))
            }

            // Bottom sheet - expands upward from bottom
            if (containerHeight > 0 && sheetHeightPx.value > 0) {
                val heightDp = with(density) { sheetHeightPx.value.toDp() }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heightDp)
                        .align(Alignment.BottomCenter)
                        .pointerInput(collapsedHeightPx, maxExpandedHeightPx) {
                            var velocity = 0f
                            detectVerticalDragGestures(
                                onDragStart = { velocity = 0f },
                                onDragEnd = {
                                    scope.launch {
                                        val target = when {
                                            velocity < -500f -> maxExpandedHeightPx
                                            velocity > 500f -> collapsedHeightPx
                                            sheetHeightPx.value > (collapsedHeightPx + maxExpandedHeightPx) / 2 -> maxExpandedHeightPx
                                            else -> collapsedHeightPx
                                        }
                                        sheetHeightPx.animateTo(target, tween(200, easing = FastOutSlowInEasing))
                                        if (target == maxExpandedHeightPx) {
                                            delay(50)
                                            try { searchFocusRequester.requestFocus() } catch (_: Exception) { }
                                        }
                                    }
                                },
                                onDragCancel = {
                                    scope.launch {
                                        val target = if (sheetHeightPx.value > (collapsedHeightPx + maxExpandedHeightPx) / 2)
                                            maxExpandedHeightPx else collapsedHeightPx
                                        sheetHeightPx.animateTo(target, tween(200))
                                    }
                                },
                                onVerticalDrag = { _, dragAmount ->
                                    velocity = dragAmount * 10
                                    scope.launch {
                                        // Swipe up (negative) = expand (increase height)
                                        val newHeight = (sheetHeightPx.value - dragAmount)
                                            .coerceIn(collapsedHeightPx, maxExpandedHeightPx)
                                        sheetHeightPx.snapTo(newHeight)
                                    }
                                }
                            )
                        },
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = sheetColor,
                    shadowElevation = 8.dp
                ) {
                    // Content: drag handle at top, results in middle, search bar at bottom
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Drag handle - always visible at top of sheet
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { if (isExpanded) collapse() else expand() }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                            )
                        }

                        // Results area - takes all space between handle and search bar
                        // Show content when we have search content OR when expanded
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            // Show results when sheet is expanded enough OR when we have content to display
                            if (expansionProgress > 0.01f || hasSearchContent) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(
                                            alpha = expansionProgress.coerceIn(0.3f, 1f)
                                        )
                                    )

                                    SearchResultsContent(
                                        coursesState = coursesState,
                                        expandedCourseCode = expandedCourseCode,
                                        onExpandToggle = viewModel::toggleCourseExpansion,
                                        isSectionSelected = { code, sec ->
                                            currentSelections.any {
                                                it.course.courseCode == code && it.section.sectionCode == sec
                                            }
                                        },
                                        getInstructorRating = { viewModel.getInstructorRating(it) },
                                        onSectionToggle = { course, section ->
                                            val isSelected = currentSelections.any {
                                                it.course.courseCode == course.courseCode &&
                                                        it.section.sectionCode == section.sectionCode
                                            }
                                            if (isSelected) {
                                                viewModel.removeSection(course.courseCode, section.sectionCode)
                                            } else {
                                                viewModel.addSection(course, section)
                                            }
                                        },
                                        hasActiveSearch = searchQuery.isNotEmpty() || selectedDepartment != null || selectedGenEds.isNotEmpty(),
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }

                        // Search bar - always at bottom
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(top = 8.dp)
                                .navigationBarsPadding()
                                .padding(bottom = 8.dp)
                        ) {
                            com.jupiterp.jupiterpmobile.ui.components.SearchBar(
                                query = searchQuery,
                                onQueryChange = viewModel::onSearchQueryChange,
                                onSearch = viewModel::searchCourses,
                                departments = (departmentsState as? ApiState.Success)?.data
                                    ?: emptyList(),
                                selectedDepartment = selectedDepartment,
                                onDepartmentSelect = viewModel::setDepartment,
                                selectedGenEds = selectedGenEds,
                                onGenEdToggle = viewModel::toggleGenEd,
                                onClearFilters = viewModel::clearFilters,
                                focusRequester = searchFocusRequester
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Search results content
 */
@Composable
private fun SearchResultsContent(
    coursesState: ApiState<List<Course>>,
    expandedCourseCode: String?,
    onExpandToggle: (String) -> Unit,
    isSectionSelected: (String, String) -> Boolean,
    getInstructorRating: (String) -> Float?,
    onSectionToggle: (Course, Section) -> Unit,
    hasActiveSearch: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Use surfaceContainerHigh to match the sheet background
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Box(
        modifier = modifier.background(backgroundColor)
    ) {
        when (coursesState) {
            is ApiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SolarSystemLoader(
                        size = 120.dp,
                        color = JupiterpTheme.extendedColors.orange
                    )
                }
            }

            is ApiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "${coursesState.data.size} courses found",
                            style = MaterialTheme.typography.labelMedium,
                            color = JupiterpTheme.extendedColors.textSecondary
                        )
                    }
                    items(coursesState.data, key = { it.courseCode }) { course ->
                        CourseCard(
                            course = course,
                            isExpanded = course.courseCode == expandedCourseCode,
                            onExpandToggle = { onExpandToggle(course.courseCode) },
                            isSectionSelected = { isSectionSelected(course.courseCode, it) },
                            getInstructorRating = getInstructorRating,
                            onSectionToggle = { onSectionToggle(course, it) }
                        )
                    }
                }
            }

            is ApiState.Error -> {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.ErrorOutline,
                            null,
                            Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Something went wrong", style = MaterialTheme.typography.titleMedium)
                        Text(
                            coursesState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = JupiterpTheme.extendedColors.textSecondary
                        )
                    }
                }
            }

            is ApiState.Empty -> {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Search,
                            null,
                            Modifier.size(48.dp),
                            tint = if (hasActiveSearch)
                                JupiterpTheme.extendedColors.textSecondary
                            else
                                JupiterpTheme.extendedColors.textSecondary.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (hasActiveSearch) "No courses found" else "Search for courses",
                            style = MaterialTheme.typography.titleMedium,
                            color = JupiterpTheme.extendedColors.textSecondary
                        )
                        Text(
                            if (hasActiveSearch) "Try a different search or filters"
                            else "Try CMSC131 or browse by department",
                            style = MaterialTheme.typography.bodyMedium,
                            color = JupiterpTheme.extendedColors.textSecondary.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact header
 */
@Composable
private fun CompactHeader(
    selectedCount: Int,
    totalCredits: IntRange,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                JupiterpLogo(size = 128.dp)

            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (selectedCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = JupiterpTheme.extendedColors.orangeContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "$selectedCount courses",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = JupiterpTheme.extendedColors.orange
                            )
                            Text("•", color = JupiterpTheme.extendedColors.orange.copy(alpha = 0.5f))
                            Text(
                                if (totalCredits.first == totalCredits.last) "${totalCredits.first} cr"
                                else "${totalCredits.first}-${totalCredits.last} cr",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = JupiterpTheme.extendedColors.orange
                            )
                        }
                    }
                }

                IconButton(onClick = onSettingsClick, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        "Settings",
                        tint = JupiterpTheme.extendedColors.textSecondary
                    )
                }
            }
        }
    }
}

/**
 * Schedule content
 */
@Composable
private fun ScheduleContent(
    selections: List<ScheduleSelection>,
    totalCredits: IntRange,
    scheduleBlocks: List<ScheduleBlock>,
    showCoursesExpanded: Boolean,
    onToggleCoursesExpanded: () -> Unit,
    onRemoveSection: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (selections.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Outlined.CalendarMonth,
                        null,
                        Modifier.size(64.dp),
                        tint = JupiterpTheme.extendedColors.textSecondary.copy(alpha = 0.5f)
                    )
                    Text(
                        "Your schedule is empty",
                        style = MaterialTheme.typography.titleMedium,
                        color = JupiterpTheme.extendedColors.textSecondary
                    )
                    Text(
                        "Search for courses below",
                        style = MaterialTheme.typography.bodyMedium,
                        color = JupiterpTheme.extendedColors.textSecondary.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                WeeklyScheduleView(
                    scheduleBlocks = scheduleBlocks,
                    onBlockClick = { },
                    modifier = Modifier.padding(8.dp)
                )
            }

            // Collapsible selected courses
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onToggleCoursesExpanded)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Selected Courses (${selections.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = JupiterpTheme.extendedColors.textSecondary
                        )
                        Icon(
                            if (showCoursesExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            if (showCoursesExpanded) "Collapse" else "Expand",
                            Modifier.size(20.dp),
                            tint = JupiterpTheme.extendedColors.textSecondary
                        )
                    }

                    AnimatedVisibility(
                        visible = showCoursesExpanded,
                        enter = expandVertically(tween(200)) + fadeIn(),
                        exit = shrinkVertically(tween(150)) + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            selections.forEach { sel ->
                                CompactCourseCard(
                                    course = sel.course,
                                    section = sel.section,
                                    colorIndex = sel.colorIndex,
                                    onRemove = { onRemoveSection(sel.course.courseCode, sel.section.sectionCode) }
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

/**
 * Save schedule dialog
 */
@Composable
private fun SaveScheduleDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var scheduleName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text("Save Schedule", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    "Enter a name for this schedule:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = JupiterpTheme.extendedColors.textSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = scheduleName,
                    onValueChange = { scheduleName = it },
                    placeholder = { Text("e.g., Fall 2025 Plan A") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JupiterpTheme.extendedColors.orange,
                        cursorColor = JupiterpTheme.extendedColors.orange
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (scheduleName.isNotBlank()) {
                        onSave(scheduleName.trim())
                    }
                },
                enabled = scheduleName.isNotBlank()
            ) {
                Text("Save", color = JupiterpTheme.extendedColors.orange)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Settings bottom sheet with schedule management
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsBottomSheet(
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    savedSchedules: List<StoredSchedule>,
    hasCurrentSchedule: Boolean,
    onSaveSchedule: () -> Unit,
    onLoadSchedule: (String) -> Unit,
    onDeleteSchedule: (String) -> Unit,
    onDismiss: () -> Unit,
    onClearSchedule: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    // Delete confirmation dialog
    showDeleteConfirm?.let { scheduleId ->
        val schedule = savedSchedules.find { it.id == scheduleId }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            shape = RoundedCornerShape(20.dp),
            title = { Text("Delete Schedule?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to delete \"${schedule?.name}\"? This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSchedule(scheduleId)
                        showDeleteConfirm = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Dark mode
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isDarkMode) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                            null,
                            tint = JupiterpTheme.extendedColors.orange
                        )
                        Column {
                            Text("Dark Mode", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                if (isDarkMode) "On" else "Off",
                                style = MaterialTheme.typography.bodySmall,
                                color = JupiterpTheme.extendedColors.textSecondary
                            )
                        }
                    }
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { onToggleDarkMode() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = JupiterpTheme.extendedColors.orange
                        )
                    )
                }
            }

            // Schedule Management Section
            Text(
                "Schedules",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Save current schedule
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = hasCurrentSchedule, onClick = onSaveSchedule),
                shape = RoundedCornerShape(12.dp),
                color = if (hasCurrentSchedule)
                    JupiterpTheme.extendedColors.orangeContainer.copy(alpha = 0.5f)
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Save,
                        null,
                        tint = if (hasCurrentSchedule)
                            JupiterpTheme.extendedColors.orange
                        else
                            JupiterpTheme.extendedColors.textSecondary.copy(alpha = 0.5f)
                    )
                    Column {
                        Text(
                            "Save Current Schedule",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = if (hasCurrentSchedule)
                                MaterialTheme.colorScheme.onSurface
                            else
                                JupiterpTheme.extendedColors.textSecondary.copy(alpha = 0.5f)
                        )
                        Text(
                            if (hasCurrentSchedule) "Save to load later" else "No courses selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = JupiterpTheme.extendedColors.textSecondary.copy(
                                alpha = if (hasCurrentSchedule) 1f else 0.5f
                            )
                        )
                    }
                }
            }

            // Saved schedules list
            if (savedSchedules.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column {
                        savedSchedules.forEachIndexed { index, schedule ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onLoadSchedule(schedule.id) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.EventNote,
                                        null,
                                        tint = JupiterpTheme.extendedColors.orange
                                    )
                                    Column {
                                        Text(
                                            schedule.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${schedule.selections.size} course${if (schedule.selections.size != 1) "s" else ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = JupiterpTheme.extendedColors.textSecondary
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { showDeleteConfirm = schedule.id },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        "Delete",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            if (index < savedSchedules.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = JupiterpTheme.extendedColors.divider
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "No saved schedules yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = JupiterpTheme.extendedColors.textSecondary.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Clear schedule
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = hasCurrentSchedule, onClick = onClearSchedule),
                shape = RoundedCornerShape(12.dp),
                color = if (hasCurrentSchedule)
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        null,
                        tint = if (hasCurrentSchedule)
                            MaterialTheme.colorScheme.error
                        else
                            JupiterpTheme.extendedColors.textSecondary.copy(alpha = 0.5f)
                    )
                    Column {
                        Text(
                            "Clear Schedule",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = if (hasCurrentSchedule)
                                MaterialTheme.colorScheme.error
                            else
                                JupiterpTheme.extendedColors.textSecondary.copy(alpha = 0.5f)
                        )
                        Text(
                            "Remove all selected courses",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasCurrentSchedule)
                                MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            else
                                JupiterpTheme.extendedColors.textSecondary.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // About
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Info, null, tint = JupiterpTheme.extendedColors.textSecondary)
                    Column {
                        Text("About Jupiterp", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            "Course planner for UMD students",
                            style = MaterialTheme.typography.bodySmall,
                            color = JupiterpTheme.extendedColors.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JupiterpLogo(
    size: Dp,
    modifier: Modifier = Modifier
) {

        // Use logo image from resources
        androidx.compose.foundation.Image(
            painter = painterResource(
                resource = Res.drawable.logo

            ),
            contentDescription = "Jupiterp Logo",
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit
        )
    }