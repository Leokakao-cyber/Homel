package com.lumen.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

enum class SmartAction { GRAMMAR, REWRITE, TRANSLATE }
enum class Tone { SIMPLE, PROFESSIONAL, FORMAL, CASUAL }

data class SmartResult(val output: String, val source: String, val error: String? = null)

class SmartFormatEngine(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun transform(input: String, action: SmartAction, tone: Tone, callback: (SmartResult) -> Unit) {
        val local = localTransform(input, action, tone)
        val preferences = context.getSharedPreferences("lumen_settings", Context.MODE_PRIVATE)
        val endpoint = preferences.getString("smart_api_url", "").orEmpty().trim()
        val token = preferences.getString("smart_access_token", "").orEmpty().trim()

        if (!endpoint.startsWith("https://") || token.length < 32) {
            callback(SmartResult(local, "Offline"))
            return
        }

        executor.execute {
            val result = runCatching { callApi(endpoint, token, input, action, tone) }
                .fold(
                    onSuccess = { SmartResult(it, "Secure API") },
                    onFailure = { SmartResult(local, "Offline fallback", it.message) }
                )
            main.post { callback(result) }
        }
    }

    private fun callApi(endpoint: String, token: String, input: String, action: SmartAction, tone: Tone): String {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            val body = JSONObject()
                .put("text", input)
                .put("action", action.name.lowercase())
                .put("tone", tone.name.lowercase())
                .toString()
            connection.outputStream.bufferedWriter().use { it.write(body) }
            if (connection.responseCode !in 200..299) error("Server returned ${connection.responseCode}")
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(response).getString("text").trim().ifBlank { error("Empty output") }
        } finally {
            connection.disconnect()
        }
    }

    private fun localTransform(input: String, action: SmartAction, tone: Tone): String {
        var output = input.trim()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\bi\\b", RegexOption.IGNORE_CASE), "I")
            .replace(Regex("\\bdont\\b", RegexOption.IGNORE_CASE), "don't")
            .replace(Regex("\\bcant\\b", RegexOption.IGNORE_CASE), "can't")
            .replace(Regex("\\bwont\\b", RegexOption.IGNORE_CASE), "won't")
        if (output.isNotBlank()) output = output.replaceFirstChar { it.uppercase() }
        if (action == SmartAction.GRAMMAR && output.isNotBlank() && output.last() !in ".!?") output += "."
        if (action == SmartAction.REWRITE) {
            output = when (tone) {
                Tone.SIMPLE -> output.replace("in order to", "to").replace("utilize", "use")
                Tone.PROFESSIONAL -> output.replace(Regex("\\bhey\\b", RegexOption.IGNORE_CASE), "Hello")
                Tone.FORMAL -> output.replace(Regex("\\bcan't\\b", RegexOption.IGNORE_CASE), "cannot")
                    .replace(Regex("\\bdon't\\b", RegexOption.IGNORE_CASE), "do not")
                Tone.CASUAL -> output.replace(Regex("\\bHello\\b"), "Hi")
            }
        }
        if (action == SmartAction.TRANSLATE) return output
        return output
    }
}

