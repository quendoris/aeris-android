// SPDX-FileCopyrightText: 2026 quendoris
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.quendoris.aeris.document

import android.net.Uri

sealed interface DocumentOpenState {
    data object NoProject : DocumentOpenState
    data class Opening(val displayName: String?) : DocumentOpenState
    data class DescriptorReady(
        val uri: Uri,
        val displayName: String?,
        val declaredSizeBytes: Long?,
        val nativeSizeBytes: Long?,
        val persistentAccess: Boolean,
    ) : DocumentOpenState
    data class Unsupported(
        val uri: Uri?,
        val displayName: String?,
        val diagnostic: String,
    ) : DocumentOpenState
    data class Failed(
        val uri: Uri?,
        val displayName: String?,
        val diagnostic: String,
    ) : DocumentOpenState
}
