// SPDX-FileCopyrightText: 2026 quendoris
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.quendoris.aeris

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import io.github.quendoris.aeris.document.AerisDocumentAccess
import io.github.quendoris.aeris.document.DocumentOpenState
import io.github.quendoris.aeris.document.DocumentSessionStore
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val documentState = mutableStateOf<DocumentOpenState>(DocumentOpenState.NoProject)
    private val documentExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "aeris-document-open").apply { isDaemon = true }
    }

    private lateinit var documentAccess: AerisDocumentAccess
    private lateinit var documentSession: DocumentSessionStore

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) openSelectedDocument(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        documentAccess = AerisDocumentAccess(contentResolver)
        documentSession = DocumentSessionStore(this)

        setContent {
            AerisTheme {
                AerisMapShell(
                    documentState = documentState.value,
                    onOpenDocument = { openDocument.launch(arrayOf("*/*")) },
                )
            }
        }

        documentSession.restore()?.let(::restorePersistedDocument)
    }

    override fun onDestroy() {
        documentExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun openSelectedDocument(uri: Uri) {
        val persistentAccess = runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            true
        }.getOrDefault(false)

        probeDocument(uri = uri, persistentAccess = persistentAccess, rememberOnSuccess = true)
    }

    private fun restorePersistedDocument(uri: Uri) {
        val persistentAccess = contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == uri && permission.isReadPermission
        }
        if (!persistentAccess) {
            documentSession.forget()
            documentState.value = DocumentOpenState.Failed(
                uri = uri,
                displayName = null,
                diagnostic = "Saved project access is no longer authorized. Open the .aeris file again.",
            )
            return
        }
        probeDocument(uri = uri, persistentAccess = true, rememberOnSuccess = false)
    }

    private fun probeDocument(
        uri: Uri,
        persistentAccess: Boolean,
        rememberOnSuccess: Boolean,
    ) {
        documentState.value = DocumentOpenState.Opening(displayName = null)
        documentExecutor.execute {
            val result = documentAccess.probeReadOnly(uri, persistentAccess)
            if (rememberOnSuccess && result is DocumentOpenState.DescriptorReady) {
                documentSession.remember(uri)
            }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) documentState.value = result
            }
        }
    }
}
