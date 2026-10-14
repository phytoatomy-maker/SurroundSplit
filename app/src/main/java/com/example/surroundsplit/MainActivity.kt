package com.example.surroundsplit

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.io.File

class MainActivity : Activity() {
    private var devices: List<AudioDeviceInfo> = emptyList()
    private lateinit var urlEdit: EditText
    private lateinit var frontSpinner: Spinner
    private lateinit var rearSpinner: Spinner
    private lateinit var offsetBar: SeekBar
    private lateinit var offsetText: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(32), dp(48), dp(32))
        }
        fun text(s: String, size: Float = 16f) = TextView(this).apply { text = s; textSize = size; setPadding(0, dp(12), 0, dp(4)) }
        fun button(s: String, onClick: () -> Unit) = Button(this).apply { text = s; setOnClickListener { onClick() } }

        root.addView(text("Surround Split Player", 28f))
        root.addView(text("Front L/R (+center, sub) -> front device.   Rear L/R -> rear device."))

        root.addView(text("Video (URL, /path/file.mkv, or Browse)"))
        urlEdit = EditText(this).apply { setSingleLine(); hint = "https://... or /sdcard/Movies/film.mkv" }
        root.addView(urlEdit, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(button("Browse files") { browse() })

        root.addView(text("FRONT output (TV speakers / HDMI)"))
        frontSpinner = Spinner(this); root.addView(frontSpinner)
        root.addView(text("REAR output (AUX / USB / Bluetooth)"))
        rearSpinner = Spinner(this); root.addView(rearSpinner)
        root.addView(button("Refresh devices") { refreshDevices() })

        root.addView(text("Sync offset  (+ delays rear, - delays front)"))
        offsetText = TextView(this).apply { textSize = 16f }
        root.addView(offsetText)
        offsetBar = SeekBar(this).apply {
            max = 1000; keyProgressIncrement = 10
            progress = Prefs.offset(this@MainActivity) + 500
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { offsetText.text = "${p - 500} ms" }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        offsetText.text = "${offsetBar.progress - 500} ms"
        root.addView(offsetBar, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        root.addView(button("PLAY") { play() })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() { super.onResume(); refreshDevices() }

    private fun selected(sp: Spinner): AudioDeviceInfo? = devices.getOrNull(sp.selectedItemPosition - 1)

    private fun refreshDevices() {
        val prevFront = selected(frontSpinner)?.key() ?: Prefs.frontKey(this)
        val prevRear = selected(rearSpinner)?.key() ?: Prefs.rearKey(this)
        devices = outputDevices(this)
        val names = listOf("Auto (system default)") + devices.map { it.label() }
        for (sp in listOf(frontSpinner, rearSpinner))
            sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        frontSpinner.setSelection(devices.indexOfFirst { it.key() == prevFront } + 1)
        var r = devices.indexOfFirst { it.key() == prevRear } + 1
        if (r == 0 && prevRear == null) r = devices.indexOfFirst { it.type in REAR_LIKELY_TYPES } + 1
        rearSpinner.setSelection(r)
    }

    private fun browse() {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "video/*"
            }, 1)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No file picker on this TV - type a path/URL, or open the video from a file manager app", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 1 && res == RESULT_OK) data?.data?.let { urlEdit.setText(it.toString()) }
    }

    private fun play() {
        val s = urlEdit.text.toString().trim()
        if (s.isEmpty()) { Toast.makeText(this, "Enter or pick a video", Toast.LENGTH_SHORT).show(); return }
        val uri = if (s.startsWith("/")) Uri.fromFile(File(s)) else Uri.parse(s)
        if (uri.scheme == "file" && Build.VERSION.SDK_INT in 23..32 &&
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 2)
            Toast.makeText(this, "Grant storage access, then press PLAY again", Toast.LENGTH_LONG).show()
            return
        }
        Prefs.save(this, selected(frontSpinner)?.key(), selected(rearSpinner)?.key(), offsetBar.progress - 500)
        startActivity(Intent(this, PlayerActivity::class.java).setData(uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}
