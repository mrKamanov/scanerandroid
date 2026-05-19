package com.tscan.scanertestov.legal

/**
 * Описание: открытие https-ссылки во внешнем браузере (политика, репозиторий).
 */
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast

fun Context.openHttpUrl(url: String) {
    val uri = Uri.parse(url)
    val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
    }
    val launchContext = findActivity() ?: this
    val chooser = Intent.createChooser(viewIntent, null)
    if (launchContext !is Activity) {
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        if (viewIntent.resolveActivity(launchContext.packageManager) == null) {
            throw ActivityNotFoundException(url)
        }
        launchContext.startActivity(chooser)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            launchContext,
            "Не удалось открыть ссылку. Установите браузер или откройте: $url",
            Toast.LENGTH_LONG,
        ).show()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
