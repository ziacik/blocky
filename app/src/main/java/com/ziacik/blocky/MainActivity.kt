package com.ziacik.blocky

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.ziacik.blocky.categorization.ExpenseTaxonomy
import com.ziacik.blocky.data.wolt.WoltSyncScheduler
import com.ziacik.blocky.model.CategoryTotal
import com.ziacik.blocky.model.ItemListEntry
import com.ziacik.blocky.model.Receipt
import com.ziacik.blocky.model.ReceiptItem
import com.ziacik.blocky.model.ReceiptSummary
import com.ziacik.blocky.model.SpendingType
import com.ziacik.blocky.model.SpendingTypeTotal
import com.ziacik.blocky.model.SubcategoryTotal
import com.ziacik.blocky.ui.BreakdownDimension
import com.ziacik.blocky.ui.BreakdownTotal
import com.ziacik.blocky.ui.ClassificationEditorState
import com.ziacik.blocky.ui.HomeOverlay
import com.ziacik.blocky.ui.HomeOverlayIntent
import com.ziacik.blocky.ui.HomeOverlayNavigation
import com.ziacik.blocky.ui.MainScreen
import com.ziacik.blocky.ui.MainUiState
import com.ziacik.blocky.ui.MainViewModel
import com.ziacik.blocky.ui.SummaryBreakdown
import com.ziacik.blocky.ui.SummaryFilter
import com.ziacik.blocky.ui.SummaryItems
import com.ziacik.blocky.ui.theme.BlockyTheme
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF171513)
private val DockMuted = Color(0xFF8F887F)
private val CategoryColors = listOf(
	Color(0xFFFF5533),
	Color(0xFFD99A38),
	Color(0xFFB66C62),
	Color(0xFF856B75),
	Color(0xFF4F4943),
)

class MainActivity : ComponentActivity() {
	private val viewModel: MainViewModel by viewModels()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()

		WoltSyncScheduler.disable(this)

		val scanner = GmsBarcodeScanning.getClient(
			this,
			GmsBarcodeScannerOptions.Builder()
				.setBarcodeFormats(Barcode.FORMAT_QR_CODE)
				.enableAutoZoom()
				.build(),
		)

