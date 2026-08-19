# AERIS Android

Android application for AERIS (`.aeris`) maps.

This repository is the Android-specific frontend and platform integration layer. The normative `.aeris` format, canonical geographic/source semantics, projection mathematics, storage/verifier implementation, private-vault ciphertext semantics, and cross-platform conformance fixtures belong to the platform-neutral AERIS core.

## Architecture

Dependency direction is one-way:

```text
AERIS core  <-  aeris-android
```

`aeris-android` must not fork, reinterpret, or independently reimplement the `.aeris` format. Android-specific code may provide file-descriptor/storage integration, caching, rendering, touch interaction, lifecycle handling, Android Keystore integration, and JNI/NDK bindings, but canonical format semantics remain in the shared core.

There is intentionally no `aeris-mobile` repository. Android is a concrete supported platform; a future iOS implementation, if it can be built and tested properly, should be a separate `aeris-ios` consumer of the same core.

## Initial target: reader first

The first Android milestone remains deliberately read-only with respect to `.aeris`:

1. Open a desktop-created `.aeris` through Android document storage APIs.
2. Verify the same format/schema/source invariants as the desktop/core verifier.
3. Render the same canonical Earth as Globe and Flat representations.
4. Navigate only the spatial/LOD coverage actually present in the file.
5. Perform offline address/POI lookup when those canonical channels are present.
6. Read encrypted private annotations only after the shared core private-vault contract exists.
7. Prove semantic equality against the shared conformance fixtures.

Only after cross-platform reading is proven should Android gain project/private-annotation mutation features.

## Direct Android document transport

The current Android work proves the storage boundary before core parsing is connected:

- `Open` uses Android `ACTION_OPEN_DOCUMENT` / the system Storage Access Framework picker;
- AERIS requests persistable read access and remembers only the active document URI, not a copied project;
- the selected document is opened read-only through `ContentResolver.openFileDescriptor()`;
- Java retains ownership of the provider `ParcelFileDescriptor` while the JNI bridge borrows its fd;
- native code immediately `dup()`s the borrowed fd, owns only the duplicate, and closes that duplicate on every return path;
- a positioned `pread()` proves that the provider exposes random-access storage suitable for a future SQLite VFS;
- stream/pipe providers fail explicitly instead of causing a silent whole-file copy into app cache;
- asynchronous open results are generation-gated so a slow old provider cannot replace a newer user selection;
- persistable grants are bounded to the active project rather than accumulated indefinitely.

This transport proof does **not** parse SQLite or claim that the selected document is a valid `.aeris`. The current core `ProjectStore` is still path/read-write oriented. Core issue #29 owns the read-only random-access/VFS project-open boundary that will connect this descriptor transport to the canonical verifier.

## Map-first shell

The first application shell is intentionally being built before native reader integration so UI/render responsibilities are fixed without duplicating core semantics.

Current work-in-progress architecture:

- Jetpack Compose owns application chrome, sheets and forms.
- A dedicated `SurfaceView` owns high-frequency map gestures and rendering.
- The render surface performs no file I/O and does not fabricate geographic data while no verified `.aeris` is open.
- Pan/zoom input collapses pending render requests so camera motion cannot build a stale-frame backlog.
- Search, layers, Globe/Flat, offline coverage and private annotation tools are contextual map overlays rather than permanent navigation tabs.

The placeholder surface is not an AERIS map renderer. Its purpose is to establish a responsive platform rendering boundary before the C++ core is connected through JNI/NDK.

## Private annotations

Personal pins, labels, notes, tracks, areas and related data are intended to be an encrypted private overlay defined by the core format, not Android-only state.

Normal use should not require an AERIS-specific password. Authorized-device key material will be protected using Android platform key facilities, while cross-device vault semantics and ciphertext remain shared with desktop/core.

## Cross-device pairing

AERIS Private Vault pairing is intended to be camera-optional and offline-capable.

The UI must explicitly tell users that a pairing QR can be scanned directly or exported and transferred to another device such as a PC. Desktop and Android consume the same platform-neutral AERIS Pairing Capsule representation.

The planned flow uses a request/response handshake:

1. A new device creates a one-time pairing request.
2. The request can be shown as QR or exported as an image/file for offline transfer.
3. An already authorized desktop/phone imports or scans that exact request and approves it.
4. The authorized device returns an authenticated response, again as QR or exportable image/file.
5. Both devices display the same short verification code before final approval.

The QR/request is not the vault key and must contain no private map plaintext. Production capsule encoding, replay/expiry rules and authenticated key transfer belong to the shared core security contract; Android only provides transport and confirmation UX.

The current branch contains the pairing UI state machine and export-aware Compose sheet, but no production capsule codec or cryptographic transfer implementation yet.

## Format 1.0 gate

AERIS Format 1.0 must not be frozen merely because the desktop implementation is stable. At minimum, desktop-created conformance fixtures must be read and verified independently on Android with identical semantic results. When Android writing exists, the inverse direction must also pass.

## Distribution direction

The intended distribution path is an open-source Android application suitable for F-Droid. Packaging, reproducible-build configuration, and Android-specific dependency policy belong here; they must not leak into the format/core repository.

## Status

Map-first Android shell, render-surface boundary, label placement baseline, pairing UX state model, and direct SAF→JNI random-access descriptor transport are under active development. Canonical native `.aeris` verification still awaits the core read-only VFS boundary.

License: AGPL-3.0-only.
