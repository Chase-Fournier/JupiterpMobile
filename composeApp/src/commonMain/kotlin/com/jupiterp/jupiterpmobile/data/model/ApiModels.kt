package com.jupiterp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * API Response models for the Jupiterp API v0
 * These models map directly to the API responses
 *
 * API Docs: http://api.jupiterp.com/v0/
 */

/**
 * Course response from /v0/courses or /v0/courses/withSections
 */
@Serializable
data class CourseResponse(
    @SerialName("course_code") val courseCode: String,
    val name: String,
    @SerialName("min_credits") val minCredits: Int,
    @SerialName("max_credits") val maxCredits: Int? = null,
    @SerialName("gen_eds") val genEds: List<String>? = null,
    val conditions: List<String>? = null,
    val description: String? = null,
    // Only present in /v0/courses/withSections response
    val sections: List<SectionResponse>? = null
)

/**
 * Minified course response from /v0/courses/minified
 */
@Serializable
data class CourseMinifiedResponse(
    @SerialName("course_code") val courseCode: String,
    val name: String
)

/**
 * Section response from /v0/sections or embedded in CourseResponse
 *
 * Meeting string formats:
 * - In-person: "Days-StartTime-EndTime-Building-Room" (e.g., "TuTh-11:00am-12:15pm-CSI-1115")
 * - Online sync: "Days-StartTime-EndTime-OnlineSync"
 * - Online async: "OnlineAsync"
 * - Unspecified: "Unspecified"
 */
@Serializable
data class SectionResponse(
    @SerialName("course_code") val courseCode: String,
    @SerialName("sec_code") val secCode: String,
    val instructors: List<String> = emptyList(),
    val meetings: List<String> = emptyList(),
    @SerialName("open_seats") val openSeats: Int = 0,
    @SerialName("total_seats") val totalSeats: Int = 0,
    val waitlist: Int = 0,
    val holdfile: Int? = null
)

/**
 * Instructor response from /v0/instructors or /v0/instructors/active
 */
@Serializable
data class InstructorResponse(
    val slug: String,
    val name: String,
    @SerialName("average_rating") val averageRating: Float? = null
)

/**
 * Department response from /v0/deptList
 */
@Serializable
data class DepartmentResponse(
    @SerialName("dept_code") val deptCode: String,
    val name: String
)

/**
 * Search/Filter parameters for course queries
 */
data class CourseSearchParams(
    val courseCodes: List<String>? = null,
    val prefix: String? = null,
    val number: String? = null,
    val genEds: List<String>? = null,
    val credits: List<String>? = null, // e.g., ["gt.2", "lt.5"]
    val limit: Int = 100,
    val offset: Int = 0,
    val sortBy: String? = null // e.g., "name.asc,min_credits.desc"
)

/**
 * Search/Filter parameters for section queries
 */
data class SectionSearchParams(
    val courseCodes: List<String>? = null,
    val prefix: String? = null,
    val totalClassSize: List<String>? = null, // e.g., ["gt.40", "lt.100"]
    val onlyOpen: Boolean? = null,
    val instructor: String? = null,
    val limit: Int = 100,
    val offset: Int = 0,
    val sortBy: String? = null
)

/**
 * Search/Filter parameters for instructor queries
 */
data class InstructorSearchParams(
    val instructorNames: List<String>? = null,
    val instructorSlugs: List<String>? = null,
    val ratings: List<String>? = null, // e.g., ["gt.3.5", "lt.5"]
    val limit: Int = 100,
    val offset: Int = 0,
    val sortBy: String? = null
)