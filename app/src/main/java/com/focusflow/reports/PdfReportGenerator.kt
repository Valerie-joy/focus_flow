package com.focusflow.reports

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReportRankingRow(val category: String, val score: Int)
data class ReportProgressRow(val dateLabel: String, val score: Int)

data class ReportContent(
    val userName: String,
    val generatedAtMs: Long,
    val ranking: List<ReportRankingRow>,
    val insightText: String?,
    val progressHistory: List<ReportProgressRow>,
    val learningStyle: String? = null,
    val learningStyleDescription: String? = null,
    val studyTechniques: List<String> = emptyList(),
    val recommendedContentFormats: List<String> = emptyList(),
    val weeklyGoals: List<String> = emptyList(),
    val successfulAssessments: Int = 0,
    val requiredAssessments: Int = 5,
    /** True when fewer than [requiredAssessments] successful assessments back this report. */
    val isProvisional: Boolean = false
)

/**
 * Builds a single-page (auto-extends to more pages if content overflows)
 * PDF assessment report using android.graphics.pdf.PdfDocument — the
 * exact API the spec names, rather than a third-party PDF library.
 * Deliberately plain typography/layout; this is a data export, not a
 * marketing document.
 */
class PdfReportGenerator(private val context: Context) {

    private val pageWidth = 595 // A4 at 72dpi
    private val pageHeight = 842

    fun generate(content: ReportContent): File {
        val document = PdfDocument()
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var y = 48f

        val titlePaint = Paint().apply { textSize = 22f; isFakeBoldText = true; color = 0xFF3B2F3A.toInt() }
        val sectionPaint = Paint().apply { textSize = 15f; isFakeBoldText = true; color = 0xFFCB6F9C.toInt() }
        val bodyPaint = Paint().apply { textSize = 12f; color = 0xFF3B2F3A.toInt() }
        val mutedPaint = Paint().apply { textSize = 11f; color = 0xFF876B82.toInt() }

        fun ensureSpace(needed: Float) {
            if (y + needed > pageHeight - 48f) {
                document.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = 48f
            }
        }

        canvas.drawText("FocusFlow Assessment Report", 48f, y, titlePaint)
        y += 24f
        val dateStr = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(content.generatedAtMs))
        canvas.drawText("${content.userName} — $dateStr", 48f, y, mutedPaint)
        y += 32f

        // Helpers for the repeated "heading then indented lines" blocks below.
        fun section(title: String) {
            ensureSpace(40f)
            canvas.drawText(title, 48f, y, sectionPaint)
            y += 20f
        }

        fun bullets(items: List<String>) {
            items.forEach { item ->
                wrapText("• $item", bodyPaint, pageWidth - 112).forEach { line ->
                    ensureSpace(16f)
                    canvas.drawText(line, 56f, y, bodyPaint)
                    y += 16f
                }
            }
            y += 12f
        }

        canvas.drawText(
            "Based on ${content.successfulAssessments} of ${content.requiredAssessments} " +
                "successful assessments",
            48f, y, mutedPaint
        )
        y += 18f

        if (content.isProvisional) {
            // The PID generates a profile only after five successful
            // assessments. A report exported before that is still useful, but
            // it must say so on its face rather than read as a settled result.
            wrapText(
                "Provisional: fewer than ${content.requiredAssessments} successful assessments " +
                    "have been completed, so this profile may change as more are added.",
                mutedPaint, pageWidth - 96
            ).forEach { line ->
                ensureSpace(14f)
                canvas.drawText(line, 48f, y, mutedPaint)
                y += 14f
            }
        }
        y += 18f

        section("Attention Summary")
        content.ranking.sortedByDescending { it.score }.forEach { row ->
            ensureSpace(18f)
            canvas.drawText(row.category, 56f, y, bodyPaint)
            canvas.drawText("${row.score}%", pageWidth - 88f, y, bodyPaint)
            y += 18f
        }
        y += 16f

        if (content.learningStyle != null) {
            section("Inferred Learning Style")
            ensureSpace(18f)
            canvas.drawText(content.learningStyle, 56f, y, bodyPaint)
            y += 18f
            content.learningStyleDescription?.let { description ->
                wrapText(description, mutedPaint, pageWidth - 112).forEach { line ->
                    ensureSpace(14f)
                    canvas.drawText(line, 56f, y, mutedPaint)
                    y += 14f
                }
            }
            y += 14f
        }

        if (content.studyTechniques.isNotEmpty()) {
            section("Suggested Study Techniques")
            bullets(content.studyTechniques)
        }

        if (content.recommendedContentFormats.isNotEmpty()) {
            section("Recommended Content Formats")
            bullets(content.recommendedContentFormats)
        }

        if (content.weeklyGoals.isNotEmpty()) {
            section("Weekly Goals")
            bullets(content.weeklyGoals)
        }

        if (content.insightText != null) {
            // Labelled "Summary", not "AI Insight": this text is generated by
            // a deterministic local template from the user's own scores, and
            // calling it AI would misrepresent where it comes from.
            section("Summary")
            wrapText(content.insightText, bodyPaint, pageWidth - 96).forEach { line ->
                ensureSpace(16f)
                canvas.drawText(line, 56f, y, bodyPaint)
                y += 16f
            }
            y += 16f
        }

        if (content.progressHistory.isNotEmpty()) {
            section("Progress History")
            content.progressHistory.forEach { row ->
                ensureSpace(18f)
                canvas.drawText(row.dateLabel, 56f, y, bodyPaint)
                canvas.drawText("${row.score}%", pageWidth - 88f, y, bodyPaint)
                y += 18f
            }
            y += 16f
        }

        // This report is designed to be shared — with a tutor, a teacher, a
        // clinician — so the limits of what it claims have to travel with it.
        // The PID is unambiguous that FocusFlow does not diagnose, screen for
        // or classify ADHD, and a shared PDF is exactly where that could
        // otherwise be misread.
        section("About this report")
        DISCLAIMER_LINES.forEach { paragraph ->
            wrapText(paragraph, mutedPaint, pageWidth - 96).forEach { line ->
                ensureSpace(14f)
                canvas.drawText(line, 48f, y, mutedPaint)
                y += 14f
            }
            y += 8f
        }

        document.finishPage(page)

        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(reportsDir, "focusflow-report-${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        words.forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) > maxWidth) {
                if (current.isNotEmpty()) lines += current.toString()
                current = StringBuilder(word)
            } else {
                current = StringBuilder(candidate)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }

    private companion object {
        val DISCLAIMER_LINES = listOf(
            "FocusFlow is an assistive self-reflection and study-habits tool. It does not " +
                "diagnose, screen for, or classify ADHD or any other condition, and it does not " +
                "prescribe or alter treatment. It is intended to inform, not replace, " +
                "professional judgement.",
            "Scores describe how the user's attention behaved while watching short clips from " +
                "nine content categories. The system measures gaze deflection and head " +
                "orientation; it is not calibrated per user and cannot determine where on the " +
                "screen someone was looking. Component weights are a stated design prior rather " +
                "than empirically calibrated values.",
            "Privacy: camera frames were processed on the device and discarded immediately. No " +
                "image, facial landmark or per-frame gaze data was stored or transmitted, and " +
                "none appears in this report — only derived numerical metrics."
        )
    }
}
