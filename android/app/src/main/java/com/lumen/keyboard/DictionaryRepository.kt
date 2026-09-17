package com.lumen.keyboard

import android.content.Context
import org.json.JSONArray

data class DictionaryEntry(
    val word: String,
    val partOfSpeech: String,
    val meaning: String,
    val example: String,
    val synonyms: List<String>
)

class DictionaryRepository(context: Context) {
    private val entries: Map<String, DictionaryEntry> = context.assets.open("dictionary.json")
        .bufferedReader().use { reader ->
            val array = JSONArray(reader.readText())
            buildMap {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val synonymsJson = item.getJSONArray("synonyms")
                    val synonyms = List(synonymsJson.length()) { synonymsJson.getString(it) }
                    val entry = DictionaryEntry(
                        word = item.getString("word"),
                        partOfSpeech = item.getString("partOfSpeech"),
                        meaning = item.getString("meaning"),
                        example = item.getString("example"),
                        synonyms = synonyms
                    )
                    put(entry.word.lowercase(), entry)
                }
            }
        }

    fun lookup(query: String): DictionaryEntry? = entries[query.trim().lowercase()]

    fun suggestions(prefix: String, limit: Int = 5): List<String> {
        val clean = prefix.trim().lowercase()
        if (clean.isBlank()) return emptyList()
        return entries.keys.filter { it.startsWith(clean) }.take(limit)
    }

    fun allWords(): Set<String> = entries.keys
}

