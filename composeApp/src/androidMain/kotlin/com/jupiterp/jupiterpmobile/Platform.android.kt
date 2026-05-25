package com.jupiterp.jupiterpmobile

import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.jupiterp.jupiterpmobile.data.storage.AndroidContextHolder
import java.io.File

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual fun currentDateInt(): Int {
    val cal = java.util.Calendar.getInstance()
    val y = cal.get(java.util.Calendar.YEAR)
    val m = cal.get(java.util.Calendar.MONTH) + 1
    val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
    return y * 10000 + m * 100 + d
}

actual fun shareIcs(content: String, filename: String) {
    val context = AndroidContextHolder.appContext ?: return
    val file = File(context.cacheDir, filename)
    file.writeText(content)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/calendar"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(
        Intent.createChooser(send, "Export Calendar").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
}