		setContent {
			BlockyTheme {
				val state by viewModel.state.collectAsState()

				val scan: () -> Unit = {
					scanner.startScan()
						.addOnSuccessListener { barcode ->
							barcode.rawValue?.let(viewModel::importReceipt)
								?: viewModel.showMessage("QR kód neobsahuje text.")
						}
						.addOnFailureListener { error ->
							viewModel.showMessage(error.message ?: "Skenovanie zlyhalo.")
						}
					Unit
				}

				BackHandler(
					enabled = state.screen is MainScreen.ReceiptDetail ||
						state.screen is MainScreen.Breakdown ||
						state.screen is MainScreen.SummaryItems ||
						state.screen == MainScreen.Settings,
				) {
					viewModel.back()
				}

				when (val screen = state.screen) {
					MainScreen.Overview -> PrimaryShell(
						state = state,
						selected = MainScreen.Overview,
						onOverview = viewModel::openOverview,
						onReceipts = viewModel::openReceipts,
						onItems = viewModel::openAllItems,
						onSettings = viewModel::openSettings,
						onScan = scan,
					) { contentPadding ->
						OverviewScreen(
							state = state,
							contentPadding = contentPadding,
							onReceipt = viewModel::openReceipt,
							onSummary = viewModel::openSummary,
							onBreakdown = viewModel::openBreakdown,
						)
					}

					MainScreen.Receipts -> PrimaryShell(
						state = state,
						selected = MainScreen.Receipts,
						onOverview = viewModel::openOverview,
						onReceipts = viewModel::openReceipts,
						onItems = viewModel::openAllItems,
						onSettings = viewModel::openSettings,
						onScan = scan,
					) { contentPadding ->
						ReceiptsScreen(
							state = state,
							contentPadding = contentPadding,
							onReceipt = viewModel::openReceipt,
						)
					}

					MainScreen.AllItems -> PrimaryShell(
						state = state,
						selected = MainScreen.AllItems,
						onOverview = viewModel::openOverview,
						onReceipts = viewModel::openReceipts,
						onItems = viewModel::openAllItems,
						onSettings = viewModel::openSettings,
						onScan = scan,
					) { contentPadding ->
						ItemsScreen(
							state = state,
							contentPadding = contentPadding,
							onReceipt = viewModel::openReceipt,
						)
					}

					is MainScreen.Breakdown -> BreakdownScreen(
						state = state,
						dimension = screen.dimension,
						onBack = viewModel::back,
						onSummary = viewModel::openSummary,
					)

					is MainScreen.SummaryItems -> SummaryItemsScreen(
						state = state,
						filter = screen.filter,
						onBack = viewModel::back,
						onReceipt = viewModel::openReceipt,
					)

					MainScreen.Settings -> SettingsScreen(
						state = state,
						onBack = viewModel::back,
						onConnectWolt = {
							startActivity(Intent(this, WoltLoginActivity::class.java))
						},
						onSyncWolt = viewModel::downloadCurrentMonthWoltOrders,
						onClearDiagnostics = viewModel::clearWoltDiagnostics,
					)

					is MainScreen.ReceiptDetail -> ReceiptDetailScreen(
						state = state,
						onBack = viewModel::back,
						onCorrectItem = viewModel::correctItemClassification,
					)
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()
		viewModel.refresh()
	}
}

@Composable
private fun PrimaryShell(
	state: MainUiState,
	selected: MainScreen,
	onOverview: () -> Unit,
	onReceipts: () -> Unit,
	onItems: () -> Unit,
	onSettings: () -> Unit,
	onScan: () -> Unit,
	content: @Composable (PaddingValues) -> Unit,
) {
	Scaffold(
		containerColor = MaterialTheme.colorScheme.background,
		topBar = {
			BrandHeader(onSettings = onSettings)
		},
		bottomBar = {
			BlockyDock(
				selected = selected,
				scanBusy = state.loading,
				onOverview = onOverview,
				onReceipts = onReceipts,
				onItems = onItems,
				onScan = onScan,
			)
		},
		content = content,
	)
}

@Composable
private fun BrandHeader(onSettings: () -> Unit) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.statusBarsPadding()
			.height(64.dp)
			.padding(start = 20.dp, end = 4.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Box(
			modifier = Modifier
				.width(8.dp)
				.height(25.dp)
				.background(MaterialTheme.colorScheme.primary),
		)
		Spacer(modifier = Modifier.width(9.dp))
		Text(
			"BLOČKY",
			modifier = Modifier.weight(1f),
			fontSize = 21.sp,
			lineHeight = 24.sp,
			fontWeight = FontWeight.Black,
			letterSpacing = (-0.6).sp,
		)
		IconButton(
			onClick = onSettings,
			modifier = Modifier.size(56.dp),
		) {
			Icon(
				imageVector = Icons.Rounded.Settings,
				contentDescription = "Nastavenia",
				modifier = Modifier.size(23.dp),
			)
		}
	}
}

@Composable
private fun BlockyDock(
	selected: MainScreen,
	scanBusy: Boolean,
	onOverview: () -> Unit,
	onReceipts: () -> Unit,
	onItems: () -> Unit,
	onScan: () -> Unit,
) {
	Surface(color = Ink) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.height(68.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			DockItem(
				label = "PREHĽAD",
				selected = selected == MainScreen.Overview,
				onClick = onOverview,
			)
			DockItem(
				label = "BLOČKY",
				selected = selected == MainScreen.Receipts,
				onClick = onReceipts,
			)
			DockItem(
				label = "POLOŽKY",
				selected = selected == MainScreen.AllItems,
				onClick = onItems,
			)
			Box(
				modifier = Modifier
					.width(68.dp)
					.height(68.dp)
					.background(MaterialTheme.colorScheme.primary)
					.clickable(enabled = !scanBusy, onClick = onScan),
				contentAlignment = Alignment.Center,
			) {
				if (scanBusy) {
					CircularProgressIndicator(
						modifier = Modifier.size(22.dp),
						strokeWidth = 2.dp,
						color = Color.White,
					)
				} else {
					Icon(
						imageVector = Icons.Rounded.QrCodeScanner,
						contentDescription = "Skenovať bloček",
						modifier = Modifier.size(27.dp),
						tint = Color.White,
					)
				}
			}
		}
	}
}

