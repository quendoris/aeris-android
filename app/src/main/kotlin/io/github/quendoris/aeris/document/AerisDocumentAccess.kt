// SPDX-FileCopyrightText: 2026 quendoris
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.quendoris.aeris.document

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.FileNotFoundException

class AerisDocumentAccess(
    private val contentResolver: ContentResolver,
) {
    data class Metadata(
        val displayName: String?,
        val declaredSizeBytes: Long?,
    )

    fun metadata(uri: Uri): Metadata {
        var displayName: String? = null
        var sizeBytes: Long? = null
        runCatching {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    displayName = cursor.getString(nameIndex)
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    cursor.getLong(sizeIndex).takeIf { it >= 0L }?.let { sizeBytes = it }
                }
            }
        }
        return Metadata(displayName = displayName, declaredSizeBytes = sizeBytes)
    }

    /**
     * Proves that a SAF document can cross into native code as a seekable,
     * positioned-read descriptor without copying the document into app storage.
     *
     * This is deliberately not `.aeris` verification. The shared core must own
     * SQLite/application-id/schema verification after it gains an FD/VFS open
     * boundary.
     */
    fun probeReadOnly(uri: Uri, persistentAccess: Boolean): DocumentOpenState {
        val metadata = metadata(uri)
        try {
            val providerDescriptor = contentResolver.openFileDescriptor(uri, "r")
                ?: return DocumentOpenState.Failed(
                    uri = uri,
                    displayName = metadata.displayName,
                    diagnostic = "The document provider returned no file descriptor.",
                )

            providerDescriptor.use { providerFd ->
                val probe = try {
                    // Borrow only. Native dup()s immediately and owns/closes its
                    // copy; ContentResolver/PFD keeps ownership of this fd.
                    NativeDocumentBridge.probeBorrowedFd(providerFd.fd)
                } catch (error: Throwable) {
                    return DocumentOpenState.Failed(
                        uri = uri,
                        displayName = metadata.displayName,
                        diagnostic = "Native document bridge unavailable: ${error.message ?: error::class.java.simpleName}",
                    )
                }

                return when (probe.status) {
                    NativeDescriptorProbe.Status.Ready -> DocumentOpenState.DescriptorReady(
                        uri = uri,
                        displayName = metadata.displayName,
                        declaredSizeBytes = metadata.declaredSizeBytes,
                        nativeSizeBytes = probe.sizeBytes,
                        persistentAccess = persistentAccess,
                    )

                    NativeDescriptorProbe.Status.RandomAccessUnsupported -> DocumentOpenState.Unsupported(
                        uri = uri,
                        displayName = metadata.displayName,
                        diagnostic = "This document provider exposes the file as a stream, not random-access storage. AERIS will not copy a large project into cache silently.",
                    )

                    NativeDescriptorProbe.Status.DuplicateFailed,
                    NativeDescriptorProbe.Status.StatFailed,
                    NativeDescriptorProbe.Status.NativeFailure,
                    -> DocumentOpenState.Failed(
                        uri = uri,
                        displayName = metadata.displayName,
                        diagnostic = buildString {
                            append("Native descriptor probe failed: ")
                            append(probe.status.name)
                            probe.errno?.let { append(" (errno ").append(it).append(')') }
                        },
                    )
                }
            }
        } catch (error: SecurityException) {
            return DocumentOpenState.Failed(
                uri = uri,
                displayName = metadata.displayName,
                diagnostic = "AERIS no longer has permission to read this document.",
            )
        } catch (error: FileNotFoundException) {
            return DocumentOpenState.Failed(
                uri = uri,
                displayName = metadata.displayName,
                diagnostic = "The selected document is unavailable: ${error.message ?: "not found"}",
            )
        }
    }
}
