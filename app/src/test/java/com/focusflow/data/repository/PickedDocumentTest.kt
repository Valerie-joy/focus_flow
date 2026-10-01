package com.focusflow.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the accept/reject rules for a picked diagnosis document.
 *
 * [PickedDocument.resolve] needs a real [android.content.ContentResolver] and
 * is not exercised here. What is tested is the decision logic on top of it —
 * exposed as pure companion functions precisely so it can be checked on the JVM
 * — including the regression that made the onboarding upload reject every file
 * a user chose.
 */
class PickedDocumentTest {

    private fun accepts(mime: String?, name: String = "diagnosis.pdf") =
        PickedDocument.isAcceptedType(mime, name)

    @Test
    fun `accepted mime types pass regardless of the display name`() {
        // The regression: a content URI's provider name is an opaque document
        // id with no extension, so validation must not depend on it.
        assertTrue(accepts("application/pdf", name = "msf:42"))
        assertTrue(accepts("image/png", name = "document"))
        assertTrue(accepts("image/jpeg", name = "1000000123"))
    }

    @Test
    fun `unsupported mime types are rejected even with a convincing name`() {
        // A .pdf name on a Word document must not get through; the provider's
        // reported type is authoritative.
        assertFalse(
            accepts(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                name = "diagnosis.pdf"
            )
        )
        assertFalse(accepts("image/gif", name = "scan.png"))
        assertFalse(accepts("text/plain", name = "notes.txt"))
        assertFalse(accepts("application/zip", name = "archive.pdf"))
    }

    /**
     * Some cloud providers report no type at all. The extension is then the
     * only signal left, so it is used — but only in that case.
     */
    @Test
    fun `a missing mime type falls back to the file extension`() {
        assertTrue(accepts(null, name = "diagnosis.pdf"))
        assertTrue(accepts(null, name = "SCAN.JPEG"))
        assertTrue(accepts(null, name = "photo.jpg"))
        assertFalse(accepts(null, name = "diagnosis.docx"))
        assertFalse(accepts(null, name = "noextension"))
        assertFalse(accepts(null, name = ""))
    }

    @Test
    fun `size limit is enforced at the boundary`() {
        assertFalse(PickedDocument.isTooLarge(PickedDocument.MAX_UPLOAD_BYTES))
        assertTrue(PickedDocument.isTooLarge(PickedDocument.MAX_UPLOAD_BYTES + 1))
        assertFalse(PickedDocument.isTooLarge(1024L))
    }

    @Test
    fun `an unreported size is not treated as too large`() {
        // A provider that omits SIZE must not block the upload; the repository
        // fails later if the file really is unreadable.
        assertFalse(PickedDocument.isTooLarge(null))
    }

    @Test
    fun `the picker mime list matches what validation accepts`() {
        // If these drift, the picker offers files the screen then rejects —
        // which is the shape of the bug this class was written to fix.
        PickedDocument.ACCEPTED_MIME_TYPES.forEach { mime ->
            assertTrue(
                "picker offers $mime but validation rejects it",
                accepts(mime, name = "opaque-provider-id")
            )
        }
    }
}