@Composable
private fun RowScope.DockItem(
	label: String,
	selected: Boolean,
	onClick: () -> Unit,
) {
	Box(
		modifier = Modifier
			.weight(1f)
			.height(68.dp)
			.clickable(onClick = onClick),
		contentAlignment = Alignment.Center,
	) {
		if (selected) {
			Box(
				modifier = Modifier
					.align(Alignment.TopCenter)
					.fillMaxWidth()
					.height(3.dp)
					.background(MaterialTheme.colorScheme.primary),
			)
		}
		Text(
			label,
			style = MaterialTheme.typography.labelMedium,
			color = if (selected) Color.White else DockMuted,
		)
	}
}

@Composable
private fun OverviewScreen(
	state: MainUiState,
	contentPadding: PaddingValues,
	onReceipt: (String) -> Unit,
	onSummary: (SummaryFilter) -> Unit,
	onBreakdown: (BreakdownDimension) -> Unit,
) {
	LazyColumn(
		modifier = Modifier
			.fillMaxSize()
			.padding(contentPadding),
		contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 34.dp),
		verticalArrangement = Arrangement.spacedBy(28.dp),
	) {
		item {
			Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
				Text(
					"EVIDOVANÉ VÝDAVKY",
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				Text(
					money(state.totalCents),
					style = MaterialTheme.typography.headlineLarge,
				)
				Text(
					"Výdavky z evidovaných nákupov",
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
		}

		state.message?.let { message ->
			item {
				StatusMessage(message)
			}
		}

		if (state.categories.isNotEmpty()) {
			item {
				CategoryBreakdown(
					categories = state.categories,
					subcategories = state.subcategories,
					spendingTypes = state.spendingTypes,
					totalCents = state.totalCents,
					onSummary = onSummary,
					onShowAll = onBreakdown,
				)
			}
		}

		if (state.receipts.isNotEmpty()) {
			item {
				Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
					SectionLabel("POSLEDNÉ BLOČKY")
					state.receipts.take(5).forEachIndexed { index, receipt ->
						ReceiptRow(
							receipt = receipt,
							onClick = { onReceipt(receipt.id) },
						)
						if (index != minOf(4, state.receipts.lastIndex)) {
							FlatDivider()
						}
					}
				}
			}
		}
	}
}

@Composable
private fun CategoryBreakdown(
	categories: List<CategoryTotal>,
	subcategories: List<SubcategoryTotal>,
	spendingTypes: List<SpendingTypeTotal>,
	totalCents: Long,
	onSummary: (SummaryFilter) -> Unit,
	onShowAll: (BreakdownDimension) -> Unit,
) {
	var dimension by remember { mutableStateOf(BreakdownDimension.Category) }
	val totals = SummaryBreakdown.totals(
		categories = categories,
		subcategories = subcategories,
		spendingTypes = spendingTypes,
		dimension = dimension,
	)
	val chartEntries = totals.take(5)

	Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
		SectionLabel("KAM IŠLI PENIAZE")

		Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			FilterChip(
				selected = dimension == BreakdownDimension.Category,
				onClick = { dimension = BreakdownDimension.Category },
				label = { Text("Kategórie") },
			)
			FilterChip(
				selected = dimension == BreakdownDimension.Subcategory,
				onClick = { dimension = BreakdownDimension.Subcategory },
				label = { Text("Podkategórie") },
			)
			FilterChip(
				selected = dimension == BreakdownDimension.SpendingType,
				onClick = { dimension = BreakdownDimension.SpendingType },
				label = { Text("Typ") },
			)
		}

		if (chartEntries.isNotEmpty()) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.height(14.dp),
			) {
				chartEntries.forEachIndexed { index, entry ->
					Box(
						modifier = Modifier
							.weight(entry.totalCents.toFloat().coerceAtLeast(1f))
							.height(14.dp)
							.background(CategoryColors[index % CategoryColors.size]),
					)
				}
			}
		}

		totals.take(4).forEachIndexed { index, entry ->
			BreakdownRow(
				entry = entry,
				index = index,
				totalCents = totalCents,
				onSummary = onSummary,
			)
		}

		if (totals.size > 4) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.End,
			) {
				TextButton(onClick = { onShowAll(dimension) }) {
					Text("Zobraziť všetky")
				}
			}
		}
	}
}

