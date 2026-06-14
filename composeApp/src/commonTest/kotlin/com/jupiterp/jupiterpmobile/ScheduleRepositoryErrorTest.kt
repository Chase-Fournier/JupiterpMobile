package com.jupiterp.jupiterpmobile

import com.jupiterp.jupiterpmobile.data.repository.ScheduleRepository
import com.jupiterp.jupiterpmobile.data.storage.AppData
import com.jupiterp.jupiterpmobile.data.storage.MutexGuardedStorage
import com.jupiterp.jupiterpmobile.domain.model.Course
import com.jupiterp.jupiterpmobile.domain.model.Section
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.test.Test
import kotlin.test.assertNotNull

class ScheduleRepositoryErrorTest {

    private class ThrowingStorage : MutexGuardedStorage() {
        override suspend fun saveAppData(data: AppData) { throw RuntimeException("disk full") }
        override suspend fun loadAppData(): AppData = AppData()
        override fun getAppDataFlow(): Flow<AppData> = MutableStateFlow(AppData()).asStateFlow()
    }

    private fun course(code: String) = Course(
        courseCode = code, name = "Test", minCredits = 3, maxCredits = null,
        description = null, genEds = null, conditions = null, sections = null
    )

    private fun section() = Section(
        courseCode = "TEST100", sectionCode = "0101", instructors = emptyList(),
        meetings = emptyList(), openSeats = 10, totalSeats = 30, waitlist = 0, holdfile = null
    )

    @Test
    fun persistFailureEmitsUserFacingError() {
        val repo = ScheduleRepository(ThrowingStorage(), CoroutineScope(Dispatchers.Unconfined))
        repo.addSection(course("TEST100"), section())   // triggers persist -> save throws
        assertNotNull(repo.errors.replayCache.lastOrNull())
    }
}
