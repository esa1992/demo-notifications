package com.demo.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.demo.notifications.ui.DemoScreen
import com.demo.notifications.ui.theme.DemoNotificationsTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        NotificationHelper.ensureChannel(this)
        requestNotificationPermission()

        setContent {
            DemoNotificationsTheme {
                val viewModel: DemoViewModel = viewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DemoScreen(
                    state = state,
                    onBackendUrl = viewModel::setBackendUrl,
                    onCustomerId = viewModel::setCustomerId,
                    onConnect = viewModel::connect,
                    onDisconnect = viewModel::disconnect,
                    onClear = viewModel::clear,
                    onLanguage = viewModel::setLanguage
                )
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