@Composable
private fun BreakdownScreen(
	state: MainUiState,
	dimension: BreakdownDimension,
	onBack: () -> Unit,
	onSummary: (SummaryFilter) -> Unit,
) {
	val totals = SummaryBreakdown.totals(
		categories = state.categories,
		subcategories = state.subcategories,
		spendingTypes = state.spendingTypes,
		dimension = dimension,
	)

	Scaffold(
		containerColor = MaterialTheme.colorScheme.background,
		topBar = {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.statusBarsPadding()
					.height(64.dp)
					.padding(start = 8.dp, end = 20.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				IconButton(onClick = onBack) {
					Icon(
						imageVector = Icons.Rounded.ArrowBack,
						contentDescription = "Späť",
					)
				}
				Text(
					SummaryBreakdown.title(dimension),
					style = MaterialTheme.typography.titleLarge,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
		},
	) { contentPadding ->
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(contentPadding),
			contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 34.dp),
		) {
			itemsIndexed(totals) { index, entry ->
				BreakdownRow(
					entry = entry,
					index = index,
					totalCents = state.totalCents,
					onSummary = onSummary,
				)
				if (index != totals.lastIndex) {
					FlatDivider()
				}
			}
		}
	}
}

@Composable
private fun BreakdownRow(
	entry: BreakdownTotal,
	index: Int,
	totalCents: Long,
	onSummary: (SummaryFilter) -> Unit,
) {
	val percentage = if (totalCents > 0) {
		entry.totalCents * 100.0 / totalCents
	} else {
		0.0
	}

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clickable { onSummary(entry.filter) }
			.padding(vertical = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Box(
			modifier = Modifier
				.size(10.dp)
				.background(CategoryColors[index % CategoryColors.size]),
		)
		Spacer(modifier = Modifier.width(10.dp))
		Text(
			entry.label,
			modifier = Modifier.weight(1f),
			style = MaterialTheme.typography.bodyMedium,
			fontWeight = FontWeight.Medium,
		)
		Text(
			String.format(Locale("sk", "SK"), "%.0f%%", percentage),
			modifier = Modifier.width(42.dp),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			textAlign = TextAlign.End,
		)
		Spacer(modifier = Modifier.width(12.dp))
		Text(
			money(entry.totalCents),
			modifier = Modifier.widthIn(min = 78.dp),
			style = MaterialTheme.typography.bodyMedium,
			fontWeight = FontWeight.Bold,
			textAlign = TextAlign.End,
		)
		Icon(
			imageVector = Icons.Rounded.ChevronRight,
			contentDescription = "Zobraziť položky",
			modifier = Modifier
				.padding(start = 7.dp)
				.size(18.dp),
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

@Composable
private fun ReceiptsScreen(
	state: MainUiState,
	contentPadding: PaddingValues,
	onReceipt: (String) -> Unit,
) {
	LazyColumn(
		modifier = Modifier
			.fillMaxSize()
			.padding(contentPadding),
		contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 34.dp),
	) {
		item {
			Text(
				"HISTÓRIA BLOČKOV",
				style = MaterialTheme.typography.headlineMedium,
				modifier = Modifier.padding(bottom = 22.dp),
			)
		}

		items(
			items = state.receipts,
			key = { it.id },
		) { receipt ->
			ReceiptRow(
				receipt = receipt,
				onClick = { onReceipt(receipt.id) },
			)
			FlatDivider()
		}
	}
}

@Composable
private fun ItemsScreen(
	state: MainUiState,
	contentPadding: PaddingValues,
	onReceipt: (String) -> Unit,
) {
	LazyColumn(
		modifier = Modifier
			.fillMaxSize()
			.padding(contentPadding),
		contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 34.dp),
	) {
		item {
			Text(
				"VŠETKY POLOŽKY",
				style = MaterialTheme.typography.headlineMedium,
				modifier = Modifier.padding(bottom = 22.dp),
			)
		}

		if (state.loading && state.allItems.isEmpty()) {
			item {
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 48.dp),
					contentAlignment = Alignment.Center,
				) {
					CircularProgressIndicator()
				}
			}
		}

		items(state.allItems) { item ->
			ItemRow(
				item = item,
				onClick = { onReceipt(item.receiptId) },
			)
			FlatDivider()
		}
	}
}

