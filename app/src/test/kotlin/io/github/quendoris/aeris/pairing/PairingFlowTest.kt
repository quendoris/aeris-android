// SPDX-FileCopyrightText: 2026 quendoris
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.quendoris.aeris.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingFlowTest {
    @Test
    fun offline_export_round_trip_reaches_paired_only_after_verification() {
        var state = PairingUiState()
        state = PairingReducer.reduce(
            state,
            PairingEvent.RequestCreated(PairingTransport.ExportedQrImage),
        )
        assertEquals(PairingStage.RequestReady, state.stage)
        assertEquals(PairingTransport.ExportedQrImage, state.requestTransport)

        state = PairingReducer.reduce(state, PairingEvent.RequestShared)
        assertEquals(PairingStage.AwaitingResponse, state.stage)

        state = PairingReducer.reduce(
            state,
            PairingEvent.ResponseImported(
                transport = PairingTransport.CapsuleFile,
                verificationCode = "482 731",
            ),
        )
        assertEquals(PairingStage.ResponseReceived, state.stage)
        assertEquals("482 731", state.verificationCode)
        assertFalse(state.terminal)

        state = PairingReducer.reduce(state, PairingEvent.BeginVerification)
        assertEquals(PairingStage.Verification, state.stage)

        state = PairingReducer.reduce(state, PairingEvent.ConfirmVerification)
        assertEquals(PairingStage.Paired, state.stage)
        assertTrue(state.terminal)
    }

    @Test(expected = IllegalArgumentException::class)
    fun response_without_active_request_is_rejected() {
        PairingReducer.reduce(
            PairingUiState(),
            PairingEvent.ResponseImported(PairingTransport.CameraQr, "123 456"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun blank_verification_code_is_rejected() {
        val request = PairingReducer.reduce(
            PairingUiState(),
            PairingEvent.RequestCreated(PairingTransport.CameraQr),
        )
        PairingReducer.reduce(
            request,
            PairingEvent.ResponseImported(PairingTransport.CameraQr, "  "),
        )
    }

    @Test
    fun cancellation_discards_verification_code() {
        var state = PairingReducer.reduce(
            PairingUiState(),
            PairingEvent.RequestCreated(PairingTransport.CameraQr),
        )
        state = PairingReducer.reduce(
            state,
            PairingEvent.ResponseImported(PairingTransport.CameraQr, "999 001"),
        )
        state = PairingReducer.reduce(state, PairingEvent.Cancel)

        assertEquals(PairingStage.Canceled, state.stage)
        assertNull(state.verificationCode)
        assertTrue(state.terminal)
    }

    @Test
    fun terminal_state_can_start_a_new_request() {
        val canceled = PairingReducer.reduce(PairingUiState(), PairingEvent.Cancel)
        val restarted = PairingReducer.reduce(
            canceled,
            PairingEvent.RequestCreated(PairingTransport.CapsuleFile),
        )

        assertEquals(PairingStage.RequestReady, restarted.stage)
        assertEquals(PairingTransport.CapsuleFile, restarted.requestTransport)
        assertFalse(restarted.terminal)
    }

    @Test
    fun user_copy_explicitly_mentions_export_and_key_boundary() {
        assertTrue(PairingCopy.ExportHint.contains("export", ignoreCase = true))
        assertTrue(PairingCopy.ExportHint.contains("offline", ignoreCase = true))
        assertTrue(PairingCopy.RequestSafety.contains("not your vault key", ignoreCase = true))
        assertTrue(PairingCopy.RequestSafety.contains("no private map data", ignoreCase = true))
    }
}
