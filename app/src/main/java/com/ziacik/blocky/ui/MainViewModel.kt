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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MainUiState(
	val loading: Boolean = false,
	val totalCents: Long = 0,
	val receipts: List<ReceiptSummary> = emptyList(),
	val categories: List<CategoryTotal> = emptyList(),
	val products: List<ProductTotal> = emptyList(),
	val allItems: List<ItemListEntry> = emptyList(),
	val selectedReceipt: Receipt? = null,
	val screen: MainScreen = MainScreen.Home,
	val woltConnected: Boolean = false,
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

	fun syncWolt() {
		viewModelScope.launch {
			_state.value = _state.value.copy(loading = true, message = null)
			val result = runCatching {
				withContext(Dispatchers.IO) {
					woltSyncService.sync()
					repository.snapshot()
				}
			}
			result.onSuccess { snapshot ->
				applySnapshot(snapshot, "Wolt synchronizovaný.")
			}.onFailure { error ->
				_state.value = _state.value.copy(
					loading = false,
					woltConnected = woltSessionStore.isConnected(),
					message = error.message ?: "Wolt sa nepodarilo synchronizovať.",
				)
			}
		}
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

	private fun applySnapshot(snapshot: RepositorySnapshot, message: String? = _state.value.message) {
		_state.value = _state.value.copy(
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
