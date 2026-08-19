// SPDX-FileCopyrightText: 2026 quendoris
// SPDX-License-Identifier: AGPL-3.0-only

package io.github.quendoris.aeris.document

import android.content.Context
import android.net.Uri

class DocumentSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        "aeris_document_session",
        Context.MODE_PRIVATE,
    )

    fun remember(uri: Uri) {
        preferences.edit().putString(KEY_URI, uri.toString()).apply()
    }

    fun restore(): Uri? = preferences.getString(KEY_URI, null)?.let(Uri::parse)

    fun forget() {
        preferences.edit().remove(KEY_URI).apply()
    }

    private companion object {
        const val KEY_URI = "active_project_uri"
    }
}
