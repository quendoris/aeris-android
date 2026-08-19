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
        InvalidDescriptor,
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
 * The native function takes ownership of [fd] and closes it on every return
 * path. It does not parse SQLite or `.aeris`; format verification remains a
 * responsibility of the shared AERIS core once the core gains an FD/VFS open
 * API.
 */
object NativeDocumentBridge {
    init {
        System.loadLibrary("aeris_android_bridge")
    }

    fun probeOwnedFd(fd: Int): NativeDescriptorProbe {
        val result = nativeProbeOwnedFd(fd)
        if (result.size < 3) {
            return NativeDescriptorProbe(
                status = NativeDescriptorProbe.Status.NativeFailure,
                sizeBytes = null,
                errno = null,
            )
        }

        val status = when (result[0].toInt()) {
            0 -> NativeDescriptorProbe.Status.Ready
            1 -> NativeDescriptorProbe.Status.InvalidDescriptor
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

    private external fun nativeProbeOwnedFd(fd: Int): LongArray
}
