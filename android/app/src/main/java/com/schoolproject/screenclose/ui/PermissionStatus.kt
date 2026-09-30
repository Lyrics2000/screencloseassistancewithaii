package com.schoolproject.screenclose.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.schoolproject.screenclose.accessibility.ConfirmedClickService

data class PermissionStatus(
    val notifications: Boolean,
    val overlay: Boolean,
    val accessibility: Boolean,
) {
    val allGranted: Boolean get() = notifications && overlay && accessibility

    companion object {
        fun read(context: Context): PermissionStatus = PermissionStatus(
            notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            overlay = Settings.canDrawOverlays(context),
            accessibility = isAccessibilityEnabled(context),
        )

        private fun isAccessibilityEnabled(context: Context): Boolean {
            val expected = ComponentName(context, ConfirmedClickService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
