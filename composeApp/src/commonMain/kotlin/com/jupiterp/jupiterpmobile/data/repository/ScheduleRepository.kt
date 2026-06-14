package com.jupiterp.jupiterpmobile.data.repository

import com.jupiterp.jupiterpmobile.data.storage.LocalStorage
import com.jupiterp.jupiterpmobile.domain.model.ClassMeeting
import com.jupiterp.jupiterpmobile.domain.model.Course
import com.jupiterp.jupiterpmobile.domain.model.DayOfWeek
import com.jupiterp.jupiterpmobile.domain.model.OtherScheduleItem
import com.jupiterp.jupiterpmobile.domain.model.ScheduleBlock
import com.jupiterp.jupiterpmobile.domain.model.ScheduleSelection
import com.jupiterp.jupiterpmobile.domain.model.Section
import com.jupiterp.jupiterpmobile.domain.model.StoredSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.time.Clock

/**
 * Repository for managing schedule selections and saved schedules
 * Uses local storage for persistence across app launches
 */
class ScheduleRepository(
    private val storage: LocalStorage,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val _currentSelections = MutableStateFlow<List<ScheduleSelection>>(emptyList())
    val currentSelections: StateFlow<List<ScheduleSelection>> = _currentSelections.asStateFlow()

    private val _savedSchedules = MutableStateFlow<List<StoredSchedule>>(emptyList())
    val savedSchedules: StateFlow<List<StoredSchedule>> = _savedSchedules.asStateFlow()

    // Emits a user-facing message whenever a load/persist operation fails.
    private val _errors = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private var colorCounter = 0

    // Set on the first user mutation so the async init load below can't
    // clobber actions taken before it completes.
    @Volatile
    private var userMutated = false

    init {
        // Load saved data on init
        scope.launch {
            try {
                val appData = storage.loadAppData()
                _currentSelections.update { existing ->
                    if (userMutated || existing.isNotEmpty()) existing else appData.currentSchedule
                }
                _savedSchedules.update { existing ->
                    if (userMutated || existing.isNotEmpty()) existing else appData.savedSchedules
                }
                if (!userMutated) {
                    colorCounter = appData.colorCounter
                }
            } catch (e: Exception) {
                _errors.tryEmit("Couldn't load your saved schedules")
            }
        }
    }

    /**
     * Add a section to the current schedule
     */
    fun addSection(course: Course, section: Section): AddSectionResult {
        userMutated = true
        val currentList = _currentSelections.value

        // Check if section is already added
        if (currentList.any { it.section.sectionCode == section.sectionCode && it.course.courseCode == course.courseCode }) {
            return AddSectionResult.AlreadyAdded
        }

        // Detect conflicts for caller awareness, but still allow the add
        val conflicts = findConflicts(section, currentList)

        // Add the section. colorIndex grows monotonically; the UI applies the
        // modulo against the theme palette when rendering.
        val selection = ScheduleSelection(
            course = course,
            section = section,
            colorIndex = colorCounter++
        )

        _currentSelections.update { it + selection }
        persistCurrentSchedule()
        return if (conflicts.isNotEmpty()) AddSectionResult.Conflict(conflicts) else AddSectionResult.Success
    }

    /**
     * Add a course without a section (for courses with no sections available)
     * Creates a placeholder section to track the course
     */
    fun addCourseWithoutSection(course: Course): AddSectionResult {
        userMutated = true
        val currentList = _currentSelections.value

        // Check if course is already added (with placeholder section)
        if (currentList.any { it.course.courseCode == course.courseCode && it.section.sectionCode == PLACEHOLDER_SECTION_CODE }) {
            return AddSectionResult.AlreadyAdded
        }

        // Create a placeholder section
        val placeholderSection = Section(
            courseCode = course.courseCode,
            sectionCode = PLACEHOLDER_SECTION_CODE,
            instructors = emptyList(),
            meetings = emptyList(),
            openSeats = 0,
            totalSeats = 0,
            waitlist = 0,
            holdfile = null
        )

        val selection = ScheduleSelection(
            course = course,
            section = placeholderSection,
            colorIndex = colorCounter++
        )

        _currentSelections.update { it + selection }
        persistCurrentSchedule()
        return AddSectionResult.Success
    }

    /**
     * Remove a section from the current schedule
     */
    fun removeSection(courseCode: String, sectionCode: String) {
        userMutated = true
        _currentSelections.update { selections ->
            selections.filter {
                !(it.course.courseCode == courseCode && it.section.sectionCode == sectionCode)
            }
        }
        persistCurrentSchedule()
    }

    /**
     * Remove all sections of a course
     */
    fun removeCourse(courseCode: String) {
        userMutated = true
        _currentSelections.update { selections ->
            selections.filter { it.course.courseCode != courseCode }
        }
        persistCurrentSchedule()
    }

    /**
     * Clear current schedule
     */
    fun clearSchedule() {
        userMutated = true
        _currentSelections.value = emptyList()
        colorCounter = 0
        persistCurrentSchedule()
    }

    /**
     * Replace the current schedule wholesale (e.g. applying a generated
     * schedule). Color indices are reassigned sequentially.
     */
    fun setCurrentSchedule(selections: List<ScheduleSelection>) {
        userMutated = true
        _currentSelections.value = selections.mapIndexed { i, selection ->
            selection.copy(colorIndex = i)
        }
        colorCounter = selections.size
        persistCurrentSchedule()
    }

    /**
     * Save a schedule with a name
     */
    fun saveSchedule(name: String, selections: List<ScheduleSelection>): StoredSchedule {
        userMutated = true
        val now = Clock.System.now().toEpochMilliseconds()
        val schedule = StoredSchedule(
            id = generateId(),
            name = name,
            selections = selections,
            createdAt = now,
            updatedAt = now
        )

        _savedSchedules.update { it + schedule }
        persistSavedSchedules()
        return schedule
    }

    /**
     * Save current schedule with a name
     */
    fun saveCurrentSchedule(name: String): StoredSchedule =
        saveSchedule(name, _currentSelections.value)

    /**
     * Load a saved schedule as current
     */
    fun loadSchedule(scheduleId: String) {
        userMutated = true
        val schedule = _savedSchedules.value.find { it.id == scheduleId }
        if (schedule != null) {
            _currentSelections.value = schedule.selections
            colorCounter = schedule.selections.maxOfOrNull { it.colorIndex + 1 } ?: 0
            persistCurrentSchedule()
        }
    }

    /**
     * Delete a saved schedule
     */
    fun deleteSchedule(scheduleId: String) {
        userMutated = true
        _savedSchedules.update { schedules ->
            schedules.filter { it.id != scheduleId }
        }
        persistSavedSchedules()
    }

    /**
     * Rename a saved schedule
     */
    fun renameSchedule(scheduleId: String, newName: String) {
        userMutated = true
        _savedSchedules.update { schedules ->
            schedules.map {
                if (it.id == scheduleId) {
                    it.copy(name = newName, updatedAt = Clock.System.now().toEpochMilliseconds())
                } else {
                    it
                }
            }
        }
        persistSavedSchedules()
    }

    /**
     * Check if a section is currently selected
     */
    fun isSelected(courseCode: String, sectionCode: String): Boolean {
        return _currentSelections.value.any {
            it.course.courseCode == courseCode && it.section.sectionCode == sectionCode
        }
    }

    /**
     * Check if adding this section would conflict with currently selected sections.
     * Returns false if the section is already selected.
     */
    fun hasConflict(courseCode: String, section: Section): Boolean {
        val current = _currentSelections.value
        if (current.any { it.course.courseCode == courseCode && it.section.sectionCode == section.sectionCode }) {
            return false
        }
        return findConflicts(section, current).isNotEmpty()
    }

    /**
     * Persist current schedule to storage
     */
    private fun persistCurrentSchedule() {
        val selections = _currentSelections.value
        val counter = colorCounter
        scope.launch {
            try {
                storage.updateAppData {
                    it.copy(currentSchedule = selections, colorCounter = counter)
                }
            } catch (e: Exception) {
                _errors.tryEmit("Couldn't save your schedule changes")
            }
        }
    }

    /**
     * Persist saved schedules to storage
     */
    private fun persistSavedSchedules() {
        val schedules = _savedSchedules.value
        scope.launch {
            try {
                storage.updateAppData {
                    it.copy(savedSchedules = schedules)
                }
            } catch (e: Exception) {
                _errors.tryEmit("Couldn't save your schedules")
            }
        }
    }

    /**
     * Check for conflicts with a potential section
     */
    private fun findConflicts(
        section: Section,
        currentSelections: List<ScheduleSelection>
    ): List<ScheduleSelection> {
        val newSlots = section.timeSlots

        return currentSelections.filter { selection ->
            val existingSlots = selection.section.timeSlots

            newSlots.any { newSlot ->
                existingSlots.any { existingSlot ->
                    newSlot.overlaps(existingSlot)
                }
            }
        }
    }

    private fun generateId(): String {
        return Clock.System.now().toEpochMilliseconds().toString(36) +
                (0..999).random().toString(36)
    }

    companion object {
        const val PLACEHOLDER_SECTION_CODE = "---"

        private val weekdays = listOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
        )

        /**
         * Get schedule blocks for rendering (M-F in-person and online sync)
         */
        fun getScheduleBlocks(selections: List<ScheduleSelection>): List<ScheduleBlock> {
            return selections.flatMap { selection ->
                selection.section.meetings.flatMap { meeting ->
                    val (classtime, location) = when (meeting) {
                        is ClassMeeting.InPerson -> meeting.classtime to meeting.location
                        is ClassMeeting.OnlineSync -> meeting.classtime to null
                        else -> return@flatMap emptyList<ScheduleBlock>()
                    }
                    classtime.daysList
                        .filter { it in weekdays }
                        .map { day ->
                            ScheduleBlock(
                                selection = selection,
                                classtime = classtime,
                                location = location,
                                day = day,
                                startTime = classtime.start,
                                endTime = classtime.end,
                                colorIndex = selection.colorIndex
                            )
                        }
                }
            }
        }

        /**
         * Get "Other" items that don't fit on the M-F schedule grid
         * (Online Async, Weekend classes, TBA, sections with no meetings)
         */
        fun getOtherItems(selections: List<ScheduleSelection>): List<OtherScheduleItem> {
            val results = mutableListOf<OtherScheduleItem>()

            for (selection in selections) {
                val meetings = selection.section.meetings

                // Check for sections with no meetings
                if (meetings.isEmpty()) {
                    results.add(OtherScheduleItem.NoMeetings(selection, selection.colorIndex))
                    continue
                }

                // Check each meeting type
                for (meeting in meetings) {
                    when (meeting) {
                        is ClassMeeting.OnlineAsync -> {
                            // Only add once per selection for async
                            if (results.none { it is OtherScheduleItem.OnlineAsync && it.selection == selection }) {
                                results.add(OtherScheduleItem.OnlineAsync(selection, selection.colorIndex))
                            }
                        }
                        is ClassMeeting.TBA, is ClassMeeting.Unknown -> {
                            if (results.none { it is OtherScheduleItem.TBA && it.selection == selection }) {
                                results.add(OtherScheduleItem.TBA(selection, selection.colorIndex))
                            }
                        }
                        is ClassMeeting.InPerson -> {
                            // Check for weekend days
                            for (day in meeting.classtime.daysList) {
                                if (day !in weekdays) {
                                    results.add(OtherScheduleItem.Weekend(
                                        selection = selection,
                                        colorIndex = selection.colorIndex,
                                        meeting = meeting,
                                        day = day,
                                        timeRange = meeting.classtime.timeRange
                                    ))
                                }
                            }
                        }
                        is ClassMeeting.OnlineSync -> {
                            // Check for weekend days for online sync
                            for (day in meeting.classtime.daysList) {
                                if (day !in weekdays) {
                                    results.add(OtherScheduleItem.Weekend(
                                        selection = selection,
                                        colorIndex = selection.colorIndex,
                                        meeting = meeting,
                                        day = day,
                                        timeRange = meeting.classtime.timeRange
                                    ))
                                }
                            }
                        }
                    }
                }
            }

            return results
        }

        /**
         * Calculate total credits
         */
        fun getTotalCredits(selections: List<ScheduleSelection>): IntRange {
            val minCredits = selections.sumOf { it.course.minCredits }
            val maxCredits = selections.sumOf { it.course.maxCredits ?: it.course.minCredits }
            return minCredits..maxCredits
        }
    }
}

sealed class AddSectionResult {
    object Success : AddSectionResult()
    object AlreadyAdded : AddSectionResult()
    data class Conflict(val conflictingSelections: List<ScheduleSelection>) : AddSectionResult()
}
