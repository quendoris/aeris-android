// SPDX-FileCopyrightText: 2026 quendoris
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.quendoris.aeris.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Platform UX for the core-owned AERIS Pairing Capsule flow.
 *
 * This composable never creates/parses capsule bytes and never touches vault
 * keys. A future native pairing service supplies state and performs protocol
 * operations; this layer only presents explicit user choices and verification.
 */
@Composable
fun PairDeviceSheet(
    state: PairingUiState,
    onShowRequestQr: () -> Unit,
    onExportRequest: () -> Unit,
    onScanResponseQr: () -> Unit,
    onImportResponse: () -> Unit,
    onBeginVerification: () -> Unit,
    onConfirmVerification: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Text(
                text = "Pair a device",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Private Vault",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )

            when (state.stage) {
                PairingStage.Idle,
                PairingStage.Canceled,
                PairingStage.Error,
                PairingStage.Paired,
                -> IdlePairingContent(
                    state = state,
                    onShowRequestQr = onShowRequestQr,
                    onExportRequest = onExportRequest,
                )

                PairingStage.RequestReady,
                PairingStage.AwaitingResponse,
                -> AwaitingPairingResponseContent(
                    onScanResponseQr = onScanResponseQr,
                    onImportResponse = onImportResponse,
                )

                PairingStage.ResponseReceived -> ResponseReceivedContent(
                    state = state,
                    onBeginVerification = onBeginVerification,
                )

                PairingStage.Verification -> VerificationContent(
                    state = state,
                    onConfirmVerification = onConfirmVerification,
                )
            }

            HorizontalDivider()
            Text(
                text = PairingCopy.RequestSafety,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )

            if (!state.terminal || state.stage == PairingStage.Error) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun IdlePairingContent(
    state: PairingUiState,
    onShowRequestQr: () -> Unit,
    onExportRequest: () -> Unit,
) {
    if (state.stage == PairingStage.Paired) {
        Text(
            text = "This device is paired. Private annotations can open transparently when the vault is available.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
    } else if (state.stage == PairingStage.Error && !state.diagnostic.isNullOrBlank()) {
        Text(
            text = state.diagnostic,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(2.dp))
    }

    Text(
        text = "Authorize this device from a desktop or another phone that already opens the same Private Vault.",
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = PairingCopy.ExportHint,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Button(
            onClick = onShowRequestQr,
            modifier = Modifier.weight(1f),
        ) {
            Text("Show request QR")
        }
        OutlinedButton(
            onClick = onExportRequest,
            modifier = Modifier.weight(1f),
        ) {
            Text("Export request")
        }
    }
}

@Composable
private fun AwaitingPairingResponseContent(
    onScanResponseQr: () -> Unit,
    onImportResponse: () -> Unit,
) {
    Text(
        text = "Request ready",
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = "Open or import this request on an already authorized device. After approval, bring the response back here.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = "A PC does not need a camera: transfer the exported request and response as files or QR images.",
        color = MaterialTheme.colorScheme.primary,
        fontSize = 13.sp,
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Button(
            onClick = onScanResponseQr,
            modifier = Modifier.weight(1f),
        ) {
            Text("Scan response")
        }
        OutlinedButton(
            onClick = onImportResponse,
            modifier = Modifier.weight(1f),
        ) {
            Text("Import response")
        }
    }
}

@Composable
private fun ResponseReceivedContent(
    state: PairingUiState,
    onBeginVerification: () -> Unit,
) {
    Text(
        text = "Response received",
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = "The response is bound to this pairing request. Verify the short code before granting access.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    VerificationCode(code = state.verificationCode)
    Button(
        onClick = onBeginVerification,
        modifier = Modifier.align(Alignment.End),
    ) {
        Text("Verify code")
    }
}

@Composable
private fun VerificationContent(
    state: PairingUiState,
    onConfirmVerification: () -> Unit,
) {
    Text(
        text = "Confirm on both devices",
        fontWeight = FontWeight.SemiBold,
    )
    VerificationCode(code = state.verificationCode)
    Text(
        text = PairingCopy.VerificationSafety,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
    )
    Button(
        onClick = onConfirmVerification,
        modifier = Modifier.align(Alignment.End),
    ) {
        Text("Codes match — pair")
    }
}

@Composable
private fun VerificationCode(code: String?) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = "Verification",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = code ?: "—",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
        }
    }
}
