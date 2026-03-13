package com.openyap.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openyap.ui.theme.Spacing
import com.openyap.viewmodel.UserProfileEvent
import com.openyap.viewmodel.UserProfileUiState

@Composable
fun UserInfoScreen(
    state: UserProfileUiState,
    onEvent: (UserProfileEvent) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("User profile", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "These details power phrase expansion so OpenYap can turn shortcuts into polished personal answers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 580.dp),
                )
            }
            if (state.saveMessage == null) {
                AssistChip(onClick = {}, enabled = false, label = { Text("Phrase expansion profile") })
            }
        }
        Text(
            "Complete only the fields you want OpenYap to expand automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )

        // Flat section — no card wrapper (it's the only content, wrapping is redundant)
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            val profile = state.profile
            ProfileField("Name", profile.name) { onEvent(UserProfileEvent.UpdateName(it)) }
            ProfileField("Email", profile.email) { onEvent(UserProfileEvent.UpdateEmail(it)) }
            ProfileField("Phone", profile.phone) { onEvent(UserProfileEvent.UpdatePhone(it)) }
            ProfileField("Job title", profile.jobTitle) { onEvent(UserProfileEvent.UpdateJobTitle(it)) }
            ProfileField("Company", profile.company) { onEvent(UserProfileEvent.UpdateCompany(it)) }

            Spacer(Modifier.height(Spacing.xs))
            FilledTonalButton(onClick = { onEvent(UserProfileEvent.Save) }, enabled = !state.isSaving) {
                Text(if (state.isSaving) "Saving..." else "Save profile")
            }
            state.saveMessage?.let { message ->
                LaunchedEffect(message) {
                    kotlinx.coroutines.delay(2000)
                    onEvent(UserProfileEvent.DismissSaveMessage)
                }
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ProfileField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}
