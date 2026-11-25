package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.ui.theme.BurnishedGold
import com.example.ui.theme.BurnishedGoldBright
import com.example.ui.theme.LedgerMonoFontFamily
import com.example.ui.theme.NavyPrimary

@Composable
fun DashboardScreen(
    profile: CompanyProfile,
    documentsState: UiState<List<BillingDocument>>,
    customerCount: Int,
    onCreateBill: () -> Unit,
    onCreateQuotation: () -> Unit,
    onOpenDocument: (BillingDocument) -> Unit,
    onViewAllDocuments: () -> Unit
) {
    val documents = (documentsState as? UiState.Success)?.data.orEmpty()
    val metrics = remember(documents) {
        val bills = documents.filter { it.parsedType == DocumentType.BILL }
        val quotes = documents.filter { it.parsedType == DocumentType.QUOTATION }
        val paidTotal = bills.filter { it.parsedStatus == DocumentStatus.PAID }.sumOf { it.grandTotal }
        val pendingTotal = bills.filter { it.parsedStatus != DocumentStatus.PAID }.sumOf { it.grandTotal }
        val openQuoteTotal = quotes.filter { it.parsedStatus != DocumentStatus.CONVERTED }.sumOf { it.grandTotal }
        DashboardMetrics(
            totalBilled = paidTotal + pendingTotal,
            paidTotal = paidTotal,
            pendingTotal = pendingTotal,
            openQuoteTotal = openQuoteTotal,
            billCount = bills.size,
            quoteCount = quotes.size
        )
    }

    val currency = profile.currencySymbol
    val companyName = profile.companyName.ifBlank { DEFAULT_COMPANY_NAME }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ExecutiveHeroCard(
                    companyName = companyName,
                    tagline = profile.tagline.ifBlank { "Commercial Billing & Quotations" },
                    currency = currency,
                    metrics = metrics,
                    customerCount = customerCount
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionTile(
                        title = stringResource(R.string.action_new_bill),
                        subtitle = "Tax Invoice",
                        icon = Icons.Outlined.ReceiptLong,
                        accent = MaterialTheme.colorScheme.primary,
                        container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        onClick = onCreateBill,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_new_bill_button")
                    )
                    QuickActionTile(
                        title = stringResource(R.string.action_new_quotation),
                        subtitle = "Price Quote",
                        icon = Icons.Outlined.Description,
                        accent = BurnishedGold,
                        container = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        onClick = onCreateQuotation,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_new_quotation_button")
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Bills & Quotations",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (documents.isNotEmpty()) {
                        TextButton(
                            onClick = onViewAllDocuments,
                            modifier = Modifier.testTag("dashboard_view_all_button")
                        ) {
                            Text("View All")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
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
                    if (documents.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Outlined.ReceiptLong,
                                title = stringResource(R.string.empty_documents_title),
                                subtitle = stringResource(R.string.empty_documents_subtitle)
                            )
                        }
                    } else {
                        items(documents.take(6), key = { it.id }) { doc ->
                            DocumentListCard(
                                document = doc,
                                onClick = { onOpenDocument(doc) }
                            )
                        }
                    }
                }
            }

            item {
                McCompanyFooterBanner(modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            }
        }
    }
}

private data class DashboardMetrics(
    val totalBilled: Double,
    val paidTotal: Double,
    val pendingTotal: Double,
    val openQuoteTotal: Double,
    val billCount: Int,
    val quoteCount: Int
)

@Composable
private fun ExecutiveHeroCard(
    companyName: String,
    tagline: String,
    currency: String,
    metrics: DashboardMetrics,
    customerCount: Int
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = NavyPrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_hm_hero_banner_1795606273716),
                contentDescription = stringResource(R.string.cd_hero_banner),
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                alpha = 0.42f
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                NavyPrimary.copy(alpha = 0.88f),
                                NavyPrimary.copy(alpha = 0.65f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Image(
                            painter = painterResource(id = R.drawable.img_hm_app_icon_1795606273078),
                            contentDescription = stringResource(R.string.cd_company_logo),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = companyName,
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = tagline,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Surface(
                        color = BurnishedGoldBright.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, BurnishedGoldBright.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
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
                                text = "Mc Company",
                                style = MaterialTheme.typography.labelMedium,
                                color = BurnishedGoldBright
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "TOTAL BILLED VOLUME",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.68f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatMoney(metrics.totalBilled, currency),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = LedgerMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HeroStatPill(
                        label = "Pending Bills",
                        value = formatMoney(metrics.pendingTotal, currency),
                        modifier = Modifier.weight(1f)
                    )
                    HeroStatPill(
                        label = "Open Quotes",
                        value = formatMoney(metrics.openQuoteTotal, currency),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "${metrics.billCount} Bills  •  ${metrics.quoteCount} Quotations  •  $customerCount Customers",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.72f)
                )
            }
        }
    }
}

@Composable
private fun HeroStatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.72f)
            )
            Spacer(modifier = Modifier.height(3.dp))
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

@Composable
private fun QuickActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    container: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(container),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
