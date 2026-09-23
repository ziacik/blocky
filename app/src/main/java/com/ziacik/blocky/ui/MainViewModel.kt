package com.ziacik.blocky.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ziacik.blocky.BuildConfig
import com.ziacik.blocky.categorization.CategorizationPipeline
import com.ziacik.blocky.categorization.ReceiptIngestor
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
import com.ziacik.blocky.model.SpendingType
import com.ziacik.blocky.model.SpendingTypeTotal
import com.ziacik.blocky.model.SubcategoryTotal
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
	val subcategories: List<SubcategoryTotal> = emptyList(),
	val spendingTypes: List<SpendingTypeTotal> = emptyList(),
	val products: List<ProductTotal> = emptyList(),
	val allItems: List<ItemListEntry> = emptyList(),
	val summaryItems: List<ItemListEntry> = emptyList(),
	val selectedReceipt: Receipt? = null,
	val screen: MainScreen = MainScreen.Overview,
	val woltConnected: Boolean = false,
	val woltDiagnostics: List<String> = emptyList(),
	val message: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
	private val database = BlockyDatabase(application)
	private val categorizer = CategorizationPipeline.create(
		endpoint = BuildConfig.CATEGORIZATION_ENDPOINT,
		apiKey = BuildConfig.OPENAI_API_KEY,
	)
	private val repository = ReceiptRepository(
		client = EkasaClient(),
		parser = EkasaReceiptParser(HeuristicItemNormalizer()),
		database = database,
		ingestor = ReceiptIngestor(categorizer, database),
	)
	private val woltSessionStore = WoltSessionStore(application)
	private val woltSyncService = WoltSyncService(application, categorizer)
	private val _state = MutableStateFlow(MainUiState())
	val state: StateFlow<MainUiState> = _state.asStateFlow()

	init {
		refresh()
		if (BuildConfig.OPENAI_API_KEY.isNotBlank() || BuildConfig.CATEGORIZATION_ENDPOINT.isNotBlank()) {
			categorizePending()
		}
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

	fun downloadCurrentMonthWoltOrders() {
		if (_state.value.woltBusy) return

		_state.update {
			it.copy(
				woltBusy = true,
				woltDiagnostics = emptyList(),
				message = null,
			)
		}
		addWoltDiagnostic("START: sťahujem všetky objednávky aktuálneho mesiaca")

		viewModelScope.launch {
			val result = runCatching {
				withContext(Dispatchers.IO) {
					val imported = woltSyncService.importCurrentMonth(::addWoltDiagnostic)
					imported to repository.snapshot()
				}
			}

			result.onSuccess { (imported, snapshot) ->
				applySnapshot(
					snapshot = snapshot,
					message = "Wolt: importované objednávky tento mesiac: " + imported,
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
						message = error.message ?: "Wolt objednávky sa nepodarilo stiahnuť.",
					)
				}
			}
		}
	}

	private fun categorizePending() {
		viewModelScope.launch {
			val snapshot = withContext(Dispatchers.IO) {
				repository.categorizePending()
				repository.snapshot()
			}
			applySnapshot(snapshot)
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

	fun correctItemClassification(
		receiptId: String,
		itemIndex: Int,
		category: String,
		subcategory: String?,
		spendingType: SpendingType,
	) {
		viewModelScope.launch {
			val result = runCatching {
				withContext(Dispatchers.IO) {
					val receipt = repository.correctItemClassification(
						receiptId = receiptId,
						itemIndex = itemIndex,
						category = category,
						subcategory = subcategory,
						spendingType = spendingType,
					)
					receipt to repository.snapshot()
				}
			}

			result.onSuccess { (receipt, snapshot) ->
				applySnapshot(snapshot, "Kategória upravená.")
				_state.update { it.copy(selectedReceipt = receipt) }
			}.onFailure { error ->
				_state.update {
					it.copy(message = error.message ?: "Kategóriu sa nepodarilo upraviť.")
				}
			}
		}
	}

	fun openOverview() {
		_state.update {
			it.copy(
				screen = MainNavigation.reduce(it.screen, MainIntent.OpenOverview),
				message = null,
			)
		}
	}

	fun openReceipts() {
		_state.update {
			it.copy(
				screen = MainNavigation.reduce(it.screen, MainIntent.OpenReceipts),
				message = null,
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

	fun openBreakdown(dimension: BreakdownDimension) {
		_state.update {
			it.copy(
				screen = MainNavigation.reduce(it.screen, MainIntent.OpenBreakdown(dimension)),
				message = null,
			)
		}
	}

	fun openSummary(filter: SummaryFilter) {
		val screen = MainNavigation.reduce(_state.value.screen, MainIntent.OpenSummary(filter))
		_state.value = _state.value.copy(
			screen = screen,
			loading = true,
			summaryItems = emptyList(),
		)
		viewModelScope.launch {
			val items = withContext(Dispatchers.IO) { repository.allItems() }
			_state.value = _state.value.copy(
				loading = false,
				summaryItems = SummaryItems.filter(items, filter),
				message = null,
			)
		}
	}

	fun openSettings() {
		_state.update {
			it.copy(
				screen = MainNavigation.reduce(it.screen, MainIntent.OpenSettings),
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

	override fun onCleared() {
		database.close()
		super.onCleared()
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
				subcategories = snapshot.subcategories,
				spendingTypes = snapshot.spendingTypes,
				products = snapshot.products,
				woltConnected = woltSessionStore.isConnected(),
				message = message,
			)
		}
	}
}
