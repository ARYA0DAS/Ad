package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.BillingDocument
import com.example.data.BillingRepository
import com.example.data.CompanyProfile
import com.example.data.Customer
import com.example.data.DEFAULT_COMPANY_NAME
import com.example.data.DocumentType
import com.example.data.signOutUser
import com.example.ui.BillingViewModel
import com.example.ui.UiState
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CompanyProfileScreen
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.DocumentDetailScreen
import com.example.ui.screens.DocumentEditorScreen
import com.example.ui.screens.DocumentsScreen
import com.example.ui.theme.HmGrupTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HmGrupTheme {
                HmGrupRoot()
            }
        }
    }
}

private enum class MainTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    INVOICES(
        labelRes = R.string.nav_invoices,
        selectedIcon = Icons.Filled.ReceiptLong,
        unselectedIcon = Icons.Outlined.ReceiptLong,
        testTag = "tab_invoices"
    ),
    QUOTATIONS(
        labelRes = R.string.nav_quotations,
        selectedIcon = Icons.Filled.Description,
        unselectedIcon = Icons.Outlined.Description,
        testTag = "tab_quotations"
    ),
    CUSTOMERS(
        labelRes = R.string.nav_customers,
        selectedIcon = Icons.Filled.People,
        unselectedIcon = Icons.Outlined.PeopleOutline,
        testTag = "tab_customers"
    )
}

private sealed interface ActiveOverlay {
    data object None : ActiveOverlay
    data class Editor(
        val document: BillingDocument?,
        val initialType: DocumentType,
        val prefilledCustomer: Customer? = null
    ) : ActiveOverlay

    data class Detail(val documentId: String) : ActiveOverlay
    data object CompanyProfile : ActiveOverlay
}

