package com.ziacik.blocky.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ziacik.blocky.categorization.ReceiptStore
import com.ziacik.blocky.model.CategoryTotal
import com.ziacik.blocky.model.ItemListEntry
import com.ziacik.blocky.model.ProductTotal
import com.ziacik.blocky.model.Receipt
import com.ziacik.blocky.model.ReceiptItem
import com.ziacik.blocky.model.ReceiptSummary
import com.ziacik.blocky.model.SpendingType

class BlockyDatabase(context: Context) : SQLiteOpenHelper(context, "blocky.db", null, 2), ReceiptStore {
	override fun onConfigure(db: SQLiteDatabase) {
		super.onConfigure(db)
		db.setForeignKeyConstraintsEnabled(true)
	}

	override fun onCreate(db: SQLiteDatabase) {
		db.execSQL(
			"""
			CREATE TABLE receipts (
				receipt_id TEXT PRIMARY KEY,
				merchant TEXT NOT NULL,
				issued_at INTEGER NOT NULL,
				total_cents INTEGER NOT NULL,
				raw_json TEXT NOT NULL
			)
			""".trimIndent()
		)
		db.execSQL(
			"""
			CREATE TABLE items (
				id INTEGER PRIMARY KEY AUTOINCREMENT,
				receipt_id TEXT NOT NULL,
				original_name TEXT NOT NULL,
				canonical_name TEXT NOT NULL,
				category TEXT NOT NULL,
				subcategory TEXT,
				quantity REAL NOT NULL,
				total_cents INTEGER NOT NULL,
				vat_rate REAL,
				spending_type TEXT,
				classification_confidence REAL,
				FOREIGN KEY(receipt_id) REFERENCES receipts(receipt_id) ON DELETE CASCADE
			)
			""".trimIndent()
		)
		db.execSQL("CREATE INDEX idx_items_receipt_id ON items(receipt_id)")
		db.execSQL("CREATE INDEX idx_items_category ON items(category)")
	}

