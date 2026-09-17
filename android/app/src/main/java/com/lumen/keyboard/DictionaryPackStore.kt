package com.lumen.keyboard

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.InputStream
import java.util.zip.GZIPInputStream

/** Disk-backed index designed for optional language packs up to 1,000,000 entries. */
class DictionaryPackStore(context: Context) : SQLiteOpenHelper(context, "lumen_words.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE words(word TEXT PRIMARY KEY, frequency INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX word_prefix ON words(word)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun importGzip(stream: InputStream, maximum: Int = 1_000_000): Int {
        val db = writableDatabase
        val statement = db.compileStatement("INSERT OR REPLACE INTO words(word, frequency) VALUES(?, ?)")
        var count = 0
        db.beginTransaction()
        try {
            GZIPInputStream(stream).bufferedReader().useLines { lines ->
                lines.take(maximum).forEach { line ->
                    val parts = line.trim().split(Regex("\\s+"), limit = 2)
                    val word = parts[0].lowercase()
                    if (word.matches(Regex("[\\p{L}'-]+"))) {
                        statement.bindString(1, word)
                        statement.bindLong(2, parts.getOrNull(1)?.toLongOrNull() ?: 0)
                        statement.executeInsert()
                        count++
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return count
    }

    fun prefix(query: String, limit: Int = 8): List<String> {
        val escaped = query.lowercase().replace("%", "\\%").replace("_", "\\_")
        return readableDatabase.rawQuery(
            "SELECT word FROM words WHERE word LIKE ? ESCAPE '\\' ORDER BY frequency DESC LIMIT ?",
            arrayOf("$escaped%", limit.toString())
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
    }
}

