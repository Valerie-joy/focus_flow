package com.focusflow.data.repository

import android.content.Context
import android.net.Uri
import com.focusflow.data.remote.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage

private const val BUCKET = "adhd-documents"

/**
 * Uploads the ADHD-diagnosis document a user selects in Phase 2's "I have a
 * diagnosis" branch to Supabase Storage, under `{user_id}/{filename}` — see
 * SUPABASE_SETUP.md for the bucket + RLS policy this depends on. Requires an
 * already-authenticated session (this screen only appears after sign-in/
 * sign-up in the real onboarding flow).
 */
class AdhdDocumentRepository(private val context: Context) {
    private val client = SupabaseClientProvider.getInstance(context)

    suspend fun uploadDocument(uri: Uri, fileName: String): Result<Unit> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id
            ?: error("You need to be signed in to upload a document.")
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Couldn't read the selected file.")

        client.storage.from(BUCKET).upload("$userId/$fileName", bytes) {
            upsert = true
        }
        Unit
    }
}
