package com.ziacik.blocky

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
			if (state.receipts.isNotEmpty()) {
				item { SectionTitle("Posledné bločky") }
				items(state.receipts) { receipt -> ReceiptRow(receipt) }
			}
		}
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
private fun ReceiptRow(receipt: ReceiptSummary) {
	Card(modifier = Modifier.fillMaxWidth()) {
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
