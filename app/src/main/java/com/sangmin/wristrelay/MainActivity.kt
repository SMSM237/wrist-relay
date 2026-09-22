package com.sangmin.wristrelay

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.sangmin.wristrelay.ui.AppScreen
import com.sangmin.wristrelay.ui.AppViewModel
import com.sangmin.wristrelay.ui.WristRelayApp

class MainActivity : ComponentActivity() {
    private lateinit var appViewModel: AppViewModel
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { appViewModel.refreshReadiness() }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (!BuildConfig.DEBUG) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        super.onCreate(savedInstanceState)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        if (Build.VERSION.SDK_INT >= 27) {
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.rgb(247, 245, 240)
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = true
        }

        val backend = application as WristRelayApplication
        appViewModel = ViewModelProvider(
            this,
            AppViewModel.factory(backend),
        )[AppViewModel::class.java]

        setContent {
            val state by appViewModel.state.collectAsState()
            WristRelayApp(
                state = state,
                onNavigate = appViewModel::navigate,
                onStartCapture = {
                    if (state.activeSession != null) appViewModel.navigate(AppScreen.CAPTURE)
                    else appViewModel.startCapture()
                },
                onCancelCapture = appViewModel::cancelCapture,
                onChooseCaptured = appViewModel::chooseCaptured,
                onUpdateDraft = appViewModel::updateDraft,
                onSendWatchTest = appViewModel::sendWatchTest,
                onConfirmWatchTest = appViewModel::confirmWatchTest,
                onSaveRule = appViewModel::saveRule,
                onSetRuleEnabled = appViewModel::setRuleEnabled,
                onEditRule = appViewModel::editRule,
                onDeleteRule = appViewModel::deleteRule,
                onRequestListenerReconnect = appViewModel::requestListenerReconnect,
                onOpenListenerSettings = ::openListenerSettings,
                onRequestNotificationPermission = ::requestNotifications,
                onOpenNotificationSettings = ::openNotificationSettings,
                onShareDiagnostics = ::shareDiagnostics,
                onDismissMessage = appViewModel::clearMessage,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (::appViewModel.isInitialized) appViewModel.refreshReadiness()
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings()
        }
    }

    private fun openListenerSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun openNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
        )
    }

    private fun shareDiagnostics() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, appViewModel.redactedDiagnostics())
        }
        startActivity(Intent.createChooser(intent, "진단 공유"))
    }
}
