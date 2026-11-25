package com.example.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.data.BillingDocument
import com.example.data.CompanyProfile
import com.example.data.DEFAULT_COMPANY_NAME
import com.example.data.DEFAULT_DEVELOPER_CREDIT
import com.example.data.DocumentType
import com.example.data.formatMoney
import com.example.data.formatQuantity
import java.io.File
import java.io.FileOutputStream

object PdfDocumentGenerator {

    private const val PAGE_WIDTH = 595 // A4 width in pt
    private const val PAGE_HEIGHT = 842 // A4 height in pt
    private const val MARGIN = 36f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN * 2)

    fun generateAndSharePdf(
        context: Context,
        document: BillingDocument,
        profile: CompanyProfile
    ): Result<File> {
        return try {
            val file = generatePdfFile(context, document, profile)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val docLabel = if (document.parsedType == DocumentType.BILL) "Bill" else "Quotation"
            val company = profile.companyName.ifBlank { DEFAULT_COMPANY_NAME }
            val credit = document.developerCredit.ifBlank { DEFAULT_DEVELOPER_CREDIT }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "$company $docLabel ${document.docNumber}")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "$company $docLabel #${document.docNumber} for ${document.customerName} — Total: ${
                        formatMoney(document.grandTotal, document.currencySymbol)
                    }\n$credit"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share $docLabel PDF"))
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun generatePdfFile(
        context: Context,
        document: BillingDocument,
        profile: CompanyProfile
    ): File {
        val pdfDocument = PdfDocument()
        val navyColor = Color.rgb(15, 30, 51)
        val goldColor = Color.rgb(184, 123, 30)
        val slateText = Color.rgb(71, 84, 103)
        val inkColor = Color.rgb(16, 24, 40)
        val tintRow = Color.rgb(246, 248, 251)
        val lineColor = Color.rgb(214, 222, 232)

        val navyFill = Paint().apply { color = navyColor; style = Paint.Style.FILL }
        val goldFill = Paint().apply { color = goldColor; style = Paint.Style.FILL }
        val lightBoxFill = Paint().apply { color = tintRow; style = Paint.Style.FILL }
        val borderPaint = Paint().apply {
            color = lineColor
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        val brandTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val headerSubPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(218, 228, 242)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val docBadgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(248, 211, 138)
            textSize = 14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val headerRightPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 9.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }
        val sectionLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = goldColor
            textSize = 9f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val boldBodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            textSize = 10.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = slateText
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val tableHeaderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 9f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val tableCellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val tableMonoRightPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            textSize = 9.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }
        val totalWhitePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 11.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val totalWhiteRightPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 12f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = slateText
            textSize = 8.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val companyName = profile.companyName.ifBlank { DEFAULT_COMPANY_NAME }
        val devCredit = document.developerCredit.ifBlank { DEFAULT_DEVELOPER_CREDIT }
        val isBill = document.parsedType == DocumentType.BILL
        val docHeading = if (isBill) "TAX INVOICE / BILL" else "OFFICIAL QUOTATION"

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        fun drawFooter(c: android.graphics.Canvas, pNum: Int) {
            c.drawLine(MARGIN, PAGE_HEIGHT - 44f, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 44f, borderPaint)
            c.drawText(
                "$companyName  •  $devCredit  •  Page $pNum",
                PAGE_WIDTH / 2f,
                PAGE_HEIGHT - 26f,
                footerPaint
            )
        }

        // Top Navy Header Band
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 118f, navyFill)
        canvas.drawRect(0f, 114f, PAGE_WIDTH.toFloat(), 118f, goldFill)

        canvas.drawText(companyName, MARGIN, 44f, brandTitlePaint)
        var leftY = 60f
        if (profile.tagline.isNotBlank()) {
            canvas.drawText(profile.tagline.take(56), MARGIN, leftY, headerSubPaint)
            leftY += 13f
        }
        val contactLine = listOf(profile.phone, profile.email)
            .filter { it.isNotBlank() }
            .joinToString("  |  ")
        if (contactLine.isNotBlank()) {
            canvas.drawText(contactLine.take(62), MARGIN, leftY, headerSubPaint)
            leftY += 13f
        }
        if (profile.address.isNotBlank()) {
            canvas.drawText(profile.address.replace("\n", ", ").take(64), MARGIN, leftY, headerSubPaint)
            leftY += 13f
        }
        if (profile.taxNumber.isNotBlank()) {
            canvas.drawText("Tax / GST ID: ${profile.taxNumber}", MARGIN, leftY, headerSubPaint)
        }

        // Right-aligned Document Metadata
        val rightEdge = PAGE_WIDTH - MARGIN
        canvas.drawText(docHeading, rightEdge, 40f, docBadgePaint)
        canvas.drawText("NO: ${document.docNumber}", rightEdge, 58f, headerRightPaint)
        canvas.drawText("DATE: ${document.issueDate}", rightEdge, 73f, headerRightPaint)
        if (document.dueDate.isNotBlank()) {
            val dateLabel = if (isBill) "DUE:" else "VALID UNTIL:"
            canvas.drawText("$dateLabel ${document.dueDate}", rightEdge, 88f, headerRightPaint)
        }
        canvas.drawText("STATUS: ${document.parsedStatus.label.uppercase()}", rightEdge, 103f, headerRightPaint)

        // Customer Block
        var y = 136f
        val custRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 78f)
        canvas.drawRoundRect(custRect, 6f, 6f, lightBoxFill)
        canvas.drawRoundRect(custRect, 6f, 6f, borderPaint)

        canvas.drawText(
            if (isBill) "BILL TO CUSTOMER" else "QUOTATION PREPARED FOR",
            MARGIN + 12f,
            y + 18f,
            sectionLabelPaint
        )
        canvas.drawText(document.customerName.take(60), MARGIN + 12f, y + 35f, boldBodyPaint)
        val custContact = listOf(document.customerPhone, document.customerEmail)
            .filter { it.isNotBlank() }
            .joinToString("  •  ")
        if (custContact.isNotBlank()) {
            canvas.drawText(custContact.take(78), MARGIN + 12f, y + 50f, bodyPaint)
        }
        val custExtra = listOfNotNull(
            document.customerAddress.replace("\n", ", ").takeIf { it.isNotBlank() },
            document.customerTaxNumber.takeIf { it.isNotBlank() }?.let { "Tax ID: $it" }
        ).joinToString("  •  ")
        if (custExtra.isNotBlank()) {
            canvas.drawText(custExtra.take(84), MARGIN + 12f, y + 65f, bodyPaint)
        }

        y += 96f

        fun drawTableHeader(c: android.graphics.Canvas, topY: Float): Float {
            c.drawRect(MARGIN, topY, PAGE_WIDTH - MARGIN, topY + 24f, navyFill)
            c.drawText("#", MARGIN + 8f, topY + 16f, tableHeaderPaint)
            c.drawText("ITEM / DESCRIPTION", MARGIN + 30f, topY + 16f, tableHeaderPaint)
            val qtyHeader = TextPaint(tableHeaderPaint).apply { textAlign = Paint.Align.RIGHT }
            c.drawText("QTY", MARGIN + 335f, topY + 16f, qtyHeader)
            c.drawText("RATE", MARGIN + 425f, topY + 16f, qtyHeader)
            c.drawText("AMOUNT", rightEdge - 8f, topY + 16f, qtyHeader)
            return topY + 24f
        }

        y = drawTableHeader(canvas, y)

        document.items.forEachIndexed { index, item ->
            if (y > PAGE_HEIGHT - 170f) {
                drawFooter(canvas, pageNumber)
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = MARGIN
                y = drawTableHeader(canvas, y)
            }

            val rowHeight = 24f
            if (index % 2 == 1) {
                canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + rowHeight, lightBoxFill)
            }
            val textY = y + 16f
            canvas.drawText("${index + 1}", MARGIN + 8f, textY, tableCellPaint)
            canvas.drawText(item.description.take(44), MARGIN + 30f, textY, tableCellPaint)
            canvas.drawText(
                "${formatQuantity(item.quantity)} ${item.unit}",
                MARGIN + 335f,
                textY,
                tableMonoRightPaint
            )
            canvas.drawText(
                formatMoney(item.unitPrice, document.currencySymbol),
                MARGIN + 425f,
                textY,
                tableMonoRightPaint
            )
            canvas.drawText(
                formatMoney(item.lineTotal, document.currencySymbol),
                rightEdge - 8f,
                textY,
                tableMonoRightPaint
            )
            y += rowHeight
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, borderPaint)
        }

        y += 18f
        if (y > PAGE_HEIGHT - 190f) {
            drawFooter(canvas, pageNumber)
            pdfDocument.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            y = MARGIN + 10f
        }

        // Totals Summary Box (Right side)
        val summaryLeft = MARGIN + 275f
        fun drawSummaryRow(label: String, value: String) {
            canvas.drawText(label, summaryLeft, y + 14f, bodyPaint)
            canvas.drawText(value, rightEdge - 8f, y + 14f, tableMonoRightPaint)
            y += 20f
        }

        drawSummaryRow("Subtotal", formatMoney(document.subtotal, document.currencySymbol))
        if (document.discountPercent > 0.0 || document.discountAmount > 0.0) {
            drawSummaryRow(
                "Discount (${formatQuantity(document.discountPercent)}%)",
                "-${formatMoney(document.discountAmount, document.currencySymbol)}"
            )
        }
        if (document.taxRate > 0.0 || document.taxAmount > 0.0) {
            drawSummaryRow(
                "Tax / GST (${formatQuantity(document.taxRate)}%)",
                formatMoney(document.taxAmount, document.currencySymbol)
            )
        }

        y += 4f
        val grandRect = RectF(summaryLeft - 10f, y, PAGE_WIDTH - MARGIN, y + 32f)
        canvas.drawRoundRect(grandRect, 6f, 6f, navyFill)
        canvas.drawText(
            if (isBill) "TOTAL DUE" else "QUOTED TOTAL",
            summaryLeft,
            y + 20f,
            totalWhitePaint
        )
        canvas.drawText(
            formatMoney(document.grandTotal, document.currencySymbol),
            rightEdge - 8f,
            y + 20f,
            totalWhiteRightPaint
        )
        y += 50f

        // Terms & Notes + Signatory
        val notesText = document.notes.ifBlank { profile.defaultTerms }
        if (notesText.isNotBlank()) {
            canvas.drawText("TERMS & NOTES", MARGIN, y, sectionLabelPaint)
            y += 14f
            val words = notesText.replace("\n", " ").split(" ")
            var line = ""
            var lineCount = 0
            for (word in words) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (candidate.length > 72) {
                    canvas.drawText(line, MARGIN, y, bodyPaint)
                    y += 13f
                    line = word
                    lineCount++
                    if (lineCount >= 4) break
                } else {
                    line = candidate
                }
            }
            if (line.isNotBlank() && lineCount < 4) {
                canvas.drawText(line, MARGIN, y, bodyPaint)
                y += 16f
            }
        }

        val sigY = maxOf(y + 26f, PAGE_HEIGHT - 102f).coerceAtMost(PAGE_HEIGHT - 76f)
        canvas.drawLine(rightEdge - 160f, sigY, rightEdge, sigY, borderPaint)
        val sigPaint = TextPaint(bodyPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(
            profile.signatoryName.ifBlank { "$companyName Authorized Signatory" },
            rightEdge,
            sigY + 13f,
            sigPaint
        )

        drawFooter(canvas, pageNumber)
        pdfDocument.finishPage(page)

        val outDir = File(context.cacheDir, "documents").apply { mkdirs() }
        val safeNumber = document.docNumber.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val outFile = File(outDir, "${companyName.replace(" ", "_")}_${safeNumber}.pdf")
        FileOutputStream(outFile).use { fos ->
            pdfDocument.writeTo(fos)
        }
        pdfDocument.close()
        return outFile
    }

    fun buildShareableTextSummary(
        document: BillingDocument,
        profile: CompanyProfile
    ): String {
        val company = profile.companyName.ifBlank { DEFAULT_COMPANY_NAME }
        val credit = document.developerCredit.ifBlank { DEFAULT_DEVELOPER_CREDIT }
        val typeHeader = if (document.parsedType == DocumentType.BILL) "BILL / INVOICE" else "QUOTATION"
        return buildString {
            appendLine("===============================")
            appendLine(company.uppercase())
            if (profile.phone.isNotBlank()) appendLine("Tel: ${profile.phone}")
            if (profile.taxNumber.isNotBlank()) appendLine("Tax/GST: ${profile.taxNumber}")
            appendLine("===============================")
            appendLine("$typeHeader #${document.docNumber}")
            appendLine("Status: ${document.parsedStatus.label}")
            appendLine("Date: ${document.issueDate}")
            if (document.dueDate.isNotBlank()) {
                val lbl = if (document.parsedType == DocumentType.BILL) "Due Date" else "Valid Until"
                appendLine("$lbl: ${document.dueDate}")
            }
            appendLine()
            appendLine("Customer: ${document.customerName}")
            if (document.customerPhone.isNotBlank()) appendLine("Phone: ${document.customerPhone}")
            appendLine()
            appendLine("Items:")
            document.items.forEachIndexed { idx, item ->
                appendLine(
                    "${idx + 1}. ${item.description} (${formatQuantity(item.quantity)} ${item.unit} × ${
                        formatMoney(item.unitPrice, document.currencySymbol)
                    }) = ${formatMoney(item.lineTotal, document.currencySymbol)}"
                )
            }
            appendLine()
            appendLine("Subtotal: ${formatMoney(document.subtotal, document.currencySymbol)}")
            if (document.discountAmount > 0.0) {
                appendLine("Discount (${formatQuantity(document.discountPercent)}%): -${formatMoney(document.discountAmount, document.currencySymbol)}")
            }
            if (document.taxAmount > 0.0) {
                appendLine("Tax (${formatQuantity(document.taxRate)}%): ${formatMoney(document.taxAmount, document.currencySymbol)}")
            }
            appendLine("GRAND TOTAL: ${formatMoney(document.grandTotal, document.currencySymbol)}")
            if (document.notes.isNotBlank()) {
                appendLine()
                appendLine("Notes: ${document.notes}")
            }
            appendLine()
            appendLine("— $company | $credit")
        }
    }
}
