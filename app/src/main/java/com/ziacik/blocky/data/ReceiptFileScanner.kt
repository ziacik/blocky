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
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.ziacik.blocky.model.Receipt
import com.ziacik.blocky.normalization.HeuristicItemNormalizer
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.IOException
import kotlin.math.roundToInt

sealed interface ScannedReceiptFile {
	data class Ekasa(val qrValue: String) : ScannedReceiptFile
	data class Spar(val receipt: Receipt) : ScannedReceiptFile
}

/**
 * Reads digital SPAR PDF text directly, falling back to existing QR/OCR scanning.
 * No uploaded document leaves the phone.
 */
class ReceiptFileScanner(private val context: Context) {
	private val sparParser = SparReceiptParser(HeuristicItemNormalizer())

	fun scan(uri: Uri): ScannedReceiptFile {
		val mimeType = context.contentResolver.getType(uri)
		return BarcodeScanning.getClient(
			BarcodeScannerOptions.Builder()
				.setBarcodeFormats(Barcode.FORMAT_QR_CODE)
				.build(),
		).use { qrScanner ->
			TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).use { textRecognizer ->
				when {
					mimeType == "application/pdf" ||
						uri.toString().substringBefore('?').endsWith(".pdf", ignoreCase = true) ->
						scanPdf(uri, qrScanner, textRecognizer)
					mimeType == null || mimeType.startsWith("image/") ->
						scanImage(uri, qrScanner, textRecognizer)
					else -> throw IllegalArgumentException("Vyber obrázok alebo PDF bločku.")
				}
			}
		}
	}

	private fun scanImage(
		uri: Uri,
		qrScanner: BarcodeScanner,
		textRecognizer: TextRecognizer,
	): ScannedReceiptFile {
		val image = InputImage.fromFilePath(context, uri)
		val qrValue = readQr(qrScanner, image)
		if (qrValue != null) return ScannedReceiptFile.Ekasa(qrValue)
		return ScannedReceiptFile.Spar(sparParser.parse(readText(textRecognizer, image)))
	}

	private fun scanPdf(
		uri: Uri,
		qrScanner: BarcodeScanner,
		textRecognizer: TextRecognizer,
	): ScannedReceiptFile {
		// SPAR exports are text PDFs: native extraction preserves prices, VAT
		// codes and two-line quantity rows more reliably than camera OCR.
		runCatching { sparParser.parse(readPdfText(uri)) }
			.getOrNull()
			?.let { return ScannedReceiptFile.Spar(it) }

		val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
			?: throw IOException("PDF sa nepodarilo otvoriť.")
		val text = StringBuilder()
		descriptor.use { file ->
			PdfRenderer(file).use { renderer ->
				for (index in 0 until renderer.pageCount) {
					renderer.openPage(index).use { page ->
						val scale = minOf(4f, 2400f / page.width, 3600f / page.height)
						val width = (page.width * scale).roundToInt().coerceAtLeast(1)
						val height = (page.height * scale).roundToInt().coerceAtLeast(1)
						val bitmap = createBitmap(width, height)
						try {
							bitmap.eraseColor(Color.WHITE)
							page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
							val image = InputImage.fromBitmap(bitmap, 0)
							val qrValue = readQr(qrScanner, image)
							if (qrValue != null) return ScannedReceiptFile.Ekasa(qrValue)
							text.append(readText(textRecognizer, image)).append('\n')
						} finally {
							bitmap.recycle()
						}
					}
				}
			}
		}
		return ScannedReceiptFile.Spar(sparParser.parse(text.toString()))
	}

	private fun readPdfText(uri: Uri): String {
		PDFBoxResourceLoader.init(context.applicationContext)
		val stream = context.contentResolver.openInputStream(uri)
			?: throw IOException("PDF sa nepodarilo otvoriť.")
		return stream.use { input ->
			PDDocument.load(input).use { document ->
				PDFTextStripper().apply { setSortByPosition(true) }.getText(document)
			}
		}
	}

	private fun readQr(scanner: BarcodeScanner, image: InputImage): String? =
		ReceiptQrSelector.select(Tasks.await(scanner.process(image)).map { it.rawValue })

	private fun readText(recognizer: TextRecognizer, image: InputImage): String =
		Tasks.await(recognizer.process(image)).text
}
