package com.ultra.autodetector.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ultra.autodetector.data.Account
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object TelegramHelper {
    fun openRenewal(context: Context, account: Account): Boolean {
        val message = buildString {
            append("Hello, I would like to request an Ultra AutoDetector renewal.\n\n")
            append("Email: ${account.email}\n")
            append("Status: ${account.status.name.lowercase()}\n")
            append("UID: ${account.uid}\n\n")
            append("Please let me know the available renewal options.")
        }
        val encoded = URLEncoder.encode(message, StandardCharsets.UTF_8.toString())
        val telegram = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/dminofclicker?text=$encoded"))
        return runCatching {
            context.startActivity(telegram)
            true
        }.getOrDefault(false)
    }
}