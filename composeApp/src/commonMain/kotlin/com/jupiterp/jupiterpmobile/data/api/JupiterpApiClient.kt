package com.jupiterp.jupiterpmobile.data.api

import com.jupiterp.jupiterpmobile.data.model.CourseMinifiedResponse
import com.jupiterp.jupiterpmobile.data.model.CourseResponse
import com.jupiterp.jupiterpmobile.data.model.CourseSearchParams
import com.jupiterp.jupiterpmobile.data.model.DepartmentResponse
import com.jupiterp.jupiterpmobile.data.model.InstructorResponse
import com.jupiterp.jupiterpmobile.data.model.InstructorSearchParams
import com.jupiterp.jupiterpmobile.data.model.SectionResponse
import com.jupiterp.jupiterpmobile.data.model.SectionSearchParams
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * Ktor HTTP client for the Jupiterp API v0
 *
 * API Documentation: http://api.jupiterp.com/v0/
 */
class JupiterpApiClient {

    companion object {
        private const val BASE_URL = "http://api.jupiterp.com/v0"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }

        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.NONE
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }

        defaultRequest {
            url(BASE_URL)
            contentType(ContentType.Application.Json)
        }
    }

    /**
     * GET /v0/courses
     * Get a list of courses with full course info (without sections)
     */
    suspend fun getCourses(params: CourseSearchParams = CourseSearchParams()): Result<List<CourseResponse>> = runCatching {
        val response = client.get("/courses") {
            applyCommonCourseParams(params)
        }
        json.decodeFromString(ListSerializer(serializer<CourseResponse>()), response.bodyAsText())
    }

    /**
     * GET /v0/courses/minified
     * Get a list of courses with just the code and title
     */
    suspend fun getCoursesMinified(params: CourseSearchParams = CourseSearchParams()): Result<List<CourseMinifiedResponse>> = runCatching {
        val response = client.get("/v0/courses/minified") {
            applyCommonCourseParams(params)
        }
        json.decodeFromString(ListSerializer(serializer<CourseMinifiedResponse>()), response.bodyAsText())
    }

    /**
     * GET /v0/courses/withSections
     * Get courses with their sections
     */
    suspend fun getCoursesWithSections(
        params: CourseSearchParams = CourseSearchParams(),
        sectionParams: SectionSearchParams = SectionSearchParams()
    ): Result<List<CourseResponse>> = runCatching {
        val response = client.get("/v0/courses/withSections") {
            applyCommonCourseParams(params)
            // Additional section filters
            sectionParams.totalClassSize?.forEach { parameter("totalClassSize", it) }
            sectionParams.onlyOpen?.let { parameter("onlyOpen", it) }
            sectionParams.instructor?.let { parameter("instructor", it) }
        }
        json.decodeFromString(ListSerializer(serializer<CourseResponse>()), response.bodyAsText())
    }

    /**
     * GET /v0/sections
     * Get sections for specific courses
     */
    suspend fun getSections(params: SectionSearchParams = SectionSearchParams()): Result<List<SectionResponse>> = runCatching {
        val response = client.get("/v0/sections") {
            params.courseCodes?.let { parameter("courseCodes", it.joinToString(",")) }
            params.prefix?.let { parameter("prefix", it) }
            params.totalClassSize?.forEach { parameter("totalClassSize", it) }
            params.onlyOpen?.let { parameter("onlyOpen", it) }
            params.instructor?.let { parameter("instructor", it) }
            parameter("limit", params.limit)
            parameter("offset", params.offset)
            params.sortBy?.let { parameter("sortBy", it) }
        }
        json.decodeFromString(ListSerializer(serializer<SectionResponse>()), response.bodyAsText())
    }

    /**
     * GET /v0/instructors
     * Get a list of all instructors and their ratings
     */
    suspend fun getInstructors(params: InstructorSearchParams = InstructorSearchParams()): Result<List<InstructorResponse>> = runCatching {
        val response = client.get("/v0/instructors") {
            applyInstructorParams(params)
        }
        json.decodeFromString(ListSerializer(serializer<InstructorResponse>()), response.bodyAsText())
    }

    /**
     * GET /v0/instructors/active
     * Get instructors currently teaching a course
     */
    suspend fun getActiveInstructors(params: InstructorSearchParams = InstructorSearchParams()): Result<List<InstructorResponse>> = runCatching {
        val response = client.get("/v0/instructors/active") {
            applyInstructorParams(params)
        }
        json.decodeFromString(ListSerializer(serializer<InstructorResponse>()), response.bodyAsText())
    }

    /**
     * GET /v0/deptList
     * Get a list of 4-letter department codes
     */
    suspend fun getDepartments(): Result<List<DepartmentResponse>> = runCatching {
        val response = client.get("/v0/deptList")
        json.decodeFromString(ListSerializer(serializer<DepartmentResponse>()), response.bodyAsText())
    }

    // Helper functions for common parameter handling

    private fun HttpRequestBuilder.applyCommonCourseParams(params: CourseSearchParams) {
        params.courseCodes?.let { parameter("courseCodes", it.joinToString(",")) }
        params.prefix?.let { parameter("prefix", it) }
        params.number?.let { parameter("number", it) }
        params.genEds?.let { parameter("genEds", it.joinToString(",")) }
        params.credits?.forEach { parameter("credits", it) }
        parameter("limit", params.limit)
        parameter("offset", params.offset)
        params.sortBy?.let { parameter("sortBy", it) }
    }

    private fun HttpRequestBuilder.applyInstructorParams(params: InstructorSearchParams) {
        params.instructorNames?.let { parameter("instructorNames", it.joinToString(",")) }
        params.instructorSlugs?.let { parameter("instructorSlugs", it.joinToString(",")) }
        params.ratings?.forEach { parameter("ratings", it) }
        parameter("limit", params.limit)
        parameter("offset", params.offset)
        params.sortBy?.let { parameter("sortBy", it) }
    }

    /**
     * Convenience method: Search courses by prefix (e.g., "CMSC1" for all CMSC1XX courses)
     */
    suspend fun searchByPrefix(prefix: String, limit: Int = 100): Result<List<CourseResponse>> {
        return getCoursesWithSections(
            params = CourseSearchParams(prefix = prefix, limit = limit)
        )
    }

    /**
     * Convenience method: Search courses by department and get sections
     */
    suspend fun searchByDepartment(department: String, limit: Int = 100): Result<List<CourseResponse>> {
        return getCoursesWithSections(
            params = CourseSearchParams(prefix = department, limit = limit)
        )
    }

    /**
     * Convenience method: Get courses by Gen-Ed requirements
     */
    suspend fun searchByGenEds(genEds: List<String>, limit: Int = 100): Result<List<CourseResponse>> {
        return getCoursesWithSections(
            params = CourseSearchParams(genEds = genEds, limit = limit)
        )
    }

    /**
     * Convenience method: Get specific courses by course codes
     */
    suspend fun getCoursesByCodes(courseCodes: List<String>): Result<List<CourseResponse>> {
        return getCoursesWithSections(
            params = CourseSearchParams(courseCodes = courseCodes)
        )
    }

    /**
     * Convenience method: Get instructor rating by name
     */
    suspend fun getInstructorByName(name: String): Result<InstructorResponse?> = runCatching {
        val result = getInstructors(
            InstructorSearchParams(instructorNames = listOf(name), limit = 1)
        )
        result.getOrNull()?.firstOrNull()
    }

    fun close() {
        client.close()
    }
}

/**
 * Sealed class for API results with loading state
 */
sealed class ApiState<out T> {
    data object Loading : ApiState<Nothing>()
    data class Success<T>(val data: T) : ApiState<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : ApiState<Nothing>()
    data object Empty : ApiState<Nothing>()

    val isLoading: Boolean get() = this is Loading
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isEmpty: Boolean get() = this is Empty

    fun getOrNull(): T? = (this as? Success)?.data
}