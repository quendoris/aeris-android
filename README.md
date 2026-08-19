# AERIS Android

Android application for AERIS (`.aeris`) maps.

This repository is the Android-specific frontend and platform integration layer. The normative `.aeris` format, canonical geographic/source semantics, projection mathematics, storage/verifier implementation, and cross-platform conformance fixtures belong to the platform-neutral AERIS core.

## Architecture

Dependency direction is one-way:

```text
AERIS core  <-  aeris-android
```

`aeris-android` must not fork, reinterpret, or independently reimplement the `.aeris` format. Android-specific code may provide file-descriptor/storage integration, caching, rendering, touch interaction, lifecycle handling, and JNI/NDK bindings, but canonical format semantics remain in the shared core.

There is intentionally no `aeris-mobile` repository. Android is a concrete supported platform; a future iOS implementation, if it can be built and tested properly, should be a separate `aeris-ios` consumer of the same core.

## Initial target: reader first

The first Android milestone is deliberately read-only:

1. Open a desktop-created `.aeris` through Android document storage APIs.
2. Verify the same format/schema/source invariants as the desktop/core verifier.
3. Render the same canonical Earth as Globe and Flat representations.
4. Navigate only the spatial/LOD coverage actually present in the file.
5. Perform offline address/POI lookup when those canonical channels are present.
6. Prove semantic equality against the shared conformance fixtures.

Only after cross-platform reading is proven should Android gain project mutation/writer features.

## Format 1.0 gate

AERIS Format 1.0 must not be frozen merely because the desktop implementation is stable. At minimum, desktop-created conformance fixtures must be read and verified independently on Android with identical semantic results. When Android writing exists, the inverse direction must also pass.

## Distribution direction

The intended distribution path is an open-source Android application suitable for F-Droid. Packaging, reproducible-build configuration, and Android-specific dependency policy belong here; they must not leak into the format/core repository.

## Status

Repository bootstrap only. No Android reader implementation is claimed yet.

License: AGPL-3.0-only.
