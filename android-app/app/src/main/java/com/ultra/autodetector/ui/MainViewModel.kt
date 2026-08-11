package com.ultra.autodetector.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ultra.autodetector.data.Account
import com.ultra.autodetector.data.AppRepository
import com.ultra.autodetector.data.AppState
import com.ultra.autodetector.data.PermissionState
import com.ultra.autodetector.data.RepositoryProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: AppRepository = RepositoryProvider.create(application)
    val state: StateFlow<AppState> = repository.state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppState(),
    )

    fun login(email: String, password: String) = action { repository.login(email, password) }
    fun register(email: String, password: String) = action { repository.register(email, password) }
    fun changePassword(currentPassword: String, newPassword: String) =
        action { repository.changePassword(currentPassword, newPassword) }
    fun sendPasswordReset(email: String) = action { repository.sendPasswordReset(email) }
    fun logout() = viewModelScope.launch { repository.logout() }
    fun refresh() = viewModelScope.launch { repository.refresh() }
    fun refreshAdminData() = viewModelScope.launch { repository.refreshAdminData() }
    fun setPermissions(state: PermissionState) = viewModelScope.launch { repository.setPermissionState(state) }
    fun setDetector(running: Boolean, paused: Boolean = false) =
        viewModelScope.launch { repository.setDetectorState(running, paused) }
    fun grantLicense(account: Account, days: Int?) =
        viewModelScope.launch { repository.grantLicense(account.uid, days) }
    fun rejectUser(account: Account) = viewModelScope.launch { repository.rejectUser(account.uid) }
    fun uploadTemplate(name: String, description: String, image: Uri?) =
        action { repository.uploadTemplate(name, description, image) }
    fun deleteTemplate(id: String) = viewModelScope.launch { repository.deleteTemplate(id) }

    private fun <T> action(block: suspend () -> Result<T>) = viewModelScope.launch {
        repository.state // Keep the repository alive while a user action is executing.
        block().onFailure { error ->
            // Implementations expose errors through their state; this is the final
            // safety net for unexpected provider failures.
            error.printStackTrace()
        }
    }
}