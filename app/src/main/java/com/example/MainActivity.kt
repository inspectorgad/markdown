package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.DiamondsDatabase
import com.example.data.get
import com.example.platform.AndroidDevice
import com.example.ui.DiamondsApp
import com.example.ui.theme.MyApplicationTheme

// The Android app is now a shell: this screen, and the shared code in
// shared/ that draws everything inside it. The iPhone app shows the same
// screens from the same module (see shared/src/iosMain/.../MainViewController.kt).

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                DiamondsApp(viewModel(factory = DiamondsViewModelFactory(applicationContext)))
            }
        }
    }
}

/**
 * Builds the shared view model with this phone's database and storage. It used
 * to be an AndroidViewModel that took the Application itself; the shared one
 * takes the two things it actually needs, so the iPhone can hand it the same.
 */
private class DiamondsViewModelFactory(
    context: android.content.Context,
) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
        DiamondsViewModel(
            DiamondsDatabase.get(appContext).dao(),
            AndroidDevice(appContext),
        ) as T
}
