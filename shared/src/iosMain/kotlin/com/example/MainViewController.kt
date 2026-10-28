package com.example

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.example.data.DiamondsDatabase
import com.example.data.createDatabase
import com.example.platform.Device
import com.example.platform.IosDevice
import com.example.ui.DiamondsApp
import com.example.ui.theme.MyApplicationTheme
import platform.UIKit.UIViewController

// The iPhone app's way in. Swift calls MainViewController() (as
// MainViewControllerKt.MainViewController(), after this file's name) and shows
// what it returns; everything on screen from there is the shared code, the
// same screens the Android app draws.

private val iosDevice: Device by lazy { IosDevice() }
private val database: DiamondsDatabase by lazy { createDatabase() }

fun MainViewController(): UIViewController = ComposeUIViewController {
    // One view model for the life of the app, as Android's ViewModel gives.
    val viewModel = remember { DiamondsViewModel(database.dao(), iosDevice) }
    MyApplicationTheme {
        DiamondsApp(viewModel)
    }
}
