package com.openyap.ui.component

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import openyap.composeapp.generated.resources.Res
import openyap.composeapp.generated.resources.ic_app_logo

@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(Res.drawable.ic_app_logo),
        contentDescription = contentDescription,
        modifier = modifier,
    )
}
