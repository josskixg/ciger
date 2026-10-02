package com.ciger.info

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.WindowManager
import com.ciger.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class Row(val key: String, val value: String)

data class Section(val title: String, val rows: List<Row>)

data class EmmcHealth(
    val productName: String,
    val storageType: String = "eMMC",
    val manufacturer: String? = null,
    val isHealthReported: Boolean, // false when kernel omits descriptors
    val healthPercent: Int?,
    val wearA: Int?,
    val wearB: Int?,
    val wearLabelA: String,
    val wearLabelB: String,
    val preEolStatus: String,
    val preEolCode: Int,
    val grade: String,
    val serial: String?,
    val cid: String?,
    val csdCapacity: String?,
    val diskCapacity: String?
)

data class QuickStats(
    val cpuSummary: String,
    val ramTotal: String,
    val ramUsed: String,
    val ramPercent: Int,
    val storageTotal: String,
    val storageUsed: String,
    val storagePercent: Int,
    val displaySummary: String,
    val androidVersion: String,
    val deviceModel: String
)

data class Snapshot(
    val rooted: Boolean,
    val sections: List<Section>,
    val emmcHealth: EmmcHealth? = null,
    val quickStats: QuickStats? = null
)

/**
 * Everything the app shows. Non-root reads go straight to /proc and /sys;
 * anything SELinux hides from apps (cid, csd, life_time, ufs) goes through RootShell.
 */
object Info {

    suspend fun collect(context: Context): Snapshot = withContext(Dispatchers.IO) {
        val rooted = RootShell.isRooted()
        var emmcHealth: EmmcHealth? = null

        val sections = buildList {
            if (rooted) {
                val storageResult = collectStorageHardware()
                storageResult.sections.forEach { add(it) }
                emmcHealth = storageResult.health
            } else {
                val nonRootStorage = collectNonRootStorage()
                nonRootStorage.sections.forEach { add(it) }
                emmcHealth = nonRootStorage.health
            }
            storagePartitionSection()?.let { add(it) }
            processorSection()?.let { add(it) }
            memorySection()?.let { add(it) }
            displaySection(context)?.let { add(it) }
            batterySection(context)?.let { add(it) }
            systemSection()?.let { add(it) }
            kernelSection()?.let { add(it) }
        }

        val quickStats = computeQuickStats(context)
        Snapshot(rooted, sections, emmcHealth, quickStats)
    }

    // ------------------------------------------------------------- STORAGE HARDWARE (UFS & eMMC & SD)

    private val STORAGE_SCRIPT_RAW = """
mount -t debugfs none /sys/kernel/debug 2>/dev/null
mount -t debugfs none /d 2>/dev/null

# 1. Probe MMC devices (eMMC & External MicroSD)
for d in /sys/bus/mmc/devices/* /sys/class/mmc_host/mmc*/* /sys/block/mmcblk*/device/; do
  [ -d "@d" ] || continue
  echo "==MMC @d"
  for f in manfid name oemid serial fwrev hwrev date type life_time pre_eol_info enhanced_size cid csd rev preferred_erase_size erase_size; do
    v=@(cat "@d@f" 2>/dev/null)
    [ -n "@v" ] && echo "@f=@v"
  done
  devname=@(basename "@d")
  hostnum=@{devname%%:*}
  for ext_path in /sys/kernel/debug/mmc@hostnum/@devname/ext_csd /d/mmc@hostnum/@devname/ext_csd /sys/kernel/debug/mmc*/*/ext_csd /d/mmc*/*/ext_csd; do
    if [ -f "@ext_path" ]; then
      ext_hex=@(od -An -tx1 -v "@ext_path" 2>/dev/null | tr -d ' \n' || xxd -p "@ext_path" 2>/dev/null | tr -d ' \n')
      if [ -n "@ext_hex" ]; then
        echo "ext_csd_hex=@ext_hex"
        break
      fi
      ext_txt=@(cat "@ext_path" 2>/dev/null)
      if [ -n "@ext_txt" ]; then
        echo "ext_csd_txt=@ext_txt"
        break
      fi
    fi
  done
done

# 2. Probe SCSI / UFS devices (Internal Flash)
for d in /sys/block/sda/device/ /sys/class/scsi_device/*/device/ /sys/devices/platform/*ufs*/ /sys/devices/platform/soc/*ufs*/ /sys/devices/platform/soc/*.ufshc/; do
  [ -d "@d" ] || continue
  echo "==UFS @d"
  for f in vendor model rev type scsi_level queue_depth state wwid inquiry life_time_estimation_a life_time_estimation_b eol_info dump_health_desc health_descriptor/life_time_estimation_a health_descriptor/life_time_estimation_b health_descriptor/eol_info health_descriptor/bDeviceLifeTimeEstA health_descriptor/bDeviceLifeTimeEstB health_descriptor/bPreEOLInfo; do
    v=@(cat "@d@f" 2>/dev/null)
    [ -n "@v" ] && echo "@f=@v"
  done
done

for ufs_dbg in /sys/kernel/debug/*ufshc*/dump_health_desc /d/*ufshc*/dump_health_desc /sys/kernel/debug/ufshcd*/dump_health_desc /d/ufshcd*/dump_health_desc; do
  if [ -f "@ufs_dbg" ]; then
    echo "==UFS_DEBUG @ufs_dbg"
    cat "@ufs_dbg" 2>/dev/null
    break
  fi
done

# 3. Block device sizes & properties
for b in /sys/block/sd* /sys/block/mmcblk* /sys/block/nvme*; do
  [ -d "@b" ] || continue
  echo "==BLK @b"
  echo "size_sectors=@(cat "@b/size" 2>/dev/null)"
  echo "removable=@(cat "@b/removable" 2>/dev/null)"
  echo "logical_block=@(cat "@b/queue/logical_block_size" 2>/dev/null)"
  echo "physical_block=@(cat "@b/queue/physical_block_size" 2>/dev/null)"
  [ -f "@b/device/type" ] && echo "dev_type=@(cat "@b/device/type" 2>/dev/null)"
  [ -f "@b/device/name" ] && echo "dev_name=@(cat "@b/device/name" 2>/dev/null)"
  [ -f "@b/device/life_time" ] && echo "dev_life_time=@(cat "@b/device/life_time" 2>/dev/null)"
  [ -f "@b/device/pre_eol_info" ] && echo "dev_pre_eol_info=@(cat "@b/device/pre_eol_info" 2>/dev/null)"
done

# 4. Storaged framework health
echo "==STORAGED"
dumpsys storaged 2>/dev/null | grep -iE 'health|eol|lifetime' | head -n 10

# 5. Boot / Hardware properties
echo "==PROP"
echo "bootdevice=@(getprop ro.boot.bootdevice)"
echo "hardware=@(getprop ro.hardware)"
echo "storage_type=@(getprop ro.boot.storage_type)"
"""

