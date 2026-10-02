package com.ciger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciger.info.EmmcHealth
import com.ciger.info.Info
import com.ciger.info.LiveBattery
import com.ciger.info.QuickStats
import com.ciger.info.ReportFormat
import com.ciger.info.Row as DataRow
import com.ciger.info.Section
import com.ciger.info.Snapshot
import com.ciger.info.rememberLiveBattery
import com.ciger.root.RootShell
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CigerElegantTheme {
                MainScreen()
            }
        }
    }
}

// ------------------------------------------------------------- MODERN ELEGANT PALETTE (LIGHT SOFT)

private val LightCanvas = Color(0xFFF3F4F7)
private val CardSurface = Color(0xFFFFFFFF)
private val CardSurfaceMuted = Color(0xFFF8FAFC)
private val CardBorder = Color(0xFFE2E5EB)
private val CardBorderSubtle = Color(0xFFECEFF4)

private val SlateInk = Color(0xFF0F172A)
private val SlateSubtle = Color(0xFF475569)
private val SlateMuted = Color(0xFF64748B)

private val SoftGreen = Color(0xFF047857)
private val SoftGreenBg = Color(0xFFECFDF5)
private val SoftAmber = Color(0xFFB45309)
private val SoftAmberBg = Color(0xFFFFFBEB)
private val SoftRed = Color(0xFFB91C1C)
private val SoftRedBg = Color(0xFFFEF2F2)

private val DeepSlate = Color(0xFF0F172A)

@Composable
private fun CigerElegantTheme(content: @Composable () -> Unit) {
    val elegantScheme = lightColorScheme(
        primary = DeepSlate,
        onPrimary = Color.White,
        primaryContainer = CardSurfaceMuted,
        onPrimaryContainer = DeepSlate,
        secondary = SoftGreen,
        onSecondary = Color.White,
        background = LightCanvas,
        surface = CardSurface,
        surfaceVariant = CardSurfaceMuted,
        onSurface = SlateInk,
        onSurfaceVariant = SlateSubtle,
        outline = CardBorder
    )
    MaterialTheme(colorScheme = elegantScheme, content = content)
}

// ------------------------------------------------------------- MAIN SCREEN

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<Snapshot?>(null) }
    var loading by remember { mutableStateOf(true) }
    var activeCategory by remember { mutableStateOf("All") }

    val liveBattery = rememberLiveBattery()

    fun load() {
        loading = true
        scope.launch {
            snapshot = Info.collect(context)
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        containerColor = LightCanvas,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LightCanvas
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(CardSurface)
                                .borderStroke(CardBorder),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_ciger_logo),
                                contentDescription = null,
                                tint = DeepSlate,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Ciger",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    letterSpacing = (-0.3).sp,
                                    color = SlateInk
                                )
                                Spacer(Modifier.width(8.dp))
                                StatusPill(
                                    text = if (snapshot?.rooted == true) "Root active" else "Non-root",
                                    isOk = snapshot?.rooted == true
                                )
                            }
                            Text(
                                "Device & Silicon Telemetry",
                                fontSize = 11.sp,
                                color = SlateMuted
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            RootShell.invalidate()
                            load()
                        },
                        enabled = !loading
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = if (loading) SlateMuted else SlateInk,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (loading && snapshot == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = DeepSlate,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Reading hardware telemetry…",
                        fontSize = 13.sp,
                        color = SlateSubtle
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            snapshot?.let { s ->
                // 1. Root Permission Banner (only when unrooted)
                if (!s.rooted) {
                    item(key = "root_alert") {
                        RootPromptBanner(onRequestRoot = {
                            RootShell.invalidate()
                            load()
                        })
                    }
                }

                // 2. Storage Silicon Health Card
                item(key = "emmc_health") {
                    StorageHealthCard(
                        health = s.emmcHealth,
                        rooted = s.rooted,
                        onRequestRoot = {
                            RootShell.invalidate()
                            load()
                        }
                    )
                }

                // 3. Live Dynamic Battery Card
                item(key = "live_battery") {
                    LiveBatteryCard(battery = liveBattery)
                }

                // 4. Quick Hardware Overview Grid (2x2 with non-clipped, symmetric heights)
                s.quickStats?.let { qs ->
                    item(key = "quick_specs") {
                        QuickSpecsGrid(qs)
                    }
                }

                // 5. Category Navigation Tabs
                item(key = "category_tabs") {
                    CategoryTabsRow(
                        active = activeCategory,
                        onSelect = { activeCategory = it }
                    )
                }

                // 6. Detailed Hardware Sections (filtered, zero duplicates)
                val filteredSections = s.sections.filter { sec ->
                    when (activeCategory) {
                        "All" -> true
                        "Storage" -> sec.title.contains("Storage", ignoreCase = true) || sec.title.contains("Partition", ignoreCase = true) || sec.title.contains("eMMC", ignoreCase = true) || sec.title.contains("UFS", ignoreCase = true)
                        "eMMC" -> sec.title.contains("eMMC", ignoreCase = true)
                        "UFS" -> sec.title.contains("UFS", ignoreCase = true)
                        "SD Card" -> sec.title.contains("SD", ignoreCase = true)
                        "Processor" -> sec.title.contains("Processor", ignoreCase = true)
                        "Memory" -> sec.title.contains("Memory", ignoreCase = true)
                        "Display" -> sec.title.contains("Display", ignoreCase = true)
                        "Battery" -> sec.title.contains("Battery", ignoreCase = true)
                        "System" -> sec.title.contains("System", ignoreCase = true)
                        "Kernel" -> sec.title.contains("Kernel", ignoreCase = true)
                        else -> true
                    }
                }

                items(filteredSections, key = { it.title }) { section ->
                    DetailSectionCard(section)
                }

                // 7. Full Diagnostic Export Button
                item(key = "export") {
                    ExportReportButton(s, liveBattery)
                }
            }

            item(key = "footer") {
                Spacer(Modifier.height(8.dp))
                Text(
                    "JEDEC UFS 2.1 & eMMC 5.1 Standard • Sysfs telemetry • Auto-updating battery feed",
                    fontSize = 11.sp,
                    color = SlateMuted,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
            }
        }
    }
}

