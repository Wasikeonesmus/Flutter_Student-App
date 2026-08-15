package com.examsystem.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import com.examsystem.app.data.models.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

object PdfGenerator {

        // Replaced local stripLatex with com.examsystem.app.util.MathUtils.stripLatex

    suspend fun generateTestPdf(context: Context, test: Test): Boolean = withContext(Dispatchers.IO) {
        try {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size: 595 x 842 points
            var page = document.startPage(pageInfo)
            var canvas = page.canvas

            val titlePaint = TextPaint().apply {
                color = Color.BLACK
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }

            val subtitlePaint = TextPaint().apply {
                color = Color.DKGRAY
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
            }

            val questionPaint = TextPaint().apply {
                color = Color.BLACK
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val optionPaint = TextPaint().apply {
                color = Color.DKGRAY
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }

            val correctPaint = TextPaint().apply {
                color = Color.rgb(0, 128, 0) // Dark Green
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val margin = 50f
            val contentWidth = pageInfo.pageWidth - (margin * 2).toInt()
            var currentY = 50f

            // --- HEADER ---
            canvas.drawText("Students Welfare Foundation", pageInfo.pageWidth / 2f, currentY, titlePaint)
            currentY += 30f
            canvas.drawText(test.title, pageInfo.pageWidth / 2f, currentY, subtitlePaint)
            currentY += 20f
            canvas.drawText("Total Marks: ${test.totalMarks}   |   Duration: ${test.durationMinutes} mins", pageInfo.pageWidth / 2f, currentY, subtitlePaint)
            currentY += 30f

            canvas.drawLine(margin, currentY, pageInfo.pageWidth - margin, currentY, Paint().apply { color = Color.BLACK; strokeWidth = 1f })
            currentY += 20f

            fun drawWrappedText(text: String, paint: TextPaint, x: Float, maxWidth: Int): Float {
                val staticLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0f, 1f)
                        .setIncludePad(false).build()
                } else {
                    @Suppress("DEPRECATION")
                    StaticLayout(text, paint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)
                }

                if (currentY + staticLayout.height > pageInfo.pageHeight - margin) {
                    document.finishPage(page)
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = margin
                }

                canvas.save()
                canvas.translate(x, currentY)
                staticLayout.draw(canvas)
                canvas.restore()

                currentY += staticLayout.height + 5f
                return staticLayout.height.toFloat()
            }

            // --- QUESTIONS ---
            var qNumber = 1
            test.sections.forEach { section ->
                if (section.title.isNotBlank() && section.title != "Section 1") {
                    currentY += 10f
                    drawWrappedText(section.title, titlePaint.apply { textSize = 16f; textAlign = Paint.Align.LEFT }, margin, contentWidth)
                    currentY += 10f
                }

                section.questions.forEach { q ->
                    // Strip LaTeX delimiters so PDF shows readable text instead of raw $...$ markup
                    val qText    = com.examsystem.app.util.MathUtils.stripLatex(q.text)
                    val optA     = com.examsystem.app.util.MathUtils.stripLatex(q.optionA)
                    val optB     = com.examsystem.app.util.MathUtils.stripLatex(q.optionB)
                    val optC     = com.examsystem.app.util.MathUtils.stripLatex(q.optionC)
                    val optD     = com.examsystem.app.util.MathUtils.stripLatex(q.optionD)

                    drawWrappedText("Q$qNumber. $qText", questionPaint, margin, contentWidth)
                    drawWrappedText("A. $optA", if (q.correctAnswer == "A") correctPaint else optionPaint, margin + 20f, contentWidth - 20)
                    drawWrappedText("B. $optB", if (q.correctAnswer == "B") correctPaint else optionPaint, margin + 20f, contentWidth - 20)
                    drawWrappedText("C. $optC", if (q.correctAnswer == "C") correctPaint else optionPaint, margin + 20f, contentWidth - 20)
                    drawWrappedText("D. $optD", if (q.correctAnswer == "D") correctPaint else optionPaint, margin + 20f, contentWidth - 20)
                    
                    currentY += 10f
                    qNumber++
                }
            }

            document.finishPage(page)

            // --- SAVE FILE ---
            val fileName = "Test_${test.title.replace(" ", "_")}_Key.pdf"
            var fos: OutputStream? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    fos = resolver.openOutputStream(uri)
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = java.io.File(downloadsDir, fileName)
                fos = java.io.FileOutputStream(file)
            }

            if (fos != null) {
                document.writeTo(fos)
                document.close()
                fos.close()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
                }
                return@withContext true
            } else {
                document.close()
                return@withContext false
            }

        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to save PDF: ${e.message}", Toast.LENGTH_LONG).show()
            }
            return@withContext false
        }
    }
}
