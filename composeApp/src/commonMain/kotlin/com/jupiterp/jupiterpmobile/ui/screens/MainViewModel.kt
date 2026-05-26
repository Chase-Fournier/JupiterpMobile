package com.jupiterp.jupiterpmobile.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiterp.jupiterpmobile.data.api.ApiState
import com.jupiterp.jupiterpmobile.data.repository.AddSectionResult
import com.jupiterp.jupiterpmobile.data.repository.CourseRepository
import com.jupiterp.jupiterpmobile.data.repository.ScheduleRepository
import com.jupiterp.jupiterpmobile.domain.model.Course
import com.jupiterp.jupiterpmobile.domain.model.Department
import com.jupiterp.jupiterpmobile.domain.model.Instructor
import com.jupiterp.jupiterpmobile.domain.model.OtherScheduleItem
import com.jupiterp.jupiterpmobile.domain.model.ScheduleBlock
import com.jupiterp.jupiterpmobile.domain.model.ScheduleSelection
import com.jupiterp.jupiterpmobile.domain.model.Section
import com.jupiterp.jupiterpmobile.domain.model.StoredSchedule
import com.jupiterp.jupiterpmobile.addToCalendar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Main ViewModel for the Jupiterp app
 * Handles course search, schedule management, and UI state
 */
