// SPDX-FileCopyrightText: 2026 quendoris
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.quendoris.aeris.pairing

/**
 * Android-owned presentation state for the core-owned AERIS Pairing Capsule
 * protocol. This file deliberately contains no cryptography or capsule codec.
 */
enum class PairingStage {
    Idle,
    RequestReady,
    AwaitingResponse,
    ResponseReceived,
    Verification,
    Paired,
    Canceled,
    Error,
}

enum class PairingTransport {
    CameraQr,
    ExportedQrImage,
    CapsuleFile,
}

data class PairingUiState(
    val stage: PairingStage = PairingStage.Idle,
    val requestTransport: PairingTransport? = null,
    val responseTransport: PairingTransport? = null,
    val verificationCode: String? = null,
    val diagnostic: String? = null,
) {
    val terminal: Boolean
        get() = stage == PairingStage.Paired ||
            stage == PairingStage.Canceled ||
            stage == PairingStage.Error
}

sealed interface PairingEvent {
    data class RequestCreated(val transport: PairingTransport) : PairingEvent
    data object RequestShared : PairingEvent
    data class ResponseImported(
        val transport: PairingTransport,
        val verificationCode: String,
    ) : PairingEvent
    data object BeginVerification : PairingEvent
    data object ConfirmVerification : PairingEvent
    data object Cancel : PairingEvent
    data class Fail(val diagnostic: String) : PairingEvent
}

object PairingCopy {
    const val ExportHint =
        "Scan on another device or export the pairing QR to transfer it offline."

    const val RequestSafety =
        "The pairing request is not your vault key and contains no private map data. " +
            "Access is granted only after an already authorized device approves this exact request."

    const val VerificationSafety =
        "Compare this code on both devices before approving. If the codes differ, cancel pairing."
}

/**
 * Small deterministic reducer that keeps pairing UX honest while the actual
 * capsule protocol is still owned by the AERIS core contract.
 */
object PairingReducer {
    fun reduce(state: PairingUiState, event: PairingEvent): PairingUiState = when (event) {
        is PairingEvent.RequestCreated -> {
            require(state.stage == PairingStage.Idle || state.terminal) {
                "pairing request can only start from idle/terminal state"
            }
            PairingUiState(
                stage = PairingStage.RequestReady,
                requestTransport = event.transport,
            )
        }

        PairingEvent.RequestShared -> {
            require(state.stage == PairingStage.RequestReady) {
                "request can only be shared after creation"
            }
            state.copy(stage = PairingStage.AwaitingResponse)
        }

        is PairingEvent.ResponseImported -> {
            require(
                state.stage == PairingStage.RequestReady ||
                    state.stage == PairingStage.AwaitingResponse,
            ) { "response requires an active request" }
            require(event.verificationCode.isNotBlank()) {
                "verification code must not be blank"
            }
            state.copy(
                stage = PairingStage.ResponseReceived,
                responseTransport = event.transport,
                verificationCode = event.verificationCode,
            )
        }

        PairingEvent.BeginVerification -> {
            require(state.stage == PairingStage.ResponseReceived) {
                "verification requires an imported response"
            }
            state.copy(stage = PairingStage.Verification)
        }

        PairingEvent.ConfirmVerification -> {
            require(state.stage == PairingStage.Verification) {
                "confirmation requires verification stage"
            }
            state.copy(stage = PairingStage.Paired)
        }

        PairingEvent.Cancel -> state.copy(
            stage = PairingStage.Canceled,
            verificationCode = null,
        )

        is PairingEvent.Fail -> state.copy(
            stage = PairingStage.Error,
            verificationCode = null,
            diagnostic = event.diagnostic,
        )
    }
}
