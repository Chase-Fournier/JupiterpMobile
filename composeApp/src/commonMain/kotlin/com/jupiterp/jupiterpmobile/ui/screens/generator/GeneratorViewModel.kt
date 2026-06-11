package com.jupiterp.jupiterpmobile.ui.screens.generator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiterp.jupiterpmobile.data.repository.CourseRepository
import com.jupiterp.jupiterpmobile.data.repository.ScheduleRepository
import com.jupiterp.jupiterpmobile.domain.model.Course
import com.jupiterp.jupiterpmobile.domain.model.DayOfWeek
import com.jupiterp.jupiterpmobile.domain.scheduler.GeneratedSchedule
import com.jupiterp.jupiterpmobile.domain.scheduler.HardConstraints
import com.jupiterp.jupiterpmobile.domain.scheduler.Relaxation
import com.jupiterp.jupiterpmobile.domain.scheduler.ScheduleGenerator
import com.jupiterp.jupiterpmobile.domain.scheduler.SortCriterion
import com.jupiterp.jupiterpmobile.domain.scheduler.singleRelaxations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the schedule generator: holds the requirement list and
 * constraints, runs the generation engine off the main thread, and exposes
 * sorted results plus relaxation hints when nothing fits.
 */
class GeneratorViewModel(
    private val courseRepository: CourseRepository,
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {

    data class RequirementItem(val courseCode: String, val courseName: String)

    data class CourseSuggestion(val courseCode: String, val courseName: String)

    data class RelaxationHint(
        val relaxation: Relaxation,
        val scheduleCount: Int,
        val countIsLowerBound: Boolean
    )

    sealed interface GenerationState {
        data object Idle : GenerationState
        data object Loading : GenerationState
        data class Done(
            val schedules: List<GeneratedSchedule>,
            val truncated: Boolean
        ) : GenerationState

        data class NoSchedules(
            val hints: List<RelaxationHint>,
            val coursesWithoutSections: List<String>
        ) : GenerationState

        data class Failed(val message: String) : GenerationState
    }

    private val _requirements = MutableStateFlow<List<RequirementItem>>(emptyList())
    val requirements: StateFlow<List<RequirementItem>> = _requirements.asStateFlow()

    private val _constraints = MutableStateFlow(HardConstraints())
    val constraints: StateFlow<HardConstraints> = _constraints.asStateFlow()

    private val _generationState = MutableStateFlow<GenerationState>(GenerationState.Idle)
    val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()

    private val _sortCriterion = MutableStateFlow(SortCriterion.BEST_RATING)
    val sortCriterion: StateFlow<SortCriterion> = _sortCriterion.asStateFlow()

    // Course search for adding requirements
    private val _courseQuery = MutableStateFlow("")
    val courseQuery: StateFlow<String> = _courseQuery.asStateFlow()

    private val _courseSuggestions = MutableStateFlow<List<CourseSuggestion>>(emptyList())
    val courseSuggestions: StateFlow<List<CourseSuggestion>> = _courseSuggestions.asStateFlow()

    val currentScheduleSize: StateFlow<Int> = scheduleRepository.currentSelections
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private var suggestionJob: Job? = null
    private var generationJob: Job? = null

    // ---- Requirements ----

    fun onCourseQueryChange(query: String) {
        _courseQuery.value = query
        suggestionJob?.cancel()
        if (query.trim().length < 3) {
            _courseSuggestions.value = emptyList()
            return
        }
        suggestionJob = viewModelScope.launch {
            delay(250)
            courseRepository.searchCourses(query = query.trim(), limit = 20)
                .onSuccess { courses ->
                    val added = _requirements.value.map { it.courseCode }.toSet()
                    _courseSuggestions.value = courses
                        .filter { it.courseCode !in added }
                        .map { CourseSuggestion(it.courseCode, it.name) }
                }
        }
    }

    fun addCourse(suggestion: CourseSuggestion) {
        _requirements.update { current ->
            if (current.any { it.courseCode == suggestion.courseCode }) current
            else current + RequirementItem(suggestion.courseCode, suggestion.courseName)
        }
        _courseQuery.value = ""
        _courseSuggestions.value = emptyList()
        suggestionJob?.cancel()
        clearStaleOutcome()
    }

    fun removeCourse(courseCode: String) {
        _requirements.update { current -> current.filter { it.courseCode != courseCode } }
        clearStaleOutcome()
    }

    /** Seed the requirement list with the courses already in the planner. */
    fun seedFromCurrentSchedule() {
        val current = scheduleRepository.currentSelections.value
        if (current.isEmpty()) return
        _requirements.update { existing ->
            val have = existing.map { it.courseCode }.toSet()
            existing + current
                .distinctBy { it.course.courseCode }
                .filter { it.course.courseCode !in have }
                .map { RequirementItem(it.course.courseCode, it.course.name) }
        }
    }

    // ---- Constraints ----

    fun setEarliestStart(minutes: Int?) {
        _constraints.update { it.copy(earliestStartMinutes = minutes) }
        clearStaleOutcome()
    }

    fun setLatestEnd(minutes: Int?) {
        _constraints.update { it.copy(latestEndMinutes = minutes) }
        clearStaleOutcome()
    }

    fun toggleDayOff(day: DayOfWeek) {
        _constraints.update {
            it.copy(daysOff = if (day in it.daysOff) it.daysOff - day else it.daysOff + day)
        }
        clearStaleOutcome()
    }

    fun setOnlyOpenSeats(onlyOpen: Boolean) {
        _constraints.update { it.copy(onlyOpenSeats = onlyOpen) }
        clearStaleOutcome()
    }

    fun setMinGap(minutes: Int) {
        _constraints.update { it.copy(minGapMinutes = minutes) }
        clearStaleOutcome()
    }

    /**
     * No-result hints and error banners describe the inputs that produced
     * them; once the user edits anything, they're stale — drop them.
     */
    private fun clearStaleOutcome() {
        if (_generationState.value is GenerationState.NoSchedules ||
            _generationState.value is GenerationState.Failed
        ) {
            _generationState.value = GenerationState.Idle
        }
    }

    // ---- Generation ----

    fun generate() {
        val requirementList = _requirements.value
        if (requirementList.isEmpty()) return
        val constraints = _constraints.value

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _generationState.value = GenerationState.Loading

            // Refetch full section lists; courses captured from a filtered
            // search (e.g. by instructor) may be missing sections
            val courses: List<Course> = courseRepository
                .getCoursesByCodes(requirementList.map { it.courseCode })
                .getOrElse { error ->
                    _generationState.value =
                        GenerationState.Failed(error.message ?: "Couldn't load course data")
                    return@launch
                }

            val byCode = courses.associateBy { it.courseCode }
            val missing = requirementList.filter { byCode[it.courseCode] == null }
            if (missing.isNotEmpty()) {
                _generationState.value = GenerationState.Failed(
                    "Couldn't find: ${missing.joinToString(", ") { it.courseCode }}"
                )
                return@launch
            }
            val ordered = requirementList.mapNotNull { byCode[it.courseCode] }

            val instructorNames = ordered
                .flatMap { it.sections.orEmpty() }
                .flatMap { it.instructors }
            val ratings = courseRepository.getInstructorRatings(instructorNames)

            val result = withContext(Dispatchers.Default) {
                ScheduleGenerator.generate(ordered, constraints, ratings)
            }

            if (result.schedules.isNotEmpty()) {
                _generationState.value = GenerationState.Done(result.schedules, result.truncated)
                return@launch
            }

            // Nothing fits: explain which single constraint to loosen.
            // Courses with zero valid sections make relaxation hints moot
            // unless the relaxation itself restores their sections, which
            // the per-relaxation rerun below accounts for.
            val hints = withContext(Dispatchers.Default) {
                singleRelaxations(constraints).mapNotNull { relaxation ->
                    val rerun = ScheduleGenerator.generate(
                        ordered, relaxation.constraints, ratings, maxResults = 50
                    )
                    if (rerun.schedules.isEmpty()) null
                    else RelaxationHint(relaxation, rerun.schedules.size, rerun.truncated)
                }
            }
            _generationState.value =
                GenerationState.NoSchedules(hints, result.coursesWithNoValidSections)
        }
    }

    /** Apply a hint: loosen that one constraint and regenerate. */
    fun applyRelaxation(hint: RelaxationHint) {
        _constraints.value = hint.relaxation.constraints
        generate()
    }

    fun setSortCriterion(criterion: SortCriterion) {
        _sortCriterion.value = criterion
    }

    fun backToRequirements() {
        generationJob?.cancel()
        _generationState.value = GenerationState.Idle
    }

    // ---- Acting on results ----

    fun applySchedule(schedule: GeneratedSchedule) {
        scheduleRepository.setCurrentSchedule(schedule.selections)
    }

    fun saveSchedule(schedule: GeneratedSchedule, name: String) {
        scheduleRepository.saveSchedule(name, schedule.selections)
    }
}
