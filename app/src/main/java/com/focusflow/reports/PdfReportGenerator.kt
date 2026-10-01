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
        var y = CONTENT_TOP

        // Colors track the app's palette (see ui/theme/Color.kt) so an
        // exported report reads as the same product as the screen it came
        // from. Ink is near-black rather than the app's on-surface color:
        // this is printed on white paper, where the screen's slightly lifted
        // text color loses contrast.
        val titlePaint = Paint().apply { textSize = 21f; isFakeBoldText = true; color = INK }
        val sectionPaint = Paint().apply { textSize = 13f; isFakeBoldText = true; color = ACCENT }
        val bodyPaint = Paint().apply { textSize = 11.5f; color = INK }
        val mutedPaint = Paint().apply { textSize = 10f; color = MUTED_INK }
        val rulePaint = Paint().apply { strokeWidth = 0.7f; color = RULE }
        val footerPaint = Paint().apply { textSize = 8.5f; color = MUTED_INK }

        val dateStr = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
            .format(Date(content.generatedAtMs))

        /**
         * Draws the running footer, then closes the page. Every page carries
         * the subject and date, because a report shared as loose pages — which
         * is what printing it produces — otherwise has pages that identify
         * nobody.
         */
        fun finishPageWithFooter() {
            canvas.drawLine(MARGIN, FOOTER_Y - 12f, pageWidth - MARGIN, FOOTER_Y - 12f, rulePaint)
            canvas.drawText("FocusFlow — ${content.userName}, $dateStr", MARGIN, FOOTER_Y, footerPaint)
            val pageLabel = "Page $pageNumber"
            canvas.drawText(
                pageLabel,
                pageWidth - MARGIN - footerPaint.measureText(pageLabel),
                FOOTER_Y,
                footerPaint
            )
            document.finishPage(page)
        }

        fun ensureSpace(needed: Float) {
            if (y + needed > CONTENT_BOTTOM) {
                finishPageWithFooter()
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = CONTENT_TOP
            }
        }

        canvas.drawText("FocusFlow Assessment Report", MARGIN, y, titlePaint)
        y += 20f
        canvas.drawText("${content.userName} — $dateStr", MARGIN, y, mutedPaint)
        y += 10f
        canvas.drawLine(MARGIN, y, pageWidth - MARGIN, y, rulePaint)
        y += 24f

        // Helpers for the repeated "heading then indented lines" blocks below.
        fun section(title: String) {
            // Reserve the heading *and* a first line of content, so a heading
            // can never be orphaned at the foot of a page with its section
            // starting overleaf.
            ensureSpace(52f)
            y += 6f
            canvas.drawText(title.uppercase(Locale.getDefault()), MARGIN, y, sectionPaint)
            y += 6f
            canvas.drawLine(MARGIN, y, pageWidth - MARGIN, y, rulePaint)
            y += 16f
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
            canvas.drawText(row.category, INDENT, y, bodyPaint)
            drawRightAligned(canvas, "${row.score}%", pageWidth - MARGIN, y, bodyPaint)
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
                canvas.drawText(row.dateLabel, INDENT, y, bodyPaint)
                drawRightAligned(canvas, "${row.score}%", pageWidth - MARGIN, y, bodyPaint)
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

        finishPageWithFooter()

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

    /** Right-aligns a value against [right] so score columns line up. */
    private fun drawRightAligned(
        canvas: android.graphics.Canvas,
        text: String,
        right: Float,
        y: Float,
        paint: Paint
    ) {
        canvas.drawText(text, right - paint.measureText(text), y, paint)
    }

    private companion object {
        /** Page geometry, in points. A4 at 72dpi. */
        const val MARGIN = 48f
        const val INDENT = 58f
        const val CONTENT_TOP = 56f

        /** Content stops above the footer rule rather than running into it. */
        const val CONTENT_BOTTOM = 842f - 66f
        const val FOOTER_Y = 842f - 34f

        /** Print inks — see the palette note in `generate`. */
        const val INK = 0xFF16201F.toInt()
        const val MUTED_INK = 0xFF5A6A69.toInt()
        const val ACCENT = 0xFF1F6F6B.toInt()
        const val RULE = 0xFFC9D4D4.toInt()

        val DISCLAIMER_LINES = listOf(
            "FocusFlow is an assistive self-reflection and study-habits tool. It does not " +
                "diagnose, screen for, or classify ADHD or any other condition, and it does not " +
                "prescribe or alter treatment. It is intended to inform, not replace, " +
                "professional judgement.",
            // Worded to hold in both gaze modes: the point-of-regard estimator
            // attaches only when the user's calibration fit is good enough, and
            // the app otherwise runs on gaze deflection and head pose alone.
            "Scores describe how the user's attention behaved while watching short clips from " +
                "nine content categories. Attention is estimated from the front camera using " +
                "eye-gaze deflection and head orientation and, where the user completed a " +
                "short on-device gaze calibration, an approximate estimate of screen position. " +
                "This is not a clinical-grade eye tracker: screen-position estimates are " +
                "approximate (centimetre-level at best), and component weights are a stated " +
                "design prior rather than empirically calibrated values.",
            "Privacy: camera frames were processed on the device and discarded immediately. No " +
                "image, facial landmark or per-frame gaze data was stored or transmitted, and " +
                "none appears in this report — only derived numerical metrics."
        )
    }
}
