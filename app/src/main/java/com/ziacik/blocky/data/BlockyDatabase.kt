package com.ziacik.blocky.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ziacik.blocky.categorization.ReceiptStore
import com.ziacik.blocky.model.CategoryTotal
import com.ziacik.blocky.model.ClassificationSource
import com.ziacik.blocky.model.ItemListEntry
import com.ziacik.blocky.model.ProductTotal
import com.ziacik.blocky.model.Receipt
import com.ziacik.blocky.model.ReceiptItem
import com.ziacik.blocky.model.ReceiptSummary
import com.ziacik.blocky.model.SpendingType
import com.ziacik.blocky.model.SpendingTypeTotal
import com.ziacik.blocky.model.SubcategoryTotal

class BlockyDatabase(context: Context) : SQLiteOpenHelper(context, "blocky.db", null, 3), ReceiptStore {
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
				classification_source TEXT,
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
		if (oldVersion < 3) {
			db.execSQL("ALTER TABLE items ADD COLUMN classification_source TEXT")
		}
	}

	override fun save(receipt: Receipt) {
		writableDatabase.beginTransaction()
		try {
			val manualOverrides = manualOverrides(receipt.id)
			val receiptValues = ContentValues().apply {
				put("receipt_id", receipt.id)
				put("merchant", receipt.merchant)
				put("issued_at", receipt.issuedAt)
				put("total_cents", receipt.totalCents)
				put("raw_json", receipt.rawJson)
			}
			writableDatabase.insertWithOnConflict("receipts", null, receiptValues, SQLiteDatabase.CONFLICT_REPLACE)
			writableDatabase.delete("items", "receipt_id = ?", arrayOf(receipt.id))
			receipt.items.forEachIndexed { index, item ->
				val override = manualOverrides[index]
				val values = ContentValues().apply {
					put("receipt_id", receipt.id)
					put("original_name", item.originalName)
					put("canonical_name", override?.canonicalName ?: item.canonicalName)
					put("category", override?.category ?: item.category)
					put("subcategory", override?.subcategory ?: item.subcategory)
					put("quantity", item.quantity)
					put("total_cents", item.totalCents)
					if (item.vatRate == null) putNull("vat_rate") else put("vat_rate", item.vatRate)

					val spendingType = override?.spendingType ?: item.spendingType
					if (spendingType == null) putNull("spending_type") else put("spending_type", spendingType.name)

					val confidence = if (override != null) null else item.classificationConfidence
					if (confidence == null) putNull("classification_confidence") else put("classification_confidence", confidence)

					val source = if (override != null) ClassificationSource.USER else item.classificationSource
					if (source == null) putNull("classification_source") else put("classification_source", source.name)
				}
				writableDatabase.insertOrThrow("items", null, values)
			}
			writableDatabase.setTransactionSuccessful()
		} finally {
			writableDatabase.endTransaction()
		}
	}

	fun updateItemClassification(
		receiptId: String,
		itemIndex: Int,
		category: String,
		subcategory: String?,
		spendingType: SpendingType,
	): Boolean {
		require(itemIndex >= 0) { "itemIndex must not be negative" }

		val itemId = readableDatabase.rawQuery(
			"""
			SELECT id
			FROM items
			WHERE receipt_id = ?
			ORDER BY id
			LIMIT 1 OFFSET ?
			""".trimIndent(),
			arrayOf(receiptId, itemIndex.toString()),
		).use { cursor ->
			if (!cursor.moveToFirst()) return false
			cursor.getLong(0)
		}

		val values = ContentValues().apply {
			put("category", category)
			if (subcategory == null) putNull("subcategory") else put("subcategory", subcategory)
			put("spending_type", spendingType.name)
			putNull("classification_confidence")
			put("classification_source", ClassificationSource.USER.name)
		}
		return writableDatabase.update(
			"items",
			values,
			"id = ?",
			arrayOf(itemId.toString()),
		) == 1
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
			       spending_type, classification_confidence, classification_source
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
							spendingType = enumOrNull<SpendingType>(cursor, 7),
							classificationConfidence = if (cursor.isNull(8)) null else cursor.getDouble(8),
							classificationSource = enumOrNull<ClassificationSource>(cursor, 9),
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
		       i.spending_type, i.classification_confidence, i.classification_source
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
						spendingType = enumOrNull<SpendingType>(cursor, 9),
						classificationConfidence = if (cursor.isNull(10)) null else cursor.getDouble(10),
						classificationSource = enumOrNull<ClassificationSource>(cursor, 11),
					)
				)
			}
		}
	}

	fun receiptIdsNeedingClassification(limit: Int = 50): List<String> = readableDatabase.rawQuery(
		"""
		SELECT r.receipt_id
		FROM receipts r
		WHERE EXISTS (
			SELECT 1
			FROM items i
			WHERE i.receipt_id = r.receipt_id
			  AND (i.classification_source IS NULL OR i.category = 'Nezaradené')
		)
		ORDER BY r.issued_at DESC
		LIMIT ?
		""".trimIndent(),
		arrayOf(limit.toString()),
	).use { cursor ->
		buildList {
			while (cursor.moveToNext()) {
				add(cursor.getString(0))
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

	fun categoryTotals(): List<CategoryTotal> = readableDatabase.rawQuery(
		"SELECT category, SUM(total_cents) total FROM items GROUP BY category ORDER BY total DESC",
		null,
	).use { cursor ->
		buildList {
			while (cursor.moveToNext()) {
				add(CategoryTotal(cursor.getString(0), cursor.getLong(1)))
			}
		}
	}

	fun subcategoryTotals(): List<SubcategoryTotal> = readableDatabase.rawQuery(
		"""
		SELECT category, subcategory, SUM(total_cents) total
		FROM items
		WHERE subcategory IS NOT NULL AND TRIM(subcategory) <> ''
		GROUP BY category, subcategory
		ORDER BY total DESC
		""".trimIndent(),
		null,
	).use { cursor ->
		buildList {
			while (cursor.moveToNext()) {
				add(
					SubcategoryTotal(
						category = cursor.getString(0),
						subcategory = cursor.getString(1),
						totalCents = cursor.getLong(2),
					)
				)
			}
		}
	}

	fun spendingTypeTotals(): List<SpendingTypeTotal> = readableDatabase.rawQuery(
		"""
		SELECT spending_type, SUM(total_cents) total
		FROM items
		GROUP BY spending_type
		ORDER BY total DESC
		""".trimIndent(),
		null,
	).use { cursor ->
		buildList {
			while (cursor.moveToNext()) {
				add(
					SpendingTypeTotal(
						spendingType = enumOrNull<SpendingType>(cursor, 0),
						totalCents = cursor.getLong(1),
					)
				)
			}
		}
	}

	private fun manualOverrides(receiptId: String): Map<Int, ManualOverride> = readableDatabase.rawQuery(
		"""
		SELECT canonical_name, category, subcategory, spending_type, classification_source
		FROM items
		WHERE receipt_id = ?
		ORDER BY id
		""".trimIndent(),
		arrayOf(receiptId),
	).use { cursor ->
		buildMap {
			var index = 0
			while (cursor.moveToNext()) {
				val source = enumOrNull<ClassificationSource>(cursor, 4)
				if (source == ClassificationSource.USER && !cursor.isNull(3)) {
					put(
						index,
						ManualOverride(
							canonicalName = cursor.getString(0),
							category = cursor.getString(1),
							subcategory = cursor.getString(2),
							spendingType = SpendingType.valueOf(cursor.getString(3)),
						),
					)
				}
				index++
			}
		}
	}

	private inline fun <reified T : Enum<T>> enumOrNull(
		cursor: android.database.Cursor,
		columnIndex: Int,
	): T? = if (cursor.isNull(columnIndex)) {
		null
	} else {
		enumValueOf<T>(cursor.getString(columnIndex))
	}

	private data class ManualOverride(
		val canonicalName: String,
		val category: String,
		val subcategory: String?,
		val spendingType: SpendingType,
	)
}
