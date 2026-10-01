package com.focusflow.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/**
 * What a picked document actually is, resolved from the content provider
 * rather than guessed from its URI.
 *
 * The onboarding upload used `uri.lastPathSegment` as the file name and then
 * checked its extension against a set of allowed ones. For a `content://` URI
 * — which is the only kind the document picker returns — the last path segment
 * is a provider-internal document id such as `msf:42`, `document/1000000123`
 * or a raw hash. It has no extension. So the extension check failed for
 * *every* file, and a user selecting a perfectly good PDF was told:
 *
 * > That file doesn't look like a PDF, PNG, or JPEG. Try another file.
 *
 * …with no way past it. Trying another file produced the same message, because
 * the file was never the problem.
 *
 * Both fields here come from the provider: [displayName] via
 * [OpenableColumns.DISPLAY_NAME] and [mimeType] via
 * [android.content.ContentResolver.getType]. The MIME type is what gets
 * validated, because it is the authoritative answer and does not depend on a
 * user having kept a sensible file extension.
 */
data class PickedDocument(
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?
) {
    val isAcceptedType: Boolean get() = isAcceptedType(mimeType, displayName)

    /** True when the file is larger than the upload limit. */
    val isTooLarge: Boolean get() = isTooLarge(sizeBytes)

    companion object {
        /** Passed straight to the picker, so unsupported files aren't offered. */
        val ACCEPTED_MIME_TYPES = arrayOf("application/pdf", "image/png", "image/jpeg")

        private val ACCEPTED_EXTENSIONS = setOf("pdf", "png", "jpg", "jpeg")

        /**
         * 10 MB. A diagnosis letter is a page or two; anything much larger is a
         * scan at a needless resolution, and reading it into a ByteArray for
         * upload would risk an OOM on a low-memory device.
         */
        const val MAX_UPLOAD_BYTES = 10L * 1024 * 1024

        /**
         * The accept rule, as a pure function.
         *
         * Kept separate from the data class so it can be unit tested on the JVM:
         * constructing a [PickedDocument] needs a [Uri], which is a
         * throw-on-use stub outside an instrumented test, and mocking the
         * framework just to check a MIME comparison would be a dependency added
         * for no benefit.
         */
        fun isAcceptedType(mimeType: String?, displayName: String): Boolean =
            mimeType in ACCEPTED_MIME_TYPES ||
                // Fall back to the extension only when the provider declines to
                // report a type at all, which some cloud providers do.
                (mimeType == null &&
                    displayName.substringAfterLast('.', "").lowercase() in ACCEPTED_EXTENSIONS)

        fun isTooLarge(sizeBytes: Long?): Boolean = (sizeBytes ?: 0L) > MAX_UPLOAD_BYTES

        /**
         * Resolves a picked URI. Returns null when the provider cannot be
         * queried at all, which happens if the URI permission has already
         * lapsed — a real case when the app is backgrounded during picking.
         */
        fun resolve(context: Context, uri: Uri): PickedDocument? {
            val resolver = context.contentResolver
            val mimeType = runCatching { resolver.getType(uri) }.getOrNull()

            var name: String? = null
            var size: Long? = null
            runCatching {
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                            name = cursor.getString(nameIndex)
                        }
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                            size = cursor.getLong(sizeIndex)
                        }
                    }
                }
            }.getOrNull() ?: if (mimeType == null) return null else Unit

            return PickedDocument(
                uri = uri,
                // A provider that reports no name still needs something to show
                // and to upload under.
                displayName = name ?: "document",
                mimeType = mimeType,
                sizeBytes = size
            )
        }
    }
}
