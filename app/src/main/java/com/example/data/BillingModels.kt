package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

const val DEFAULT_COMPANY_NAME = "HM GRUP"
const val DEFAULT_DEVELOPER_CREDIT = "All Development by Mc Company"
const val DEFAULT_CURRENCY = "₹"

enum class DocumentType(val code: String, val label: String, val prefix: String) {
    BILL("BILL", "Bill / Invoice", "HMG-INV"),
    QUOTATION("QUOTATION", "Quotation", "HMG-QTN");

    companion object {
        fun fromCode(raw: String?): DocumentType =
            entries.firstOrNull { it.code.equals(raw, ignoreCase = true) } ?: BILL
    }
}

enum class DocumentStatus(val code: String, val label: String) {
    UNPAID("UNPAID", "Unpaid"),
    PARTIAL("PARTIAL", "Partial"),
    PAID("PAID", "Paid"),
    DRAFT("DRAFT", "Draft"),
    SENT("SENT", "Sent"),
    APPROVED("APPROVED", "Approved"),
    CONVERTED("CONVERTED", "Converted");

    val isTerminal: Boolean
        get() = this == PAID || this == CONVERTED

    companion object {
        fun fromCode(raw: String?, docType: DocumentType): DocumentStatus {
            val parsed = entries.firstOrNull { it.code.equals(raw, ignoreCase = true) }
            if (parsed != null) return parsed
            return if (docType == DocumentType.BILL) UNPAID else DRAFT
        }

        fun validFor(docType: DocumentType): List<DocumentStatus> = when (docType) {
            DocumentType.BILL -> listOf(UNPAID, PARTIAL, PAID)
            DocumentType.QUOTATION -> listOf(DRAFT, SENT, APPROVED, CONVERTED)
        }
    }
}

@Keep
@IgnoreExtraProperties
data class CompanyProfile(
    val userId: String = "",
    val companyName: String = DEFAULT_COMPANY_NAME,
    val developerCredit: String = DEFAULT_DEVELOPER_CREDIT,
    val tagline: String = "Commercial Billing & Quotations",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val taxNumber: String = "",
    val currencySymbol: String = DEFAULT_CURRENCY,
    val defaultTaxRate: Double = 0.0,
    val defaultTerms: String = "Thank you for your business with HM GRUP. Payment is due within 15 days.",
    val signatoryName: String = "Authorized Signatory",
    val nextBillNumber: Long = 1L,
    val nextQuotationNumber: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Customer(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val taxNumber: String = "",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LineItem(
    val description: String = "",
    val quantity: Double = 1.0,
    val unit: String = "pcs",
    val unitPrice: Double = 0.0,
    val lineTotal: Double = 0.0
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "description" to description.trim().take(200),
        "quantity" to quantity.coerceIn(0.0, 1_000_000.0),
        "unit" to unit.trim().ifBlank { "pcs" }.take(20),
        "unitPrice" to unitPrice.coerceIn(0.0, 100_000_000.0),
        "lineTotal" to roundCurrency(quantity * unitPrice).coerceIn(0.0, 1_000_000_000.0)
    )
}

@Keep
@IgnoreExtraProperties
data class BillingDocument(
    @DocumentId val id: String = "",
    val userId: String = "",
    val docType: String = DocumentType.BILL.code,
    val docNumber: String = "",
    val status: String = DocumentStatus.UNPAID.code,
    val customerId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val customerEmail: String = "",
    val customerAddress: String = "",
    val customerTaxNumber: String = "",
    val issueDate: String = todayDateString(),
    val dueDate: String = defaultDueDateString(),
    val currencySymbol: String = DEFAULT_CURRENCY,
    val items: List<LineItem> = emptyList(),
    val subtotal: Double = 0.0,
    val discountPercent: Double = 0.0,
    val discountAmount: Double = 0.0,
    val taxRate: Double = 0.0,
    val taxAmount: Double = 0.0,
    val grandTotal: Double = 0.0,
    val notes: String = "",
    val developerCredit: String = DEFAULT_DEVELOPER_CREDIT,
    val convertedToBillId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedType: DocumentType
        get() = DocumentType.fromCode(docType)

    val parsedStatus: DocumentStatus
        get() = DocumentStatus.fromCode(status, parsedType)
}

data class CalculatedTotals(
    val subtotal: Double,
    val discountAmount: Double,
    val taxAmount: Double,
    val grandTotal: Double
)

fun calculateTotals(
    items: List<LineItem>,
    discountPercent: Double,
    taxRate: Double
): CalculatedTotals {
    val rawSubtotal = items.sumOf { (it.quantity.coerceAtLeast(0.0) * it.unitPrice.coerceAtLeast(0.0)) }
    val subtotal = roundCurrency(rawSubtotal)
    val clampedDiscountPct = discountPercent.coerceIn(0.0, 100.0)
    val discountAmount = roundCurrency(subtotal * (clampedDiscountPct / 100.0))
    val taxable = (subtotal - discountAmount).coerceAtLeast(0.0)
    val clampedTaxPct = taxRate.coerceIn(0.0, 100.0)
    val taxAmount = roundCurrency(taxable * (clampedTaxPct / 100.0))
    val grandTotal = roundCurrency(taxable + taxAmount)
    return CalculatedTotals(
        subtotal = subtotal,
        discountAmount = discountAmount,
        taxAmount = taxAmount,
        grandTotal = grandTotal
    )
}

fun roundCurrency(value: Double): Double =
    (value * 100.0).roundToLong() / 100.0

fun formatMoney(amount: Double, currencySymbol: String = DEFAULT_CURRENCY): String {
    val formatter = DecimalFormat("#,##0.00")
    return "$currencySymbol${formatter.format(amount)}"
}

fun formatQuantity(qty: Double): String {
    return if (qty % 1.0 == 0.0) qty.toLong().toString()
    else DecimalFormat("#,##0.##").format(qty)
}

fun todayDateString(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

fun defaultDueDateString(daysAhead: Int = 15): String {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, daysAhead)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
}

fun formatNextDocumentNumber(docType: DocumentType, sequenceNumber: Long): String {
    val year = Calendar.getInstance().get(Calendar.YEAR)
    val padded = sequenceNumber.coerceAtLeast(1L).toString().padStart(3, '0')
    return "${docType.prefix}-$year-$padded"
}