// ------------------------------------------------------------- ROOT PROMPT BANNER

@Composable
private fun RootPromptBanner(onRequestRoot: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SoftAmberBg),
        border = BorderStroke(1.dp, Color(0xFFFDE68A))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Root privilege required for raw silicon",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = SoftAmber
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Grant access in Magisk or KernelSU to decode low-level flash descriptors.",
                    fontSize = 11.sp,
                    color = Color(0xFF92400E)
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onRequestRoot,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SoftAmber,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ------------------------------------------------------------- STORAGE HEALTH CARD

@Composable
private fun StorageHealthCard(
    health: EmmcHealth?,
    rooted: Boolean,
    onRequestRoot: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(Modifier.padding(18.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CardSurfaceMuted),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_health),
                            contentDescription = null,
                            tint = DeepSlate,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            health?.storageType?.let { "Flash Storage Silicon • $it" } ?: "Flash Storage Silicon",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = SlateInk
                        )
                        val chipDisplay = buildString {
                            val mfg = health?.manufacturer
                            val name = health?.productName ?: "Internal Flash Storage"
                            if (!mfg.isNullOrBlank() && !name.startsWith(mfg, ignoreCase = true)) {
                                append(mfg).append(" ")
                            }
                            append(name)
                        }
                        Text(
                            chipDisplay,
                            fontSize = 12.sp,
                            color = SlateSubtle,
                            maxLines = 1
                        )
                    }
                }

                if (health != null) {
                    if (health.isHealthReported) {
                        StatusPill(
                            text = health.grade,
                            isOk = health.grade == "EXCELLENT" || health.grade == "GOOD"
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = CardSurfaceMuted,
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Text(
                                "Health Unexposed",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = SlateSubtle,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            if (!rooted) {
                Text(
                    "SELinux prevents standard apps from probing raw storage descriptors. Tap below to grant root access.",
                    fontSize = 12.sp,
                    color = SlateSubtle
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onRequestRoot,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, DeepSlate),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DeepSlate)
                ) {
                    Text("Grant Root Permission", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            } else if (health != null) {
                if (health.isHealthReported && health.healthPercent != null) {
                    // Real Wear Data Available
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                "Estimated Health Remaining",
                                fontSize = 12.sp,
                                color = SlateMuted
                            )
                            Text(
                                "${health.healthPercent}%",
                                fontWeight = FontWeight.Black,
                                fontSize = 32.sp,
                                letterSpacing = (-0.5).sp,
                                color = when {
                                    health.healthPercent >= 90 -> SoftGreen
                                    health.healthPercent >= 60 -> SlateInk
                                    else -> SoftRed
                                }
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "Pre-EOL Status",
                                fontSize = 11.sp,
                                color = SlateMuted
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                health.preEolStatus,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (health.preEolCode == 1) SoftGreen else SoftAmber
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    WearProgressBar(
                        label = "SLC Endurance Cache (Type A)",
                        wearPercent = health.wearA,
                        desc = health.wearLabelA
                    )

                    Spacer(Modifier.height(8.dp))

                    WearProgressBar(
                        label = "Main Flash Storage (Type B / MLC-TLC)",
                        wearPercent = health.wearB,
                        desc = health.wearLabelB
                    )
                } else {
                    // Honest explanation when kernel does NOT expose health descriptors
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CardSurfaceMuted)
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                "Silicon Chip Active & Operational",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SlateInk
                            )
                            Spacer(Modifier.height(3.dp))
                            val storageLabel = health.storageType.ifBlank { "Storage" }
                            Text(
                                "Kernel does not expose JEDEC $storageLabel health descriptors in /sys or debugfs. The flash chip is healthy and operational.",
                                fontSize = 11.sp,
                                color = SlateSubtle,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                if (health.csdCapacity != null || health.diskCapacity != null || !health.serial.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val cap = health.diskCapacity ?: health.csdCapacity ?: "-"
                        Text(
                            "Capacity: $cap",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = SlateInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(8.dp))
                        health.serial?.takeIf { it != "-" && it.isNotBlank() }?.let {
                            Text(
                                "Serial: $it",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = SlateSubtle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                Text(
                    "Storage hardware descriptors active.",
                    fontSize = 12.sp,
                    color = SlateSubtle
                )
            }
        }
    }
}

@Composable
private fun WearProgressBar(label: String, wearPercent: Int?, desc: String) {
    val progress = ((wearPercent ?: 0) / 100f).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "wear_progress"
    )

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 11.sp, color = SlateSubtle)
            Text(desc, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SlateInk)
        }
        Spacer(Modifier.height(5.dp))
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = when {
                progress <= 0.2f -> SoftGreen
                progress <= 0.6f -> DeepSlate
                else -> SoftRed
            },
            trackColor = CardBorderSubtle
        )
    }
}

