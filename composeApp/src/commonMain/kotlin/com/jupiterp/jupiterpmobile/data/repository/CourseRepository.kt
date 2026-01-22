package com.jupiterp.data.repository

import com.jupiterp.data.api.JupiterpApiClient
import com.jupiterp.data.model.CourseSearchParams
import com.jupiterp.data.model.InstructorSearchParams
import com.jupiterp.data.model.SectionSearchParams
import com.jupiterp.data.model.toDomain
import com.jupiterp.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Repository for course-related data operations
 * Uses the Jupiterp API v0
 */
class CourseRepository(
    private val apiClient: JupiterpApiClient
) {
    /**
     * Search courses with filters and include section data
     *
     * @param query Search query - used as prefix (e.g., "CMSC1" for CMSC1XX courses)
     * @param department Department prefix (e.g., "CMSC")
     * @param genEds List of Gen-Ed codes to filter by
     * @param instructor Filter by instructor name
     * @param onlyOpen If true, only return courses with sections that have open seats
     * @param limit Maximum number of results
     */
    suspend fun searchCourses(
        query: String? = null,
        department: String? = null,
        genEds: List<String>? = null,
        instructor: String? = null,
        onlyOpen: Boolean? = null,
        limit: Int = 100
    ): Result<List<Course>> {
        // Build the prefix from query or department
        val prefix = when {
            !query.isNullOrBlank() -> query.uppercase()
            !department.isNullOrBlank() -> department.uppercase()
            else -> null
        }

        val courseParams = CourseSearchParams(
            prefix = prefix,
            genEds = genEds?.takeIf { it.isNotEmpty() },
            limit = limit
        )

        val sectionParams = SectionSearchParams(
            instructor = instructor,
            onlyOpen = onlyOpen
        )

        return apiClient.getCoursesWithSections(courseParams, sectionParams).map { courses ->
            courses.map { it.toDomain() }
        }
    }

    /**
     * Search courses by specific course codes
     */
    suspend fun getCoursesByCodes(courseCodes: List<String>): Result<List<Course>> {
        return apiClient.getCoursesByCodes(courseCodes).map { courses ->
            courses.map { it.toDomain() }
        }
    }

    /**
     * Search courses by Gen-Ed requirements
     */
    suspend fun searchByGenEds(genEds: List<String>, limit: Int = 100): Result<List<Course>> {
        return apiClient.searchByGenEds(genEds, limit).map { courses ->
            courses.map { it.toDomain() }
        }
    }

    /**
     * Get all departments
     */
    suspend fun getDepartments(): Result<List<Department>> {
        return apiClient.getDepartments().map { departments ->
            departments.map { it.toDomain() }.sortedBy { it.code }
        }
    }

    /**
     * Get all active instructors (teaching this semester)
     */
    suspend fun getActiveInstructors(limit: Int = 500): Result<List<Instructor>> {
        return apiClient.getActiveInstructors(
            InstructorSearchParams(limit = limit, sortBy = "name.asc")
        ).map { instructors ->
            instructors.map { it.toDomain() }
        }
    }

    /**
     * Search instructors by name
     */
    suspend fun searchInstructors(names: List<String>): Result<List<Instructor>> {
        return apiClient.getInstructors(
            InstructorSearchParams(instructorNames = names)
        ).map { instructors ->
            instructors.map { it.toDomain() }
        }
    }

    /**
     * Get instructor by exact name
     */
    suspend fun getInstructorByName(name: String): Result<Instructor?> {
        return apiClient.getInstructorByName(name).map { response ->
            response?.toDomain()
        }
    }

    /**
     * Get highly-rated instructors
     */
    suspend fun getTopRatedInstructors(minRating: Float = 4.0f, limit: Int = 50): Result<List<Instructor>> {
        return apiClient.getActiveInstructors(
            InstructorSearchParams(
                ratings = listOf("gte.$minRating"),
                limit = limit,
                sortBy = "average_rating.desc"
            )
        ).map { instructors ->
            instructors.map { it.toDomain() }
        }
    }

    /**
     * Flow-based search for reactive UI updates
     */
    fun searchCoursesFlow(
        query: String? = null,
        department: String? = null,
        genEds: List<String>? = null
    ): Flow<Result<List<Course>>> = flow {
        emit(searchCourses(query, department, genEds))
    }
}