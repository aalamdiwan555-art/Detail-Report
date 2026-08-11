package com.ultra.autodetector.data

import android.content.Context

object RepositoryProvider {
    /**
     * The app currently runs on its private on-device database.
     *
     * FirebaseRepository remains in the project as an optional cloud adapter,
     * but local mode is explicit and does not depend on Firebase billing,
     * authentication setup, or network access.
     */
    fun create(context: Context): AppRepository = LocalDatabaseRepository(context)
}