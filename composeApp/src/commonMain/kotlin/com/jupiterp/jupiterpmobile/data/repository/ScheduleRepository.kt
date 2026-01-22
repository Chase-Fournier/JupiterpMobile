package com.jupiterp.jupiterpmobile.data.repository

import com.jupiterp.jupiterpmobile.data.storage.LocalStorage
import com.jupiterp.jupiterpmobile.domain.model.ClassMeeting
import com.jupiterp.jupiterpmobile.domain.model.Course
import com.jupiterp.jupiterpmobile.domain.model.ScheduleBlock
import com.jupiterp.jupiterpmobile.domain.model.ScheduleSelection
import com.jupiterp.jupiterpmobile.domain.model.Section
import com.jupiterp.jupiterpmobile.domain.model.StoredSchedule
import com.jupiterp.jupiterpmobile.domain.model.TimeSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Repository for managing schedule selections and saved schedules
 * Uses local storage for persistence across app launches
 */
class ScheduleRepository(
    private val storage: LocalStorage
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _currentSelections = MutableStateFlow<List<ScheduleSelection>>(emptyList())
    val currentSelections: Flow<List<ScheduleSelection>> = _currentSelections.asStateFlow()

    private val _savedSchedules = MutableStateFlow<List<StoredSchedule>>(emptyList())
    val savedSchedules: Flow<List<StoredSchedule>> = _savedSchedules.asStateFlow()

    private var colorCounter = 0
    private var isInitialized = false

    init {
        // Load saved data on init
        scope.launch {
            try {
                val appData = storage.loadAppData()
                _currentSelections.value = appData.currentSchedule
                _savedSchedules.value = appData.savedSchedules
                colorCounter = appData.colorCounter
                isInitialized = true
            } catch (e: Exception) {
                e.printStackTrace()
                isInitialized = true
            }
        }
    }

    /**
     * Add a section to the current schedule
     */
    fun addSection(course: Course, section: Section): AddSectionResult {
        val currentList = _currentSelections.value

        // Check if section is already added
        if (currentList.any { it.section.sectionCode == section.sectionCode && it.course.courseCode == course.courseCode }) {
            return AddSectionResult.AlreadyAdded
        }

        // Check for time conflicts
        val conflicts = findConflicts(section, currentList)
        if (conflicts.isNotEmpty()) {
            return AddSectionResult.Conflict(conflicts)
        }

        // Add the section
        val selection = ScheduleSelection(
            course = course,
            section = section,
            colorIndex = colorCounter++ % ScheduleColors.size
        )

        _currentSelections.update { it + selection }
        persistCurrentSchedule()
        return AddSectionResult.Success
    }

    /**
     * Remove a section from the current schedule
     */
    fun removeSection(courseCode: String, sectionCode: String) {
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
        _currentSelections.update { selections ->
            selections.filter { it.course.courseCode != courseCode }
        }
        persistCurrentSchedule()
    }

    /**
     * Clear current schedule
     */
    fun clearSchedule() {
        _currentSelections.value = emptyList()
        colorCounter = 0
        persistCurrentSchedule()
    }

    /**
     * Save current schedule with a name
     */
    fun saveCurrentSchedule(name: String): StoredSchedule {
        val now = Clock.System.now().toEpochMilliseconds()
        val schedule = StoredSchedule(
            id = generateId(),
            name = name,
            selections = _currentSelections.value,
            createdAt = now,
            updatedAt = now
        )

        _savedSchedules.update { it + schedule }
        persistSavedSchedules()
        return schedule
    }

    /**
     * Load a saved schedule as current
     */
    fun loadSchedule(scheduleId: String) {
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
        _savedSchedules.update { schedules ->
            schedules.filter { it.id != scheduleId }
        }
        persistSavedSchedules()
    }

    /**
     * Rename a saved schedule
     */
    fun renameSchedule(scheduleId: String, newName: String) {
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
     * Update hover state for a selection
     */
    fun setHovered(courseCode: String, sectionCode: String, isHovered: Boolean) {
        _currentSelections.update { selections ->
            selections.map { selection ->
                if (selection.course.courseCode == courseCode &&
                    selection.section.sectionCode == sectionCode) {
                    selection.copy(isHovered = isHovered)
                } else {
                    selection
                }
            }
        }
    }

    /**
     * Get schedule blocks for rendering
     */
    fun getScheduleBlocks(): List<ScheduleBlock> {
        return _currentSelections.value.flatMap { selection ->
            selection.section.meetings.filterIsInstance<ClassMeeting.InPerson>().flatMap { meeting ->
                meeting.classtime.daysList.map { day ->
                    ScheduleBlock(
                        selection = selection,
                        meeting = meeting,
                        day = day,
                        startTime = meeting.classtime.start,
                        endTime = meeting.classtime.end,
                        colorIndex = selection.colorIndex
                    )
                }
            }
        }
    }

    /**
     * Calculate total credits
     */
    fun getTotalCredits(): IntRange {
        val selections = _currentSelections.value
        val minCredits = selections.sumOf { it.course.minCredits }
        val maxCredits = selections.sumOf { it.course.maxCredits ?: it.course.minCredits }
        return minCredits..maxCredits
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
     * Persist current schedule to storage
     */
    private fun persistCurrentSchedule() {
        scope.launch {
            try {
                val currentData = storage.loadAppData()
                storage.saveAppData(currentData.copy(
                    currentSchedule = _currentSelections.value,
                    colorCounter = colorCounter
                ))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Persist saved schedules to storage
     */
    private fun persistSavedSchedules() {
        scope.launch {
            try {
                val currentData = storage.loadAppData()
                storage.saveAppData(currentData.copy(
                    savedSchedules = _savedSchedules.value
                ))
            } catch (e: Exception) {
                e.printStackTrace()
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
        val newSlots = extractTimeSlots(section.meetings)

        return currentSelections.filter { selection ->
            val existingSlots = extractTimeSlots(selection.section.meetings)

            newSlots.any { newSlot ->
                existingSlots.any { existingSlot ->
                    newSlot.overlaps(existingSlot)
                }
            }
        }
    }

    /**
     * Extract time slots from meetings
     */
    private fun extractTimeSlots(meetings: List<ClassMeeting>): List<TimeSlot> {
        return meetings.flatMap { meeting ->
            when (meeting) {
                is ClassMeeting.InPerson -> {
                    meeting.classtime.daysList.map { day ->
                        TimeSlot(day, meeting.classtime.start, meeting.classtime.end)
                    }
                }
                is ClassMeeting.OnlineSync -> {
                    meeting.classtime.daysList.map { day ->
                        TimeSlot(day, meeting.classtime.start, meeting.classtime.end)
                    }
                }
                else -> emptyList()
            }
        }
    }

    private fun generateId(): String {
        return Clock.System.now().toEpochMilliseconds().toString(36) +
                (0..999).random().toString(36)
    }

    companion object {
        val ScheduleColors = listOf(
            0xFFB3C8F2, 0xFFF2B3B3, 0xFFF2EFB3,
            0xFFB3F2E6, 0xFFDEB3F2, 0xFFB8F2B3,
            0xFFF2C996, 0xFFEDAFD6, 0xFFE6CDE3,
            0xFFFEDFCC, 0xFFADE0F6, 0xFFFDD4F3,
            0xFFC4BBF1, 0xFFD3F4BA, 0xFFAAEFF4,
            0xFFFFC6B0
        )
    }
}

sealed class AddSectionResult {
    object Success : AddSectionResult()
    object AlreadyAdded : AddSectionResult()
    data class Conflict(val conflictingSelections: List<ScheduleSelection>) : AddSectionResult()
}