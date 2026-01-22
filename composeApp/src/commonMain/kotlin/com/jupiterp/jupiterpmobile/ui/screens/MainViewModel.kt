package com.jupiterp.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiterp.data.api.ApiState
import com.jupiterp.jupiterpmobile.data.repository.AddSectionResult
import com.jupiterp.data.repository.CourseRepository
import com.jupiterp.jupiterpmobile.data.repository.ScheduleRepository
import com.jupiterp.domain.model.*
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

    private var searchJob: Job? = null

    init {
        loadDepartments()
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
        _coursesState.value = ApiState.Empty
    }

    /**
     * Search courses with current filters
     */
    fun searchCourses() {
        val query = _searchQuery.value.trim()
        val department = _selectedDepartment.value
        val genEds = _selectedGenEds.value

        if (query.isEmpty() && department == null && genEds.isEmpty()) {
            _coursesState.value = ApiState.Empty
            return
        }

        viewModelScope.launch {
            _coursesState.value = ApiState.Loading

            courseRepository.searchCourses(
                query = query.ifEmpty { null },
                department = department,
                genEds = genEds.ifEmpty { null }
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
     * Get schedule blocks for rendering
     */
    fun getScheduleBlocks(): List<ScheduleBlock> {
        return scheduleRepository.getScheduleBlocks()
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