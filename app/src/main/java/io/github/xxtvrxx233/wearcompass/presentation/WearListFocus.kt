package io.github.xxtvrxx233.wearcompass.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable

@Composable
internal fun Modifier.wearRotaryScroll(
    listState: TransformingLazyColumnState,
    isActive: Boolean,
): Modifier {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isActive) {
        if (isActive) runCatching { focusRequester.requestFocus() }
    }
    return rotaryScrollable(
        behavior = RotaryScrollableDefaults.behavior(listState),
        focusRequester = focusRequester,
    )
}