class MainViewModel(
    private val courseRepository: CourseRepository,
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {

    // Search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedDepartment = MutableStateFlow<String?>(null)
    val selectedDepartment: StateFlow<String?> = _selectedDepartment.asStateFlow()

    private val _selectedGenEds = MutableStateFlow<List<String>>(emptyList())
    val selectedGenEds: StateFlow<List<String>> = _selectedGenEds.asStateFlow()

    // Sticky instructor filter applied by tapping an @-suggestion. Lives outside
    // the query string so users can type a course code without retyping @Name.
    private val _selectedInstructor = MutableStateFlow<String?>(null)
    val selectedInstructor: StateFlow<String?> = _selectedInstructor.asStateFlow()

    private val _coursesState = MutableStateFlow<ApiState<List<Course>>>(ApiState.Empty)
    val coursesState: StateFlow<ApiState<List<Course>>> = _coursesState.asStateFlow()

    private val _departmentsState = MutableStateFlow<ApiState<List<Department>>>(ApiState.Loading)
    val departmentsState: StateFlow<ApiState<List<Department>>> = _departmentsState.asStateFlow()

    // Selected course for detail view
    private val _selectedCourse = MutableStateFlow<Course?>(null)
    val selectedCourse: StateFlow<Course?> = _selectedCourse.asStateFlow()

    private val _expandedCourseCode = MutableStateFlow<String?>(null)
    val expandedCourseCode: StateFlow<String?> = _expandedCourseCode.asStateFlow()

    // Schedule state
    val currentSelections: StateFlow<List<ScheduleSelection>> = scheduleRepository
        .currentSelections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedSchedules: StateFlow<List<StoredSchedule>> = scheduleRepository
        .savedSchedules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI state
    private val _showSchedulePanel = MutableStateFlow(false)
    val showSchedulePanel: StateFlow<Boolean> = _showSchedulePanel.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _isSearchFocused = MutableStateFlow(false)
    val isSearchFocused: StateFlow<Boolean> = _isSearchFocused.asStateFlow()

    // Instructor ratings cache
    private val _instructorRatings = MutableStateFlow<Map<String, Instructor>>(emptyMap())
    val instructorRatings: StateFlow<Map<String, Instructor>> = _instructorRatings.asStateFlow()

    // All instructors for @mention autocomplete
    private val _allInstructors = MutableStateFlow<List<Instructor>>(emptyList())

    val instructorSuggestions: StateFlow<List<String>> = combine(
        _searchQuery, _allInstructors
    ) { query, instructors ->
        val atIdx = query.indexOf('@')
        if (atIdx < 0) return@combine emptyList()
        val token = query.substring(atIdx + 1).trim()
        if (token.length < 2) return@combine emptyList()
        instructors
            .map { it.name }
            .filter { it.contains(token, ignoreCase = true) }
            .take(5)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var searchJob: Job? = null

    init {
        loadDepartments()
        loadAllInstructors()
    }

    /**
     * Load all departments
     */
    private fun loadDepartments() {
        viewModelScope.launch {
            _departmentsState.value = ApiState.Loading
            courseRepository.getDepartments()
                .onSuccess { departments ->
                    _departmentsState.value = ApiState.Success(departments)
                }
                .onFailure { error ->
                    _departmentsState.value = ApiState.Error(error.message ?: "Failed to load departments")
                }
        }
    }

    /**
     * Load all instructors for @mention autocomplete
     */
    private fun loadAllInstructors() {
        viewModelScope.launch {
            courseRepository.getAllInstructorsForSuggestions()
                .onSuccess { instructors ->
                    _allInstructors.value = instructors
                }
        }
    }

    /**
     * Update search query with debounce
     */
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300) // Debounce
            searchCourses()
        }
    }

    /**
     * Set department filter
     */
    fun setDepartment(department: String?) {
        _selectedDepartment.value = department
        searchCourses()
    }

    /**
     * Toggle GenEd filter
     */
    fun toggleGenEd(genEd: String) {
        _selectedGenEds.update { current ->
            if (genEd in current) current - genEd else current + genEd
        }
        searchCourses()
    }

    /**
     * Clear all filters
     */
    fun clearFilters() {
        _searchQuery.value = ""
        _selectedDepartment.value = null
        _selectedGenEds.value = emptyList()
        _selectedInstructor.value = null
        _coursesState.value = ApiState.Empty
    }

    /**
     * Remove the sticky instructor filter (chip "X" button)
     */
    fun clearInstructorFilter() {
        _selectedInstructor.value = null
        searchCourses()
    }

    /**
     * Search courses with current filters, supporting @instructor syntax
     */
    fun searchCourses() {
        val rawQuery = _searchQuery.value.trim()
        val department = _selectedDepartment.value
        val genEds = _selectedGenEds.value

        // Parse @instructor token from query — only used if no sticky filter is set
        val atIdx = rawQuery.indexOf('@')
        val courseQuery = if (atIdx >= 0) rawQuery.substring(0, atIdx).trim() else rawQuery
        val inlineInstructor = if (atIdx >= 0) rawQuery.substring(atIdx + 1).trim().ifEmpty { null } else null

        // Sticky filter (set by suggestion click) wins over inline @ syntax
        val instructorQuery = _selectedInstructor.value ?: inlineInstructor

        if (courseQuery.isEmpty() && department == null && genEds.isEmpty() && instructorQuery == null) {
            _coursesState.value = ApiState.Empty
            return
        }

        viewModelScope.launch {
            _coursesState.value = ApiState.Loading

            courseRepository.searchCourses(
                query = courseQuery.ifEmpty { null },
                department = department,
                genEds = genEds.ifEmpty { null },
                instructor = instructorQuery
            ).onSuccess { courses ->
                _coursesState.value = if (courses.isEmpty()) {
                    ApiState.Empty
                } else {
                    ApiState.Success(courses)
                }

                // Load instructor ratings for found courses
                loadInstructorRatings(courses)
            }.onFailure { error ->
                _coursesState.value = ApiState.Error(error.message ?: "Search failed")
            }
        }
    }

    /**
     * Load instructor ratings for courses
     */
    private fun loadInstructorRatings(courses: List<Course>) {
        val instructorNames = courses
            .flatMap { it.sections ?: emptyList() }
            .flatMap { it.instructors }
            .filter { it.isNotBlank() && !it.contains("TBA", ignoreCase = true) }
            .distinct()

        if (instructorNames.isEmpty()) return

        viewModelScope.launch {
            // Fetch all instructor ratings at once
            courseRepository.searchInstructors(instructorNames)
                .onSuccess { instructors ->
                    _instructorRatings.update { current ->
                        current + instructors.associateBy { it.name }
                    }
                }
        }
    }

    /**
     * Toggle course expansion
     */
    fun toggleCourseExpansion(courseCode: String) {
        _expandedCourseCode.update { current ->
            if (current == courseCode) null else courseCode
        }
    }

    /**
     * Select course for detail view
     */
    fun selectCourse(course: Course?) {
        _selectedCourse.value = course
    }

    /**
     * Add section to schedule
     */
    fun addSection(course: Course, section: Section) {
        when (val result = scheduleRepository.addSection(course, section)) {
            is AddSectionResult.Success -> {
                showSnackbar("Added ${course.courseCode} - ${section.sectionCode}")
            }
            is AddSectionResult.AlreadyAdded -> {
                showSnackbar("Section already in schedule")
            }
            is AddSectionResult.Conflict -> {
                val conflicts = result.conflictingSelections
                    .map { it.course.courseCode }
                    .joinToString(", ")
                showSnackbar("Time conflict with: $conflicts")
            }
        }
    }

    /**
     * Add course without section (for courses with no sections available)
     */
    fun addCourseWithoutSection(course: Course) {
        when (val result = scheduleRepository.addCourseWithoutSection(course)) {
            is AddSectionResult.Success -> {
                showSnackbar("Added ${course.courseCode}")
            }
            is AddSectionResult.AlreadyAdded -> {
                showSnackbar("Course already in schedule")
            }
            is AddSectionResult.Conflict -> {
                showSnackbar("Course already in schedule")
            }
        }
    }

    /**
     * Remove section from schedule
     */
    fun removeSection(courseCode: String, sectionCode: String) {
        scheduleRepository.removeSection(courseCode, sectionCode)
        showSnackbar("Removed section")
    }

    /**
     * Remove all sections of a course
     */
    fun removeCourse(courseCode: String) {
        scheduleRepository.removeCourse(courseCode)
        showSnackbar("Removed course")
    }

    /**
     * Clear entire schedule
     */
    fun clearSchedule() {
        scheduleRepository.clearSchedule()
        showSnackbar("Schedule cleared")
    }

    /**
     * Save current schedule
     */
    fun saveSchedule(name: String) {
        scheduleRepository.saveCurrentSchedule(name)
        showSnackbar("Schedule saved as \"$name\"")
    }

    /**
     * Load a saved schedule
     */
    fun loadSchedule(scheduleId: String) {
        scheduleRepository.loadSchedule(scheduleId)
        showSnackbar("Schedule loaded")
    }

    /**
     * Delete a saved schedule
     */
    fun deleteSchedule(scheduleId: String) {
        scheduleRepository.deleteSchedule(scheduleId)
        showSnackbar("Schedule deleted")
    }

    /**
     * Check if section is selected
     */
    fun isSectionSelected(courseCode: String, sectionCode: String): Boolean {
        return scheduleRepository.isSelected(courseCode, sectionCode)
    }

    /**
     * Check if section would conflict with current schedule
     */
    fun hasConflict(courseCode: String, section: Section): Boolean {
        return scheduleRepository.hasConflict(courseCode, section)
    }

    /**
     * Get schedule blocks for rendering
     */
    fun getScheduleBlocks(): List<ScheduleBlock> {
        return scheduleRepository.getScheduleBlocks()
    }

    /**
     * Get "Other" items (async, weekend, TBA classes)
     */
    fun getOtherItems(): List<OtherScheduleItem> {
        return scheduleRepository.getOtherItems()
    }

    /**
     * Get total credits range
     */
    fun getTotalCredits(): IntRange {
        return scheduleRepository.getTotalCredits()
    }

    /**
     * Toggle schedule panel visibility
     */
    fun toggleSchedulePanel() {
        _showSchedulePanel.update { !it }
    }

    /**
     * Set search focus state
     */
    fun setSearchFocused(focused: Boolean) {
        _isSearchFocused.value = focused
    }

    /**
     * Apply the picked instructor as a sticky filter and clear the @-token from the
     * query so the user can immediately type a course code.
     */
    fun selectInstructorSuggestion(name: String) {
        val current = _searchQuery.value
        val atIdx = current.indexOf('@')
        val remainingCoursePart = if (atIdx >= 0) current.substring(0, atIdx).trim() else current.trim()
        _searchQuery.value = remainingCoursePart
        _selectedInstructor.value = name
        searchJob?.cancel()
        searchCourses()
    }

    /**
     * Export current schedule to the device calendar
     */
    fun exportSchedule() {
        val selections = currentSelections.value
        if (selections.isEmpty()) {
            showSnackbar("No courses in schedule to export")
            return
        }
        addToCalendar(selections) { success ->
            if (success) showSnackbar("Schedule added to Calendar")
            else showSnackbar("Could not access Calendar — check permissions")
        }
    }

    /**
     * Show snackbar message
     */
    private fun showSnackbar(message: String) {
        _snackbarMessage.value = message
        viewModelScope.launch {
            delay(3000)
            _snackbarMessage.value = null
        }
    }

    /**
     * Dismiss snackbar
     */
    fun dismissSnackbar() {
        _snackbarMessage.value = null
    }

    /**
     * Get instructor rating
     */
    fun getInstructorRating(instructorName: String): Float? {
        return _instructorRatings.value[instructorName]?.averageRating
    }
}