	override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
		if (oldVersion < 2) {
			db.execSQL("ALTER TABLE items ADD COLUMN spending_type TEXT")
			db.execSQL("ALTER TABLE items ADD COLUMN classification_confidence REAL")
		}
	}

	override fun save(receipt: Receipt) {
		writableDatabase.beginTransaction()
		try {
			val receiptValues = ContentValues().apply {
				put("receipt_id", receipt.id)
				put("merchant", receipt.merchant)
				put("issued_at", receipt.issuedAt)
				put("total_cents", receipt.totalCents)
				put("raw_json", receipt.rawJson)
			}
			writableDatabase.insertWithOnConflict("receipts", null, receiptValues, SQLiteDatabase.CONFLICT_REPLACE)
			writableDatabase.delete("items", "receipt_id = ?", arrayOf(receipt.id))
			receipt.items.forEach { item ->
				val values = ContentValues().apply {
					put("receipt_id", receipt.id)
					put("original_name", item.originalName)
					put("canonical_name", item.canonicalName)
					put("category", item.category)
					put("subcategory", item.subcategory)
					put("quantity", item.quantity)
					put("total_cents", item.totalCents)
					if (item.vatRate == null) putNull("vat_rate") else put("vat_rate", item.vatRate)
					if (item.spendingType == null) putNull("spending_type") else put("spending_type", item.spendingType.name)
					if (item.classificationConfidence == null) {
						putNull("classification_confidence")
					} else {
						put("classification_confidence", item.classificationConfidence)
					}
				}
				writableDatabase.insertOrThrow("items", null, values)
			}
			writableDatabase.setTransactionSuccessful()
		} finally {
			writableDatabase.endTransaction()
		}
	}

	fun deleteWoltReceiptsSince(sinceMillis: Long) {
		writableDatabase.delete(
			"receipts",
			"receipt_id LIKE ? AND issued_at >= ?",
			arrayOf("wolt:%", sinceMillis.toString()),
		)
	}

	fun hasPricedReceipt(receiptId: String): Boolean = readableDatabase.rawQuery(
		"""
		SELECT EXISTS(
			SELECT 1
			FROM items
			WHERE receipt_id = ? AND total_cents > 0
		)
		""".trimIndent(),
		arrayOf(receiptId),
	).use { cursor ->
		cursor.moveToFirst()
		cursor.getInt(0) == 1
	}

	fun receipt(receiptId: String): Receipt? {
		val header = readableDatabase.rawQuery(
			"SELECT receipt_id, merchant, issued_at, total_cents, raw_json FROM receipts WHERE receipt_id = ?",
			arrayOf(receiptId),
		).use { cursor ->
			if (!cursor.moveToFirst()) return null
			Receipt(
				id = cursor.getString(0),
				merchant = cursor.getString(1),
				issuedAt = cursor.getLong(2),
				totalCents = cursor.getLong(3),
				rawJson = cursor.getString(4),
				items = emptyList(),
			)
		}

		val items = readableDatabase.rawQuery(
			"""
			SELECT original_name, canonical_name, category, subcategory, quantity, total_cents, vat_rate,
			       spending_type, classification_confidence
			FROM items
			WHERE receipt_id = ?
			ORDER BY id
			""".trimIndent(),
			arrayOf(receiptId),
		).use { cursor ->
			buildList {
				while (cursor.moveToNext()) {
					add(
						ReceiptItem(
							originalName = cursor.getString(0),
							canonicalName = cursor.getString(1),
							category = cursor.getString(2),
							subcategory = cursor.getString(3),
							quantity = cursor.getDouble(4),
							totalCents = cursor.getLong(5),
							vatRate = if (cursor.isNull(6)) null else cursor.getDouble(6),
							spendingType = if (cursor.isNull(7)) null else SpendingType.valueOf(cursor.getString(7)),
							classificationConfidence = if (cursor.isNull(8)) null else cursor.getDouble(8),
						)
					)
				}
			}
		}

		return header.copy(items = items)
	}

	fun allItems(limit: Int = 500): List<ItemListEntry> = readableDatabase.rawQuery(
		"""
		SELECT i.receipt_id, r.merchant, r.issued_at, i.original_name, i.canonical_name,
		       i.category, i.subcategory, i.quantity, i.total_cents,
		       i.spending_type, i.classification_confidence
		FROM items i
		JOIN receipts r ON r.receipt_id = i.receipt_id
		ORDER BY r.issued_at DESC, i.id DESC
		LIMIT ?
		""".trimIndent(),
		arrayOf(limit.toString()),
	).use { cursor ->
		buildList {
			while (cursor.moveToNext()) {
				add(
					ItemListEntry(
						receiptId = cursor.getString(0),
						merchant = cursor.getString(1),
						issuedAt = cursor.getLong(2),
						originalName = cursor.getString(3),
						canonicalName = cursor.getString(4),
						category = cursor.getString(5),
						subcategory = cursor.getString(6),
						quantity = cursor.getDouble(7),
						totalCents = cursor.getLong(8),
						spendingType = if (cursor.isNull(9)) null else SpendingType.valueOf(cursor.getString(9)),
						classificationConfidence = if (cursor.isNull(10)) null else cursor.getDouble(10),
					)
				)
			}
		}
	}

	fun receiptSummaries(limit: Int = 20): List<ReceiptSummary> = readableDatabase.rawQuery(
		"SELECT receipt_id, merchant, issued_at, total_cents FROM receipts ORDER BY issued_at DESC LIMIT ?",
		arrayOf(limit.toString()),
	).use { cursor ->
		buildList {
			while (cursor.moveToNext()) {
				add(
					ReceiptSummary(
						id = cursor.getString(0),
						merchant = cursor.getString(1),
						issuedAt = cursor.getLong(2),
						totalCents = cursor.getLong(3),
					)
				)
			}
		}
	}

	fun totalCents(): Long = readableDatabase.rawQuery(
		"SELECT COALESCE(SUM(total_cents), 0) FROM receipts",
		null,
	).use { cursor ->
		cursor.moveToFirst()
		cursor.getLong(0)
	}

	fun productTotals(limit: Int = 6): List<ProductTotal> = readableDatabase.rawQuery(
		"SELECT canonical_name, SUM(total_cents) total FROM items GROUP BY canonical_name ORDER BY total DESC LIMIT ?",
		arrayOf(limit.toString()),
	).use { cursor ->
		buildList {
			while (cursor.moveToNext()) {
				add(ProductTotal(cursor.getString(0), cursor.getLong(1)))
			}
		}
	}

	fun categoryTotals(limit: Int = 6): List<CategoryTotal> = readableDatabase.rawQuery(
		"SELECT category, SUM(total_cents) total FROM items GROUP BY category ORDER BY total DESC LIMIT ?",
		arrayOf(limit.toString()),
	).use { cursor ->
		buildList {
			while (cursor.moveToNext()) {
				add(CategoryTotal(cursor.getString(0), cursor.getLong(1)))
			}
	}
	}
}
