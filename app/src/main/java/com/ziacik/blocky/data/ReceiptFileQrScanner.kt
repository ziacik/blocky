package com.ziacik.blocky.data

import android.content.Context
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.core.graphics.createBitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.IOException
import kotlin.math.roundToInt

class ReceiptFileQrScanner(private val context: Context) {
	fun scan(uri: Uri): String {
		val mimeType = context.contentResolver.getType(uri)
		val qrValue = BarcodeScanning.getClient(
			BarcodeScannerOptions.Builder()
				.setBarcodeFormats(Barcode.FORMAT_QR_CODE)
				.build(),
		).use { scanner ->
			when (mimeType) {
				"application/pdf" -> scanPdf(uri, scanner)
				null -> scanImage(uri, scanner)
				else -> {
					require(mimeType.startsWith("image/")) {
						"Vyber obrázok alebo PDF."
					}
					scanImage(uri, scanner)
				}
			}
		}
		return qrValue ?: throw IllegalArgumentException("V súbore sa nenašiel QR kód eKasa.")
	}

	private fun scanImage(uri: Uri, scanner: BarcodeScanner): String? =
		readQrs(scanner, InputImage.fromFilePath(context, uri))

	private fun scanPdf(uri: Uri, scanner: BarcodeScanner): String? {
		val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
			?: throw IOException("PDF sa nepodarilo otvoriť.")
		descriptor.use { file ->
			PdfRenderer(file).use { renderer ->
				for (index in 0 until renderer.pageCount) {
					val qrValue = renderer.openPage(index).use { page ->
						val scale = minOf(
							4f,
							2400f / page.width,
							3600f / page.height,
						)
						val width = (page.width * scale).roundToInt().coerceAtLeast(1)
						val height = (page.height * scale).roundToInt().coerceAtLeast(1)
						val bitmap = createBitmap(width, height)
						try {
							bitmap.eraseColor(Color.WHITE)
							page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
							readQrs(scanner, InputImage.fromBitmap(bitmap, 0))
						} finally {
							bitmap.recycle()
						}
					}
					if (qrValue != null) return qrValue
				}
			}
		}
		return null
	}

	private fun readQrs(scanner: BarcodeScanner, image: InputImage): String? =
		ReceiptQrSelector.select(Tasks.await(scanner.process(image)).map { it.rawValue })
}
