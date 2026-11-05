package io.github.xxtvrxx233.wearcompass.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.util.withJson
import io.github.xxtvrxx233.wearcompass.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun rememberLibraries(): Libs? {
    val context = LocalContext.current
    val libraries by produceState<Libs?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                Libs.Builder().withJson(context, R.raw.aboutlibraries).build()
            }.getOrNull()
        }
    }
    return libraries
}

private fun chunkParagraphs(text: String, maxChars: Int = 400): List<String> {
    val result = mutableListOf<String>()
    val builder = StringBuilder()
    for (line in text.split('\n')) {
        if (builder.isNotEmpty() && builder.length + line.length > maxChars) {
            result += builder.toString().trim()
            builder.clear()
        }
        if (builder.isNotEmpty()) builder.append('\n')
        builder.append(line)
    }
    if (builder.isNotBlank()) result += builder.toString().trim()
    return result
}

@Composable
internal fun LicensesScreen(
    listState: TransformingLazyColumnState,
    isActive: Boolean,
    onOpenLibrary: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val libraries = rememberLibraries()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = listState,
        modifier = Modifier.wearRotaryScroll(listState, isActive),
        edgeButton = {
            EdgeButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
        },
    ) { contentPadding ->
        val layoutDirection = LocalLayoutDirection.current
        TransformingLazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + ListExtraBottomPadding,
            ),
        ) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Text(stringResource(R.string.open_source_licenses))
                }
            }
            if (libraries == null) {
                item {
                    Text(
                        text = stringResource(R.string.licenses_loading),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                libraries.libraries.forEachIndexed { index, library ->
                    item(key = "library-${library.uniqueId ?: index}") {
                        FilledTonalButton(
                            onClick = { onOpenLibrary(index) },
                            label = {
                                Text(
                                    text = library.name,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            icon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LicenseDetailScreen(
    index: Int,
    listState: TransformingLazyColumnState,
    isActive: Boolean,
    onBack: () -> Unit,
) {
    val libraries = rememberLibraries()
    val library = libraries?.libraries?.getOrNull(index)
    val transformationSpec = rememberTransformationSpec()
    val noLicenseText = stringResource(R.string.no_license_text)

    val licenseChunks = remember(library, noLicenseText) {
        library?.licenses?.map { license ->
            license.name to chunkParagraphs(license.licenseContent ?: noLicenseText)
        }.orEmpty()
    }

    ScreenScaffold(
        scrollState = listState,
        modifier = Modifier.wearRotaryScroll(listState, isActive),
        edgeButton = {
            EdgeButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
        },
    ) { contentPadding ->
        val layoutDirection = LocalLayoutDirection.current
        TransformingLazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + ListExtraBottomPadding,
            ),
        ) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Text(library?.name ?: stringResource(R.string.open_source_licenses))
                }
            }

            licenseChunks.forEachIndexed { licenseIndex, (licenseName, chunks) ->
                item(key = "license-$licenseIndex-name") {
                    ListSubHeader(
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    ) {
                        Text(licenseName)
                    }
                }
                chunks.forEachIndexed { chunkIndex, chunk ->
                    item(key = "license-$licenseIndex-$chunkIndex") {
                        Text(
                            text = chunk,
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)

                                .graphicsLayer {
                                    with(transformationSpec) { applyContainerTransformation(scrollProgress) }
                                }
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
