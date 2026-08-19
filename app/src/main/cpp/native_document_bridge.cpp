// SPDX-FileCopyrightText: 2026 quendoris
// SPDX-License-Identifier: AGPL-3.0-only

#include <jni.h>

#include <cerrno>
#include <cstdint>
#include <sys/stat.h>
#include <unistd.h>

namespace {

class OwnedFd final {
public:
    explicit OwnedFd(const int fd) noexcept : fd_(fd) {}
    ~OwnedFd() {
        if (fd_ >= 0) {
            (void)::close(fd_);
        }
    }

    OwnedFd(const OwnedFd&) = delete;
    OwnedFd& operator=(const OwnedFd&) = delete;

    [[nodiscard]] int get() const noexcept { return fd_; }

private:
    int fd_{-1};
};

[[nodiscard]] jlongArray make_result(
    JNIEnv* env,
    const std::int64_t status,
    const std::int64_t size_bytes,
    const std::int64_t error_number
) {
    const jlong values[3]{
        static_cast<jlong>(status),
        static_cast<jlong>(size_bytes),
        static_cast<jlong>(error_number),
    };
    jlongArray result = env->NewLongArray(3);
    if (result == nullptr) return nullptr;
    env->SetLongArrayRegion(result, 0, 3, values);
    return result;
}

}  // namespace

extern "C" JNIEXPORT jlongArray JNICALL
Java_io_github_quendoris_aeris_document_NativeDocumentBridge_nativeProbeOwnedFd(
    JNIEnv* env,
    jobject,
    const jint raw_fd
) {
    // Ownership is transferred by Kotlin only after duplicating the provider's
    // ParcelFileDescriptor. Closing this fd therefore never closes the
    // provider-owned descriptor directly.
    OwnedFd fd(raw_fd);
    if (fd.get() < 0) return make_result(env, 1, -1, EBADF);

    struct stat metadata {};
    if (::fstat(fd.get(), &metadata) != 0) {
        return make_result(env, 2, -1, errno);
    }

    // SQLite-style access requires positioned/random reads. A SAF provider is
    // allowed to satisfy read-only opens with a pipe/socket, so prove that the
    // descriptor can service a positioned read before handing this transport
    // shape to the future core FD/VFS adapter. pread() does not mutate the
    // shared file offset inherited through dup().
    unsigned char byte = 0;
    errno = 0;
    const ssize_t read_count = ::pread(fd.get(), &byte, 1, 0);
    if (read_count < 0) {
        return make_result(env, 3, static_cast<std::int64_t>(metadata.st_size), errno);
    }

    return make_result(env, 0, static_cast<std::int64_t>(metadata.st_size), 0);
}
