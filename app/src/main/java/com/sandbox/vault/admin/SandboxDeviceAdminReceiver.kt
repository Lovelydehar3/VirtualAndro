package com.sandbox.vault.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.sandbox.vault.MainActivity
import com.sandbox.vault.core.workprofile.WorkProfileManager

class SandboxDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        WorkProfileManager(context).applyDefaultPolicies()

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(launchIntent)
    }
}