@Composable
private fun SummaryItemsScreen(
	state: MainUiState,
	filter: SummaryFilter,
	onBack: () -> Unit,
	onReceipt: (String) -> Unit,
) {
	Scaffold(
		containerColor = MaterialTheme.colorScheme.background,
		topBar = {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.statusBarsPadding()
					.height(64.dp)
					.padding(start = 8.dp, end = 20.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				IconButton(onClick = onBack) {
					Icon(
						imageVector = Icons.Rounded.ArrowBack,
						contentDescription = "Späť",
					)
				}
				Text(
					SummaryItems.title(filter),
					style = MaterialTheme.typography.titleLarge,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
		},
	) { contentPadding ->
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(contentPadding),
			contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 34.dp),
		) {
			if (state.loading && state.summaryItems.isEmpty()) {
				item {
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.padding(top = 48.dp),
						contentAlignment = Alignment.Center,
					) {
						CircularProgressIndicator()
					}
				}
			}

			items(state.summaryItems) { item ->
				ItemRow(
					item = item,
					onClick = { onReceipt(item.receiptId) },
				)
				FlatDivider()
			}
		}
	}
}

@Composable
private fun SettingsScreen(
	state: MainUiState,
	onBack: () -> Unit,
	onConnectWolt: () -> Unit,
	onSyncWolt: () -> Unit,
	onClearDiagnostics: () -> Unit,
) {
	var overlay by remember { mutableStateOf(HomeOverlay.None) }

	Scaffold(
		containerColor = MaterialTheme.colorScheme.background,
		topBar = {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.statusBarsPadding()
					.height(64.dp)
					.padding(start = 8.dp, end = 20.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				IconButton(onClick = onBack) {
					Icon(
						imageVector = Icons.Rounded.ArrowBack,
						contentDescription = "Späť",
					)
				}
				Text(
					"NASTAVENIA",
					style = MaterialTheme.typography.titleLarge,
				)
			}
		},
	) { contentPadding ->
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(contentPadding),
			contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp),
			verticalArrangement = Arrangement.spacedBy(30.dp),
		) {
			item {
				Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
					SectionLabel("ZDROJE")
					SettingsRow(
						title = "Wolt",
						subtitle = if (state.woltConnected) "Pripojený" else "Nepripojený",
						action = when {
							state.woltBusy -> "Synchronizujem…"
							state.woltConnected -> "Synchronizovať"
							else -> "Pripojiť"
						},
						enabled = !state.woltBusy,
						onClick = if (state.woltConnected) onSyncWolt else onConnectWolt,
					)
				}
			}

			item {
				Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
					SectionLabel("TECHNICKÉ")
					SettingsRow(
						title = "Wolt diagnostika",
						subtitle = if (state.woltDiagnostics.isEmpty()) {
							"Žiadne záznamy"
						} else {
							"${state.woltDiagnostics.size} záznamov"
						},
						action = "Otvoriť",
						onClick = {
							overlay = HomeOverlayNavigation.reduce(
								overlay,
								HomeOverlayIntent.OpenDiagnostics,
							)
						},
					)
				}
			}
		}
	}

	if (overlay == HomeOverlay.Diagnostics) {
		DiagnosticsDialog(
			lines = state.woltDiagnostics,
			onClear = onClearDiagnostics,
			onDismiss = {
				overlay = HomeOverlayNavigation.reduce(
					overlay,
					HomeOverlayIntent.Close,
				)
			},
		)
	}
}

