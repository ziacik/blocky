package com.ziacik.blocky.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ziacik.blocky.data.BlockyDatabase
import com.ziacik.blocky.data.EkasaClient
import com.ziacik.blocky.data.EkasaReceiptParser
import com.ziacik.blocky.data.ReceiptRepository
import com.ziacik.blocky.data.RepositorySnapshot
import com.ziacik.blocky.data.wolt.WoltSessionStore
import com.ziacik.blocky.data.wolt.WoltSyncService
import com.ziacik.blocky.model.CategoryTotal
import com.ziacik.blocky.model.ItemListEntry
import com.ziacik.blocky.model.ProductTotal
import com.ziacik.blocky.model.Receipt
import com.ziacik.blocky.model.ReceiptSummary
import com.ziacik.blocky.normalization.HeuristicItemNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MainUiState(
	val loading: Boolean = false,
	val woltBusy: Boolean = false,
	val totalCents: Long = 0,
	val receipts: List<ReceiptSummary> = emptyList(),
	val categories: List<CategoryTotal> = emptyList(),
	val products: List<ProductTotal> = emptyList(),
	val allItems: List<ItemListEntry> = emptyList(),
	val selectedReceipt: Receipt? = null,
	val screen: MainScreen = MainScreen.Home,
	val woltConnected: Boolean = false,
	val woltDiagnostics: List<String> = emptyList(),
	val message: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
	private val repository = ReceiptRepository(
		client = EkasaClient(),
		parser = EkasaReceiptParser(HeuristicItemNormalizer()),
		database = BlockyDatabase(application),
	)
	private val woltSessionStore = WoltSessionStore(application)
	private val woltSyncService = WoltSyncService(application)
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
				applySnapshot(snapshot, "Bloček uložený.")
			}.onFailure { error ->
				_state.value = _state.value.copy(
					loading = false,
					woltConnected = woltSessionStore.isConnected(),
					message = error.message ?: "Bloček sa nepodarilo načítať.",
				)
			}
		}
	}

	fun downloadLatestWoltOrder() {
		if (_state.value.woltBusy) return

		_state.update {
			it.copy(
				woltBusy = true,
				woltDiagnostics = emptyList(),
				message = null,
			)
		}
		addWoltDiagnostic("START: sťahujem iba jednu najnovšiu objednávku")

		viewModelScope.launch {
			val result = runCatching {
				withContext(Dispatchers.IO) {
					val receipt = woltSyncService.importLatestOrder(::addWoltDiagnostic)
					receipt to repository.snapshot()
				}
			}

			result.onSuccess { (receipt, snapshot) ->
				if (receipt == null) {
					addWoltDiagnostic("DONE: nič sa neuložilo")
				} else {
					addWoltDiagnostic(
						"DONE: " + receipt.merchant +
							", items=" + receipt.items.size +
							", total=" + receipt.totalCents,
					)
				}
				applySnapshot(
					snapshot = snapshot,
					message = if (receipt == null) {
						"Wolt objednávka sa nenašla."
					} else {
						"Wolt objednávka uložená: " + receipt.merchant
					},
				)
				_state.update { it.copy(woltBusy = false) }
			}.onFailure { error ->
				addWoltDiagnostic(
					"FAIL: " + (error.message ?: error::class.java.simpleName),
				)
				_state.update {
					it.copy(
						woltBusy = false,
						woltConnected = woltSessionStore.isConnected(),
						message = error.message ?: "Wolt objednávku sa nepodarilo stiahnuť.",
					)
				}
			}
		}
	}

	fun clearWoltDiagnostics() {
		_state.update { it.copy(woltDiagnostics = emptyList()) }
	}

	fun openReceipt(receiptId: String) {
		val screen = MainNavigation.reduce(_state.value.screen, MainIntent.OpenReceipt(receiptId))
		_state.value = _state.value.copy(screen = screen, loading = true)
		viewModelScope.launch {
			val receipt = withContext(Dispatchers.IO) { repository.receipt(receiptId) }
			_state.value = _state.value.copy(
				loading = false,
				selectedReceipt = receipt,
				message = if (receipt == null) "Bloček sa nenašiel." else null,
			)
		}
	}

	fun openAllItems() {
		val screen = MainNavigation.reduce(_state.value.screen, MainIntent.OpenAllItems)
		_state.value = _state.value.copy(screen = screen, loading = true)
		viewModelScope.launch {
			val items = withContext(Dispatchers.IO) { repository.allItems() }
			_state.value = _state.value.copy(
				loading = false,
				allItems = items,
				message = null,
			)
		}
	}

	fun back() {
		_state.value = _state.value.copy(
			screen = MainNavigation.reduce(_state.value.screen, MainIntent.Back),
			selectedReceipt = null,
			message = null,
		)
	}

	fun showMessage(message: String) {
		_state.value = _state.value.copy(message = message)
	}

	fun refresh() {
		viewModelScope.launch {
			val snapshot = withContext(Dispatchers.IO) { repository.snapshot() }
			applySnapshot(snapshot)
		}
	}

	private fun addWoltDiagnostic(line: String) {
		val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
		_state.update { state ->
			state.copy(
				woltDiagnostics = (state.woltDiagnostics + "$timestamp  $line").takeLast(100),
			)
		}
	}

	private fun applySnapshot(
		snapshot: RepositorySnapshot,
		message: String? = _state.value.message,
	) {
		_state.update {
			it.copy(
				loading = false,
				totalCents = snapshot.totalCents,
				receipts = snapshot.receipts,
				categories = snapshot.categories,
				products = snapshot.products,
				woltConnected = woltSessionStore.isConnected(),
				message = message,
			)
		}
	}
}