@Composable
fun HmGrupRoot() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
    } else {
        key(user.uid) {
            HmGrupMainScreen(
                currentUserId = user.uid,
                userEmail = user.email.orEmpty(),
                userDisplayName = user.displayName.orEmpty()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HmGrupMainScreen(
    currentUserId: String,
    userEmail: String,
    userDisplayName: String
) {
    val viewModel: BillingViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                BillingViewModel(
                    repository = BillingRepository(db),
                    currentUserId = currentUserId,
                    userEmail = userEmail,
                    userDisplayName = userDisplayName
                )
            }
        }
    )

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val customersState by viewModel.customersState.collectAsStateWithLifecycle()
    val documentsState by viewModel.documentsState.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val profile = (profileState as? UiState.Success)?.data
        ?: CompanyProfile(userId = currentUserId, email = userEmail)
    val customers = (customersState as? UiState.Success)?.data.orEmpty()
    val documents = (documentsState as? UiState.Success)?.data.orEmpty()

    var selectedTab by remember { mutableStateOf(MainTab.INVOICES) }
    var overlay by remember { mutableStateOf<ActiveOverlay>(ActiveOverlay.None) }
    var triggerAddCustomer by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    when (val currentOverlay = overlay) {
        is ActiveOverlay.Editor -> {
            DocumentEditorScreen(
                existingDocument = currentOverlay.document,
                initialType = currentOverlay.initialType,
                prefilledCustomer = currentOverlay.prefilledCustomer,
                profile = profile,
                customers = customers,
                isSubmitting = isSubmitting,
                onSave = { doc, saveAsNewCust ->
                    viewModel.saveDocument(doc, saveAsNewCust) { savedId ->
                        selectedTab = if (doc.parsedType == DocumentType.BILL) {
                            MainTab.INVOICES
                        } else {
                            MainTab.QUOTATIONS
                        }
                        overlay = ActiveOverlay.Detail(savedId)
                    }
                },
                onNavigateBack = { overlay = ActiveOverlay.None }
            )
            return
        }

        is ActiveOverlay.Detail -> {
            val activeDoc = documents.firstOrNull { it.id == currentOverlay.documentId }
            if (activeDoc != null) {
                DocumentDetailScreen(
                    document = activeDoc,
                    profile = profile,
                    isSubmitting = isSubmitting,
                    onEdit = {
                        overlay = ActiveOverlay.Editor(
                            document = activeDoc,
                            initialType = activeDoc.parsedType
                        )
                    },
                    onUpdateStatus = { newStatus ->
                        viewModel.updateDocumentStatus(activeDoc.id, newStatus)
                    },
                    onConvertToBill = {
                        viewModel.convertQuotationToBill(activeDoc, profile.nextBillNumber) { newBillId ->
                            selectedTab = MainTab.INVOICES
                            overlay = ActiveOverlay.Detail(newBillId)
                        }
                    },
                    onDelete = {
                        viewModel.deleteDocument(activeDoc.id) {
                            overlay = ActiveOverlay.None
                        }
                    },
                    onFeedback = { msg -> viewModel.postMessage(msg) },
                    onNavigateBack = { overlay = ActiveOverlay.None }
                )
                return
            } else {
                overlay = ActiveOverlay.None
            }
        }

        ActiveOverlay.CompanyProfile -> {
            CompanyProfileScreen(
                profile = profile,
                userDisplayName = userDisplayName,
                userEmail = userEmail,
                isSubmitting = isSubmitting,
                onSaveProfile = { updated -> viewModel.saveCompanyProfile(updated) },
                onSignOut = {
                    signOutUser(
                        credentialManager = credentialManager,
                        scope = scope
                    )
                },
                onNavigateBack = { overlay = ActiveOverlay.None }
            )
            return
        }

        ActiveOverlay.None -> Unit
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useNavRail) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    MainTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        NavigationRailItem(
                            selected = isSelected,
                            onClick = { selectedTab = tab },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = stringResource(tab.labelRes)
                                )
                            },
                            label = { Text(stringResource(tab.labelRes)) },
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = "${profile.companyName.ifBlank { DEFAULT_COMPANY_NAME }}  •  ${stringResource(selectedTab.labelRes)}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.developer_credit),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { overlay = ActiveOverlay.CompanyProfile },
                                modifier = Modifier.testTag("open_company_profile_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Business,
                                    contentDescription = stringResource(R.string.cd_company_settings)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                },
                bottomBar = {
                    if (!useNavRail) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            MainTab.entries.forEach { tab ->
                                val isSelected = selectedTab == tab
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { selectedTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                },
                floatingActionButton = {
                    when (selectedTab) {
                        MainTab.INVOICES -> {
                            ExtendedFloatingActionButton(
                                onClick = {
                                    overlay = ActiveOverlay.Editor(null, DocumentType.BILL)
                                },
                                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                text = { Text(stringResource(R.string.action_new_invoice)) },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.testTag("invoices_create_fab")
                            )
                        }

                        MainTab.QUOTATIONS -> {
                            ExtendedFloatingActionButton(
                                onClick = {
                                    overlay = ActiveOverlay.Editor(null, DocumentType.QUOTATION)
                                },
                                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                text = { Text(stringResource(R.string.action_new_quotation)) },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.testTag("quotations_create_fab")
                            )
                        }

                        MainTab.CUSTOMERS -> {
                            ExtendedFloatingActionButton(
                                onClick = { triggerAddCustomer = true },
                                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                text = { Text(stringResource(R.string.action_add_customer)) },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.testTag("customers_add_fab")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (selectedTab) {
                        MainTab.INVOICES -> DocumentsScreen(
                            fixedDocType = DocumentType.BILL,
                            profile = profile,
                            documentsState = documentsState,
                            onOpenDocument = { doc ->
                                overlay = ActiveOverlay.Detail(doc.id)
                            }
                        )

                        MainTab.QUOTATIONS -> DocumentsScreen(
                            fixedDocType = DocumentType.QUOTATION,
                            profile = profile,
                            documentsState = documentsState,
                            onOpenDocument = { doc ->
                                overlay = ActiveOverlay.Detail(doc.id)
                            }
                        )

                        MainTab.CUSTOMERS -> CustomersScreen(
                            customersState = customersState,
                            documents = documents,
                            currencySymbol = profile.currencySymbol,
                            isSubmitting = isSubmitting,
                            showAddDialogTrigger = triggerAddCustomer,
                            onDismissAddDialogTrigger = { triggerAddCustomer = false },
                            onSaveCustomer = { cust -> viewModel.saveCustomer(cust) },
                            onDeleteCustomer = { custId -> viewModel.deleteCustomer(custId) },
                            onNewBillForCustomer = { cust ->
                                overlay = ActiveOverlay.Editor(null, DocumentType.BILL, cust)
                            },
                            onNewQuotationForCustomer = { cust ->
                                overlay = ActiveOverlay.Editor(null, DocumentType.QUOTATION, cust)
                            }
                        )
                    }
                }
            }
        }
    }
}
