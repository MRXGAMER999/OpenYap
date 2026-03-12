package com.openyap.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openyap.viewmodel.UserProfileEvent
import com.openyap.viewmodel.UserProfileUiState

@Composable
fun UserInfoScreen(
    state: UserProfileUiState,
    onEvent: (UserProfileEvent) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("User Profile", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Your info is used for phrase expansion (e.g., \"my name\" → your name).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()

        val profile = state.profile
        ProfileField("Name", profile.name) { onEvent(UserProfileEvent.UpdateName(it)) }
        ProfileField("Email", profile.email) { onEvent(UserProfileEvent.UpdateEmail(it)) }
        ProfileField("Phone", profile.phone) { onEvent(UserProfileEvent.UpdatePhone(it)) }
        ProfileField("Job Title", profile.jobTitle) { onEvent(UserProfileEvent.UpdateJobTitle(it)) }
        ProfileField("Company", profile.company) { onEvent(UserProfileEvent.UpdateCompany(it)) }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { onEvent(UserProfileEvent.Save) },
            enabled = !state.isSaving,
        ) {
            Text(if (state.isSaving) "Saving..." else "Save Profile")
        }
    }
}

@Composable
private fun ProfileField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}
