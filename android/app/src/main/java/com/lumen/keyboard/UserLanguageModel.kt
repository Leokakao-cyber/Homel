package com.lumen.keyboard

import android.content.Context

/** Private, device-only adaptive vocabulary. Nothing is uploaded. */
class UserLanguageModel(context: Context) {
    private val prefs = context.getSharedPreferences("lumen_learning", Context.MODE_PRIVATE)

    fun learn(text: String) {
        val words = Regex("[\\p{L}'-]{2,}").findAll(text.lowercase()).map { it.value }.toList()
        val edit = prefs.edit()
        words.forEach { word -> edit.putInt("w:$word", prefs.getInt("w:$word", 0) + 1) }
        words.zipWithNext().forEach { (left, right) ->
            edit.putInt("b:$left:$right", prefs.getInt("b:$left:$right", 0) + 1)
        }
        edit.apply()
    }

    fun words(prefix: String, limit: Int = 5): List<String> = prefs.all.asSequence()
        .filter { (key, _) -> key.startsWith("w:$prefix") }
        .map { (key, value) -> key.removePrefix("w:") to (value as? Int ?: 0) }
        .sortedByDescending { it.second }
        .map { it.first }.take(limit).toList()

    fun next(after: String, limit: Int = 5): List<String> = prefs.all.asSequence()
        .filter { (key, _) -> key.startsWith("b:$after:") }
        .map { (key, value) -> key.substringAfter("b:$after:") to (value as? Int ?: 0) }
        .sortedByDescending { it.second }
        .map { it.first }.take(limit).toList()

    fun clear() = prefs.edit().clear().apply()
}

