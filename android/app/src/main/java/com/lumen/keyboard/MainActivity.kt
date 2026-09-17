package com.lumen.keyboard

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.ScrollView
import android.widget.Toast
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val importWords = 7001
    private val worker = Executors.newSingleThreadExecutor()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val padding = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(padding, padding * 2, padding, padding)
            setBackgroundColor(Color.rgb(16, 19, 26))
        }

        root.addView(TextView(this).apply {
            text = "Lumen Keyboard"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, matchWrap())

        root.addView(TextView(this).apply {
            text = "Your Dictionary-first Android keyboard.\n\n1. Enable Lumen Keyboard\n2. Select it as your current keyboard\n3. Tap Dictionary in the toolbar to look up and insert words"
            textSize = 17f
            setTextColor(Color.rgb(190, 198, 214))
            gravity = Gravity.CENTER
            setPadding(0, padding, 0, padding)
        }, matchWrap())

        root.addView(Button(this).apply {
            text = "Enable Lumen Keyboard"
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        }, matchWrap())

        val prefs = getSharedPreferences("lumen_settings", MODE_PRIVATE)
        root.addView(CheckBox(this).apply {
            text = "Keypress sound"; setTextColor(Color.WHITE); isChecked = prefs.getBoolean("sound", true)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("sound", checked).apply() }
        }, matchWrap())
        root.addView(CheckBox(this).apply {
            text = "Key popup preview"; setTextColor(Color.WHITE); isChecked = prefs.getBoolean("popup", true)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("popup", checked).apply() }
        }, matchWrap())

        val fonts = listOf(
            "Default", "Light", "Medium", "Condensed", "Black", "Thin", "Small caps", "Serif",
            "Serif mono", "Monospace", "Cursive", "Casual", "Rounded", "Condensed light",
            "Condensed medium", "Condensed bold", "Medium italic", "Light italic", "Classic mono", "System"
        )
        root.addView(TextView(this).apply { text = "Keyboard font style (20)"; setTextColor(Color.WHITE) }, matchWrap())
        root.addView(Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, fonts)
            setSelection(prefs.getInt("font", 0))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    prefs.edit().putInt("font", position).apply()
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
        }, matchWrap())

        root.addView(Button(this).apply {
            text = "Install dictionary pack (.txt.gz)"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "application/gzip"
                }, importWords)
            }
        }, matchWrap())
        root.addView(Button(this).apply {
            text = "Clear words Lumen learned"
            setOnClickListener { UserLanguageModel(this@MainActivity).clear(); text = "Learning cleared" }
        }, matchWrap())

        root.addView(TextView(this).apply {
            text = "Optional secure Smart Format endpoint"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(0, padding, 0, 8)
        }, matchWrap())

        val endpoint = EditText(this).apply {
            hint = "https://kakaos.name.ng/api/assist"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setText(getSharedPreferences("lumen_settings", MODE_PRIVATE).getString("smart_api_url", ""))
        }
        root.addView(endpoint, matchWrap())
        val accessToken = EditText(this).apply {
            hint = "Private beta access token"; setSingleLine(true); inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
        }
        root.addView(accessToken, matchWrap())
        root.addView(Button(this).apply {
            text = "Save secure endpoint"
            setOnClickListener {
                val url = endpoint.text.toString().trim()
                if (url.isNotEmpty() && !url.startsWith("https://")) {
                    endpoint.error = "Lumen only accepts HTTPS endpoints"
                } else {
                    getSharedPreferences("lumen_settings", MODE_PRIVATE).edit()
                        .putString("smart_api_url", url).putString("smart_access_token", accessToken.text.toString().trim()).apply()
                    text = "Saved"
                }
            }
        }, matchWrap())

        root.addView(Button(this).apply {
            text = "Choose Keyboard"
            setOnClickListener {
                (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                    .showInputMethodPicker()
            }
        }, matchWrap())

        setContentView(ScrollView(this).apply { addView(root) })
    }

    @Deprecated("Uses the compatible document picker callback for Android 8+")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != importWords || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        Toast.makeText(this, "Importing language pack…", Toast.LENGTH_SHORT).show()
        worker.execute {
            val result = runCatching {
                contentResolver.openInputStream(uri)?.use { DictionaryPackStore(this).importGzip(it) }
                    ?: error("Could not open file")
            }
            runOnUiThread {
                Toast.makeText(this, result.fold(
                    onSuccess = { "Installed $it words" },
                    onFailure = { "Import failed: ${it.message}" }
                ), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
}

