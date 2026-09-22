package com.sangmin.wristrelay

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ManifestPolicyTest {
    @Test
    fun prohibitedSensitivePermissionsAreAbsent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
        )

        val requested = packageInfo.requestedPermissions.orEmpty().toSet()
        val prohibited = setOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.NFC,
        )

        assertTrue(requested.contains(Manifest.permission.POST_NOTIFICATIONS))
        val unexpected = requested.intersect(prohibited)
        assertTrue("Unexpected sensitive permissions: $unexpected", unexpected.isEmpty())
    }

    @Test
    fun applicationBackupAndCleartextTrafficAreDisabled() {
        val appInfo = ApplicationProvider.getApplicationContext<Context>().applicationInfo

        assertFalse(appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
        assertFalse(appInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC != 0)
    }

    @Test
    fun listenerServiceUsesSystemBindingPermission() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SERVICES.toLong()),
        )
        val listener = packageInfo.services.orEmpty().single {
            it.name == "com.sangmin.wristrelay.notifications.WristNotificationListenerService"
        }

        assertEquals(Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE, listener.permission)
        assertTrue(listener.exported)
    }
}
