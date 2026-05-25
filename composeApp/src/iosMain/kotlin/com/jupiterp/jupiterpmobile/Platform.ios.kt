package com.jupiterp.jupiterpmobile

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

actual fun currentDateInt(): Int {
    val formatter = NSDateFormatter().apply { dateFormat = "yyyyMMdd" }
    return formatter.stringFromDate(NSDate()).toInt()
}

@OptIn(ExperimentalForeignApi::class)
actual fun shareIcs(content: String, filename: String) {
    val filePath = "${NSTemporaryDirectory()}$filename"
    val bytes = content.encodeToByteArray()
    bytes.usePinned { pinned ->
        val file = fopen(filePath, "wb")
        if (file != null) {
            fwrite(pinned.addressOf(0), 1uL, bytes.size.toULong(), file)
            fclose(file)
        }
    }
    val url = NSURL.fileURLWithPath(filePath)
    val activityVC = UIActivityViewController(
        activityItems = listOf(url),
        applicationActivities = null
    )
    UIApplication.sharedApplication.keyWindow?.rootViewController
        ?.presentViewController(activityVC, animated = true, completion = null)
}