@Composable
private fun SettingsRow(
	title: String,
	subtitle: String,
	action: String,
	enabled: Boolean = true,
	onClick: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(enabled = enabled, onClick = onClick)
			.padding(vertical = 12.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Column(
			modifier = Modifier
				.weight(1f)
				.padding(end = 16.dp),
		) {
			Text(
				title,
				style = MaterialTheme.typography.bodyLarge,
				fontWeight = FontWeight.Bold,
			)
			Text(
				subtitle,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		Text(
			action.uppercase(Locale("sk", "SK")),
			style = MaterialTheme.typography.labelMedium,
			color = if (enabled) {
				MaterialTheme.colorScheme.primary
			} else {
				MaterialTheme.colorScheme.onSurfaceVariant
			},
		)
	}
}

private data class IndexedReceiptItem(
	val index: Int,
	val item: ReceiptItem,
)

@Composable
private fun ReceiptDetailScreen(
	state: MainUiState,
	onBack: () -> Unit,
	onCorrectItem: (String, Int, String, String?, SpendingType) -> Unit,
) {
	var editingItem by remember { mutableStateOf<IndexedReceiptItem?>(null) }

	Scaffold(
		containerColor = MaterialTheme.colorScheme.background,
		topBar = {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(start = 8.dp, top = 12.dp, end = 20.dp, bottom = 8.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				IconButton(onClick = onBack) {
					Icon(
						imageVector = Icons.Rounded.ArrowBack,
						contentDescription = "Späť",
					)
				}
				SectionLabel("DETAIL BLOČKU")
			}
		},
	) { contentPadding ->
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(contentPadding),
			contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 34.dp),
			verticalArrangement = Arrangement.spacedBy(28.dp),
		) {
			if (state.loading && state.selectedReceipt == null) {
				item {
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.padding(top = 48.dp),
						contentAlignment = Alignment.Center,
					) {
						CircularProgressIndicator()
					}
				}
			}

			state.selectedReceipt?.let { receipt ->
				item {
					ReceiptHeader(receipt)
				}

				item {
					Column {
						SectionLabel("POLOŽKY")
						Spacer(modifier = Modifier.height(10.dp))
						receipt.items.forEachIndexed { index, item ->
							ReceiptItemRow(
								item = item,
								onClick = { editingItem = IndexedReceiptItem(index, item) },
							)
							if (index != receipt.items.lastIndex) {
								FlatDivider()
							}
						}
					}
				}
			}

			state.message?.let { message ->
				item {
					StatusMessage(message)
				}
			}
		}
	}

	editingItem?.let { editing ->
		ClassificationDialog(
			item = editing.item,
			onDismiss = { editingItem = null },
			onSave = { category, subcategory, spendingType ->
				val receiptId = state.selectedReceipt?.id
				if (receiptId != null) {
					onCorrectItem(
						receiptId,
						editing.index,
						category,
						subcategory,
						spendingType,
					)
				}
				editingItem = null
			},
		)
	}
}

@Composable
private fun ReceiptHeader(receipt: Receipt) {
	Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
		Text(
			receiptSource(receipt.id),
			style = MaterialTheme.typography.labelMedium,
			color = MaterialTheme.colorScheme.primary,
		)
		Text(
			receipt.merchant,
			style = MaterialTheme.typography.headlineMedium,
		)
		Text(
			DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
				.format(Date(receipt.issuedAt)),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Spacer(modifier = Modifier.height(8.dp))
		Text(
			money(receipt.totalCents),
			style = MaterialTheme.typography.headlineLarge,
		)
	}
}

