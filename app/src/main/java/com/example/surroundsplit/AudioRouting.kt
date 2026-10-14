package com.example.surroundsplit

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/** Shared state read by the audio sink / processors. */
object SplitConfig {
    @Volatile var frontDelayMs = 0
    @Volatile var rearDelayMs = 0
    @Volatile var frontDevice: AudioDeviceInfo? = null
    @Volatile var rearDevice: AudioDeviceInfo? = null

    /** ms > 0 : delay the REAR output (use when front/TV path is slower).
     *  ms < 0 : delay the FRONT output (use when rear path, e.g. Bluetooth, is slower). */
    fun setSyncOffsetMs(ms: Int) {
        if (ms >= 0) { rearDelayMs = ms; frontDelayMs = 0 }
        else { rearDelayMs = 0; frontDelayMs = -ms }
    }
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("split", Context.MODE_PRIVATE)
    fun frontKey(c: Context): String? = sp(c).getString("front", null)
    fun rearKey(c: Context): String? = sp(c).getString("rear", null)
    fun offset(c: Context): Int = sp(c).getInt("offset", 0)
    fun save(c: Context, front: String?, rear: String?, offset: Int) =
        sp(c).edit().putString("front", front).putString("rear", rear).putInt("offset", offset).apply()
    fun setOffset(c: Context, v: Int) = sp(c).edit().putInt("offset", v).apply()
}

fun outputDevices(ctx: Context): List<AudioDeviceInfo> =
    ctx.getSystemService(AudioManager::class.java)
        .getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()

/** Device ids change when plugged/unplugged, so persist type+name instead. */
fun AudioDeviceInfo.key(): String = "$type|$productName"

fun findDevice(ctx: Context, key: String?): AudioDeviceInfo? =
    if (key == null) null else outputDevices(ctx).firstOrNull { it.key() == key }

fun AudioDeviceInfo.label(): String = "${typeName(type)}  [$productName]"

fun typeName(t: Int): String = when (t) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in speaker"
    AudioDeviceInfo.TYPE_HDMI -> "HDMI"
    10 -> "HDMI ARC"
    29 -> "HDMI eARC"
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "3.5mm headphones/aux"
    AudioDeviceInfo.TYPE_WIRED_HEADSET -> "3.5mm headset"
    AudioDeviceInfo.TYPE_LINE_ANALOG -> "Analog line out"
    AudioDeviceInfo.TYPE_AUX_LINE -> "AUX line"
    AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio"
    AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
    else -> "Output type $t"
}

val REAR_LIKELY_TYPES = setOf(
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_AUX_LINE,
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
)
