package com.t3tools.t3code.compose.app

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

internal object FoundationSemantics {
  const val Root = "native-android-foundation"
}

@Composable
internal fun FoundationScreen(modifier: Modifier = Modifier) {
  Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
    FoundationContent(innerPadding)
  }
}

@Composable
private fun FoundationContent(innerPadding: PaddingValues) {
  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 32.dp, vertical = 24.dp)
        .testTag(FoundationSemantics.Root),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = "T3 Code Compose",
      style = MaterialTheme.typography.headlineMedium,
      textAlign = TextAlign.Center,
    )
    Text(
      modifier = Modifier.padding(top = 12.dp),
      text = "Native Android foundation",
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
    )
  }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FoundationScreenPreview() {
  T3CodeComposeTheme {
    Surface {
      FoundationScreen()
    }
  }
}