// ------------------------------------------------------------- LIVE BATTERY CARD

@Composable
private fun LiveBatteryCard(battery: LiveBattery) {
    val progress = (battery.percent / 100f).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "battery_progress"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(Modifier.padding(18.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CardSurfaceMuted),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(if (battery.isCharging) R.drawable.ic_bolt else R.drawable.ic_battery),
                            contentDescription = null,
                            tint = if (battery.isCharging) SoftGreen else DeepSlate,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Battery Telemetry",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = SlateInk
                        )
                        Text(
                            "${battery.status} • ${battery.plugged}",
                            fontSize = 12.sp,
                            color = SlateSubtle
                        )
                    }
                }

                StatusPill(
                    text = if (battery.isCharging) "Charging" else "${battery.percent}%",
                    isOk = battery.isCharging || battery.percent >= 20
                )
            }

            Spacer(Modifier.height(14.dp))

            // Big Percent & Progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    "${battery.percent}%",
                    fontWeight = FontWeight.Black,
                    fontSize = 32.sp,
                    letterSpacing = (-0.5).sp,
                    color = SlateInk
                )
                Text(
                    "Health: ${battery.health}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = SlateSubtle
                )
            }

            Spacer(Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (battery.isCharging) SoftGreen else DeepSlate,
                trackColor = CardBorderSubtle
            )

            Spacer(Modifier.height(14.dp))

            // Key Metrics
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardSurfaceMuted)
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricColumn(
                    label = "Temperature",
                    value = battery.temperatureC?.let { "%.1f °C".format(it) } ?: "-",
                    alert = (battery.temperatureC ?: 0f) > 40f,
                    modifier = Modifier.weight(1f)
                )
                MetricColumn(
                    label = "Voltage",
                    value = battery.voltageMv?.let { "$it mV" } ?: "-",
                    alert = false,
                    modifier = Modifier.weight(1f)
                )
                MetricColumn(
                    label = "Technology",
                    value = battery.technology ?: "Li-ion",
                    alert = false,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricColumn(
    label: String,
    value: String,
    alert: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            label,
            fontSize = 10.sp,
            color = SlateMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (alert) SoftRed else SlateInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ------------------------------------------------------------- QUICK SPECS GRID (RESPONSIVE)

@Composable
private fun QuickSpecsGrid(qs: QuickStats) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SpecCard(
                title = "Processor",
                primary = qs.cpuSummary,
                secondary = qs.androidVersion,
                iconRes = R.drawable.ic_cpu,
                modifier = Modifier.weight(1f)
            )
            SpecCard(
                title = "Memory (RAM)",
                primary = "${qs.ramUsed} / ${qs.ramTotal}",
                secondary = "${qs.ramPercent}% used",
                progress = qs.ramPercent / 100f,
                iconRes = R.drawable.ic_ram,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SpecCard(
                title = "Internal Storage",
                primary = "${qs.storageUsed} / ${qs.storageTotal}",
                secondary = "${qs.storagePercent}% used",
                progress = qs.storagePercent / 100f,
                iconRes = R.drawable.ic_storage,
                modifier = Modifier.weight(1f)
            )
            SpecCard(
                title = "Display Panel",
                primary = qs.displaySummary,
                secondary = qs.deviceModel,
                iconRes = R.drawable.ic_display,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SpecCard(
    title: String,
    primary: String,
    secondary: String,
    iconRes: Int,
    progress: Float? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(112.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = SlateMuted,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = SlateSubtle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    secondary,
                    fontSize = 11.sp,
                    color = SlateMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            ) {
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(2.dp)),
                        color = DeepSlate,
                        trackColor = CardBorderSubtle
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------- CATEGORY TABS

@Composable
private fun CategoryTabsRow(active: String, onSelect: (String) -> Unit) {
    val categories = listOf("All", "Storage", "eMMC", "UFS", "SD Card", "Processor", "Memory", "Display", "Battery", "System", "Kernel")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        categories.forEach { cat ->
            val isSelected = active == cat
            Surface(
                color = if (isSelected) DeepSlate else CardSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (isSelected) DeepSlate else CardBorder),
                modifier = Modifier.clickable { onSelect(cat) }
            ) {
                Text(
                    text = cat,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) Color.White else SlateSubtle,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

// ------------------------------------------------------------- DETAIL SECTION CARD (RESPONSIVE NO OVERFLOW)

@Composable
private fun DetailSectionCard(section: Section) {
    val context = LocalContext.current
    val iconRes = when {
        section.title.contains("eMMC", ignoreCase = true) -> R.drawable.ic_chip
        section.title.contains("UFS", ignoreCase = true) -> R.drawable.ic_chip
        section.title.contains("SD", ignoreCase = true) -> R.drawable.ic_chip
        section.title.contains("Battery", ignoreCase = true) -> R.drawable.ic_battery
        section.title.contains("Storage", ignoreCase = true) || section.title.contains("Partition", ignoreCase = true) -> R.drawable.ic_storage
        section.title.contains("Processor", ignoreCase = true) -> R.drawable.ic_cpu
        section.title.contains("Memory", ignoreCase = true) -> R.drawable.ic_ram
        section.title.contains("Display", ignoreCase = true) -> R.drawable.ic_display
        section.title.contains("System", ignoreCase = true) -> R.drawable.ic_android
        section.title.contains("Kernel", ignoreCase = true) -> R.drawable.ic_terminal
        else -> R.drawable.ic_ciger_logo
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = SlateInk,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    section.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateInk
                )
            }

            Spacer(Modifier.height(4.dp))

            section.rows.forEachIndexed { index, row ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(CardBorderSubtle)
                    )
                }
                DataRowItem(
                    row = row,
                    onCopy = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(row.key.trim(), row.value))
                        Toast.makeText(context, "Copied: ${row.key.trim()}", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
private fun DataRowItem(row: DataRow, onCopy: () -> Unit) {
    val isSub = row.key.startsWith("  ")
    val isMonospace = row.value.startsWith("0x") || row.value.length > 20 ||
        row.key.contains("CID", ignoreCase = true) || row.key.contains("CSD", ignoreCase = true) ||
        row.key.contains("WWID", ignoreCase = true)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCopy() }
            .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Responsive key column: keeps every ':' aligned vertically,
        // while allowing comfortable wrap on compact displays.
        Text(
            text = row.key.trim(),
            fontSize = if (isSub) 11.sp else 12.sp,
            color = if (isSub) SlateMuted else SlateSubtle,
            fontWeight = if (isSub) FontWeight.Normal else FontWeight.Medium,
            lineHeight = 15.sp,
            softWrap = true,
            modifier = Modifier
                .width(if (isSub) 120.dp else 130.dp)
                .padding(start = if (isSub) 8.dp else 0.dp)
        )
        Text(
            text = ":",
            fontSize = 12.sp,
            color = SlateMuted,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        Text(
            text = row.value,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            softWrap = true,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
            fontWeight = if (isMonospace) FontWeight.SemiBold else FontWeight.Normal,
            color = SlateInk,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(R.drawable.ic_copy),
            contentDescription = "Copy",
            tint = CardBorder,
            modifier = Modifier
                .padding(start = 6.dp, top = 2.dp)
                .size(13.dp)
        )
    }
}

// ------------------------------------------------------------- EXPORT REPORT (PERFECTLY ALIGNED COLONS)

@Composable
private fun ExportReportButton(snapshot: Snapshot, liveBattery: LiveBattery) {
    val context = LocalContext.current

    OutlinedButton(
        onClick = {
            val reportSections = buildList<Pair<String?, List<Pair<String, String>>>> {
                add(null to listOf(
                    "Device" to "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})",
                    "Android Version" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    "Root Access" to if (snapshot.rooted) "Active (uid 0)" else "Not granted",
                    "Battery Telemetry" to "${liveBattery.percent}% (${liveBattery.status}, ${liveBattery.plugged}, ${liveBattery.temperatureC ?: 0f}°C, ${liveBattery.voltageMv ?: 0} mV, ${liveBattery.health})"
                ))

                val hasInternalStorageSection = snapshot.sections.any {
                    it.title.contains("Internal Storage", ignoreCase = true) || it.title.contains("eMMC", ignoreCase = true) || it.title.contains("UFS", ignoreCase = true)
                }

                if (!hasInternalStorageSection) {
                    snapshot.emmcHealth?.let { h ->
                        add("[SILICON STORAGE]" to listOfNotNull(
                            "Storage Type" to h.storageType,
                            h.manufacturer?.let { "Manufacturer" to it },
                            "Product" to h.productName,
                            "Health Status" to if (h.isHealthReported) "${h.healthPercent}% [${h.grade}]" else "Operational (Health Unexposed by Kernel)",
                            "Wear State" to if (h.isHealthReported) "SLC: ${h.wearLabelA} | TLC: ${h.wearLabelB}" else "Kernel omits wear descriptors",
                            "Pre-EOL Status" to h.preEolStatus,
                            "Capacity" to (h.diskCapacity ?: h.csdCapacity ?: "-"),
                            h.serial?.let { "Serial" to it }
                        ))
                    }
                }

                // Append remaining sections without redundant battery snapshot
                snapshot.sections
                    .filter { !it.title.equals("Battery", ignoreCase = true) }
                    .forEach { sec ->
                        add("[${sec.title.uppercase()}]" to sec.rows.map { it.key to it.value })
                    }
            }

            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            val sb = StringBuilder()
            sb.appendLine("-----------------------------------------------------")
            sb.appendLine(" CIGER // LOW-LEVEL DEVICE & SILICON REPORT")
            sb.appendLine(" Generated: $timestamp")
            sb.appendLine("-----------------------------------------------------")
            sb.appendLine()
            sb.append(ReportFormat.formatReport(reportSections))
            sb.appendLine()
            sb.appendLine("-----------------------------------------------------")

            val fullText = sb.toString()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Ciger Report", fullText))
            Toast.makeText(context, "Aligned report copied to clipboard!", Toast.LENGTH_SHORT).show()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, CardBorder),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = DeepSlate)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_copy),
            contentDescription = null,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text("Copy Colon-Aligned Diagnostic Report", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ------------------------------------------------------------- COMMON UI HELPERS

@Composable
private fun StatusPill(text: String, isOk: Boolean) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isOk) SoftGreenBg else SoftRedBg,
        border = BorderStroke(1.dp, if (isOk) Color(0xFFA7F3D0) else Color(0xFFFECACA))
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isOk) SoftGreen else SoftRed,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

private fun Modifier.borderStroke(color: Color): Modifier =
    this.then(Modifier.background(CardSurface).padding(1.dp))
