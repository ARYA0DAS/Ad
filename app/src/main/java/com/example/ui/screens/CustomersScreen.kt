package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.BillingDocument
import com.example.data.Customer
import com.example.data.DocumentType
import com.example.data.formatMoney
import com.example.ui.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.LedgerMonoFontFamily

@Composable
fun CustomersScreen(
    customersState: UiState<List<Customer>>,
    documents: List<BillingDocument>,
    currencySymbol: String,
    isSubmitting: Boolean,
    showAddDialogTrigger: Boolean,
    onDismissAddDialogTrigger: () -> Unit,
    onSaveCustomer: (Customer) -> Unit,
    onDeleteCustomer: (String) -> Unit,
    onNewBillForCustomer: (Customer) -> Unit,
    onNewQuotationForCustomer: (Customer) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var editingCustomer by remember { mutableStateOf<Customer?>(null) }
    var deletingCustomer by remember { mutableStateOf<Customer?>(null) }

    val allCustomers = (customersState as? UiState.Success)?.data.orEmpty()
    val filteredCustomers = remember(allCustomers, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) allCustomers
        else allCustomers.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.phone.contains(q, ignoreCase = true) ||
                it.email.contains(q, ignoreCase = true)
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search customers by name, phone, email") },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.cd_search))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_clear_search))
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("customers_search_input")
            )

            when (customersState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is UiState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = customersState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                is UiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = 96.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (filteredCustomers.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Outlined.PeopleOutline,
                                    title = if (searchQuery.isNotBlank()) "No customers found" else stringResource(R.string.empty_customers_title),
                                    subtitle = if (searchQuery.isNotBlank()) {
                                        "Try adjusting your search query."
                                    } else {
                                        stringResource(R.string.empty_customers_subtitle)
                                    }
                                )
                            }
                        } else {
                            items(filteredCustomers, key = { it.id }) { customer ->
                                val relatedDocs = documents.filter {
                                    it.customerId == customer.id ||
                                        it.customerName.equals(customer.name, ignoreCase = true)
                                }
                                val totalBilled = relatedDocs
                                    .filter { it.parsedType == DocumentType.BILL }
                                    .sumOf { it.grandTotal }

                                CustomerDirectoryCard(
                                    customer = customer,
                                    docCount = relatedDocs.size,
                                    totalBilled = totalBilled,
                                    currencySymbol = currencySymbol,
                                    onEdit = { editingCustomer = customer },
                                    onDelete = { deletingCustomer = customer },
                                    onNewBill = { onNewBillForCustomer(customer) },
                                    onNewQuotation = { onNewQuotationForCustomer(customer) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialogTrigger || editingCustomer != null) {
        CustomerEditorDialog(
            initial = editingCustomer,
            isSubmitting = isSubmitting,
            onDismiss = {
                editingCustomer = null
                onDismissAddDialogTrigger()
            },
            onSave = { customer ->
                onSaveCustomer(customer)
                editingCustomer = null
                onDismissAddDialogTrigger()
            }
        )
    }

    deletingCustomer?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingCustomer = null },
            title = { Text("Delete Customer?") },
            text = {
                Text("Remove ${target.name} from your saved directory? Existing bills and quotations will remain intact.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCustomer(target.id)
                        deletingCustomer = null
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
                TextButton(onClick = { deletingCustomer = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun CustomerDirectoryCard(
    customer: Customer,
    docCount: Int,
    totalBilled: Double,
    currencySymbol: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onNewBill: () -> Unit,
    onNewQuotation: () -> Unit
) {
    val initials = customer.name.trim()
        .split(Regex("\\s+"))
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "C" }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("customer_card_${customer.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = customer.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val meta = listOf(customer.phone, customer.email)
                        .filter { it.isNotBlank() }
                        .joinToString(" • ")
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (customer.address.isNotBlank()) {
                        Text(
                            text = customer.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.action_edit)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (docCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$docCount record(s)  •  Billed: ${formatMoney(totalBilled, currencySymbol)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = LedgerMonoFontFamily),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onNewBill,
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Make Bill", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onNewQuotation,
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Make Quote", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun CustomerEditorDialog(
    initial: Customer?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (Customer) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var phone by remember(initial) { mutableStateOf(initial?.phone ?: "") }
    var email by remember(initial) { mutableStateOf(initial?.email ?: "") }
    var address by remember(initial) { mutableStateOf(initial?.address ?: "") }
    var taxNumber by remember(initial) { mutableStateOf(initial?.taxNumber ?: "") }
    var notes by remember(initial) { mutableStateOf(initial?.notes ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = if (initial == null) "Add Customer" else "Edit Customer")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Customer / Company Name *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_dialog_name_input")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = taxNumber,
                    onValueChange = { taxNumber = it },
                    label = { Text("GST / Tax Registration ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Billing Address") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        error = "Customer name is required."
                    } else {
                        onSave(
                            Customer(
                                id = initial?.id ?: "",
                                name = trimmed,
                                phone = phone.trim(),
                                email = email.trim(),
                                address = address.trim(),
                                taxNumber = taxNumber.trim(),
                                notes = notes.trim()
                            )
                        )
                    }
                },
                enabled = !isSubmitting,
                modifier = Modifier.testTag("customer_dialog_save_button")
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
