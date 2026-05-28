package com.jupiterp.jupiterpmobile.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jupiterp.jupiterpmobile.domain.model.Department
import com.jupiterp.ui.theme.JupiterpTheme

/**
 * Main search bar with expandable filters
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    departments: List<Department>,
    selectedDepartment: String?,
    onDepartmentSelect: (String?) -> Unit,
    selectedGenEds: List<String>,
    onGenEdToggle: (String) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
    instructorSuggestions: List<String> = emptyList(),
    onInstructorSelected: (String) -> Unit = {},
    suggestionsAbove: Boolean = false,
    selectedInstructor: String? = null,
    onClearInstructor: () -> Unit = {}
) {
    var showFilters by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val internalFocusRequester = remember { FocusRequester() }
    val actualFocusRequester = focusRequester ?: internalFocusRequester

    val atIdx = query.indexOf('@')
    val hasActiveFilters =
        selectedDepartment != null || selectedGenEds.isNotEmpty() || !selectedInstructor.isNullOrEmpty()

    @Composable
    fun SuggestionDropdown() {
        AnimatedVisibility(
            visible = atIdx >= 0 && instructorSuggestions.isNotEmpty(),
            enter = expandVertically(animationSpec = tween(150)) + fadeIn(animationSpec = tween(150)),
            exit = shrinkVertically(animationSpec = tween(100)) + fadeOut(animationSpec = tween(100))
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Column {
                    instructorSuggestions.forEachIndexed { index, name ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onInstructorSelected(name)
                                    focusManager.clearFocus()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                tint = JupiterpTheme.extendedColors.orange,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (index < instructorSuggestions.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // On phone the search bar sits at the bottom of the sheet, so the dropdown
        // must render above the field to stay within the visible surface area.
        if (suggestionsAbove) SuggestionDropdown()

        // Main search field
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search icon
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search",
                    modifier = Modifier.padding(12.dp),
                    tint = JupiterpTheme.extendedColors.textSecondary
                )

                // Text field
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(actualFocusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                onFocused?.invoke()
                            }
                        },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(JupiterpTheme.extendedColors.orange),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            onSearch()
                            focusManager.clearFocus()
                        }
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Try CMSC132 or @Smith",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = JupiterpTheme.extendedColors.textSecondary
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                // Clear button
                AnimatedVisibility(
                    visible = query.isNotEmpty(),
                    enter = fadeIn(animationSpec = tween(150)) + scaleIn(animationSpec = tween(150)),
                    exit = fadeOut(animationSpec = tween(100)) + scaleOut(animationSpec = tween(100))
                ) {
                    IconButton(
                        onClick = { onQueryChange("") }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Clear search",
                            tint = JupiterpTheme.extendedColors.textSecondary
                        )
                    }
                }

                // Filter button
                Box {
                    IconButton(
                        onClick = { showFilters = !showFilters },
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = if (showFilters) Icons.Filled.FilterList else Icons.Outlined.FilterList,
                            contentDescription = "Filters",
                            tint = if (hasActiveFilters) JupiterpTheme.extendedColors.orange
                            else JupiterpTheme.extendedColors.textSecondary
                        )
                    }

                    // Active filter indicator
                    if (hasActiveFilters) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(JupiterpTheme.extendedColors.orange)
                        )
                    }
                }
            }
        }

        // On tablet the search bar is at the top, so the dropdown renders below the field.
        if (!suggestionsAbove) SuggestionDropdown()

        // Active filters row (compact squircle style)
        AnimatedVisibility(
            visible = hasActiveFilters,
            enter = expandVertically(animationSpec = tween(150)) + fadeIn(animationSpec = tween(150)),
            exit = shrinkVertically(animationSpec = tween(100)) + fadeOut(animationSpec = tween(100))
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                // Instructor filter chip (sticky — set by suggestion click)
                selectedInstructor?.takeIf { it.isNotEmpty() }?.let { name ->
                    item {
                        FilterChip(
                            label = "@$name",
                            selected = true,
                            onClick = onClearInstructor
                        )
                    }
                }

                // Department filter chip
                selectedDepartment?.let { dept ->
                    item {
                        FilterChip(
                            label = dept,
                            selected = true,
                            onClick = { onDepartmentSelect(null) }
                        )
                    }
                }

                // GenEd filter chips
                items(selectedGenEds) { genEd ->
                    FilterChip(
                        label = genEd,
                        selected = true,
                        onClick = { onGenEdToggle(genEd) }
                    )
                }

                // Clear all button
                if (hasActiveFilters) {
                    item {
                        FilterChip(
                            label = "Clear",
                            selected = false,
                            onClick = onClearFilters
                        )
                    }
                }
            }
        }

        // Expandable filter panel
        AnimatedVisibility(
            visible = showFilters,
            enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(200)),
            exit = shrinkVertically(animationSpec = tween(150)) + fadeOut(animationSpec = tween(150))
        ) {
            FilterPanel(
                departments = departments,
                selectedDepartment = selectedDepartment,
                onDepartmentSelect = onDepartmentSelect,
                selectedGenEds = selectedGenEds,
                onGenEdToggle = onGenEdToggle
            )
        }
    }
}

/**
 * Expandable filter panel
 */