    private val STORAGE_SCRIPT = STORAGE_SCRIPT_RAW.replace('@', '$')

    private data class StorageResult(val sections: List<Section>, val health: EmmcHealth?)

    internal data class ExtCsdInfo(
        val rev: Int?,
        val versionName: String?,
        val preEol: Int?,
        val lifeA: Int?,
        val lifeB: Int?,
        val secCount: Long?
    )

    internal data class StoragedHealth(val eol: Int?, val lifeA: Int?, val lifeB: Int?)

    internal data class UfsHealthDesc(val preEol: Int?, val lifeA: Int?, val lifeB: Int?)

    internal fun manufacturerName(manfidHex: String?, isSd: Boolean = false): String? {
        val mid = manfidHex?.trim()?.removePrefix("0x")?.removePrefix("0X")?.toIntOrNull(16) ?: return null
        return when (mid) {
            0x15 -> "Samsung"
            0x11 -> "Toshiba / Kioxia"
            0x13 -> "Micron"
            0x90 -> "SK Hynix"
            0x45 -> "SanDisk / Western Digital"
            0x70 -> "Kingston"
            0x27 -> "Phison"
            0xFE -> "Micron / SpecTek"
            0x02 -> if (isSd) "SanDisk" else "Kingston"
            0x1B -> "Samsung"
            0x03 -> "SanDisk"
            0x28 -> "Macronix"
            0x65 -> "Dynacard / Longsys / Foresee"
            0x88 -> "Foresee"
            0x51 -> "GigaDevice"
            0x38 -> "Nanya"
            else -> null
        }
    }

    internal fun emmcVersionName(revInt: Int?): String? = when (revInt) {
        8 -> "eMMC 5.1"
        7 -> "eMMC 5.0"
        6 -> "eMMC 4.5"
        5 -> "eMMC 4.41"
        3 -> "eMMC 4.3"
        2 -> "eMMC 4.2"
        1 -> "eMMC 4.1"
        0 -> "eMMC 4.0"
        else -> null
    }

    internal fun parseExtCsdHex(hex: String): ExtCsdInfo? {
        val clean = hex.replace(Regex("[^0-9a-fA-F]"), "")
        if (clean.length < 540) return null

        fun byteAt(index: Int): Int? {
            val start = index * 2
            return if (start + 2 <= clean.length) clean.substring(start, start + 2).toIntOrNull(16) else null
        }

        val rev = byteAt(192)
        val preEol = byteAt(267)
        val lifeA = byteAt(268)
        val lifeB = byteAt(269)

        val b212 = byteAt(212)?.toLong() ?: 0L
        val b213 = byteAt(213)?.toLong() ?: 0L
        val b214 = byteAt(214)?.toLong() ?: 0L
        val b215 = byteAt(215)?.toLong() ?: 0L
        val secCount = b212 or (b213 shl 8) or (b214 shl 16) or (b215 shl 24)

        return ExtCsdInfo(
            rev = rev,
            versionName = emmcVersionName(rev),
            preEol = preEol,
            lifeA = lifeA,
            lifeB = lifeB,
            secCount = if (secCount > 0) secCount else null
        )
    }

