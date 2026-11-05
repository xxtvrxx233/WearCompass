package io.github.xxtvrxx233.wearcompass.presentation

import java.util.Locale

fun formatDeclination(declination: Float): String =
    if (declination.isNaN()) "" else String.format(Locale.getDefault(), "%+.1f°", declination)
