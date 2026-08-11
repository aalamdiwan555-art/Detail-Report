package com.ultra.autodetector.data

import android.content.Context

object RepositoryProvider {
    fun create(context: Context): AppRepository =
        if (FirebaseRepository.isConfigured(context)) FirebaseRepository(context)
        else LocalDemoRepository(context)
}