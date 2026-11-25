package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.BillingDocument
import com.example.data.CompanyProfile
import com.example.data.DEFAULT_COMPANY_NAME
import com.example.data.DEFAULT_DEVELOPER_CREDIT
import com.example.data.DocumentStatus
import com.example.data.DocumentType
import com.example.data.formatMoney
import com.example.data.formatQuantity
import com.example.pdf.PdfDocumentGenerator
import com.example.ui.components.StatusBadge
import com.example.ui.theme.BurnishedGold
import com.example.ui.theme.BurnishedGoldBright
import com.example.ui.theme.InkOnSurface
import com.example.ui.theme.InkOnSurfaceVariant
import com.example.ui.theme.LedgerMonoFontFamily
import com.example.ui.theme.NavyPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    document: BillingDocument,
    profile: CompanyProfile,
    isSubmitting: Boolean,
    onEdit: () -> Unit,
    onUpdateStatus: (DocumentStatus) -> Unit,
    onConvertToBill: () -> Unit,
    onDelete: () -> Unit,
    onFeedback: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler(onBack = onNavigateBack)

    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMarkPaidDialog by remember { mutableStateOf(false) }
    var showConvertDialog by remember { mutableStateOf(false) }

    val isBill = document.parsedType == DocumentType.BILL
    val isLocked = document.parsedStatus.isTerminal
    val companyName = profile.companyName.ifBlank { DEFAULT_COMPANY_NAME }
    val devCredit = document.developerCredit.ifBlank { DEFAULT_DEVELOPER_CREDIT }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = document.docNumber, style = MaterialTheme.typography.titleMedium)
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                },
                actions = {
                    if (!isLocked) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.testTag("detail_edit_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.action_edit)
                            )
                        }
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.testTag("detail_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Primary Action Strip: Export PDF & Share Text
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val result = PdfDocumentGenerator.generateAndSharePdf(context, document, profile)
                            result.onFailure {
                                onFeedback(it.localizedMessage ?: "Failed to export PDF.")
                            }
                        },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("detail_export_pdf_button")
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_export_pdf))
                    }

                    OutlinedButton(
                        onClick = {
                            val textSummary = PdfDocumentGenerator.buildShareableTextSummary(document, profile)
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "$companyName ${document.docNumber}")
                                putExtra(Intent.EXTRA_TEXT, textSummary)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Summary"))
                        },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("detail_share_text_button")
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_share_summary))
                    }
                }

                // Lifecycle Action Bar (Convert Quotation to Bill / Mark Bill Paid)
                if (!isLocked) {
                    if (isBill) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (document.parsedStatus != DocumentStatus.PARTIAL) {
                                OutlinedButton(
                                    onClick = { onUpdateStatus(DocumentStatus.PARTIAL) },
                                    enabled = !isSubmitting,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Text("Mark Partial")
                                }
                            }
                            Button(
                                onClick = { showMarkPaidDialog = true },
                                enabled = !isSubmitting,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0E7A4B),
                                    contentColor = Color.White
                                ),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("detail_mark_paid_button")
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.action_mark_paid))
                            }
                        }
                    } else {
                        Button(
                            onClick = { showConvertDialog = true },
                            enabled = !isSubmitting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BurnishedGold,
                                contentColor = Color.White
                            ),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("detail_convert_to_bill_button")
                        ) {
                            Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.action_convert_to_bill),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBill) {
                                    "This bill is marked PAID and locked as a finalized record."
                                } else {
                                    "This quotation has been CONVERTED into a Bill and archived."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Crisp Paper-Styled Executive Letterhead Preview
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFD0D7E2)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Navy Header Banner
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NavyPrimary)
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = companyName,
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    if (profile.tagline.isNotBlank()) {
                                        Text(
                                            text = profile.tagline,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.78f)
                                        )
                                    }
                                    val contact = listOf(profile.phone, profile.email)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" • ")
                                    if (contact.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = contact,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                    }
                                    if (profile.taxNumber.isNotBlank()) {
                                        Text(
                                            text = "Tax/GST: ${profile.taxNumber}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BurnishedGoldBright
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = if (isBill) "TAX INVOICE" else "QUOTATION",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = BurnishedGoldBright,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = document.docNumber,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    StatusBadge(status = document.parsedStatus)
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(BurnishedGold)
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Customer & Dates Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1.3f)) {
                                    Text(
                                        text = if (isBill) "BILL TO" else "QUOTATION FOR",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = BurnishedGold,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = document.customerName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = InkOnSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (document.customerPhone.isNotBlank()) {
                                        Text(
                                            text = document.customerPhone,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = InkOnSurfaceVariant
                                        )
                                    }
                                    if (document.customerEmail.isNotBlank()) {
                                        Text(
                                            text = document.customerEmail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = InkOnSurfaceVariant
                                        )
                                    }
                                    if (document.customerAddress.isNotBlank()) {
                                        Text(
                                            text = document.customerAddress,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = InkOnSurfaceVariant
                                        )
                                    }
                                    if (document.customerTaxNumber.isNotBlank()) {
                                        Text(
                                            text = "Tax ID: ${document.customerTaxNumber}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = InkOnSurfaceVariant
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(0.9f),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = "ISSUE DATE",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = InkOnSurfaceVariant
                                    )
                                    Text(
                                        text = document.issueDate,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = InkOnSurface,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (document.dueDate.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = if (isBill) "DUE DATE" else "VALID UNTIL",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = InkOnSurfaceVariant
                                        )
                                        Text(
                                            text = document.dueDate,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = InkOnSurface,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            // Line Items Table
                            Surface(
                                color = Color(0xFFF6F8FB),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFFE4E8F0))
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(NavyPrimary)
                                            .padding(horizontal = 12.dp, vertical = 9.dp)
                                    ) {
                                        Text(
                                            text = "ITEM",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White,
                                            modifier = Modifier.weight(1.7f)
                                        )
                                        Text(
                                            text = "QTY",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.weight(0.8f)
                                        )
                                        Text(
                                            text = "AMOUNT",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.weight(1.1f)
                                        )
                                    }

                                    document.items.forEachIndexed { idx, item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(if (idx % 2 == 0) Color.White else Color(0xFFF9FAFC))
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1.7f)) {
                                                Text(
                                                    text = item.description,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = InkOnSurface,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "@ ${formatMoney(item.unitPrice, document.currencySymbol)} / ${item.unit}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = InkOnSurfaceVariant
                                                )
                                            }
                                            Text(
                                                text = "${formatQuantity(item.quantity)} ${item.unit}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = InkOnSurface,
                                                textAlign = TextAlign.End,
                                                modifier = Modifier.weight(0.8f)
                                            )
                                            Text(
                                                text = formatMoney(item.lineTotal, document.currencySymbol),
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontFamily = LedgerMonoFontFamily,
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = InkOnSurface,
                                                textAlign = TextAlign.End,
                                                modifier = Modifier.weight(1.1f)
                                            )
                                        }
                                        if (idx < document.items.lastIndex) {
                                            HorizontalDivider(color = Color(0xFFE4E8F0))
                                        }
                                    }
                                }
                            }

                            // Totals Block
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                PaperTotalRow("Subtotal", formatMoney(document.subtotal, document.currencySymbol))
                                if (document.discountAmount > 0.0) {
                                    PaperTotalRow(
                                        "Discount (${formatQuantity(document.discountPercent)}%)",
                                        "-${formatMoney(document.discountAmount, document.currencySymbol)}"
                                    )
                                }
                                if (document.taxAmount > 0.0) {
                                    PaperTotalRow(
                                        "Tax / GST (${formatQuantity(document.taxRate)}%)",
                                        formatMoney(document.taxAmount, document.currencySymbol)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    color = NavyPrimary,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isBill) "GRAND TOTAL" else "QUOTED TOTAL",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = formatMoney(document.grandTotal, document.currencySymbol),
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontFamily = LedgerMonoFontFamily,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = BurnishedGoldBright
                                        )
                                    }
                                }
                            }

                            val displayNotes = document.notes.ifBlank { profile.defaultTerms }
                            if (displayNotes.isNotBlank()) {
                                HorizontalDivider(color = Color(0xFFE4E8F0))
                                Column {
                                    Text(
                                        text = "TERMS & NOTES",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = BurnishedGold,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = displayNotes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = InkOnSurfaceVariant
                                    )
                                }
                            }

                            // Official Footer Stamp (HM GRUP & Mc Company)
                            HorizontalDivider(color = Color(0xFFE4E8F0))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Verified,
                                    contentDescription = null,
                                    tint = BurnishedGold,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$companyName  •  $devCredit",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = InkOnSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMarkPaidDialog) {
        AlertDialog(
            onDismissRequest = { showMarkPaidDialog = false },
            title = { Text("Mark Bill as Paid?") },
            text = {
                Text("Marking ${document.docNumber} as Paid finalizes the bill and locks it from further edits. You can still export PDFs or share it anytime.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMarkPaidDialog = false
                        onUpdateStatus(DocumentStatus.PAID)
                    }
                ) {
                    Text("Confirm Paid")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMarkPaidDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showConvertDialog) {
        AlertDialog(
            onDismissRequest = { showConvertDialog = false },
            title = { Text("Convert Quotation to Bill?") },
            text = {
                Text("This will generate a new numbered HM GRUP Bill with all customer details and items from ${document.docNumber}, and mark this quotation as Converted.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConvertDialog = false
                        onConvertToBill()
                    }
                ) {
                    Text("Convert Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConvertDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete ${document.docNumber}?") },
            text = { Text("This record will be permanently removed from your HM GRUP cloud ledger.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun PaperTotalRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = InkOnSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = LedgerMonoFontFamily),
            color = InkOnSurface
        )
    }
}
