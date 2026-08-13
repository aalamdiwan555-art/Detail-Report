package com.ultra.autodetector.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.text.DateFormat
import java.util.Date

fun Context.showToast(message: CharSequence) =
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

fun Long?.asDateLabel(): String =
    this?.takeIf { it != Long.MAX_VALUE }?.let { 
        DateFormat.getDateTimeInstance().format(Date(it)) 
    } ?: "Lifetime"

fun Context.openUrl(url: String): Boolean = runCatching {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    true
}.getOrDefault(false)