@Composable
private fun ReceiptRow(
	receipt: ReceiptSummary,
	onClick: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick)
			.padding(vertical = 14.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Column(
			modifier = Modifier
				.weight(1f)
				.padding(end = 12.dp),
			verticalArrangement = Arrangement.spacedBy(3.dp),
		) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text(
					receiptSource(receipt.id),
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.primary,
				)
				Spacer(modifier = Modifier.width(8.dp))
				Text(
					DateFormat.getDateInstance(DateFormat.SHORT).format(Date(receipt.issuedAt)),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
			Text(
				receipt.merchant,
				style = MaterialTheme.typography.bodyLarge,
				fontWeight = FontWeight.Bold,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
		Text(
			money(receipt.totalCents),
			modifier = Modifier.widthIn(min = 82.dp),
			textAlign = TextAlign.End,
			fontWeight = FontWeight.Bold,
		)
		Icon(
			imageVector = Icons.Rounded.ChevronRight,
			contentDescription = null,
			modifier = Modifier
				.padding(start = 7.dp)
				.size(18.dp),
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

@Composable
private fun ItemRow(
	item: ItemListEntry,
	onClick: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick)
			.padding(vertical = 14.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Column(
			modifier = Modifier
				.weight(1f)
				.padding(end = 12.dp),
			verticalArrangement = Arrangement.spacedBy(3.dp),
		) {
			Text(
				item.originalName,
				style = MaterialTheme.typography.bodyLarge,
				fontWeight = FontWeight.Bold,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
			)
			Text(
				item.category.uppercase(Locale("sk", "SK")) +
					"  ·  " + item.merchant,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
		Text(
			money(item.totalCents),
			modifier = Modifier.widthIn(min = 82.dp),
			textAlign = TextAlign.End,
			fontWeight = FontWeight.Bold,
		)
	}
}

@Composable
private fun ReceiptItemRow(
	item: ReceiptItem,
	onClick: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick)
			.padding(vertical = 14.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Column(
			modifier = Modifier
				.weight(1f)
				.padding(end = 16.dp),
			verticalArrangement = Arrangement.spacedBy(3.dp),
		) {
			Text(
				item.originalName,
				style = MaterialTheme.typography.bodyLarge,
				fontWeight = FontWeight.Bold,
				maxLines = 3,
				overflow = TextOverflow.Ellipsis,
			)
			Text(
				itemMeta(item),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		Text(
			money(item.totalCents),
			modifier = Modifier.widthIn(min = 82.dp),
			textAlign = TextAlign.End,
			fontWeight = FontWeight.Bold,
		)
		Icon(
			imageVector = Icons.Rounded.ChevronRight,
			contentDescription = "Upraviť zaradenie",
			modifier = Modifier
				.padding(start = 7.dp)
				.size(18.dp),
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

@Composable
private fun ClassificationDialog(
	item: ReceiptItem,
	onDismiss: () -> Unit,
	onSave: (String, String?, SpendingType) -> Unit,
) {
	var editor by remember(item) {
		mutableStateOf(ClassificationEditorState.from(item))
	}
	var categoryExpanded by remember { mutableStateOf(false) }
	var subcategoryExpanded by remember { mutableStateOf(false) }

	AlertDialog(
		onDismissRequest = onDismiss,
		title = {
			Text("Upraviť zaradenie")
		},
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
				Text(
					item.originalName,
					style = MaterialTheme.typography.bodyLarge,
					fontWeight = FontWeight.Bold,
				)

				SectionLabel("KATEGÓRIA")
				Box {
					OutlinedButton(
						onClick = { categoryExpanded = true },
						modifier = Modifier.fillMaxWidth(),
					) {
						Text(editor.category)
					}
					DropdownMenu(
						expanded = categoryExpanded,
						onDismissRequest = { categoryExpanded = false },
					) {
						ExpenseTaxonomy.categories.keys.forEach { category ->
							DropdownMenuItem(
								text = { Text(category) },
								onClick = {
									editor = editor.selectCategory(category)
									categoryExpanded = false
								},
							)
						}
					}
				}

				SectionLabel("PODKATEGÓRIA")
				Box {
					OutlinedButton(
						onClick = { subcategoryExpanded = true },
						modifier = Modifier.fillMaxWidth(),
					) {
						Text(editor.subcategory ?: "Bez podkategórie")
					}
					DropdownMenu(
						expanded = subcategoryExpanded,
						onDismissRequest = { subcategoryExpanded = false },
					) {
						ExpenseTaxonomy.categories[editor.category].orEmpty().forEach { subcategory ->
							DropdownMenuItem(
								text = { Text(subcategory) },
								onClick = {
									editor = editor.selectSubcategory(subcategory)
									subcategoryExpanded = false
								},
							)
						}
					}
				}

				SectionLabel("TYP VÝDAVKU")
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(6.dp),
				) {
					SpendingType.entries.forEach { spendingType ->
						FilterChip(
							selected = editor.spendingType == spendingType,
							onClick = { editor = editor.selectSpendingType(spendingType) },
							label = { Text(spendingTypeLabel(spendingType)) },
						)
					}
				}
			}
		},
		confirmButton = {
			TextButton(
				onClick = {
					onSave(editor.category, editor.subcategory, editor.spendingType)
				},
			) {
				Text("Uložiť")
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text("Zrušiť")
			}
		},
	)
}

@Composable
private fun StatusMessage(message: String) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Box(
			modifier = Modifier
				.width(4.dp)
				.height(34.dp)
				.background(MaterialTheme.colorScheme.primary),
		)
		Spacer(modifier = Modifier.width(10.dp))
		Text(
			message,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

@Composable
private fun SectionLabel(value: String) {
	Text(
		value,
		style = MaterialTheme.typography.labelMedium,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
	)
}

@Composable
private fun FlatDivider() {
	HorizontalDivider(
		color = MaterialTheme.colorScheme.outlineVariant,
	)
}

@Composable
private fun DiagnosticsDialog(
	lines: List<String>,
	onClear: () -> Unit,
	onDismiss: () -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = {
			Text("Wolt diagnostika")
		},
		text = {
			if (lines.isEmpty()) {
				Text(
					"Žiadne diagnostické záznamy.",
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			} else {
				LazyColumn(
					modifier = Modifier.heightIn(max = 360.dp),
					verticalArrangement = Arrangement.spacedBy(7.dp),
				) {
					items(lines) { line ->
						Text(
							text = line,
							style = MaterialTheme.typography.bodySmall,
							fontFamily = FontFamily.Monospace,
						)
					}
				}
			}
		},
		confirmButton = {
			TextButton(onClick = onDismiss) {
				Text("Zavrieť")
			}
		},
		dismissButton = {
			if (lines.isNotEmpty()) {
				TextButton(onClick = onClear) {
					Text("Vymazať")
				}
			}
		},
	)
}

private fun receiptSource(id: String): String = if (id.startsWith("wolt:")) {
	"WOLT"
} else {
	"E-KASA"
}

private fun itemMeta(item: ReceiptItem): String {
	val quantityText = if (item.quantity == item.quantity.toLong().toDouble()) {
		item.quantity.toLong().toString()
	} else {
		String.format(Locale.US, "%.2f", item.quantity).trimEnd('0').trimEnd('.')
	}
	val classification = buildList {
		add(item.category)
		item.subcategory?.let(::add)
		item.spendingType?.let { add(spendingTypeLabel(it)) }
	}.joinToString("  ·  ")
	return quantityText + "×  ·  " + classification.uppercase(Locale("sk", "SK"))
}

private fun spendingTypeLabel(spendingType: SpendingType): String = when (spendingType) {
	SpendingType.ESSENTIAL -> "Nevyhnutné"
	SpendingType.REGULAR -> "Bežné"
	SpendingType.DISCRETIONARY -> "Voliteľné"
}

private fun money(cents: Long): String = NumberFormat.getCurrencyInstance(Locale("sk", "SK"))
	.format(cents / 100.0)
