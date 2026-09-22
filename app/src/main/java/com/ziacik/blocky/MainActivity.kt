package com.ziacik.blocky

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.ziacik.blocky.data.wolt.WoltSyncScheduler
import com.ziacik.blocky.model.CategoryTotal
import com.ziacik.blocky.model.ItemListEntry
import com.ziacik.blocky.model.ProductTotal
import com.ziacik.blocky.model.Receipt
import com.ziacik.blocky.model.ReceiptItem
import com.ziacik.blocky.model.ReceiptSummary
import com.ziacik.blocky.ui.HomeOverlay
import com.ziacik.blocky.ui.HomeOverlayIntent
import com.ziacik.blocky.ui.HomeOverlayNavigation
import com.ziacik.blocky.ui.MainScreen
import com.ziacik.blocky.ui.MainUiState
import com.ziacik.blocky.ui.MainViewModel
import com.ziacik.blocky.ui.WoltAction
import com.ziacik.blocky.ui.WoltControls
import com.ziacik.blocky.ui.theme.BlockyTheme
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

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

				BackHandler(enabled = state.screen != MainScreen.Home) {
					viewModel.back()
				}

				when (state.screen) {
					MainScreen.Home -> BlockyHome(
						state = state,
						onScan = {
							scanner.startScan()
								.addOnSuccessListener { barcode ->
									barcode.rawValue?.let(viewModel::importReceipt)
										?: viewModel.showMessage("QR kód neobsahuje text.")
								}
								.addOnFailureListener { error ->
									viewModel.showMessage(error.message ?: "Skenovanie zlyhalo.")
								}
						},
						onConnectWolt = {
							startActivity(Intent(this, WoltLoginActivity::class.java))
						},
						onCurrentMonthWolt = viewModel::downloadCurrentMonthWoltOrders,
						onClearWoltDiagnostics = viewModel::clearWoltDiagnostics,
						onReceipt = viewModel::openReceipt,
						onAllItems = viewModel::openAllItems,
					)

					MainScreen.AllItems -> AllItemsScreen(
						state = state,
						onBack = viewModel::back,
						onReceipt = viewModel::openReceipt,
					)

					is MainScreen.ReceiptDetail -> ReceiptDetailScreen(
						state = state,
						onBack = viewModel::back,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockyHome(
	state: MainUiState,
	onScan: () -> Unit,
	onConnectWolt: () -> Unit,
	onCurrentMonthWolt: () -> Unit,
	onClearWoltDiagnostics: () -> Unit,
	onReceipt: (String) -> Unit,
	onAllItems: () -> Unit,
) {
	var menuExpanded by remember { mutableStateOf(false) }
	var overlay by remember { mutableStateOf(HomeOverlay.None) }

	Scaffold(
		containerColor = MaterialTheme.colorScheme.background,
		topBar = {
			TopAppBar(
				title = {
					Text(
						"Bločky",
						style = MaterialTheme.typography.titleLarge,
						fontWeight = FontWeight.Bold,
					)
				},
				colors = TopAppBarDefaults.topAppBarColors(
					containerColor = MaterialTheme.colorScheme.background,
				),
				actions = {
					Box {
						IconButton(onClick = { menuExpanded = true }) {
							Icon(
								imageVector = Icons.Rounded.MoreVert,
								contentDescription = "Menu",
							)
						}
						DropdownMenu(
							expanded = menuExpanded,
							onDismissRequest = { menuExpanded = false },
						) {
							DropdownMenuItem(
								leadingIcon = {
									Icon(
										imageVector = Icons.Rounded.BugReport,
										contentDescription = null,
									)
								},
								text = {
									Text(
										if (state.woltDiagnostics.isEmpty()) {
											"Wolt diagnostika"
										} else {
											"Wolt diagnostika · ${state.woltDiagnostics.size}"
										},
									)
								},
								onClick = {
									menuExpanded = false
									overlay = HomeOverlayNavigation.reduce(
										overlay,
										HomeOverlayIntent.OpenDiagnostics,
									)
								},
							)
						}
					}
				},
			)
		},
	) { innerPadding ->
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(innerPadding),
			contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
			verticalArrangement = Arrangement.spacedBy(24.dp),
		) {
			item {
				Column(
					modifier = Modifier.padding(top = 8.dp),
					verticalArrangement = Arrangement.spacedBy(4.dp),
				) {
					Text(
						"Výdavky celkom",
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
					Text(
						money(state.totalCents),
						style = MaterialTheme.typography.headlineLarge.copy(
							fontSize = 38.sp,
							lineHeight = 44.sp,
						),
						fontWeight = FontWeight.Bold,
					)
					Row(
						horizontalArrangement = Arrangement.spacedBy(8.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						Text(
							"${state.receipts.size} bločkov",
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
						if (state.woltConnected) {
							Text(
								"•",
								color = MaterialTheme.colorScheme.outline,
							)
							Text(
								"Wolt pripojený",
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
					}
				}
			}

			item {
				val woltAction = WoltControls.actions(
					connected = state.woltConnected,
					busy = state.woltBusy,
				).firstOrNull()

				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(10.dp),
				) {
					Button(
						onClick = onScan,
						enabled = !state.loading,
						modifier = Modifier
							.weight(1f)
							.height(50.dp),
						shape = RoundedCornerShape(10.dp),
					) {
						if (state.loading) {
							CircularProgressIndicator(
								modifier = Modifier.size(18.dp),
								strokeWidth = 2.dp,
								color = MaterialTheme.colorScheme.onPrimary,
							)
						} else {
							Icon(
								imageVector = Icons.Rounded.QrCodeScanner,
								contentDescription = null,
								modifier = Modifier.size(20.dp),
							)
						}
						Spacer(modifier = Modifier.width(8.dp))
						Text("Skenovať")
					}

					OutlinedButton(
						onClick = when (woltAction) {
							WoltAction.Connect -> onConnectWolt
							WoltAction.CurrentMonth -> onCurrentMonthWolt
							null -> ({})
						},
						enabled = !state.loading && !state.woltBusy,
						modifier = Modifier
							.weight(1f)
							.height(50.dp),
						shape = RoundedCornerShape(10.dp),
						border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
					) {
						if (state.woltBusy) {
							CircularProgressIndicator(
								modifier = Modifier.size(18.dp),
								strokeWidth = 2.dp,
							)
						} else {
							Icon(
								imageVector = Icons.Rounded.Sync,
								contentDescription = null,
								modifier = Modifier.size(20.dp),
							)
						}
						Spacer(modifier = Modifier.width(8.dp))
						Text(
							if (woltAction == WoltAction.Connect) "Pripojiť Wolt" else "Sync Wolt",
							maxLines = 1,
						)
					}
				}
			}

			state.message?.let { message ->
				item {
					Surface(
						modifier = Modifier.fillMaxWidth(),
						shape = RoundedCornerShape(10.dp),
						color = MaterialTheme.colorScheme.surfaceVariant,
					) {
						Text(
							message,
							modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
							style = MaterialTheme.typography.bodyMedium,
						)
					}
				}
			}

			if (state.categories.isNotEmpty()) {
				item {
					SectionHeader(title = "Kategórie")
				}
				item {
					SectionSurface {
						state.categories.take(5).forEachIndexed { index, category ->
							CategoryRow(
								category = category,
								totalCents = state.totalCents,
							)
							if (index != minOf(4, state.categories.lastIndex)) {
								SectionDivider()
							}
						}
					}
				}
			}

			if (state.products.isNotEmpty()) {
				item {
					SectionHeader(
						title = "Najdrahšie položky",
						action = "Všetky",
						onAction = onAllItems,
					)
				}
				item {
					SectionSurface {
						state.products.take(5).forEachIndexed { index, product ->
							ProductRow(product)
							if (index != minOf(4, state.products.lastIndex)) {
								SectionDivider()
							}
						}
					}
				}
			}

			if (state.receipts.isNotEmpty()) {
				item {
					SectionHeader(title = "Posledné bločky")
				}
				item {
					SectionSurface {
						state.receipts.forEachIndexed { index, receipt ->
							ReceiptRow(
								receipt = receipt,
								onClick = { onReceipt(receipt.id) },
							)
							if (index != state.receipts.lastIndex) {
								SectionDivider()
							}
						}
					}
				}
			}
		}
	}

	if (overlay == HomeOverlay.Diagnostics) {
		DiagnosticsDialog(
			lines = state.woltDiagnostics,
			onClear = onClearWoltDiagnostics,
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
private fun SectionSurface(content: @Composable () -> Unit) {
	Surface(
		modifier = Modifier.fillMaxWidth(),
		shape = RoundedCornerShape(12.dp),
		color = MaterialTheme.colorScheme.surface,
		border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
	) {
		Column {
			content()
		}
	}
}

@Composable
private fun SectionHeader(
	title: String,
	action: String? = null,
	onAction: (() -> Unit)? = null,
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			title,
			modifier = Modifier.weight(1f),
			style = MaterialTheme.typography.titleMedium,
			fontWeight = FontWeight.SemiBold,
		)
		if (action != null && onAction != null) {
			TextButton(
				onClick = onAction,
				contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
			) {
				Text(action)
			}
		}
	}
}

@Composable
private fun SectionDivider() {
	HorizontalDivider(
		modifier = Modifier.padding(horizontal = 14.dp),
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
			Text(
				"Wolt diagnostika",
				style = MaterialTheme.typography.titleLarge,
			)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiptDetailScreen(
	state: MainUiState,
	onBack: () -> Unit,
) {
	Scaffold(
		containerColor = MaterialTheme.colorScheme.background,
		topBar = {
			TopAppBar(
				title = { Text("Bloček") },
				navigationIcon = {
					IconButton(onClick = onBack) {
						Icon(
							imageVector = Icons.Rounded.ArrowBack,
							contentDescription = "Späť",
						)
					}
				},
				colors = TopAppBarDefaults.topAppBarColors(
					containerColor = MaterialTheme.colorScheme.background,
				),
			)
		},
	) { innerPadding ->
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(innerPadding),
			contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
			verticalArrangement = Arrangement.spacedBy(22.dp),
		) {
			if (state.loading && state.selectedReceipt == null) {
				item {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(top = 48.dp),
						horizontalArrangement = Arrangement.Center,
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
					Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
						SectionHeader(title = "Položky")
						SectionSurface {
							receipt.items.forEachIndexed { index, item ->
								ReceiptItemRow(item)
								if (index != receipt.items.lastIndex) {
									SectionDivider()
								}
							}
						}
					}
				}
			}

			state.message?.let { message ->
				item { Text(message) }
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllItemsScreen(
	state: MainUiState,
	onBack: () -> Unit,
	onReceipt: (String) -> Unit,
) {
	Scaffold(
		containerColor = MaterialTheme.colorScheme.background,
		topBar = {
			TopAppBar(
				title = { Text("Všetky položky") },
				navigationIcon = {
					IconButton(onClick = onBack) {
						Icon(
							imageVector = Icons.Rounded.ArrowBack,
							contentDescription = "Späť",
						)
					}
				},
				colors = TopAppBarDefaults.topAppBarColors(
					containerColor = MaterialTheme.colorScheme.background,
				),
			)
		},
	) { innerPadding ->
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(innerPadding),
			contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			if (state.loading && state.allItems.isEmpty()) {
				item {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(top = 48.dp),
						horizontalArrangement = Arrangement.Center,
					) {
						CircularProgressIndicator()
					}
				}
			}

			items(state.allItems) { item ->
				AllItemRow(
					item = item,
					onClick = { onReceipt(item.receiptId) },
				)
			}
		}
	}
}

@Composable
private fun ReceiptHeader(receipt: Receipt) {
	Column(
		modifier = Modifier.padding(top = 8.dp),
		verticalArrangement = Arrangement.spacedBy(5.dp),
	) {
		Text(
			receipt.merchant,
			style = MaterialTheme.typography.headlineMedium,
			fontWeight = FontWeight.SemiBold,
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
			fontWeight = FontWeight.Bold,
		)
	}
}

@Composable
private fun CategoryRow(
	category: CategoryTotal,
	totalCents: Long,
) {
	val percentage = if (totalCents > 0) {
		(category.totalCents * 100.0 / totalCents).coerceIn(0.0, 100.0)
	} else {
		0.0
	}

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 14.dp, vertical = 13.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Column(
			modifier = Modifier
				.weight(1f)
				.padding(end = 16.dp),
			verticalArrangement = Arrangement.spacedBy(2.dp),
		) {
			Text(
				category.category,
				style = MaterialTheme.typography.bodyLarge,
				fontWeight = FontWeight.Medium,
			)
			Text(
				String.format(Locale("sk", "SK"), "%.0f %% z celku", percentage),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		Text(
			money(category.totalCents),
			fontWeight = FontWeight.SemiBold,
			textAlign = TextAlign.End,
		)
	}
}

@Composable
private fun ProductRow(product: ProductTotal) {
	PriceRow(
		title = product.product,
		priceCents = product.totalCents,
	)
}

@Composable
private fun PriceRow(
	title: String,
	priceCents: Long,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 14.dp, vertical = 13.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			text = title,
			modifier = Modifier
				.weight(1f)
				.padding(end = 16.dp),
			maxLines = 2,
			overflow = TextOverflow.Ellipsis,
			style = MaterialTheme.typography.bodyLarge,
			fontWeight = FontWeight.Medium,
		)
		Text(
			text = money(priceCents),
			modifier = Modifier.widthIn(min = 82.dp),
			textAlign = TextAlign.End,
			maxLines = 1,
			fontWeight = FontWeight.SemiBold,
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
			.padding(horizontal = 14.dp, vertical = 13.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Column(
			modifier = Modifier
				.weight(1f)
				.padding(end = 12.dp),
			verticalArrangement = Arrangement.spacedBy(2.dp),
		) {
			Text(
				receipt.merchant,
				style = MaterialTheme.typography.bodyLarge,
				fontWeight = FontWeight.Medium,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			Text(
				DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
					.format(Date(receipt.issuedAt)),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		Text(
			money(receipt.totalCents),
			modifier = Modifier.widthIn(min = 78.dp),
			textAlign = TextAlign.End,
			fontWeight = FontWeight.SemiBold,
		)
		Icon(
			imageVector = Icons.Rounded.ChevronRight,
			contentDescription = null,
			modifier = Modifier
				.padding(start = 8.dp)
				.size(18.dp),
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

@Composable
private fun ReceiptItemRow(item: ReceiptItem) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 14.dp, vertical = 13.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Column(
			modifier = Modifier
				.weight(1f)
				.padding(end = 16.dp),
			verticalArrangement = Arrangement.spacedBy(2.dp),
		) {
			Text(
				item.originalName,
				style = MaterialTheme.typography.bodyLarge,
				fontWeight = FontWeight.Medium,
				maxLines = 3,
				overflow = TextOverflow.Ellipsis,
			)
			Text(
				itemMeta(item.quantity, item.category),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		Text(
			money(item.totalCents),
			modifier = Modifier.widthIn(min = 82.dp),
			textAlign = TextAlign.End,
			fontWeight = FontWeight.SemiBold,
		)
	}
}

@Composable
private fun AllItemRow(
	item: ItemListEntry,
	onClick: () -> Unit,
) {
	Card(
		onClick = onClick,
		modifier = Modifier.fillMaxWidth(),
		shape = RoundedCornerShape(10.dp),
		colors = CardDefaults.cardColors(
			containerColor = MaterialTheme.colorScheme.surface,
		),
		border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
		elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 14.dp, vertical = 13.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Column(
				modifier = Modifier
					.weight(1f)
					.padding(end = 12.dp),
				verticalArrangement = Arrangement.spacedBy(2.dp),
			) {
				Text(
					item.originalName,
					style = MaterialTheme.typography.bodyLarge,
					fontWeight = FontWeight.Medium,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
				)
				Text(
					item.merchant + " · " +
						DateFormat.getDateInstance(DateFormat.SHORT).format(Date(item.issuedAt)),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				Text(
					itemMeta(item.quantity, item.category),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
			Text(
				money(item.totalCents),
				modifier = Modifier.widthIn(min = 78.dp),
				textAlign = TextAlign.End,
				fontWeight = FontWeight.SemiBold,
			)
			Icon(
				imageVector = Icons.Rounded.ChevronRight,
				contentDescription = null,
				modifier = Modifier
					.padding(start = 8.dp)
					.size(18.dp),
				tint = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	}
}

private fun itemMeta(quantity: Double, category: String): String {
	val quantityText = if (quantity == quantity.toLong().toDouble()) {
		quantity.toLong().toString()
	} else {
		String.format(Locale.US, "%.2f", quantity).trimEnd('0').trimEnd('.')
	}
	return quantityText + "× · " + category
}

private fun money(cents: Long): String = NumberFormat.getCurrencyInstance(Locale("sk", "SK"))
	.format(cents / 100.0)
