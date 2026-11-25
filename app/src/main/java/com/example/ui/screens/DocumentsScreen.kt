package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.BillingDocument
import com.example.data.CompanyProfile
import com.example.data.DEFAULT_COMPANY_NAME
import com.example.data.DocumentStatus
import com.example.data.DocumentType
import com.example.data.formatMoney
import com.example.ui.UiState
import com.example.ui.components.DocumentListCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.McCompanyFooterBanner
import com.example.ui.theme.BurnishedGoldBright
import com.example.ui.theme.LedgerMonoFontFamily
import com.example.ui.theme.NavyPrimary

@Composable
fun DocumentsScreen(
    fixedDocType: DocumentType,
    profile: CompanyProfile,
    documentsState: UiState<List<BillingDocument>>,
    onOpenDocument: (BillingDocument) -> Unit
) {
    val isInvoices = fixedDocType == DocumentType.BILL
    var searchQuery by remember(fixedDocType) { mutableStateOf("") }
    var selectedStatus by remember(fixedDocType) { mutableStateOf<DocumentStatus?>(null) }

    val statusOptions = remember(fixedDocType) { DocumentStatus.validFor(fixedDocType) }
    val allTypeDocs = remember(documentsState, fixedDocType) {
        ((documentsState as? UiState.Success)?.data.orEmpty())
            .filter { it.parsedType == fixedDocType }
    }

    val filteredDocuments = remember(allTypeDocs, searchQuery, selectedStatus) {
        val q = searchQuery.trim()
        allTypeDocs.filter { doc ->
            val matchesStatus = selectedStatus == null || doc.parsedStatus == selectedStatus
            val matchesSearch = q.isEmpty() ||
                doc.customerName.contains(q, ignoreCase = true) ||
                doc.docNumber.contains(q, ignoreCase = true) ||
                doc.customerPhone.contains(q, ignoreCase = true)
            matchesStatus && matchesSearch
        }
    }

    val currency = profile.currencySymbol
    val totalValue = remember(allTypeDocs) { allTypeDocs.sumOf { it.grandTotal } }
    val primarySubTotal = remember(allTypeDocs, isInvoices) {
        if (isInvoices) {
            allTypeDocs.filter { it.parsedStatus == DocumentStatus.PAID }.sumOf { it.grandTotal }
        } else {
            allTypeDocs.filter { it.parsedStatus != DocumentStatus.CONVERTED }.sumOf { it.grandTotal }
        }
    }
    val secondarySubTotal = remember(allTypeDocs, isInvoices) {
        if (isInvoices) {
            allTypeDocs.filter { it.parsedStatus != DocumentStatus.PAID }.sumOf { it.grandTotal }
        } else {
            allTypeDocs.filter { it.parsedStatus == DocumentStatus.CONVERTED }.sumOf { it.grandTotal }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TabSummaryHeroBanner(
                    companyName = profile.companyName.ifBlank { DEFAULT_COMPANY_NAME },
                    isInvoices = isInvoices,
                    totalLabel = if (isInvoices) "TOTAL INVOICED" else "TOTAL QUOTED",
                    totalFormatted = formatMoney(totalValue, currency),
                    leftLabel = if (isInvoices) "Collected (Paid)" else "Open Quotes",
                    leftValue = formatMoney(primarySubTotal, currency),
                    rightLabel = if (isInvoices) "Pending Balance" else "Converted",
                    rightValue = formatMoney(secondarySubTotal, currency),
                    countSummary = if (isInvoices) {
                        "${allTypeDocs.size} invoice(s)"
                    } else {
                        "${allTypeDocs.size} quotation(s)"
                    }
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                if (isInvoices) "Search invoice #, customer, or phone"
                                else "Search quotation #, customer, or phone"
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = stringResource(R.string.cd_search)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.cd_clear_search)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(if (isInvoices) "invoices_search_input" else "quotations_search_input")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedStatus == null,
                            onClick = { selectedStatus = null },
                            label = { Text("All (${allTypeDocs.size})") },
                            modifier = Modifier.testTag("filter_chip_all")
                        )
                        statusOptions.forEach { st ->
                            val count = allTypeDocs.count { it.parsedStatus == st }
                            FilterChip(
                                selected = selectedStatus == st,
                                onClick = { selectedStatus = if (selectedStatus == st) null else st },
                                label = { Text("${st.label} ($count)") },
                                modifier = Modifier.testTag("filter_chip_${st.code.lowercase()}")
                            )
                        }
                    }
                }
            }

            when (documentsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                is UiState.Error -> {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = documentsState.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                is UiState.Success -> {
                    if (filteredDocuments.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = if (isInvoices) Icons.Outlined.ReceiptLong else Icons.Outlined.Description,
                                title = when {
                                    searchQuery.isNotBlank() || selectedStatus != null ->
                                        if (isInvoices) "No matching invoices" else "No matching quotations"
                                    isInvoices -> "No Invoices Yet"
                                    else -> "No Quotations Yet"
                                },
                                subtitle = when {
                                    searchQuery.isNotBlank() || selectedStatus != null ->
                                        "Try clearing your search text or status filter."
                                    isInvoices ->
                                        "Tap New Invoice below to generate a numbered HM GRUP bill for your customer."
                                    else ->
                                        "Tap New Quotation below to prepare a customer price quote convertible into a bill."
                                }
                            )
                        }
                    } else {
                        items(filteredDocuments, key = { it.id }) { doc ->
                            DocumentListCard(
                                document = doc,
                                onClick = { onOpenDocument(doc) }
                            )
                        }
                    }
                }
            }

            item {
                McCompanyFooterBanner(modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun TabSummaryHeroBanner(
    companyName: String,
    isInvoices: Boolean,
    totalLabel: String,
    totalFormatted: String,
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String,
    countSummary: String
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = NavyPrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_hm_hero_banner_1795606273716),
                contentDescription = stringResource(R.string.cd_hero_banner),
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                alpha = 0.38f
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                NavyPrimary.copy(alpha = 0.9f),
                                NavyPrimary.copy(alpha = 0.7f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.img_hm_app_icon_1795606273078),
                            contentDescription = stringResource(R.string.cd_company_logo),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = companyName,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = countSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.72f)
                            )
                        }
                    }

                    Surface(
                        color = BurnishedGoldBright.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, BurnishedGoldBright.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Verified,
                                contentDescription = null,
                                tint = BurnishedGoldBright,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isInvoices) "Bills" else "Quotes",
                                style = MaterialTheme.typography.labelMedium,
                                color = BurnishedGoldBright
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = totalLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.68f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = totalFormatted,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = LedgerMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SummaryStatTile(label = leftLabel, value = leftValue, modifier = Modifier.weight(1f))
                    SummaryStatTile(label = rightLabel, value = rightValue, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SummaryStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.1f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = LedgerMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = BurnishedGoldBright,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
