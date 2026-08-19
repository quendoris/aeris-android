// SPDX-FileCopyrightText: 2026 quendoris
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.quendoris.aeris.document

data class NativeDescriptorProbe(
    val status: Status,
    val sizeBytes: Long?,
    val errno: Int?,
) {
    enum class Status {
        Ready,
        DuplicateFailed,
        StatFailed,
        RandomAccessUnsupported,
        NativeFailure,
    }

    val ready: Boolean
        get() = status == Status.Ready
}

/**
 * JNI transport boundary only.
 *
 * [probeBorrowedFd] never takes ownership of the Android/Java descriptor. The
 * native function duplicates it immediately and owns/closes only that duplicate.
 * It does not parse SQLite or `.aeris`; format verification remains a shared
 * AERIS-core responsibility once the core gains an FD/VFS open API.
 */
object NativeDocumentBridge {
    init {
        System.loadLibrary("aeris_android_bridge")
    }

    fun probeBorrowedFd(fd: Int): NativeDescriptorProbe {
        val result = nativeProbeBorrowedFd(fd)
        if (result.size < 3) {
            return NativeDescriptorProbe(
                status = NativeDescriptorProbe.Status.NativeFailure,
                sizeBytes = null,
                errno = null,
            )
        }

        val status = when (result[0].toInt()) {
            0 -> NativeDescriptorProbe.Status.Ready
            1 -> NativeDescriptorProbe.Status.DuplicateFailed
            2 -> NativeDescriptorProbe.Status.StatFailed
            3 -> NativeDescriptorProbe.Status.RandomAccessUnsupported
            else -> NativeDescriptorProbe.Status.NativeFailure
        }
        return NativeDescriptorProbe(
            status = status,
            sizeBytes = result[1].takeIf { it >= 0L },
            errno = result[2].toInt().takeIf { it != 0 },
        )
    }

    private external fun nativeProbeBorrowedFd(fd: Int): LongArray
}
