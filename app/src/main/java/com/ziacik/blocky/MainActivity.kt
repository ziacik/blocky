package com.ziacik.blocky

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
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
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

private val BlockyColors = lightColorScheme(
	primary = Color(0xFF5D315E),
	onPrimary = Color.White,
	primaryContainer = Color(0xFFF0DFEF),
	onPrimaryContainer = Color(0xFF311733),
	secondary = Color(0xFFB6533F),
	onSecondary = Color.White,
	secondaryContainer = Color(0xFFFFE1D8),
	onSecondaryContainer = Color(0xFF4A170D),
	tertiary = Color(0xFF76603D),
	tertiaryContainer = Color(0xFFF4E3BD),
	background = Color(0xFFF7F3EE),
	onBackground = Color(0xFF261F25),
	surface = Color(0xFFFFFCF8),
	onSurface = Color(0xFF261F25),
	surfaceVariant = Color(0xFFEDE5E7),
	onSurfaceVariant = Color(0xFF635A60),
	outline = Color(0xFF8A7F85),
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
			MaterialTheme(colorScheme = BlockyColors) {
				Surface(
					modifier = Modifier.fillMaxSize(),
					color = MaterialTheme.colorScheme.background,
				) {
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
	}

	override fun onResume() {
		super.onResume()
		viewModel.refresh()
	}
}

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
	var overflowExpanded by remember { mutableStateOf(false) }
	var overlay by remember { mutableStateOf(HomeOverlay.None) }

	Scaffold(
		containerColor = MaterialTheme.colorScheme.background,
	) { innerPadding ->
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(innerPadding),
			contentPadding = PaddingValues(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 28.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp),
		) {
			item {
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.Top,
				) {
					Column(modifier = Modifier.weight(1f)) {
						Text(
							"Bločky",
							style = MaterialTheme.typography.headlineLarge,
							fontWeight = FontWeight.Black,
						)
						Text(
							"Čo presne žerie tvoje peniaze.",
							style = MaterialTheme.typography.bodyLarge,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
					Box {
						TextButton(onClick = { overflowExpanded = true }) {
							Text(
								"•••",
								style = MaterialTheme.typography.titleLarge,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
						DropdownMenu(
							expanded = overflowExpanded,
							onDismissRequest = { overflowExpanded = false },
						) {
							DropdownMenuItem(
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
									overflowExpanded = false
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

			item {
				Card(
					modifier = Modifier.fillMaxWidth(),
					shape = RoundedCornerShape(28.dp),
					colors = CardDefaults.cardColors(
						containerColor = MaterialTheme.colorScheme.primaryContainer,
					),
				) {
					Column(
						modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
						verticalArrangement = Arrangement.spacedBy(6.dp),
					) {
						Text(
							"SPOLU EVIDOVANÉ",
							style = MaterialTheme.typography.labelMedium,
							fontWeight = FontWeight.Bold,
							color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.68f),
						)
						Text(
							money(state.totalCents),
							style = MaterialTheme.typography.displaySmall,
							fontWeight = FontWeight.Black,
							color = MaterialTheme.colorScheme.onPrimaryContainer,
						)
						Text(
							"${state.receipts.size} bločkov v evidencii",
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.74f),
						)
					}
				}
			}

			item {
				Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
					Button(
						onClick = onScan,
						enabled = !state.loading,
						modifier = Modifier
							.fillMaxWidth()
							.height(54.dp),
						shape = RoundedCornerShape(18.dp),
					) {
						if (state.loading) {
							CircularProgressIndicator(
								modifier = Modifier.height(20.dp),
								strokeWidth = 2.dp,
								color = MaterialTheme.colorScheme.onPrimary,
							)
							Spacer(modifier = Modifier.padding(5.dp))
						}
						Text(
							if (state.loading) "Načítavam…" else "Naskenovať QR bločku",
							fontWeight = FontWeight.Bold,
						)
					}

					val woltAction = WoltControls.actions(
						connected = state.woltConnected,
						busy = state.woltBusy,
					).firstOrNull()

					FilledTonalButton(
						onClick = when (woltAction) {
							WoltAction.Connect -> onConnectWolt
							WoltAction.CurrentMonth -> onCurrentMonthWolt
							null -> ({})
						},
						enabled = !state.loading && !state.woltBusy,
						modifier = Modifier
							.fillMaxWidth()
							.height(52.dp),
						shape = RoundedCornerShape(18.dp),
					) {
						Text(
							when {
								state.woltBusy -> "Synchronizujem Wolt…"
								woltAction == WoltAction.Connect -> "Prepojiť Wolt"
								else -> "Synchronizovať Wolt · tento mesiac"
							},
							fontWeight = FontWeight.SemiBold,
						)
					}
				}
			}

			state.message?.let { message ->
				item {
					Surface(
						modifier = Modifier.fillMaxWidth(),
						shape = RoundedCornerShape(18.dp),
						color = MaterialTheme.colorScheme.tertiaryContainer,
					) {
						Text(
							message,
							modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
							style = MaterialTheme.typography.bodyMedium,
						)
					}
				}
			}

			if (state.categories.isNotEmpty()) {
				item { SectionTitle("Najväčšie kategórie", "Kam odteká najviac") }
				item {
					Card(
						modifier = Modifier.fillMaxWidth(),
						shape = RoundedCornerShape(22.dp),
						colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
					) {
						Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
							state.categories.take(5).forEachIndexed { index, category ->
								CategoryRow(category)
								if (index != minOf(4, state.categories.lastIndex)) {
									HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
								}
							}
						}
					}
				}
			}

			if (state.products.isNotEmpty()) {
				item { SectionTitle("Najdrahšie položky", "Top produkty podľa výdavkov") }
				item {
					Card(
						modifier = Modifier.fillMaxWidth(),
						shape = RoundedCornerShape(22.dp),
						colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
					) {
						Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
							state.products.take(5).forEachIndexed { index, product ->
								ProductRow(product)
								if (index != minOf(4, state.products.lastIndex)) {
									HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
								}
							}
						}
					}
				}
				item {
					TextButton(
						onClick = onAllItems,
						modifier = Modifier.fillMaxWidth(),
					) {
						Text("Zobraziť všetky položky")
					}
				}
			}

			if (state.receipts.isNotEmpty()) {
				item { SectionTitle("Posledné bločky", "Najnovšie nákupy") }
				items(state.receipts) { receipt ->
					ReceiptRow(
						receipt = receipt,
						onClick = { onReceipt(receipt.id) },
					)
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
private fun DiagnosticsDialog(
	lines: List<String>,
	onClear: () -> Unit,
	onDismiss: () -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = {
			Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
				Text("Wolt diagnostika", fontWeight = FontWeight.Bold)
				Text(
					"${lines.size} záznamov",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
		},
		text = {
			if (lines.isEmpty()) {
				Text(
					"Zatiaľ tu nič nie je.",
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
				Text("Hotovo")
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

@Composable
private fun ReceiptDetailScreen(
	state: MainUiState,
	onBack: () -> Unit,
) {
	Scaffold(
		containerColor = MaterialTheme.colorScheme.background,
	) { innerPadding ->
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(innerPadding),
			contentPadding = PaddingValues(20.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp),
		) {
			item { BackButton(onBack) }

			if (state.loading && state.selectedReceipt == null) {
				item {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.Center,
					) {
						CircularProgressIndicator()
					}
				}
			}

			state.selectedReceipt?.let { receipt ->
				item { ReceiptHeader(receipt) }
				item { SectionTitle("Položky", "${receipt.items.size} položiek") }
				item {
					Card(
						modifier = Modifier.fillMaxWidth(),
						shape = RoundedCornerShape(22.dp),
					) {
						Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
							receipt.items.forEachIndexed { index, item ->
								ReceiptItemRow(item)
								if (index != receipt.items.lastIndex) {
									HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
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

@Composable
private fun AllItemsScreen(
	state: MainUiState,
	onBack: () -> Unit,
	onReceipt: (String) -> Unit,
) {
	Scaffold(
		containerColor = MaterialTheme.colorScheme.background,
	) { innerPadding ->
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(innerPadding),
			contentPadding = PaddingValues(20.dp),
			verticalArrangement = Arrangement.spacedBy(10.dp),
		) {
			item { BackButton(onBack) }
			item { SectionTitle("Všetky položky", "${state.allItems.size} položiek") }

			if (state.loading && state.allItems.isEmpty()) {
				item {
					Row(
						modifier = Modifier.fillMaxWidth(),
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
private fun BackButton(onBack: () -> Unit) {
	TextButton(onClick = onBack) {
		Text("‹ Späť", fontWeight = FontWeight.SemiBold)
	}
}

@Composable
private fun ReceiptHeader(receipt: Receipt) {
	Card(
		modifier = Modifier.fillMaxWidth(),
		shape = RoundedCornerShape(26.dp),
		colors = CardDefaults.cardColors(
			containerColor = MaterialTheme.colorScheme.secondaryContainer,
		),
	) {
		Column(
			modifier = Modifier.padding(22.dp),
			verticalArrangement = Arrangement.spacedBy(5.dp),
		) {
			Text(
				receipt.merchant,
				style = MaterialTheme.typography.headlineMedium,
				fontWeight = FontWeight.Black,
			)
			Text(
				DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
					.format(Date(receipt.issuedAt)),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f),
			)
			Spacer(modifier = Modifier.height(4.dp))
			Text(
				money(receipt.totalCents),
				style = MaterialTheme.typography.displaySmall,
				fontWeight = FontWeight.Black,
			)
		}
	}
}

@Composable
private fun SectionTitle(
	value: String,
	subtitle: String? = null,
) {
	Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
		Text(
			value,
			style = MaterialTheme.typography.titleLarge,
			fontWeight = FontWeight.Bold,
		)
		subtitle?.let {
			Text(
				it,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	}
}

@Composable
private fun CategoryRow(category: CategoryTotal) {
	PriceRow(
		title = category.category,
		priceCents = category.totalCents,
	)
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
			.padding(vertical = 13.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			text = title,
			modifier = Modifier
				.weight(1f)
				.padding(end = 16.dp),
			maxLines = 2,
			overflow = TextOverflow.Ellipsis,
			fontWeight = FontWeight.Medium,
		)
		Text(
			text = money(priceCents),
			modifier = Modifier.widthIn(min = 88.dp),
			textAlign = TextAlign.End,
			maxLines = 1,
			fontWeight = FontWeight.Bold,
		)
	}
}

@Composable
private fun ReceiptRow(
	receipt: ReceiptSummary,
	onClick: () -> Unit,
) {
	Card(
		onClick = onClick,
		modifier = Modifier.fillMaxWidth(),
		shape = RoundedCornerShape(22.dp),
		colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
		elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
	) {
		Row(
			modifier = Modifier
				.padding(horizontal = 17.dp, vertical = 16.dp)
				.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Column(
				modifier = Modifier
					.weight(1f)
					.padding(end = 16.dp),
			) {
				Text(
					receipt.merchant,
					fontWeight = FontWeight.Bold,
					maxLines = 2,
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
				modifier = Modifier.widthIn(min = 88.dp),
				textAlign = TextAlign.End,
				maxLines = 1,
				fontWeight = FontWeight.Black,
			)
		}
	}
}

@Composable
private fun ReceiptItemRow(item: ReceiptItem) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(vertical = 12.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Column(
			modifier = Modifier
				.weight(1f)
				.padding(end = 16.dp),
		) {
			Text(
				item.originalName,
				fontWeight = FontWeight.SemiBold,
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
			modifier = Modifier.widthIn(min = 88.dp),
			textAlign = TextAlign.End,
			maxLines = 1,
			fontWeight = FontWeight.Bold,
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
		shape = RoundedCornerShape(20.dp),
		colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(16.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Column(
				modifier = Modifier
					.weight(1f)
					.padding(end = 16.dp),
			) {
				Text(
					item.originalName,
					fontWeight = FontWeight.SemiBold,
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
				modifier = Modifier.widthIn(min = 88.dp),
				textAlign = TextAlign.End,
				maxLines = 1,
				fontWeight = FontWeight.Bold,
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
