package com.ziacik.blocky

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.ziacik.blocky.ui.MainScreen
import com.ziacik.blocky.ui.MainUiState
import com.ziacik.blocky.ui.MainViewModel
import com.ziacik.blocky.ui.WoltAction
import com.ziacik.blocky.ui.WoltControls
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
			MaterialTheme {
				Surface(modifier = Modifier.fillMaxSize()) {
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
							onLatestWolt = viewModel::downloadLatestWoltOrder,
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
	onLatestWolt: () -> Unit,
	onCurrentMonthWolt: () -> Unit,
	onClearWoltDiagnostics: () -> Unit,
	onReceipt: (String) -> Unit,
	onAllItems: () -> Unit,
) {
	Scaffold { innerPadding ->
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(innerPadding),
			contentPadding = PaddingValues(20.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp),
		) {
			item {
				Text("Bločky", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
				Text("Čo presne žerie tvoje peniaze.", style = MaterialTheme.typography.bodyLarge)
			}
			item {
				Card(modifier = Modifier.fillMaxWidth()) {
					Column(modifier = Modifier.padding(20.dp)) {
						Text("Zaevidované výdavky", style = MaterialTheme.typography.labelLarge)
						Text(
							money(state.totalCents),
							style = MaterialTheme.typography.displaySmall,
							fontWeight = FontWeight.Bold,
						)
					}
				}
			}
			item {
				Button(
					onClick = onScan,
					enabled = !state.loading,
					modifier = Modifier.fillMaxWidth(),
				) {
					if (state.loading) {
						CircularProgressIndicator(modifier = Modifier.height(20.dp))
						Spacer(modifier = Modifier.padding(4.dp))
					}
					Text(if (state.loading) "Načítavam…" else "Naskenovať QR bločku")
				}
			}
			item {
				val actions = WoltControls.actions(
					connected = state.woltConnected,
					busy = state.woltBusy,
				)

				if (state.woltBusy) {
					Button(
						onClick = {},
						enabled = false,
						modifier = Modifier.fillMaxWidth(),
					) {
						Text("Sťahujem Wolt…")
					}
				} else {
					actions.forEach { action ->
						Button(
							onClick = when (action) {
								WoltAction.Connect -> onConnectWolt
								WoltAction.Latest -> onLatestWolt
								WoltAction.CurrentMonth -> onCurrentMonthWolt
							},
							enabled = !state.loading,
							modifier = Modifier.fillMaxWidth(),
						) {
							Text(
								when (action) {
									WoltAction.Connect -> "Prepojiť Wolt"
									WoltAction.Latest -> "Stiahnuť poslednú Wolt objednávku"
									WoltAction.CurrentMonth ->
										"Stiahnuť všetky Wolt objednávky za aktuálny mesiac"
								}
							)
						}
						if (action != actions.last()) {
							Spacer(modifier = Modifier.height(8.dp))
						}
					}
				}

				state.message?.let {
					Spacer(modifier = Modifier.height(8.dp))
					Text(it, style = MaterialTheme.typography.bodyMedium)
				}
			}
			if (state.woltDiagnostics.isNotEmpty()) {
				item {
					Card(modifier = Modifier.fillMaxWidth()) {
						Column(
							modifier = Modifier.padding(14.dp),
							verticalArrangement = Arrangement.spacedBy(6.dp),
						) {
							Row(
								modifier = Modifier.fillMaxWidth(),
								verticalAlignment = Alignment.CenterVertically,
							) {
								Text(
									"Wolt diagnostika",
									modifier = Modifier.weight(1f),
									fontWeight = FontWeight.SemiBold,
								)
								TextButton(onClick = onClearWoltDiagnostics) {
									Text("Vymazať")
								}
							}
							state.woltDiagnostics.forEach { line ->
								Text(
									text = line,
									style = MaterialTheme.typography.bodySmall,
									fontFamily = FontFamily.Monospace,
								)
							}
						}
					}
				}
			}
			if (state.categories.isNotEmpty()) {
				item { SectionTitle("Najväčšie kategórie") }
				items(state.categories) { category -> CategoryRow(category) }
			}
			if (state.products.isNotEmpty()) {
				item { SectionTitle("Najdrahšie produkty") }
				items(state.products) { product -> ProductRow(product) }
				item {
					Button(
						onClick = onAllItems,
						modifier = Modifier.fillMaxWidth(),
					) {
						Text("Všetky položky")
					}
				}
			}
			if (state.receipts.isNotEmpty()) {
				item { SectionTitle("Posledné bločky") }
				items(state.receipts) { receipt ->
					ReceiptRow(
						receipt = receipt,
						onClick = { onReceipt(receipt.id) },
					)
				}
			}
		}
	}
}

@Composable
private fun ReceiptDetailScreen(
	state: MainUiState,
	onBack: () -> Unit,
) {
	Scaffold { innerPadding ->
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(innerPadding),
			contentPadding = PaddingValues(20.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
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
				item { SectionTitle("Položky") }
				items(receipt.items) { item ->
					ReceiptItemRow(item)
					HorizontalDivider()
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
	Scaffold { innerPadding ->
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(innerPadding),
			contentPadding = PaddingValues(20.dp),
			verticalArrangement = Arrangement.spacedBy(10.dp),
		) {
			item { BackButton(onBack) }
			item { Text("Všetky položky", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }

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
		Text("‹ Späť")
	}
}

@Composable
private fun ReceiptHeader(receipt: Receipt) {
	Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
		Text(
			receipt.merchant,
			style = MaterialTheme.typography.headlineMedium,
			fontWeight = FontWeight.Bold,
		)
		Text(
			DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
				.format(Date(receipt.issuedAt)),
			style = MaterialTheme.typography.bodyMedium,
		)
		Text(
			money(receipt.totalCents),
			style = MaterialTheme.typography.headlineLarge,
			fontWeight = FontWeight.Bold,
		)
	}
}

@Composable
private fun SectionTitle(value: String) {
	Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			text = title,
			modifier = Modifier
				.weight(1f)
				.padding(end = 16.dp),
			maxLines = 2,
			overflow = TextOverflow.Ellipsis,
		)
		Text(
			text = money(priceCents),
			modifier = Modifier.widthIn(min = 88.dp),
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
	Card(
		onClick = onClick,
		modifier = Modifier.fillMaxWidth(),
	) {
		Row(
			modifier = Modifier
				.padding(16.dp)
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
					fontWeight = FontWeight.SemiBold,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
				)
				Text(
					DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
						.format(Date(receipt.issuedAt)),
					style = MaterialTheme.typography.bodyMedium,
				)
			}
			Text(
				money(receipt.totalCents),
				modifier = Modifier.widthIn(min = 88.dp),
				textAlign = TextAlign.End,
				maxLines = 1,
				fontWeight = FontWeight.Bold,
			)
		}
	}
}

@Composable
private fun ReceiptItemRow(item: ReceiptItem) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(vertical = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Column(
			modifier = Modifier
				.weight(1f)
				.padding(end = 16.dp),
		) {
			Text(
				item.originalName,
				fontWeight = FontWeight.Medium,
				maxLines = 3,
				overflow = TextOverflow.Ellipsis,
			)
			Text(
				itemMeta(item.quantity, item.category),
				style = MaterialTheme.typography.bodySmall,
			)
		}
		Text(
			money(item.totalCents),
			modifier = Modifier.widthIn(min = 88.dp),
			textAlign = TextAlign.End,
			maxLines = 1,
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
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(14.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Column(
				modifier = Modifier
					.weight(1f)
					.padding(end = 16.dp),
			) {
				Text(
					item.originalName,
					fontWeight = FontWeight.Medium,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
				)
				Text(
					item.merchant + " · " +
						DateFormat.getDateInstance(DateFormat.SHORT).format(Date(item.issuedAt)),
					style = MaterialTheme.typography.bodySmall,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				Text(
					itemMeta(item.quantity, item.category),
					style = MaterialTheme.typography.bodySmall,
				)
			}
			Text(
				money(item.totalCents),
				modifier = Modifier.widthIn(min = 88.dp),
				textAlign = TextAlign.End,
				maxLines = 1,
				fontWeight = FontWeight.SemiBold,
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