    internal fun parseExtCsdText(text: String): ExtCsdInfo? {
        if (text.isBlank()) return null
        val preEol = Regex("""(?:PRE_EOL_INFO|pre_eol_info|Pre EOL)[^0-9xX]*0?[xX]?([0-9a-fA-F]+)""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.toIntOrNull(16)
        val lifeA = Regex("""(?:DEVICE_LIFE_TIME_EST_TYP_A|life_time.*typ_a|Life Time Estimation A)[^0-9xX]*0?[xX]?([0-9a-fA-F]+)""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.toIntOrNull(16)
        val lifeB = Regex("""(?:DEVICE_LIFE_TIME_EST_TYP_B|life_time.*typ_b|Life Time Estimation B)[^0-9xX]*0?[xX]?([0-9a-fA-F]+)""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.toIntOrNull(16)
        val rev = Regex("""(?:EXT_CSD_REV|ext_csd_rev|CSD_REV)[^0-9xX]*0?[xX]?([0-9a-fA-F]+)""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.toIntOrNull(16)

        if (preEol == null && lifeA == null && lifeB == null && rev == null) return null
        return ExtCsdInfo(
            rev = rev,
            versionName = emmcVersionName(rev),
            preEol = preEol,
            lifeA = lifeA,
            lifeB = lifeB,
            secCount = null
        )
    }

    internal fun parseStoragedHealth(text: String): StoragedHealth? {
        if (text.isBlank()) return null
        val eol = Regex("""\beol\s*[=:]\s*(\d+)""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toIntOrNull()
        val lifeA = Regex("""\blifetime_a\s*[=:]\s*(\d+)""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toIntOrNull()
        val lifeB = Regex("""\blifetime_b\s*[=:]\s*(\d+)""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toIntOrNull()
        if (eol == null && lifeA == null && lifeB == null) return null
        return StoragedHealth(eol, lifeA, lifeB)
    }

    internal fun parseUfsHealthDesc(text: String): UfsHealthDesc? {
        if (text.isBlank()) return null
        val eol = Regex("""bPreEOLInfo\s*=\s*(0x[0-9a-fA-F]+|\d+)""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.let {
            if (it.startsWith("0x", ignoreCase = true)) it.substring(2).toIntOrNull(16) else it.toIntOrNull()
        }
        val lifeA = Regex("""bDeviceLifeTimeEstA\s*=\s*(0x[0-9a-fA-F]+|\d+)""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.let {
            if (it.startsWith("0x", ignoreCase = true)) it.substring(2).toIntOrNull(16) else it.toIntOrNull()
        }
        val lifeB = Regex("""bDeviceLifeTimeEstB\s*=\s*(0x[0-9a-fA-F]+|\d+)""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.let {
            if (it.startsWith("0x", ignoreCase = true)) it.substring(2).toIntOrNull(16) else it.toIntOrNull()
        }
        if (eol == null && lifeA == null && lifeB == null) return null
        return UfsHealthDesc(eol, lifeA, lifeB)
    }

    internal fun isMmcSdCard(
        path: String,
        attrs: Map<String, String>,
        blocks: Map<String, Map<String, String>>
    ): Boolean {
        val type = attrs["type"]?.trim()?.uppercase(Locale.ROOT)
        if (type == "SD") return true
        if (type == "MMC") return false

        // Only eMMC chips have life_time, pre_eol_info, or enhanced_size in JEDEC standard
        if (attrs.containsKey("life_time") || attrs.containsKey("pre_eol_info") || attrs.containsKey("enhanced_size") ||
            attrs.containsKey("dev_life_time") || attrs.containsKey("dev_pre_eol_info")
        ) {
            return false
        }

        // Check block device removable flag
        for ((bPath, bProps) in blocks) {
            val blkName = bPath.substringAfterLast('/')
            if (path.contains(blkName)) {
                if (bProps["removable"] == "1") return true
                if (bProps["removable"] == "0") return false
            }
        }

        if (path.contains("mmcblk1") || path.contains("sdcard") || path.contains("externdevice")) return true
        if (path.contains("mmcblk0")) return false

        return false
    }

    private fun collectNonRootStorage(): StorageResult {
        val sections = mutableListOf<Section>()
        val mmcName = readText("/sys/block/mmcblk0/device/name").ifBlank {
            readText("/sys/class/mmc_host/mmc0/mmc0:0001/name")
        }
        val mmcType = readText("/sys/block/mmcblk0/device/type").ifBlank { "MMC" }
        val mmcManf = readText("/sys/block/mmcblk0/device/manfid")
        val mfg = manufacturerName(mmcManf, isSd = false)

        val ufsModel = readText("/sys/block/sda/device/model").ifBlank {
            readText("/sys/class/block/sda/device/model")
        }
        val ufsVendor = readText("/sys/block/sda/device/vendor")

        var health: EmmcHealth? = null

        if (ufsModel.isNotBlank()) {
            val ufsRows = listOf(
                Row("Storage type", "UFS (Universal Flash Storage)"),
                Row("Vendor", ufsVendor.ifBlank { "Detected" }),
                Row("Product model", ufsModel),
                Row("Root access", "Required to read hardware wear registers")
            )
            sections.add(Section("Internal Storage (UFS)", ufsRows))
            health = EmmcHealth(
                productName = ufsModel,
                storageType = "UFS",
                manufacturer = ufsVendor.takeIf { it.isNotBlank() },
                isHealthReported = false,
                healthPercent = null,
                wearA = null,
                wearB = null,
                wearLabelA = "Root required",
                wearLabelB = "Root required",
                preEolStatus = "Root required",
                preEolCode = 0,
                grade = "UNEXPOSED",
                serial = null,
                cid = null,
                csdCapacity = null,
                diskCapacity = null
            )
        } else if (mmcName.isNotBlank()) {
            val mmcRows = buildList {
                add(Row("Storage type", "eMMC (Embedded MultiMediaCard)"))
                if (mfg != null) add(Row("Manufacturer", "$mfg ($mmcManf)"))
                add(Row("Product name", mmcName))
                add(Row("Type", mmcType))
                add(Row("Root access", "Required to read hardware wear registers"))
            }
            sections.add(Section("Internal Storage (eMMC)", mmcRows))
            health = EmmcHealth(
                productName = mmcName,
                storageType = "eMMC",
                manufacturer = mfg,
                isHealthReported = false,
                healthPercent = null,
                wearA = null,
                wearB = null,
                wearLabelA = "Root required",
                wearLabelB = "Root required",
                preEolStatus = "Root required",
                preEolCode = 0,
                grade = "UNEXPOSED",
                serial = null,
                cid = null,
                csdCapacity = null,
                diskCapacity = null
            )
        }

        return StorageResult(sections, health)
    }

    private suspend fun collectStorageHardware(): StorageResult {
        val dump = RootShell.sh(STORAGE_SCRIPT)
        if (dump.isBlank()) return StorageResult(emptyList(), null)

        val ufsDirs = LinkedHashMap<String, MutableMap<String, String>>()
        val mmcDirs = LinkedHashMap<String, MutableMap<String, String>>()
        val blocks = LinkedHashMap<String, MutableMap<String, String>>()
        val ufsDebugLines = StringBuilder()
        val storagedLines = StringBuilder()
        val props = mutableMapOf<String, String>()

        var currentMode = 0 // 1: UFS, 2: MMC, 3: BLK, 4: UFS_DEBUG, 5: STORAGED, 6: PROP
        var target: MutableMap<String, String>? = null

        dump.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("==UFS ") -> {
                    currentMode = 1
                    val path = line.removePrefix("==UFS ").trim().trimEnd('/')
                    ufsDirs.getOrPut(path) { mutableMapOf() }.let { target = it }
                }
                line.startsWith("==MMC ") -> {
                    currentMode = 2
                    val path = line.removePrefix("==MMC ").trim().trimEnd('/')
                    mmcDirs.getOrPut(path) { mutableMapOf() }.let { target = it }
                }
                line.startsWith("==BLK ") -> {
                    currentMode = 3
                    val path = line.removePrefix("==BLK ").trim()
                    blocks.getOrPut(path) { mutableMapOf() }.let { target = it }
                }
                line.startsWith("==UFS_DEBUG") -> {
                    currentMode = 4
                    target = null
                }
                line.startsWith("==STORAGED") -> {
                    currentMode = 5
                    target = null
                }
                line.startsWith("==PROP") -> {
                    currentMode = 6
                    target = null
                }
                currentMode == 4 -> {
                    if (line.isNotEmpty()) ufsDebugLines.appendLine(line)
                }
                currentMode == 5 -> {
                    if (line.isNotEmpty()) storagedLines.appendLine(line)
                }
                currentMode == 6 && '=' in line -> {
                    val (k, v) = line.split('=', limit = 2)
                    if (v.isNotBlank()) props[k.trim()] = v.trim()
                }
                '=' in line -> {
                    val (k, v) = line.split('=', limit = 2)
                    if (v.isNotBlank()) target?.put(k.trim(), v.trim())
                }
            }
        }

        val resultSections = mutableListOf<Section>()
        var primaryHealth: EmmcHealth? = null

        // Determine if primary storage is UFS or eMMC:
        val hasSdaBlock = blocks.entries.any { it.key.contains("/sda") && (it.value["size_sectors"]?.toLongOrNull() ?: 0L) > 0 }
        val bootdeviceIsUfs = props["bootdevice"]?.contains("ufs", ignoreCase = true) == true ||
            props["storage_type"]?.contains("ufs", ignoreCase = true) == true
        val ufsHasRealProduct = ufsDirs.values.any { it["model"]?.isNotBlank() == true || it["vendor"]?.isNotBlank() == true }
        val hasMmcblk0 = blocks.entries.any { it.key.contains("mmcblk0") && (it.value["size_sectors"]?.toLongOrNull() ?: 0L) > 0 }

        val hasUfs = (hasSdaBlock || bootdeviceIsUfs || (ufsHasRealProduct && !hasMmcblk0))

        // 1. Process UFS (Internal Flash Storage)
        if (hasUfs) {
            val ufsPrimary = ufsDirs.values.firstOrNull { it.containsKey("model") || it.containsKey("vendor") }
            val vendor = ufsPrimary?.get("vendor")?.trim().takeUnless { it.isNullOrBlank() }
            val model = ufsPrimary?.get("model")?.trim().takeUnless { it.isNullOrBlank() } ?: "Internal UFS Storage"
            val rev = ufsPrimary?.get("rev")?.trim() ?: "-"
            val scsiLevel = ufsPrimary?.get("scsi_level")?.trim() ?: "-"
            val queueDepth = ufsPrimary?.get("queue_depth")?.trim() ?: "-"
            val wwid = ufsPrimary?.get("wwid")?.trim() ?: "-"

            val totalBytes = blocks.entries
                .filter { it.key.contains("/sd") && !it.key.any { c -> c.isDigit() && it.key.substringAfter("/sd").length > 1 } }
                .mapNotNull { it.value["size_sectors"]?.toLongOrNull() }
                .maxOrNull()?.let { it * 512 } ?: 0L

            val capacityStr = if (totalBytes > 0) bytesHuman(totalBytes) else null

            val allUfsAttrs = mutableMapOf<String, String>().apply {
                ufsDirs.values.forEach { putAll(it) }
            }
            var ufsLifeA = allUfsAttrs["life_time_estimation_a"] ?: allUfsAttrs["bDeviceLifeTimeEstA"]
            var ufsLifeB = allUfsAttrs["life_time_estimation_b"] ?: allUfsAttrs["bDeviceLifeTimeEstB"]
            var ufsEol = allUfsAttrs["eol_info"] ?: allUfsAttrs["bPreEOLInfo"]

            // Check dump_health_desc
            val debugDesc = allUfsAttrs["dump_health_desc"] ?: ufsDebugLines.toString()
            if (ufsLifeA == null && debugDesc.isNotBlank()) {
                val parsedDesc = parseUfsHealthDesc(debugDesc)
                if (parsedDesc != null) {
                    if (parsedDesc.lifeA != null) ufsLifeA = "0x%02X".format(parsedDesc.lifeA)
                    if (parsedDesc.lifeB != null) ufsLifeB = "0x%02X".format(parsedDesc.lifeB)
                    if (parsedDesc.preEol != null) ufsEol = "0x%02X".format(parsedDesc.preEol)
                }
            }

            // Check storaged
            if (ufsLifeA == null && storagedLines.isNotEmpty()) {
                val st = parseStoragedHealth(storagedLines.toString())
                if (st != null) {
                    if (st.lifeA != null) ufsLifeA = "${st.lifeA}"
                    if (st.lifeB != null) ufsLifeB = "${st.lifeB}"
                    if (st.eol != null) ufsEol = "${st.eol}"
                }
            }

            val ufsRows = mutableListOf(
                Row("Storage type", "UFS (Universal Flash Storage)"),
                Row("Vendor", vendor ?: "OEM Standard"),
                Row("Product model", model),
                Row("Firmware revision", rev),
                Row("Capacity", capacityStr ?: "Not reported"),
                Row("Command queue depth", queueDepth),
                Row("SCSI standard", if (scsiLevel != "-") "SCSI Level $scsiLevel" else "-"),
                Row("WWID", wwid)
            )

            if (ufsLifeA != null || ufsLifeB != null || ufsEol != null) {
                val parsedLifeA = parseWearToken(ufsLifeA)
                val parsedLifeB = parseWearToken(ufsLifeB)
                val preEolLabel = parsePreEolToken(ufsEol)
                val eolCode = if (ufsEol?.startsWith("0x", ignoreCase = true) == true) {
                    ufsEol.substring(2).toIntOrNull(16) ?: 1
                } else {
                    ufsEol?.toIntOrNull() ?: 1
                }
                ufsRows.add(Row("Life time (SLC)", parsedLifeA.second))
                ufsRows.add(Row("Life time (TLC)", parsedLifeB.second))
                ufsRows.add(Row("Pre-EOL status", preEolLabel))

                primaryHealth = EmmcHealth(
                    productName = listOfNotNull(vendor, model).joinToString(" ").ifBlank { "UFS Flash" },
                    storageType = "UFS",
                    manufacturer = vendor,
                    isHealthReported = true,
                    healthPercent = calculateHealth(parsedLifeA.first, parsedLifeB.first, eolCode),
                    wearA = parsedLifeA.first,
                    wearB = parsedLifeB.first,
                    wearLabelA = parsedLifeA.second,
                    wearLabelB = parsedLifeB.second,
                    preEolStatus = preEolLabel,
                    preEolCode = eolCode,
                    grade = "EXCELLENT",
                    serial = wwid.takeIf { it != "-" },
                    cid = null,
                    csdCapacity = capacityStr,
                    diskCapacity = capacityStr
                )
            } else {
                ufsRows.add(Row("Health status", "Operational (Active • Normal)"))
                ufsRows.add(Row("Health descriptor", "Kernel omits debugfs health table"))

                primaryHealth = EmmcHealth(
                    productName = listOfNotNull(vendor, model).joinToString(" ").ifBlank { "UFS Flash" },
                    storageType = "UFS",
                    manufacturer = vendor,
                    isHealthReported = false,
                    healthPercent = null,
                    wearA = null,
                    wearB = null,
                    wearLabelA = "Not exposed by kernel",
                    wearLabelB = "Not exposed by kernel",
                    preEolStatus = "Not exposed by kernel",
                    preEolCode = 0,
                    grade = "UNEXPOSED",
                    serial = wwid.takeIf { it != "-" },
                    cid = null,
                    csdCapacity = capacityStr,
                    diskCapacity = capacityStr
                )
            }

            resultSections.add(Section("Internal Storage (UFS)", ufsRows))
        }

        // 2. Process MMC Devices (differentiating eMMC vs External MicroSD)
        val seenMmc = HashSet<String>()
        val mmcDevices = mmcDirs.filterValues { attrs ->
            val fp = attrs["serial"] ?: attrs["cid"] ?: attrs["name"] ?: attrs["manfid"]
            fp != null && seenMmc.add(fp)
        }

        for ((path, attrs) in mmcDevices) {
            val isSdCard = isMmcSdCard(path, attrs, blocks)
            val revInt = attrs["rev"]?.trim()?.removePrefix("0x")?.toIntOrNull(16) ?: attrs["rev"]?.trim()?.toIntOrNull()
            val specName = emmcVersionName(revInt) ?: "eMMC 5.1"
            val sectionTitle = if (isSdCard) "External MicroSD Card" else "Internal Storage ($specName)"

            val rows = ArrayList<Row>()
            rows.addAll(attributeRows(attrs, isSd = isSdCard))

            // Capacity from block or CSD
            val mmcCap = blocks.entries
                .filter { it.key.contains("mmcblk") && !it.key.any { c -> c.isDigit() && it.key.substringAfter("mmcblk").contains("p") } }
                .mapNotNull { it.value["size_sectors"]?.toLongOrNull()?.takeIf { s -> s > 0 } }
                .maxOrNull()?.let { bytesHuman(it * 512) }

            if (mmcCap != null && rows.none { it.key.contains("capacity", ignoreCase = true) }) {
                rows.add(Row("Capacity", mmcCap))
            }

            resultSections.add(Section(sectionTitle, rows))

            // If primaryHealth is not yet claimed (or eMMC is the real internal storage):
            if (!isSdCard && (primaryHealth == null || !hasUfs)) {
                primaryHealth = parseEmmcHealth(attrs, blocks, storagedLines.toString())
            }
        }

        // Fallback: if no MMC directory matched but mmcblk0 block device exists:
        if (primaryHealth == null && !hasUfs && blocks.containsKey("/sys/block/mmcblk0")) {
            val blk0 = blocks["/sys/block/mmcblk0"] ?: emptyMap()
            val synthAttrs = mutableMapOf<String, String>()
            blk0["dev_name"]?.let { synthAttrs["name"] = it }
            blk0["dev_type"]?.let { synthAttrs["type"] = it }
            blk0["dev_life_time"]?.let { synthAttrs["life_time"] = it }
            blk0["dev_pre_eol_info"]?.let { synthAttrs["pre_eol_info"] = it }
            primaryHealth = parseEmmcHealth(synthAttrs, blocks, storagedLines.toString())
            val rows = attributeRows(synthAttrs, isSd = false).toMutableList()
            blk0["size_sectors"]?.toLongOrNull()?.takeIf { it > 0 }?.let {
                rows.add(Row("Capacity", bytesHuman(it * 512)))
            }
            resultSections.add(Section("Internal Storage (${primaryHealth.storageType})", rows))
        }

        return StorageResult(resultSections, primaryHealth)
    }

    internal fun parseWearToken(token: String?): Pair<Int?, String> {
        if (token.isNullOrBlank()) return null to "Not reported"
        val trimmed = token.trim()
        val n = if (trimmed.startsWith("0x", ignoreCase = true)) {
            trimmed.substring(2).toIntOrNull(16)
        } else {
            trimmed.toIntOrNull() ?: trimmed.toIntOrNull(16)
        } ?: return null to trimmed

        return when {
            n == 0 -> null to "Not defined / Normal"
            n in 1..10 -> (n * 10) to "${(n - 1) * 10}-${n * 10}% consumed"
            n >= 11 -> 100 to "Exceeded maximum life time"
            else -> null to trimmed
        }
    }

    internal fun parsePreEolToken(raw: String?): String {
        if (raw.isNullOrBlank()) return "Normal"
        val trimmed = raw.trim()
        val n = if (trimmed.startsWith("0x", ignoreCase = true)) {
            trimmed.substring(2).toIntOrNull(16)
        } else {
            trimmed.toIntOrNull() ?: trimmed.toIntOrNull(16)
        } ?: return raw

        return when (n) {
            1 -> "Normal (<80% reserved blocks used)"
            2 -> "Warning (80% reserved blocks used)"
            3 -> "Urgent (90%+ reserved blocks used)"
            else -> if (n == 0) "Normal (Not defined)" else raw
        }
    }

    internal fun calculateHealth(wearA: Int?, wearB: Int?, preEolCode: Int): Int {
        val maxWear = maxOf(wearA ?: 0, wearB ?: 0)
        return when {
            preEolCode == 3 -> 10
            preEolCode == 2 && maxWear < 80 -> 20
            maxWear > 0 -> (100 - maxWear + 5).coerceIn(0, 100)
            else -> 98
        }
    }

    internal fun parseEmmcHealth(
        attrs: Map<String, String>,
        blocks: Map<String, Map<String, String>> = emptyMap(),
        storagedText: String = ""
    ): EmmcHealth {
        var rawLife = attrs["life_time"] ?: attrs["dev_life_time"] ?: ""
        var rawPreEol = attrs["pre_eol_info"] ?: attrs["dev_pre_eol_info"] ?: ""
        var versionName = attrs["rev"]?.let { r ->
            emmcVersionName(r.trim().removePrefix("0x").toIntOrNull(16) ?: r.trim().toIntOrNull())
        }

        // 1. Try EXT_CSD hex dump
        val extHex = attrs["ext_csd_hex"]
        if (extHex != null) {
            val extInfo = parseExtCsdHex(extHex)
            if (extInfo != null) {
                if (versionName == null) versionName = extInfo.versionName
                if (rawLife.isBlank() && (extInfo.lifeA != null || extInfo.lifeB != null)) {
                    rawLife = "0x%02X 0x%02X".format(extInfo.lifeA ?: 0, extInfo.lifeB ?: 0)
                }
                if (rawPreEol.isBlank() && extInfo.preEol != null) {
                    rawPreEol = "0x%02X".format(extInfo.preEol)
                }
            }
        }

        // 2. Try EXT_CSD text or mmc tool output
        val extTxt = attrs["ext_csd_txt"] ?: attrs["ext_csd"]
        if (extTxt != null && (rawLife.isBlank() || rawPreEol.isBlank())) {
            val extInfo = parseExtCsdText(extTxt)
            if (extInfo != null) {
                if (versionName == null) versionName = extInfo.versionName
                if (rawLife.isBlank() && (extInfo.lifeA != null || extInfo.lifeB != null)) {
                    rawLife = "0x%02X 0x%02X".format(extInfo.lifeA ?: 0, extInfo.lifeB ?: 0)
                }
                if (rawPreEol.isBlank() && extInfo.preEol != null) {
                    rawPreEol = "0x%02X".format(extInfo.preEol)
                }
            }
        }

        // 3. Try dumpsys storaged
        if (storagedText.isNotBlank() && (rawLife.isBlank() || rawPreEol.isBlank())) {
            val st = parseStoragedHealth(storagedText)
            if (st != null) {
                if (rawLife.isBlank() && (st.lifeA != null || st.lifeB != null)) {
                    rawLife = "${st.lifeA ?: 1} ${st.lifeB ?: 1}"
                }
                if (rawPreEol.isBlank() && st.eol != null) {
                    rawPreEol = "${st.eol}"
                }
            }
        }

        val name = attrs["name"] ?: attrs["dev_name"] ?: "eMMC/Flash"
        val manfid = attrs["manfid"]
        val mfg = manufacturerName(manfid, isSd = false)
        val serial = attrs["serial"]
        val cid = attrs["cid"]

        val lifeParts = rawLife.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val (wearA, labelA) = if (lifeParts.isNotEmpty()) parseWearToken(lifeParts[0]) else null to "Not reported"
        val (wearB, labelB) = if (lifeParts.size > 1) parseWearToken(lifeParts[1]) else wearA to labelA

        val preEolCode = if (rawPreEol.trim().startsWith("0x", ignoreCase = true)) {
            rawPreEol.trim().substring(2).toIntOrNull(16) ?: 0
        } else {
            rawPreEol.trim().toIntOrNull() ?: 0
        }
        val preEolStatus = parsePreEolToken(rawPreEol)
        val hasRealReport = wearA != null || wearB != null || preEolCode != 0
        val healthPercent = if (hasRealReport) calculateHealth(wearA, wearB, preEolCode) else null

        val grade = when {
            !hasRealReport -> "UNEXPOSED"
            preEolCode == 3 || (healthPercent ?: 100) <= 15 -> "CRITICAL"
            preEolCode == 2 || (healthPercent ?: 100) <= 40 -> "WARNING"
            (healthPercent ?: 100) <= 70 -> "MODERATE"
            (healthPercent ?: 100) >= 90 -> "EXCELLENT"
            else -> "GOOD"
        }

        val diskCap = blocks.entries
            .filter { it.key.contains("mmcblk") && !it.key.any { c -> c.isDigit() && it.key.substringAfter("mmcblk").contains("p") } }
            .mapNotNull { it.value["size_sectors"]?.toLongOrNull()?.takeIf { s -> s > 0 } }
            .maxOrNull()?.let { bytesHuman(it * 512) }
            ?: blocks.values.firstNotNullOfOrNull { it["size_sectors"]?.toLongOrNull()?.takeIf { s -> s > 0 } }
                ?.let { bytesHuman(it * 512) }

        val csdCap = attrs["csd"]?.let { csdRows(it).find { r -> r.key.contains("capacity", ignoreCase = true) }?.value }

        return EmmcHealth(
            productName = name,
            storageType = versionName ?: "eMMC 5.1",
            manufacturer = mfg,
            isHealthReported = hasRealReport,
            healthPercent = healthPercent,
            wearA = wearA,
            wearB = wearB,
            wearLabelA = labelA,
            wearLabelB = labelB,
            preEolStatus = preEolStatus,
            preEolCode = preEolCode,
            grade = grade,
            serial = serial,
            cid = cid,
            csdCapacity = csdCap,
            diskCapacity = diskCap
        )
    }

    private fun attributeRows(attrs: Map<String, String>, isSd: Boolean = false): List<Row> = buildList {
        attrs["name"]?.let { add(Row("Product name", it)) }
        val manfid = attrs["manfid"]
        val mfg = manufacturerName(manfid, isSd = isSd)
        if (mfg != null) {
            add(Row("Manufacturer", "$mfg ($manfid)"))
        } else if (manfid != null) {
            add(Row("Manufacturer ID", manfid))
        }
        attrs["oemid"]?.let { add(Row("OEM ID", it)) }
        attrs["serial"]?.let { add(Row("Serial", it)) }
        attrs["type"]?.let { add(Row("Type", it)) }
        attrs["rev"]?.let { r ->
            emmcVersionName(r.trim().removePrefix("0x").toIntOrNull(16) ?: r.trim().toIntOrNull())?.let {
                add(Row("Specification version", it))
            } ?: add(Row("Revision", r))
        }
        attrs["hwrev"]?.let { add(Row("HW revision", it)) }
        attrs["fwrev"]?.let { add(Row("FW revision", it)) }
        attrs["date"]?.let { add(Row("Date code", it)) }
        attrs["life_time"]?.let { addAll(lifeTimeRows(it)) }
        attrs["pre_eol_info"]?.let { add(Row("Pre-EOL status", parsePreEolToken(it))) }
        attrs["enhanced_size"]?.let { add(Row("Enhanced user area size", it)) }
        attrs["preferred_erase_size"]?.let { add(Row("Preferred erase size", it)) }
        attrs["erase_size"]?.let { add(Row("Erase size", it)) }
        attrs["cid"]?.let { addAll(cidRows(it, isSd = isSd)) }
        attrs["csd"]?.let { addAll(csdRows(it)) }
    }

    private fun lifeTimeRows(raw: String): List<Row> {
        val parts = raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return when (parts.size) {
            0 -> emptyList()
            1 -> listOf(Row("Life time", parseWearToken(parts[0]).second))
            else -> listOf(
                Row("Life time (Type A / SLC)", parseWearToken(parts[0]).second),
                Row("Life time (Type B / MLC-TLC)", parseWearToken(parts[1]).second)
            )
        }
    }

    internal fun cidRows(hex: String, isSd: Boolean = false): List<Row> {
        val b = hexBytes(hex) ?: return listOf(Row("CID", hex))
        val mid = b[0].toInt() and 0xFF
        val oid = ((b[1].toInt() and 0xFF) shl 8) or (b[2].toInt() and 0xFF)

        val pnm: String
        val prv: String
        val psn: Long
        val mfg: String

        if (isSd) {
            // SD Physical Layer Specification:
            // PNM: 5 ASCII characters (bytes 3..7)
            pnm = (3..7).joinToString("") { (b[it].toInt() and 0xFF).toChar().toString() }
                .filter { it in ' '..'~' }
            // PRV: byte 8 (major.minor)
            val prvByte = b[8].toInt() and 0xFF
            prv = "${(prvByte shr 4) and 0xF}.${prvByte and 0xF}"
            // PSN: bytes 9..12 (32-bit big-endian serial)
            psn = (9..12).fold(0L) { acc, i -> (acc shl 8) or (b[i].toLong() and 0xFF) }
            // MDT: bytes 13..14 (12-bit year from 2000 + 4-bit month)
            val yearOffset = ((b[13].toInt() and 0x0F) shl 4) or ((b[14].toInt() shr 4) and 0x0F)
            val year = 2000 + yearOffset
            val month = b[14].toInt() and 0x0F
            mfg = if (month in 1..12 && year in 2000..2099) String.format(Locale.ROOT, "%02d/%04d", month, year) else "-"
        } else {
            // eMMC JEDEC JESD84-B51 Specification:
            // PNM: 6 ASCII characters (bytes 3..8)
            pnm = (3..8).joinToString("") { (b[it].toInt() and 0xFF).toChar().toString() }
                .filter { it in ' '..'~' }
            // PRV: byte 9 (major.minor)
            val prvByte = b[9].toInt() and 0xFF
            prv = "${(prvByte shr 4) and 0xF}.${prvByte and 0xF}"
            // PSN: bytes 10..13 (32-bit big-endian serial)
            psn = (10..13).fold(0L) { acc, i -> (acc shl 8) or (b[i].toLong() and 0xFF) }
            // MDT: byte 14 (month nibble + year nibble)
            val hi = (b[14].toInt() shr 4) and 0x0F
            val lo = b[14].toInt() and 0x0F
            val month = if (hi in 1..12) hi else lo
            val year = if (hi in 1..12) 2000 + lo else 2000 + hi
            mfg = if (month in 1..12 && year in 2000..2099) String.format(Locale.ROOT, "%02d/%04d", month, year) else "-"
        }

        val mfgName = manufacturerName(String.format(Locale.ROOT, "0x%02X", mid), isSd = isSd)

        return buildList {
            add(Row("CID", hex.uppercase(Locale.ROOT)))
            add(Row("  MID", String.format(Locale.ROOT, "0x%02X", mid)))
            if (mfgName != null) {
                add(Row("  Manufacturer", mfgName))
            }
            add(Row("  OID", String.format(Locale.ROOT, "0x%04X", oid)))
            add(Row("  Product name", pnm.ifBlank { "-" }))
            add(Row("  PRV", prv))
            add(Row("  PSN", String.format(Locale.ROOT, "0x%08X", psn)))
            add(Row("  Manufacturing date", mfg))
        }
    }

    internal fun csdRows(hex: String): List<Row> {
        val b = hexBytes(hex) ?: return listOf(Row("CSD", hex))
        val structure = (b[0].toInt() and 0xC0) shr 6
        val bytes = if (structure == 1) {
            val cSize = ((b[7].toInt() and 0x3F) shl 16) or
                ((b[8].toInt() and 0xFF) shl 8) or
                (b[9].toInt() and 0xFF)
            (cSize.toLong() + 1) * 512L * 1024L
        } else {
            val cSize = ((b[6].toInt() and 0x03) shl 10) or
                ((b[7].toInt() and 0xFF) shl 2) or
                ((b[8].toInt() and 0x03))
            val cSizeMult = ((b[9].toInt() and 0x03) shl 1) or ((b[10].toInt() and 0x80) shr 7)
            val readBlLen = b[5].toInt() and 0x0F
            (cSize.toLong() + 1) * (1L shl (cSizeMult + 2)) * (1L shl readBlLen)
        }
        val sane = bytes in (1L shl 20)..(16L shl 40)
        return buildList {
            add(Row("CSD", hex.uppercase(Locale.ROOT)))
            add(Row("  Structure", "v${if (structure == 1) "2.0" else "1.x"}"))
            if (sane) add(Row("  CSD capacity", bytesHuman(bytes)))
        }
    }

    private fun hexBytes(raw: String): ByteArray? {
        val clean = raw.replace(Regex("[^0-9a-fA-F]"), "")
        if (clean.length < 32) return null
        return ByteArray(16) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    // ------------------------------------------------------------- storage partitions

    private fun storagePartitionSection(): Section? {
        val rows = buildList {
            statFs("/data")?.let {
                add(Row("User data total", it.first))
                add(Row("User data free", it.second))
            }
            statFs("/system")?.let { add(Row("System partition", it.first)) }
            File("/proc/partitions").takeIf { it.canRead() }?.readLines()
                ?.drop(1)
                ?.mapNotNull { line ->
                    val p = line.trim().split(Regex("\\s+"))
                    if (p.size >= 4) {
                        val blocks = p[2].toLongOrNull() ?: 0L
                        val name = p[3]
                        if (blocks > 0 && !name.startsWith("loop") && !name.startsWith("ram")) {
                            Row("  Partition $name", bytesHuman(blocks * 1024))
                        } else null
                    } else null
                }
                ?.let { addAll(it.take(12)) }
        }
        return rows.takeIf { it.isNotEmpty() }?.let { Section("Storage Partitions", it) }
    }

    private fun statFs(path: String): Pair<String, String>? = try {
        val s = StatFs(path)
        val total = s.blockCountLong * s.blockSizeLong
        val free = s.availableBlocksLong * s.blockSizeLong
        bytesHuman(total) to bytesHuman(free)
    } catch (_: Exception) {
        null
    }

    // ----------------------------------------------------------- processor

    private fun processorSection(): Section? {
        val cpuinfo = parseColonFile("/proc/cpuinfo")
        val cores = Runtime.getRuntime().availableProcessors()
        val rows = buildList {
            add(Row("Cores", cores.toString()))
            cpuinfo["Hardware"]?.let { add(Row("Hardware", it)) }
            cpuinfo["Processor"]?.let { add(Row("Processor", it)) }
            cpuinfo["model name"]?.let { add(Row("Model", it)) }
            cpuinfo["Features"]?.let { add(Row("Features", it)) }
            readText("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
                .toLongOrNull()?.let { add(Row("CPU0 Current freq", "${it / 1000} MHz")) }
            readText("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
                .toLongOrNull()?.let { add(Row("CPU0 Max freq", "${it / 1000} MHz")) }
            add(Row("Supported ABIs", Build.SUPPORTED_ABIS.joinToString(", ")))
        }
        return rows.takeIf { it.isNotEmpty() }?.let { Section("Processor", it) }
    }

    // -------------------------------------------------------------- memory

    private fun memorySection(): Section? {
        val mem = parseColonFile("/proc/meminfo")
        fun kb(key: String): Long? = mem[key]?.takeWhile { it.isDigit() }?.toLongOrNull()
        val rt = Runtime.getRuntime()
        val rows = buildList {
            kb("MemTotal")?.let { add(Row("Total RAM", bytesHuman(it * 1024))) }
            kb("MemAvailable")?.let { add(Row("Available RAM", bytesHuman(it * 1024))) }
            kb("MemFree")?.let { add(Row("Free RAM", bytesHuman(it * 1024))) }
            kb("Cached")?.let { add(Row("Cached", bytesHuman(it * 1024))) }
            kb("SwapTotal")?.let { add(Row("Swap total", bytesHuman(it * 1024))) }
            kb("SwapFree")?.let { add(Row("Swap free", bytesHuman(it * 1024))) }
            add(Row("VM heap max", bytesHuman(rt.maxMemory())))
            add(Row("VM heap allocated", bytesHuman(rt.totalMemory())))
        }
        return rows.takeIf { it.isNotEmpty() }?.let { Section("Memory", it) }
    }

    // ------------------------------------------------------------- display

    private fun displaySection(context: Context): Section? {
        val dm = DisplayMetrics()
        val refresh = resolveDisplay(context, dm)
        val density = context.resources.displayMetrics.density
        val rows = listOf(
            Row("Resolution", "${dm.widthPixels} × ${dm.heightPixels} px"),
            Row("Size", "${(dm.widthPixels / density).toInt()} × ${(dm.heightPixels / density).toInt()} dp"),
            Row("Density", "${dm.densityDpi} dpi (${"%.2f".format(Locale.ROOT, density)}×)"),
            Row("Refresh rate", if (refresh > 0f) "${"%.0f".format(Locale.ROOT, refresh)} Hz" else "-")
        )
        return Section("Display", rows)
    }

    @Suppress("DEPRECATION")
    private fun resolveDisplay(context: Context, dm: DisplayMetrics): Float {
        if (Build.VERSION.SDK_INT >= 30) {
            val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            dm.widthPixels = bounds.width()
            dm.heightPixels = bounds.height()
            dm.densityDpi = context.resources.displayMetrics.densityDpi
            return context.display?.refreshRate ?: 0f
        }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.defaultDisplay.getRealMetrics(dm)
        return wm.defaultDisplay.refreshRate
    }

    // ------------------------------------------------------------- battery

    @Suppress("DEPRECATION")
    private fun batterySection(context: Context): Section? {
        val intent = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) {
            null
        } ?: return null

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val rows = buildList {
            if (level >= 0 && scale > 0) add(Row("Level", "${level * 100 / scale}%"))
            add(Row("Status", batteryStatus(intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1))))
            add(Row("Health", batteryHealth(intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1))))
            add(Row("Plugged", plugged(intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0))))
            intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                .takeIf { it != Int.MIN_VALUE }
                ?.let { add(Row("Temperature", "%.1f °C".format(Locale.ROOT, it / 10f))) }
            intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
                .takeIf { it > 0 }
                ?.let { add(Row("Voltage", "$it mV")) }
            intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
                ?.takeIf { it.isNotBlank() }
                ?.let { add(Row("Technology", it)) }
        }
        return rows.takeIf { it.isNotEmpty() }?.let { Section("Battery Snapshot", it) }
    }

    private fun batteryStatus(v: Int) = when (v) {
        2 -> "Charging"
        3 -> "Discharging"
        4 -> "Not charging"
        5 -> "Full"
        else -> "Unknown"
    }

    private fun batteryHealth(v: Int) = when (v) {
        2 -> "Good"
        3 -> "Overheat"
        4 -> "Dead"
        5 -> "Over voltage"
        6 -> "Unspecified failure"
        7 -> "Cold"
        else -> "Unknown"
    }

    private fun plugged(v: Int) = when (v) {
        1 -> "AC Charger"
        2 -> "USB Port"
        4 -> "Wireless"
        else -> "Unplugged"
    }

    // -------------------------------------------------------------- system

    private fun systemSection(): Section? {
        val rows = listOf(
            Row("Brand / Manufacturer", "${Build.BRAND} / ${Build.MANUFACTURER}"),
            Row("Model", Build.MODEL),
            Row("Device", Build.DEVICE),
            Row("Product", Build.PRODUCT),
            Row("Hardware", Build.HARDWARE),
            Row("Bootloader", Build.BOOTLOADER.ifBlank { "-" }),
            Row("Android version", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
            Row("Security patch", Build.VERSION.SECURITY_PATCH.ifBlank { "-" }),
            Row("Build ID", Build.DISPLAY),
            Row("Build type", "${Build.TYPE} / ${Build.TAGS}"),
            Row("Fingerprint", Build.FINGERPRINT)
        )
        return Section("System", rows)
    }

    private fun kernelSection(): Section? {
        val rows = buildList {
            readText("/proc/version").takeIf { it.isNotBlank() }?.let {
                add(Row("Version", it))
            }
            readText("/proc/uptime").trim().split(Regex("\\s+")).firstOrNull()
                ?.toDoubleOrNull()
                ?.let {
                    val s = it.toLong()
                    add(Row("Uptime", "${s / 86400}d ${(s % 86400) / 3600}h ${(s % 3600) / 60}m"))
                }
            add(Row("VM", System.getProperty("java.vm.version") ?: "-"))
            val selinux = readText("/sys/fs/selinux/enforce").let {
                when (it) {
                    "1" -> "Enforcing"
                    "0" -> "Permissive"
                    else -> it.ifBlank { "Enforcing" }
                }
            }
            add(Row("SELinux", selinux))
        }
        return rows.takeIf { it.isNotEmpty() }?.let { Section("Kernel & Security", it) }
    }

    // ------------------------------------------------------------- quick stats

    private fun computeQuickStats(context: Context): QuickStats {
        val cpuinfo = parseColonFile("/proc/cpuinfo")
        val cores = Runtime.getRuntime().availableProcessors()
        val cpuSummary = cpuinfo["Hardware"] ?: cpuinfo["model name"] ?: "$cores Cores"

        val mem = parseColonFile("/proc/meminfo")
        fun kb(key: String): Long = mem[key]?.takeWhile { it.isDigit() }?.toLongOrNull() ?: 0L
        val totalMemKb = kb("MemTotal")
        val availMemKb = kb("MemAvailable")
        val usedMemKb = (totalMemKb - availMemKb).coerceAtLeast(0)
        val ramPercent = if (totalMemKb > 0) ((usedMemKb * 100) / totalMemKb).toInt() else 0

        var storageTotalStr = "-"
        var storageUsedStr = "-"
        var storagePercent = 0
        try {
            val s = StatFs("/data")
            val total = s.blockCountLong * s.blockSizeLong
            val free = s.availableBlocksLong * s.blockSizeLong
            val used = total - free
            if (total > 0) {
                storagePercent = ((used * 100) / total).toInt()
                storageTotalStr = bytesHuman(total)
                storageUsedStr = bytesHuman(used)
            }
        } catch (_: Exception) {}

        val dm = DisplayMetrics()
        val refresh = resolveDisplay(context, dm)
        val displaySummary = "${dm.widthPixels}×${dm.heightPixels}" + (if (refresh > 0f) " • ${"%.0f".format(Locale.ROOT, refresh)}Hz" else "")

        return QuickStats(
            cpuSummary = cpuSummary,
            ramTotal = bytesHuman(totalMemKb * 1024),
            ramUsed = bytesHuman(usedMemKb * 1024),
            ramPercent = ramPercent,
            storageTotal = storageTotalStr,
            storageUsed = storageUsedStr,
            storagePercent = storagePercent,
            displaySummary = displaySummary,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        )
    }

    // -------------------------------------------------------------- helpers

    private fun readText(path: String): String = try {
        val f = File(path)
        if (f.canRead()) f.readText().trim() else ""
    } catch (_: Exception) {
        ""
    }

    private fun parseColonFile(path: String): Map<String, String> =
        readText(path).lineSequence().mapNotNull { line ->
            val i = line.indexOf(':')
            if (i > 0) line.substring(0, i).trim() to line.substring(i + 1).trim() else null
        }.toMap()

    internal fun bytesHuman(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var v = bytes.toDouble()
        var i = 0
        while (v >= 1024.0 && i < units.lastIndex) {
            v /= 1024.0
            i++
        }
        return if (i == 0) String.format(Locale.ROOT, "%.0f %s", v, units[i])
        else String.format(Locale.ROOT, "%.1f %s", v, units[i])
    }
}
