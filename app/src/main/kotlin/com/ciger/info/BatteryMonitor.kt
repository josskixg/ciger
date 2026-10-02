package com.ciger.info

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

data class LiveBattery(
    val level: Int = -1,
    val scale: Int = 100,
    val percent: Int = 0,
    val status: String = "Detecting…",
    val isCharging: Boolean = false,
    val health: String = "Good",
    val plugged: String = "Unplugged",
    val temperatureC: Float? = null,
    val voltageMv: Int? = null,
    val technology: String? = null
)

object BatteryMonitor {

    fun parse(intent: Intent): LiveBattery {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val percent = if (level >= 0) (level * 100) / scale else 0
        val statusVal = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = statusVal == BatteryManager.BATTERY_STATUS_CHARGING ||
            statusVal == BatteryManager.BATTERY_STATUS_FULL

        val status = when (statusVal) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full (Charged)"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            else -> "Unknown"
        }

        val health = when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good (Healthy)"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat!"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Normal"
        }

        val pluggedVal = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val plugged = when (pluggedVal) {
            BatteryManager.BATTERY_PLUGGED_AC -> "Fast AC Charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB Cable"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Dock"
            else -> "On Battery"
        }

        val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val temp = if (tempRaw != Int.MIN_VALUE) tempRaw / 10f else null
        val volt = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1).takeIf { it > 0 }
        val tech = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)

        return LiveBattery(
            level = level,
            scale = scale,
            percent = percent,
            status = status,
            isCharging = isCharging,
            health = health,
            plugged = plugged,
            temperatureC = temp,
            voltageMv = volt,
            technology = tech
        )
    }

    fun getInitial(context: Context): LiveBattery {
        val intent = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) {
            null
        }
        return if (intent != null) parse(intent) else LiveBattery()
    }
}

@Composable
fun rememberLiveBattery(): LiveBattery {
    val context = LocalContext.current
    var liveBattery by remember { mutableStateOf(BatteryMonitor.getInitial(context)) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent != null && intent.action == Intent.ACTION_BATTERY_CHANGED) {
                    liveBattery = BatteryMonitor.parse(intent)
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        try {
            val sticky = context.registerReceiver(receiver, filter)
            if (sticky != null) {
                liveBattery = BatteryMonitor.parse(sticky)
            }
        } catch (_: Exception) {}

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }
    return liveBattery
}
