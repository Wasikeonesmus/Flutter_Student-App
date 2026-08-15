package com.examsystem.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.examsystem.app.data.models.Attempt
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pro / Institute: PDF pass certificates (one page per student who met passing marks).
 */
object CertificatePdfExporter {

    fun exportPassCertificates(
        context: Context,
        examTitle: String,
        conductedBy: String,
        totalMarks: Int,
        passingMarks: Int,
        attempts: List<Attempt>
    ) {
        val passed = attempts
            .filter { it.totalScore >= passingMarks }
            .sortedByDescending { it.totalScore }

        if (passed.isEmpty()) {
            Toast.makeText(
                context,
                "No students reached passing marks ($passingMarks / $totalMarks).",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {
            val document = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            val dateStr = SimpleDateFormat("MMMM d, yyyy", Locale.US).format(Date())

            passed.forEachIndexed { index, attempt ->
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas

                val borderPaint = Paint().apply {
                    color = Color.rgb(178, 34, 34)
                    style = Paint.Style.STROKE
                    strokeWidth = 4f
                }
                canvas.drawRect(40f, 40f, pageWidth - 40f, pageHeight - 40f, borderPaint)

                val orgPaint = Paint().apply {
                    color = Color.DKGRAY
                    textSize = 14f
                    typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
                    textAlign = Paint.Align.CENTER
                }
                val titlePaint = Paint().apply {
                    color = Color.BLACK
                    textSize = 26f
                    typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                val namePaint = Paint().apply {
                    color = Color.rgb(178, 34, 34)
                    textSize = 32f
                    typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                val bodyPaint = Paint().apply {
                    color = Color.BLACK
                    textSize = 16f
                    typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
                    textAlign = Paint.Align.CENTER
                }
                val smallPaint = Paint().apply {
                    color = Color.DKGRAY
                    textSize = 12f
                    typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                    textAlign = Paint.Align.CENTER
                }

                var y = 120f
                canvas.drawText(conductedBy.ifBlank { "Students Welfare Foundation" }, pageWidth / 2f, y, orgPaint)
                y += 50f
                canvas.drawText("Certificate of Achievement", pageWidth / 2f, y, titlePaint)
                y += 55f
                canvas.drawText("This is to certify that", pageWidth / 2f, y, bodyPaint)
                y += 45f
                canvas.drawText(attempt.studentName.ifBlank { "Student" }, pageWidth / 2f, y, namePaint)
                y += 40f
                canvas.drawText("has successfully completed", pageWidth / 2f, y, bodyPaint)
                y += 35f
                val examPaint = Paint(titlePaint).apply { textSize = 20f }
                canvas.drawText(examTitle, pageWidth / 2f, y, examPaint)
                y += 45f
                val pct = if (totalMarks > 0) (attempt.totalScore * 100 / totalMarks) else 0
                canvas.drawText(
                    "Score: ${attempt.totalScore} / $totalMarks ($pct%) · Passing: $passingMarks",
                    pageWidth / 2f,
                    y,
                    bodyPaint
                )
                y += 35f
                if (attempt.district.isNotBlank()) {
                    canvas.drawText("District: ${attempt.district}", pageWidth / 2f, y, bodyPaint)
                    y += 30f
                }
                canvas.drawText("Date: $dateStr", pageWidth / 2f, y, smallPaint)
                y = pageHeight - 80f
                canvas.drawText("ExamPro · Secure Examination Platform", pageWidth / 2f, y, smallPaint)

                document.finishPage(page)
            }

            val fileName = "Pass_Certificates_${System.currentTimeMillis()}.pdf"
            val saved = savePdfToDownloads(context, document, fileName)
            document.close()

            if (saved) {
                Toast.makeText(
                    context,
                    "Saved $fileName (${passed.size} certificate(s)) to Downloads",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(context, "Failed to save certificates PDF.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Certificate error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun savePdfToDownloads(context: Context, document: PdfDocument, fileName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { document.writeTo(it) }
                    return true
                }
                false
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                FileOutputStream(File(downloadsDir, fileName)).use { document.writeTo(it) }
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
