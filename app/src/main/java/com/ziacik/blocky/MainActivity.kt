package com.ziacik.blocky

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.ziacik.blocky.model.CategoryTotal
import com.ziacik.blocky.model.ProductTotal
import com.ziacik.blocky.model.Receipt
import com.ziacik.blocky.model.ReceiptItem
import com.ziacik.blocky.model.ReceiptSummary
import com.ziacik.blocky.ui.MainUiState
import com.ziacik.blocky.ui.MainViewModel
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
	private val viewModel: MainViewModel by viewModels()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()

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
					BlockyHome(
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
						onOpenReceipt = viewModel::openReceipt,
						onCloseReceipt = viewModel::closeReceipt,
					)
				}
			}
		}
	}
}

@Composable
private fun BlockyHome(
	state: MainUiState,
	onScan: () -> Unit,
	onOpenReceipt: (String) -> Unit,
	onCloseReceipt: () -> Unit,
) {
	state.selectedReceipt?.let { receipt ->
		ReceiptDetail(receipt = receipt, onBack = onCloseReceipt)
		return
	}

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
						Text(money(state.totalCents), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
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
					Text(if (state.loading) "Načítavam bloček…" else "Naskenovať QR bločku")
				}
				state.message?.let {
					Spacer(modifier = Modifier.height(8.dp))
					Text(it, style = MaterialTheme.typography.bodyMedium)
				}
			}
			if (state.categories.isNotEmpty()) {
				item { SectionTitle("Najväčšie kategórie") }
				items(state.categories) { category -> CategoryRow(category) }
			}
			if (state.products.isNotEmpty()) {
				item { SectionTitle("Najdrahšie produkty") }
				items(state.products) { product -> ProductRow(product) }
			}
			if (state.receipts.isNotEmpty()) {
				item { SectionTitle("Posledné bločky") }
				items(state.receipts) { receipt ->
					ReceiptRow(receipt = receipt, onClick = { onOpenReceipt(receipt.id) })
				}
			}
		}
	}
}

@Composable
private fun ReceiptDetail(
	receipt: Receipt,
	onBack: () -> Unit,
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
				Button(onClick = onBack) {
					Text("Späť")
				}
			}
			item {
				Text(receipt.merchant, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
				Text(
					DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(receipt.issuedAt)),
					style = MaterialTheme.typography.bodyMedium,
				)
				Spacer(modifier = Modifier.height(8.dp))
				Text(money(receipt.totalCents), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
			}
			item {
				SectionTitle("Položky (" + receipt.items.size + ")")
			}
			items(receipt.items) { item ->
				ReceiptItemRow(item)
			}
		}
	}
}

@Composable
private fun ReceiptItemRow(item: ReceiptItem) {
	Column(modifier = Modifier.fillMaxWidth()) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.Top,
		) {
			Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
				Text(item.canonicalName, fontWeight = FontWeight.SemiBold)
				if (!item.originalName.equals(item.canonicalName, ignoreCase = true)) {
					Text(item.originalName, style = MaterialTheme.typography.bodySmall)
				}
				Text(
					buildString {
						append(item.category)
						item.subcategory?.let {
							append(" · ")
							append(it)
						}
						if (item.quantity != 1.0) {
							append(" · ")
							append(quantity(item.quantity))
							append("×")
						}
					},
					style = MaterialTheme.typography.bodySmall,
				)
			}
			Text(money(item.totalCents), fontWeight = FontWeight.Bold)
		}
		Spacer(modifier = Modifier.height(10.dp))
		HorizontalDivider()
	}
}

@Composable
private fun SectionTitle(value: String) {
	Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun CategoryRow(category: CategoryTotal) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(category.category)
		Text(money(category.totalCents), fontWeight = FontWeight.SemiBold)
	}
}

@Composable
private fun ProductRow(product: ProductTotal) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(product.product)
		Text(money(product.totalCents), fontWeight = FontWeight.SemiBold)
	}
}

@Composable
private fun ReceiptRow(
	receipt: ReceiptSummary,
	onClick: () -> Unit,
) {
	Card(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick),
	) {
		Row(
			modifier = Modifier.padding(16.dp).fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically,
		) {
			Column(modifier = Modifier.weight(1f)) {
				Text(receipt.merchant, fontWeight = FontWeight.SemiBold)
				Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(receipt.issuedAt)))
			}
			Text(money(receipt.totalCents), fontWeight = FontWeight.Bold)
		}
	}
}

private fun money(cents: Long): String = NumberFormat.getCurrencyInstance(Locale("sk", "SK"))
	.format(cents / 100.0)

private fun quantity(value: Double): String = if (value % 1.0 == 0.0) {
	value.toLong().toString()
} else {
	NumberFormat.getNumberInstance(Locale("sk", "SK")).format(value)
}
