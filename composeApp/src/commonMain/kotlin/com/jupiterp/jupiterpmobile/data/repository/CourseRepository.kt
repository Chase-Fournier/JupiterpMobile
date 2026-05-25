package com.jupiterp.jupiterpmobile.data.repository

import com.jupiterp.jupiterpmobile.data.api.JupiterpApiClient
import com.jupiterp.jupiterpmobile.data.model.CourseSearchParams
import com.jupiterp.jupiterpmobile.data.model.InstructorSearchParams
import com.jupiterp.jupiterpmobile.data.model.SectionSearchParams
import com.jupiterp.jupiterpmobile.data.model.toDomain
import com.jupiterp.jupiterpmobile.domain.model.Course
import com.jupiterp.jupiterpmobile.domain.model.Department
import com.jupiterp.jupiterpmobile.domain.model.Instructor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
        val prefix = when {
            !query.isNullOrBlank() -> query.uppercase()
            !department.isNullOrBlank() -> department.uppercase()
            else -> null
        }

        // Without a prefix, /courses/withSections ignores the instructor filter and returns nothing.
        // Route through sections first instead.
        if (prefix == null && genEds.isNullOrEmpty() && !instructor.isNullOrBlank()) {
            return searchCoursesByInstructor(instructor, onlyOpen, limit)
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
     * Fetch active instructors for autocomplete. The API caps each request at 500 and
     * returns ~2.6k entries unsorted, so we pull pages in parallel and sort client-side.
     */
    suspend fun getAllInstructorsForSuggestions(): Result<List<Instructor>> = runCatching {
        coroutineScope {
            val pageSize = 500
            val maxPages = 8
            (0 until maxPages).map { idx ->
                async {
                    apiClient.getActiveInstructors(
                        InstructorSearchParams(limit = pageSize, offset = idx * pageSize)
                    ).getOrNull().orEmpty()
                }
            }.awaitAll()
                .flatten()
                .distinctBy { it.name }
                .map { it.toDomain() }
                .sortedBy { it.name }
        }
    }

    /**
     * Get all active instructors (teaching this semester)
     */
    suspend fun getActiveInstructors(limit: Int = 500): Result<List<Instructor>> {
        return apiClient.getActiveInstructors(
            InstructorSearchParams(limit = limit)
        ).map { instructors ->
            instructors.map { it.toDomain() }.sortedBy { it.name }
        }
    }

    /**
     * When only instructor is provided (no course prefix), the /courses/withSections
     * endpoint returns nothing. Use a two-step lookup instead.
     */
    suspend fun searchCoursesByInstructor(
        instructor: String,
        onlyOpen: Boolean? = null,
        limit: Int = 100
    ): Result<List<Course>> {
        val sectionsResult = apiClient.getSections(
            SectionSearchParams(instructor = instructor, onlyOpen = onlyOpen, limit = limit)
        )
        val courseCodes = sectionsResult.getOrElse { return Result.failure(it) }
            .map { it.courseCode }.distinct()
        if (courseCodes.isEmpty()) return Result.success(emptyList())
        return apiClient.getCoursesWithSections(CourseSearchParams(courseCodes = courseCodes))
            .map { courses -> courses.map { it.toDomain() } }
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