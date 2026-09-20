package com.ziacik.blocky.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ziacik.blocky.model.CategoryTotal
import com.ziacik.blocky.model.ProductTotal
import com.ziacik.blocky.model.Receipt
import com.ziacik.blocky.model.ReceiptSummary

class BlockyDatabase(context: Context) : SQLiteOpenHelper(context, "blocky.db", null, 1) {
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
				FOREIGN KEY(receipt_id) REFERENCES receipts(receipt_id) ON DELETE CASCADE
			)
			""".trimIndent()
		)
		db.execSQL("CREATE INDEX idx_items_receipt_id ON items(receipt_id)")
		db.execSQL("CREATE INDEX idx_items_category ON items(category)")
	}

	override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

	fun save(receipt: Receipt) {
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
				}
				writableDatabase.insertOrThrow("items", null, values)
			}
			writableDatabase.setTransactionSuccessful()
		} finally {
			writableDatabase.endTransaction()
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
