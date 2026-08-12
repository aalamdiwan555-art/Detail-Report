package com.ultra.autodetector.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.ultra.autodetector.data.model.User
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Date

object TelegramHelper {
    const val ADMIN_USERNAME = "dminofclicker"

    fun openRenewalChat(context: Context, user: User): Boolean {
        val message = Constants.TELEGRAM_MESSAGE_TEMPLATE.format(
            user.email,
            user.licenseStatus,
            user.id,
            "${Build.MANUFACTURER} ${Build.MODEL}",
            Build.VERSION.RELEASE,
            Date(),
        )
        val encoded = URLEncoder.encode(message, StandardCharsets.UTF_8.toString())
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$ADMIN_USERNAME&text=$encoded"))
            .setPackage("org.telegram.messenger")
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$ADMIN_USERNAME?text=$encoded"))
        return runCatching {
            if (appIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(appIntent)
            } else {
                context.startActivity(webIntent)
            }
            true
        }.getOrDefault(false)
    }
}