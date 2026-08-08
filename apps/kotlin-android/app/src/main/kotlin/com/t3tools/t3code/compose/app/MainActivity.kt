package com.t3tools.t3code.compose.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
  private lateinit var foundationViewModel: FoundationViewModel

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    foundationViewModel = ViewModelProvider(this, FoundationViewModel.Factory)[FoundationViewModel::class.java]
    enableEdgeToEdge()
    setContent {
      val state = foundationViewModel.uiState.collectAsStateWithLifecycle().value
      val actions = remember(foundationViewModel) {
        ActivityFoundationActions(foundationViewModel, ::openAppSettings)
      }
      T3CodeComposeTheme {
        FoundationScreen(
          state = state,
          actions = actions,
        )
      }
    }
    if (savedInstanceState == null) consumePairingIntent(intent)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    consumePairingIntent(intent)
  }

  override fun onStart() {
    super.onStart()
    foundationViewModel.setForeground(true)
  }

  override fun onStop() {
    foundationViewModel.setForeground(false)
    super.onStop()
  }

  private fun consumePairingIntent(intent: Intent) {
    intent.dataString?.let(foundationViewModel::acceptPairingRoute)
  }

  private fun openAppSettings() {
    startActivity(
      Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
      ),
    )
  }
}

private class ActivityFoundationActions(
  private val model: FoundationViewModel,
  private val openSettings: () -> Unit,
) : FoundationActions {
  override fun addEnvironment() = model.showAddEnvironment()
  override fun dismissPairing() = model.dismissPairing()
  override fun editPairing() = model.editPairing()
  override fun submitPairing(input: String) = model.pair(input)
  override fun retryPairing() = model.retryPairing()
  override fun cancelPairing() = model.cancelPairing()
  override fun openAppSettings() = openSettings()
  override fun activateEnvironment(environmentId: String) = model.activateEnvironment(environmentId)
  override fun removeEnvironment(environmentId: String) = model.removeEnvironment(environmentId)
  override fun retryEnvironment(environmentId: String) = model.retryEnvironment(environmentId)
  override fun pairAgain(environmentId: String) = model.pairAgain(environmentId)
  override fun showEnvironments() = model.showEnvironments()
  override fun showProjects() = model.showProjects()
  override fun selectProject(uiId: String) = model.selectProject(uiId)
  override fun selectThread(uiId: String) = model.selectThread(uiId)
  override fun navigateBack(): Boolean = model.navigateBack()
  override fun clearMessage() = model.clearMessage()
}
