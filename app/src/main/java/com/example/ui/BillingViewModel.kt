package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BillingDocument
import com.example.data.BillingRepository
import com.example.data.CompanyProfile
import com.example.data.Customer
import com.example.data.DocumentStatus
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class BillingViewModel(
    private val repository: BillingRepository,
    val currentUserId: String,
    val userEmail: String,
    val userDisplayName: String
) : ViewModel() {

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    val profileState: StateFlow<UiState<CompanyProfile>> = repository
        .observeCompanyProfile(currentUserId)
        .map<CompanyProfile?, UiState<CompanyProfile>> { profile ->
            UiState.Success(
                profile ?: CompanyProfile(
                    userId = currentUserId,
                    email = userEmail
                )
            )
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing company profile", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Failed to load company profile"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val customersState: StateFlow<UiState<List<Customer>>> = repository
        .observeCustomers(currentUserId)
        .map<List<Customer>, UiState<List<Customer>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing customers", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Failed to load customers"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val documentsState: StateFlow<UiState<List<BillingDocument>>> = repository
        .observeDocuments(currentUserId)
        .map<List<BillingDocument>, UiState<List<BillingDocument>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing billing documents", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Failed to load bills and quotations"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    init {
        viewModelScope.launch {
            repository.ensureCompanyProfileExists(userEmail)
        }
    }

    fun clearMessage() {
        _snackbarMessage.value = null
    }

    fun postMessage(message: String) {
        _snackbarMessage.value = message
    }

    fun saveCompanyProfile(profile: CompanyProfile) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.saveCompanyProfile(profile)
            _isSubmitting.value = false
            result.fold(
                onSuccess = { _snackbarMessage.value = "HM GRUP company profile saved." },
                onFailure = { _snackbarMessage.value = it.localizedMessage ?: "Failed to save profile." }
            )
        }
    }

    fun saveCustomer(customer: Customer, onSaved: (String) -> Unit = {}) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.saveCustomer(customer)
            _isSubmitting.value = false
            result.fold(
                onSuccess = { id ->
                    _snackbarMessage.value = if (customer.id.isBlank()) "Customer added." else "Customer updated."
                    onSaved(id)
                },
                onFailure = { _snackbarMessage.value = it.localizedMessage ?: "Failed to save customer." }
            )
        }
    }

    fun deleteCustomer(customerId: String) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.deleteCustomer(customerId)
            _isSubmitting.value = false
            result.fold(
                onSuccess = { _snackbarMessage.value = "Customer deleted." },
                onFailure = { _snackbarMessage.value = it.localizedMessage ?: "Failed to delete customer." }
            )
        }
    }

    fun saveDocument(
        document: BillingDocument,
        saveAsNewCustomer: Boolean = false,
        onSaved: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isSubmitting.value = true
            var resolvedDoc = document
            if (saveAsNewCustomer && document.customerId.isBlank() && document.customerName.isNotBlank()) {
                val custResult = repository.saveCustomer(
                    Customer(
                        name = document.customerName,
                        phone = document.customerPhone,
                        email = document.customerEmail,
                        address = document.customerAddress,
                        taxNumber = document.customerTaxNumber
                    )
                )
                custResult.onSuccess { newCustId ->
                    resolvedDoc = resolvedDoc.copy(customerId = newCustId)
                }
            }

            val isNew = resolvedDoc.id.isBlank()
            val result = repository.saveDocument(
                document = resolvedDoc,
                incrementSequenceCounter = isNew
            )
            _isSubmitting.value = false
            result.fold(
                onSuccess = { docId ->
                    _snackbarMessage.value = if (isNew) {
                        "${resolvedDoc.parsedType.label} created."
                    } else {
                        "${resolvedDoc.parsedType.label} updated."
                    }
                    onSaved(docId)
                },
                onFailure = {
                    _snackbarMessage.value = it.localizedMessage ?: "Failed to save document."
                }
            )
        }
    }

    fun updateDocumentStatus(documentId: String, newStatus: DocumentStatus) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.updateDocumentStatus(documentId, newStatus)
            _isSubmitting.value = false
            result.fold(
                onSuccess = { _snackbarMessage.value = "Status updated to ${newStatus.label}." },
                onFailure = { _snackbarMessage.value = it.localizedMessage ?: "Failed to update status." }
            )
        }
    }

    fun convertQuotationToBill(
        quotation: BillingDocument,
        nextBillNumber: Long,
        onConverted: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.convertQuotationToBill(quotation, nextBillNumber)
            _isSubmitting.value = false
            result.fold(
                onSuccess = { newBillId ->
                    _snackbarMessage.value = "Quotation converted into a new Bill!"
                    onConverted(newBillId)
                },
                onFailure = {
                    _snackbarMessage.value = it.localizedMessage ?: "Could not convert quotation."
                }
            )
        }
    }

    fun deleteDocument(documentId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.deleteDocument(documentId)
            _isSubmitting.value = false
            result.fold(
                onSuccess = {
                    _snackbarMessage.value = "Document deleted."
                    onDeleted()
                },
                onFailure = {
                    _snackbarMessage.value = it.localizedMessage ?: "Failed to delete document."
                }
            )
        }
    }

    private companion object {
        const val TAG = "BillingViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
