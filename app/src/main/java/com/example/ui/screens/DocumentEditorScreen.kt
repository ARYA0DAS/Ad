package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.BillingDocument
import com.example.data.CompanyProfile
import com.example.data.Customer
import com.example.data.DEFAULT_DEVELOPER_CREDIT
import com.example.data.DocumentStatus
import com.example.data.DocumentType
import com.example.data.LineItem
import com.example.data.calculateTotals
import com.example.data.defaultDueDateString
import com.example.data.formatMoney
import com.example.data.formatNextDocumentNumber
import com.example.data.roundCurrency
import com.example.data.todayDateString
import com.example.ui.theme.LedgerMonoFontFamily

private class EditableLineItem(
    description: String = "",
    quantityText: String = "1",
    unit: String = "pcs",
    unitPriceText: String = ""
) {
    var description by mutableStateOf(description)
    var quantityText by mutableStateOf(quantityText)
    var unit by mutableStateOf(unit)
    var unitPriceText by mutableStateOf(unitPriceText)

    fun toLineItem(): LineItem {
        val qty = quantityText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
        val price = unitPriceText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
        return LineItem(
            description = description,
            quantity = qty,
            unit = unit.ifBlank { "pcs" },
            unitPrice = price,
            lineTotal = roundCurrency(qty * price)
        )
    }
}

