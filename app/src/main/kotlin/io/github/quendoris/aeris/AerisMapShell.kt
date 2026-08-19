// SPDX-FileCopyrightText: 2026 quendoris
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.quendoris.aeris

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

private data class LayerItem(
    val id: String,
    val label: String,
)

private val PoliticalLayers = listOf(
    LayerItem("countries", "Countries"),
    LayerItem("borders", "Borders"),
    LayerItem("labels", "Country labels"),
    LayerItem("regions", "Regions"),
    LayerItem("roads", "Roads"),
    LayerItem("buildings", "Buildings"),
    LayerItem("addresses", "Addresses"),
)

private val PrivateTools = listOf(
    "Pin",
    "Label",
    "Note",
    "Track",
    "Area",
    "Radius",
)

@Composable
fun AerisMapShell() {
    var presentationMode by remember { mutableStateOf(MapPresentationMode.Globe) }
    var political by remember { mutableStateOf(true) }
    var layersExpanded by remember { mutableStateOf(false) }
    var privateToolsExpanded by remember { mutableStateOf(false) }
    val layerVisibility = remember {
        mutableStateMapOf(
            "countries" to true,
            "borders" to true,
            "labels" to true,
            "regions" to false,
            "roads" to false,
            "buildings" to false,
            "addresses" to false,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AndroidView(
            factory = { context ->
                AerisMapSurfaceView(context).apply {
                    setPresentationMode(presentationMode)
                    setPolitical(political)
                }
            },
            update = { surface ->
                surface.setPresentationMode(presentationMode)
                surface.setPolitical(political)
            },
            modifier = Modifier.fillMaxSize(),
        )

        TopMapChrome(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .systemBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .systemBarsPadding()
                .padding(end = 14.dp, bottom = if (layersExpanded) 350.dp else 92.dp),
        ) {
            if (privateToolsExpanded) {
                PrivateToolPalette(
                    onDismiss = { privateToolsExpanded = false },
                )
            }

            RoundMapAction(
                label = "+",
                onClick = { privateToolsExpanded = !privateToolsExpanded },
            )

            ProjectionControl(
                mode = presentationMode,
                onToggle = {
                    presentationMode = if (presentationMode == MapPresentationMode.Globe) {
                        MapPresentationMode.Flat
                    } else {
                        MapPresentationMode.Globe
                    }
                },
            )
        }

        LayerSheet(
            political = political,
            expanded = layersExpanded,
            layerVisibility = layerVisibility,
            onToggleExpanded = { layersExpanded = !layersExpanded },
            onToggleContent = { political = !political },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding(),
        )
    }
}

@Composable
private fun TopMapChrome(modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            shape = RoundedCornerShape(14.dp),
            shadowElevation = 4.dp,
            modifier = Modifier.weight(1f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "⌕",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 20.sp,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Search place or address",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            shape = RoundedCornerShape(14.dp),
            shadowElevation = 4.dp,
        ) {
            Text(
                text = "Open",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
            )
        }
    }
}

@Composable
private fun ProjectionControl(
    mode: MapPresentationMode,
    onToggle: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 5.dp,
        modifier = Modifier.clickable(onClick = onToggle),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Text(
                text = if (mode == MapPresentationMode.Globe) "◉" else "▱",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 19.sp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (mode == MapPresentationMode.Globe) "Globe" else "Flat",
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun RoundMapAction(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 5.dp,
        modifier = Modifier
            .size(50.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Light,
            )
        }
    }
}

@Composable
private fun PrivateToolPalette(onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text = "Private annotation",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
            PrivateTools.forEach { tool ->
                Text(
                    text = tool,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
    }
}

@Composable
private fun LayerSheet(
    political: Boolean,
    expanded: Boolean,
    layerVisibility: SnapshotStateMap<String, Boolean>,
    onToggleExpanded: () -> Unit,
    onToggleContent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(top = 8.dp, bottom = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(4.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(2.dp),
                        ),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.clickable(onClick = onToggleContent),
                ) {
                    Text(
                        text = if (political) "Political" else "Physical",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (expanded) "Layers ▾" else "Layers ▴",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onToggleExpanded),
                )
            }

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                PoliticalLayers.forEach { layer ->
                    LayerRow(
                        label = layer.label,
                        checked = layerVisibility[layer.id] == true,
                        onCheckedChange = { checked -> layerVisibility[layer.id] = checked },
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    Column {
                        Text(
                            text = "Offline coverage",
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "No .aeris project open",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "—",
                        color = Color(0xFFC09B62),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun LayerRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Text(
            text = label,
            color = if (checked) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
