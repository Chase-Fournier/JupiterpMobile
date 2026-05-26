@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.jupiterp.jupiterpmobile

import com.jupiterp.jupiterpmobile.domain.model.ClassMeeting
import com.jupiterp.jupiterpmobile.domain.model.DayOfWeek
import com.jupiterp.jupiterpmobile.domain.model.ScheduleSelection
import platform.EventKit.EKEntityType
import platform.EventKit.EKEvent
import platform.EventKit.EKEventStore
import platform.EventKit.EKRecurrenceDayOfWeek
import platform.EventKit.EKRecurrenceEnd
import platform.EventKit.EKRecurrenceFrequency
import platform.EventKit.EKRecurrenceRule
import platform.EventKit.EKSpan
import platform.EventKit.EKWeekdayFriday
import platform.EventKit.EKWeekdayMonday
import platform.EventKit.EKWeekdaySaturday
import platform.EventKit.EKWeekdaySunday
import platform.EventKit.EKWeekdayThursday
import platform.EventKit.EKWeekdayTuesday
import platform.EventKit.EKWeekdayWednesday
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIDevice

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

actual fun currentDateInt(): Int {
    val formatter = NSDateFormatter().apply { dateFormat = "yyyyMMdd" }
    return formatter.stringFromDate(NSDate()).toInt()
}

actual fun addToCalendar(selections: List<ScheduleSelection>, onResult: (Boolean) -> Unit) {
    val store = EKEventStore()
    store.requestAccessToEntityType(EKEntityType.EKEntityTypeEvent) { granted, _ ->
        NSOperationQueue.mainQueue.addOperationWithBlock {
            if (!granted) {
                onResult(false)
                return@addOperationWithBlock
            }
            val defaultCalendar = store.defaultCalendarForNewEvents
            if (defaultCalendar == null) {
                onResult(false)
                return@addOperationWithBlock
            }

            val semester = activeSemester()
            val endDate = parseIcsEndDate(semester.endIcs)
            var success = true

            for (selection in selections) {
                val courseCode = selection.course.courseCode
                val sectionCode = selection.section.sectionCode
                val courseName = selection.course.name

                for (meeting in selection.section.meetings) {
                    val days: List<DayOfWeek>
                    val startTime: Float
                    val endTime: Float
                    val location: String
                    when (meeting) {
                        is ClassMeeting.InPerson -> {
                            days = meeting.classtime.daysList
                            startTime = meeting.classtime.start
                            endTime = meeting.classtime.end
                            location = meeting.location.display
                        }
                        is ClassMeeting.OnlineSync -> {
                            days = meeting.classtime.daysList
                            startTime = meeting.classtime.start
                            endTime = meeting.classtime.end
                            location = "Online Sync"
                        }
                        else -> continue
                    }
                    if (days.isEmpty()) continue

                    val firstDay = days.minByOrNull { it.column } ?: continue
                    val ekDays = days.map { EKRecurrenceDayOfWeek.dayOfWeek(it.toEKWeekday()) }
                    val rule = EKRecurrenceRule(
                        recurrenceWithFrequency = EKRecurrenceFrequency.EKRecurrenceFrequencyWeekly,
                        interval = 1,
                        daysOfTheWeek = ekDays,
                        daysOfTheMonth = null,
                        monthsOfTheYear = null,
                        weeksOfTheYear = null,
                        daysOfTheYear = null,
                        setPositions = null,
                        end = EKRecurrenceEnd.recurrenceEndWithEndDate(endDate)
                    )

                    val event = EKEvent.eventWithEventStore(store)
                    event.title = "$courseCode ($sectionCode) - $location"
                    event.notes = courseName
                    event.location = location
                    event.calendar = defaultCalendar
                    event.startDate = makeEventDate(semester.firstMondayInt, firstDay, startTime)
                    event.endDate = makeEventDate(semester.firstMondayInt, firstDay, endTime)
                    event.addRecurrenceRule(rule)

                    val saved = runCatching {
                        store.saveEvent(event, span = EKSpan.EKSpanFutureEvents, commit = false, error = null)
                    }.getOrDefault(false)
                    if (!saved) success = false
                }
            }

            if (success) store.commit(null)
            onResult(success)
        }
    }
}

private fun makeEventDate(firstMondayInt: Int, day: DayOfWeek, time: Float): NSDate {
    val year = firstMondayInt / 10000
    val month = (firstMondayInt / 100) % 100
    val dayOfMonthRaw = firstMondayInt % 100 + day.column
    val monthDays = daysInMonth(year, month)
    val (finalYear, finalMonth, finalDay) = if (dayOfMonthRaw <= monthDays) {
        Triple(year, month, dayOfMonthRaw)
    } else {
        val nm = if (month < 12) month + 1 else 1
        val ny = if (month < 12) year else year + 1
        Triple(ny, nm, dayOfMonthRaw - monthDays)
    }
    val components = NSDateComponents()
    components.year = finalYear.toLong()
    components.month = finalMonth.toLong()
    components.day = finalDay.toLong()
    components.hour = time.toInt().toLong()
    components.minute = ((time - time.toInt()) * 60).toInt().toLong()
    components.second = 0L
    return NSCalendar(NSCalendarIdentifierGregorian).dateFromComponents(components)!!
}

private fun parseIcsEndDate(endIcs: String): NSDate {
    val formatter = NSDateFormatter().apply { dateFormat = "yyyyMMdd" }
    return formatter.dateFromString(endIcs.take(8)) ?: NSDate()
}

private fun DayOfWeek.toEKWeekday() = when (this) {
    DayOfWeek.MONDAY -> EKWeekdayMonday
    DayOfWeek.TUESDAY -> EKWeekdayTuesday
    DayOfWeek.WEDNESDAY -> EKWeekdayWednesday
    DayOfWeek.THURSDAY -> EKWeekdayThursday
    DayOfWeek.FRIDAY -> EKWeekdayFriday
    DayOfWeek.SATURDAY -> EKWeekdaySaturday
    DayOfWeek.SUNDAY -> EKWeekdaySunday
    else -> EKWeekdayMonday
}
