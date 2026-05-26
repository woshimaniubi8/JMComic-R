package com.batsd.jmcomict.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Material Snackbar 式 Toast 宿主 */
val LocalToast = staticCompositionLocalOf<suspend (String) -> Unit> { {} }

@Composable
fun MaterialToastHost(content: @Composable () -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showToast: suspend (String) -> Unit = { msg ->
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
    }

    CompositionLocalProvider(
        LocalToast provides showToast
    ) {
        Box {
            content()
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
