package com.t3tools.t3code.compose.app

internal interface FoundationActions {
  fun addEnvironment()

  fun dismissPairing()

  fun editPairing()

  fun submitPairing(input: String)

  fun retryPairing()

  fun cancelPairing()

  fun openAppSettings()

  fun activateEnvironment(environmentId: String)

  fun removeEnvironment(environmentId: String)

  fun retryEnvironment(environmentId: String)

  fun pairAgain(environmentId: String)

  fun showEnvironments()

  fun showProjects()

  fun selectProject(uiId: String)

  fun selectThread(uiId: String)

  fun navigateBack(): Boolean

  fun clearMessage()
}

internal object NoOpFoundationActions : FoundationActions {
  override fun addEnvironment() = Unit
  override fun dismissPairing() = Unit
  override fun editPairing() = Unit
  override fun submitPairing(input: String) = Unit
  override fun retryPairing() = Unit
  override fun cancelPairing() = Unit
  override fun openAppSettings() = Unit
  override fun activateEnvironment(environmentId: String) = Unit
  override fun removeEnvironment(environmentId: String) = Unit
  override fun retryEnvironment(environmentId: String) = Unit
  override fun pairAgain(environmentId: String) = Unit
  override fun showEnvironments() = Unit
  override fun showProjects() = Unit
  override fun selectProject(uiId: String) = Unit
  override fun selectThread(uiId: String) = Unit
  override fun navigateBack(): Boolean = false
  override fun clearMessage() = Unit
}