private val COMMON_UNITS = listOf("pcs", "hrs", "kg", "sq.ft", "set", "lot")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentEditorScreen(
    existingDocument: BillingDocument?,
    initialType: DocumentType,
    prefilledCustomer: Customer?,
    profile: CompanyProfile,
    customers: List<Customer>,
    isSubmitting: Boolean,
    onSave: (BillingDocument, Boolean) -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler(onBack = onNavigateBack)

    val isEditing = existingDocument != null && existingDocument.id.isNotBlank()
    var docType by remember(existingDocument, initialType) {
        mutableStateOf(existingDocument?.parsedType ?: initialType)
    }

    fun suggestedDocNumber(type: DocumentType): String {
        val seq = if (type == DocumentType.BILL) profile.nextBillNumber else profile.nextQuotationNumber
        return formatNextDocumentNumber(type, seq)
    }

    var docNumber by remember(existingDocument, initialType) {
        mutableStateOf(existingDocument?.docNumber?.ifBlank { suggestedDocNumber(docType) } ?: suggestedDocNumber(docType))
    }
    var status by remember(existingDocument, initialType) {
        mutableStateOf(
            existingDocument?.parsedStatus ?: if (docType == DocumentType.BILL) DocumentStatus.UNPAID else DocumentStatus.DRAFT
        )
    }
    var customerId by remember(existingDocument, prefilledCustomer) {
        mutableStateOf(existingDocument?.customerId ?: prefilledCustomer?.id ?: "")
    }
    var customerName by remember(existingDocument, prefilledCustomer) {
        mutableStateOf(existingDocument?.customerName ?: prefilledCustomer?.name ?: "")
    }
    var customerPhone by remember(existingDocument, prefilledCustomer) {
        mutableStateOf(existingDocument?.customerPhone ?: prefilledCustomer?.phone ?: "")
    }
    var customerEmail by remember(existingDocument, prefilledCustomer) {
        mutableStateOf(existingDocument?.customerEmail ?: prefilledCustomer?.email ?: "")
    }
    var customerAddress by remember(existingDocument, prefilledCustomer) {
        mutableStateOf(existingDocument?.customerAddress ?: prefilledCustomer?.address ?: "")
    }
    var customerTaxNumber by remember(existingDocument, prefilledCustomer) {
        mutableStateOf(existingDocument?.customerTaxNumber ?: prefilledCustomer?.taxNumber ?: "")
    }
    var saveAsNewCustomer by remember { mutableStateOf(false) }

    var issueDate by remember(existingDocument) {
        mutableStateOf(existingDocument?.issueDate?.ifBlank { todayDateString() } ?: todayDateString())
    }
    var dueDate by remember(existingDocument) {
        mutableStateOf(existingDocument?.dueDate ?: defaultDueDateString())
    }
    var discountPercentText by remember(existingDocument) {
        mutableStateOf(
            existingDocument?.discountPercent?.takeIf { it > 0.0 }?.toString() ?: ""
        )
    }
    var taxRateText by remember(existingDocument, profile) {
        val rate = existingDocument?.taxRate ?: profile.defaultTaxRate
        mutableStateOf(if (rate > 0.0) rate.toString() else "")
    }
    var notes by remember(existingDocument, profile) {
        mutableStateOf(existingDocument?.notes ?: profile.defaultTerms)
    }

    val lineItems = remember(existingDocument) {
        mutableStateListOf<EditableLineItem>().apply {
            val existing = existingDocument?.items.orEmpty()
            if (existing.isEmpty()) {
                add(EditableLineItem())
            } else {
                existing.forEach { item ->
                    add(
                        EditableLineItem(
                            description = item.description,
                            quantityText = if (item.quantity % 1.0 == 0.0) item.quantity.toLong().toString() else item.quantity.toString(),
                            unit = item.unit,
                            unitPriceText = if (item.unitPrice > 0.0) item.unitPrice.toString() else ""
                        )
                    )
                }
            }
        }
    }

    var showCustomerPicker by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val currency = existingDocument?.currencySymbol?.ifBlank { profile.currencySymbol } ?: profile.currencySymbol
    val discountPct = discountPercentText.toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 0.0
    val taxPct = taxRateText.toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 0.0
    val domainItems = lineItems.map { it.toLineItem() }
    val totals = calculateTotals(domainItems, discountPct, taxPct)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditing) {
                            "Edit ${docType.label}"
                        } else {
                            "Create ${docType.label}"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (docType == DocumentType.BILL) "TOTAL DUE" else "QUOTED TOTAL",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatMoney(totals.grandTotal, currency),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = LedgerMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Button(
                        onClick = {
                            val trimmedCustomer = customerName.trim()
                            val trimmedNumber = docNumber.trim()
                            val validItems = domainItems.filter { it.description.isNotBlank() }
                            when {
                                trimmedNumber.isEmpty() -> validationError = "Please enter a document number."
                                trimmedCustomer.isEmpty() -> validationError = "Please enter the customer name."
                                validItems.isEmpty() -> validationError = "Please add at least one item with a description."
                                else -> {
                                    validationError = null
                                    val docToSave = BillingDocument(
                                        id = existingDocument?.id ?: "",
                                        docType = docType.code,
                                        docNumber = trimmedNumber,
                                        status = status.code,
                                        customerId = customerId,
                                        customerName = trimmedCustomer,
                                        customerPhone = customerPhone.trim(),
                                        customerEmail = customerEmail.trim(),
                                        customerAddress = customerAddress.trim(),
                                        customerTaxNumber = customerTaxNumber.trim(),
                                        issueDate = issueDate.trim().ifEmpty { todayDateString() },
                                        dueDate = dueDate.trim(),
                                        currencySymbol = currency,
                                        items = validItems,
                                        subtotal = totals.subtotal,
                                        discountPercent = discountPct,
                                        discountAmount = totals.discountAmount,
                                        taxRate = taxPct,
                                        taxAmount = totals.taxAmount,
                                        grandTotal = totals.grandTotal,
                                        notes = notes.trim(),
                                        developerCredit = profile.developerCredit.ifBlank { DEFAULT_DEVELOPER_CREDIT }
                                    )
                                    onSave(docToSave, saveAsNewCustomer)
                                }
                            }
                        },
                        enabled = !isSubmitting,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("editor_save_button")
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (docType == DocumentType.BILL) "Save Bill" else "Save Quotation",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (validationError != null) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = validationError!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                // Document Type & Metadata Section
                item {
                    EditorSectionCard(title = "Document Details") {
                        if (!isEditing) {
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                DocumentType.entries.forEachIndexed { idx, type ->
                                    SegmentedButton(
                                        selected = docType == type,
                                        onClick = {
                                            if (docType != type) {
                                                docType = type
                                                docNumber = suggestedDocNumber(type)
                                                status = if (type == DocumentType.BILL) DocumentStatus.UNPAID else DocumentStatus.DRAFT
                                            }
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(idx, DocumentType.entries.size),
                                        modifier = Modifier.testTag("doc_type_segment_${type.code.lowercase()}")
                                    ) {
                                        Text(if (type == DocumentType.BILL) "Bill / Invoice" else "Quotation")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        OutlinedTextField(
                            value = docNumber,
                            onValueChange = { docNumber = it },
                            label = { Text(if (docType == DocumentType.BILL) "Bill / Invoice Number *" else "Quotation Number *") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_doc_number_input")
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = issueDate,
                                onValueChange = { issueDate = it },
                                label = { Text("Issue Date") },
                                placeholder = { Text("YYYY-MM-DD") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = dueDate,
                                onValueChange = { dueDate = it },
                                label = { Text(if (docType == DocumentType.BILL) "Due Date" else "Valid Until") },
                                placeholder = { Text("YYYY-MM-DD") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Status",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val selectableStatuses = DocumentStatus.validFor(docType).filter {
                            if (docType == DocumentType.QUOTATION) it != DocumentStatus.CONVERTED else true
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            selectableStatuses.forEach { st ->
                                FilterChip(
                                    selected = status == st,
                                    onClick = { status = st },
                                    label = { Text(st.label) }
                                )
                            }
                        }
                    }
                }

                // Customer Section
                item {
                    EditorSectionCard(
                        title = "Customer Details",
                        headerAction = {
                            if (customers.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { showCustomerPicker = true },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("editor_pick_customer_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PersonSearch,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pick Saved")
                                }
                            }
                        }
                    ) {
                        OutlinedTextField(
                            value = customerName,
                            onValueChange = {
                                customerName = it
                                if (customerId.isNotEmpty()) customerId = ""
                            },
                            label = { Text("Customer / Company Name *") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_customer_name_input")
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = customerPhone,
                                onValueChange = { customerPhone = it },
                                label = { Text("Phone") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = customerTaxNumber,
                                onValueChange = { customerTaxNumber = it },
                                label = { Text("GST / Tax ID") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = customerEmail,
                            onValueChange = { customerEmail = it },
                            label = { Text("Email (optional)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = customerAddress,
                            onValueChange = { customerAddress = it },
                            label = { Text("Billing Address (optional)") },
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (customerId.isBlank() && customerName.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { saveAsNewCustomer = !saveAsNewCustomer }
                            ) {
                                Checkbox(
                                    checked = saveAsNewCustomer,
                                    onCheckedChange = { saveAsNewCustomer = it }
                                )
                                Text(
                                    text = "Also save this customer to my directory",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Line Items Section
                item {
                    EditorSectionCard(
                        title = "Items & Services (${lineItems.size})",
                        headerAction = {
                            if (lineItems.size < 50) {
                                OutlinedButton(
                                    onClick = { lineItems.add(EditableLineItem()) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("editor_add_item_button")
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Item")
                                }
                            }
                        }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            lineItems.forEachIndexed { index, item ->
                                val itemTotal = item.toLineItem().lineTotal
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    shape = MaterialTheme.shapes.medium,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Item #${index + 1}",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = formatMoney(itemTotal, currency),
                                                    style = MaterialTheme.typography.titleSmall.copy(
                                                        fontFamily = LedgerMonoFontFamily,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                if (lineItems.size > 1) {
                                                    IconButton(
                                                        onClick = { lineItems.removeAt(index) }
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.DeleteOutline,
                                                            contentDescription = stringResource(R.string.cd_delete_item),
                                                            tint = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        OutlinedTextField(
                                            value = item.description,
                                            onValueChange = { item.description = it },
                                            label = { Text("Item / Service Description *") },
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("editor_item_desc_$index")
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = item.quantityText,
                                                onValueChange = { item.quantityText = it },
                                                label = { Text("Qty") },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                modifier = Modifier
                                                    .weight(0.8f)
                                                    .testTag("editor_item_qty_$index")
                                            )
                                            OutlinedTextField(
                                                value = item.unit,
                                                onValueChange = { item.unit = it },
                                                label = { Text("Unit") },
                                                singleLine = true,
                                                modifier = Modifier.weight(0.8f)
                                            )
                                            OutlinedTextField(
                                                value = item.unitPriceText,
                                                onValueChange = { item.unitPriceText = it },
                                                label = { Text("Rate ($currency)") },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                modifier = Modifier
                                                    .weight(1.2f)
                                                    .testTag("editor_item_rate_$index")
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            COMMON_UNITS.forEach { preset ->
                                                FilterChip(
                                                    selected = item.unit.equals(preset, ignoreCase = true),
                                                    onClick = { item.unit = preset },
                                                    label = { Text(preset, style = MaterialTheme.typography.labelMedium) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Tax, Discount & Summary
                item {
                    EditorSectionCard(title = "Discount, Tax & Totals") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = discountPercentText,
                                onValueChange = { discountPercentText = it },
                                label = { Text("Discount (%)") },
                                placeholder = { Text("0") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = taxRateText,
                                onValueChange = { taxRateText = it },
                                label = { Text("Tax / GST (%)") },
                                placeholder = { Text("0") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        SummaryLineRow("Subtotal", formatMoney(totals.subtotal, currency))
                        if (totals.discountAmount > 0.0) {
                            SummaryLineRow(
                                "Discount (${discountPct}%)",
                                "-${formatMoney(totals.discountAmount, currency)}"
                            )
                        }
                        if (totals.taxAmount > 0.0) {
                            SummaryLineRow(
                                "Tax / GST (${taxPct}%)",
                                formatMoney(totals.taxAmount, currency)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Grand Total",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = formatMoney(totals.grandTotal, currency),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = LedgerMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Notes & Terms
                item {
                    EditorSectionCard(title = "Terms, Notes & Attribution") {
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Payment Terms / Notes") },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Footer stamp on PDF & Preview: HM GRUP • ${profile.developerCredit.ifBlank { DEFAULT_DEVELOPER_CREDIT }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    if (showCustomerPicker) {
        ModalBottomSheet(onDismissRequest = { showCustomerPicker = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Select Saved Customer",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 28.dp)
                ) {
                    items(customers, key = { it.id }) { cust ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    customerId = cust.id
                                    customerName = cust.name
                                    customerPhone = cust.phone
                                    customerEmail = cust.email
                                    customerAddress = cust.address
                                    customerTaxNumber = cust.taxNumber
                                    saveAsNewCustomer = false
                                    showCustomerPicker = false
                                }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = cust.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val sub = listOf(cust.phone, cust.email).filter { it.isNotBlank() }.joinToString(" • ")
                                if (sub.isNotBlank()) {
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorSectionCard(
    title: String,
    headerAction: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                headerAction?.invoke()
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SummaryLineRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = LedgerMonoFontFamily),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