@Composable
private fun FilterPanel(
    departments: List<Department>,
    selectedDepartment: String?,
    onDepartmentSelect: (String?) -> Unit,
    selectedGenEds: List<String>,
    onGenEdToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDepartmentPicker by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Department selector
            Text(
                text = "Department",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDepartmentPicker = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedDepartment ?: "All departments",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selectedDepartment != null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            JupiterpTheme.extendedColors.textSecondary
                        }
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = JupiterpTheme.extendedColors.textSecondary
                    )
                }
            }

            // GenEd filters
            Text(
                text = "General Education",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            GenEdFilterGrid(
                selectedGenEds = selectedGenEds,
                onGenEdToggle = onGenEdToggle
            )
        }
    }

    // Department picker dialog
    if (showDepartmentPicker) {
        DepartmentPickerDialog(
            departments = departments,
            selectedDepartment = selectedDepartment,
            onDepartmentSelect = { dept ->
                onDepartmentSelect(dept)
                showDepartmentPicker = false
            },
            onDismiss = { showDepartmentPicker = false }
        )
    }
}

/**
 * GenEd filter grid
 */
@Composable
private fun GenEdFilterGrid(
    selectedGenEds: List<String>,
    onGenEdToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Common UMD GenEd codes
    val genEdCodes = listOf(
        "FSAW", "FSAR", "FSMA", "FSOC", "FSPW",
        "DSHS", "DSHU", "DSNS", "DSNL", "DSSP",
        "SCIS", "DVUP", "DVCC"
    )

    val rows = genEdCodes.chunked(5)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { genEd ->
                    val isSelected = genEd in selectedGenEds

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onGenEdToggle(genEd) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) {
                            JupiterpTheme.extendedColors.orangeContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        tonalElevation = 2.dp
                    ) {
                        Box(
                            modifier = Modifier.padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = genEd,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) {
                                    JupiterpTheme.extendedColors.orange
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }

                // Fill remaining space
                repeat(5 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Department picker dialog
 */
@Composable
private fun DepartmentPickerDialog(
    departments: List<Department>,
    selectedDepartment: String?,
    onDepartmentSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredDepartments = remember(searchQuery, departments) {
        if (searchQuery.isEmpty()) {
            departments
        } else {
            departments.filter { dept ->
                dept.code.contains(searchQuery, ignoreCase = true) ||
                        dept.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Select Department")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search departments") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, null)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // All departments option
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDepartmentSelect(null) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selectedDepartment == null) {
                        JupiterpTheme.extendedColors.orangeContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ) {
                    Text(
                        text = "All departments",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Department list (scrollable)
                Column(
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    filteredDepartments.forEach { dept ->
                        val isSelected = dept.code == selectedDepartment

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDepartmentSelect(dept.code) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) {
                                JupiterpTheme.extendedColors.orangeContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = dept.code,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isSelected) {
                                        JupiterpTheme.extendedColors.orange
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Text(
                                    text = dept.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = JupiterpTheme.extendedColors.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = JupiterpTheme.extendedColors.orange)
            }
        }
    )
}