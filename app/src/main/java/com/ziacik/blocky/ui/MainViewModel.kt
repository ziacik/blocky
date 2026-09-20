package com.ziacik.blocky.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ziacik.blocky.data.BlockyDatabase
import com.ziacik.blocky.data.EkasaClient
import com.ziacik.blocky.data.EkasaReceiptParser
import com.ziacik.blocky.data.ReceiptRepository
import com.ziacik.blocky.model.CategoryTotal
import com.ziacik.blocky.model.ProductTotal
import com.ziacik.blocky.model.Receipt
import com.ziacik.blocky.model.ReceiptSummary
import com.ziacik.blocky.normalization.HeuristicItemNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MainUiState(
	val loading: Boolean = false,
	val totalCents: Long = 0,
	val receipts: List<ReceiptSummary> = emptyList(),
	val categories: List<CategoryTotal> = emptyList(),
	val products: List<ProductTotal> = emptyList(),
	val selectedReceipt: Receipt? = null,
	val message: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
	private val repository = ReceiptRepository(
		client = EkasaClient(),
		parser = EkasaReceiptParser(HeuristicItemNormalizer()),
		database = BlockyDatabase(application),
	)
	private val _state = MutableStateFlow(MainUiState())
	val state: StateFlow<MainUiState> = _state.asStateFlow()

	init {
		refresh()
	}

	fun importReceipt(qrValue: String) {
		viewModelScope.launch {
			_state.value = _state.value.copy(loading = true, message = null)
			val result = runCatching {
				withContext(Dispatchers.IO) {
					repository.import(qrValue)
					repository.snapshot()
				}
			}
			result.onSuccess { snapshot ->
				_state.value = MainUiState(
					totalCents = snapshot.totalCents,
					receipts = snapshot.receipts,
					categories = snapshot.categories,
					products = snapshot.products,
					message = "Bloček uložený.",
				)
			}.onFailure { error ->
				_state.value = _state.value.copy(
					loading = false,
					message = error.message ?: "Bloček sa nepodarilo načítať.",
				)
			}
		}
	}

	fun openReceipt(id: String) {
		viewModelScope.launch {
			val receipt = withContext(Dispatchers.IO) { repository.receipt(id) }
			_state.value = _state.value.copy(
				selectedReceipt = receipt,
				message = if (receipt == null) "Bloček sa nepodarilo otvoriť." else null,
			)
		}
	}

	fun closeReceipt() {
		_state.value = _state.value.copy(selectedReceipt = null)
	}

	fun showMessage(message: String) {
		_state.value = _state.value.copy(message = message)
	}

	private fun refresh() {
		viewModelScope.launch {
			val snapshot = withContext(Dispatchers.IO) { repository.snapshot() }
			_state.value = MainUiState(
				totalCents = snapshot.totalCents,
				receipts = snapshot.receipts,
				categories = snapshot.categories,
				products = snapshot.products,
			)
		}
	}
}
