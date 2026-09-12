package com.focusflow.services

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * "Email: JavaMail Intent" per the spec — interpreted as launching the
 * user's own mail app via an ACTION_SEND intent with the PDF attached,
 * NOT an embedded SMTP/JavaMail client. That's the correct approach for
 * an Android app: it never touches the user's email credentials, and lets
 * them pick whichever mail app they actually use.
 *
 * Requires a FileProvider entry in AndroidManifest.xml — see the README
 * for the exact manifest + res/xml/file_paths.xml needed; a placeholder
 * file_paths.xml is included in this package for convenience.
 */
class EmailReportService(private val context: Context) {

    fun shareViaEmail(file: File, subject: String, body: String) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Send report via email"))
    }

    /**
     * Plain mailto — no attachment, no FileProvider needed. Used by
     * Profile's "Contact support," which isn't sending a report.
     */
    fun contactSupport(subject: String = "FocusFlow support", body: String = "") {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = android.net.Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("support@focusflow.app"))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        context.startActivity(Intent.createChooser(intent, "Contact support"))
    }
}
