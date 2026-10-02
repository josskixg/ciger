package com.ciger

import com.ciger.info.Info
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmmcDecodeTest {

    private fun rowsOf(hex: String, isSd: Boolean = false) =
        Info.cidRows(hex, isSd).associate { it.key to it.value }

    @Test
    fun `cid decodes mid oid name rev serial and date for emmc`() {
        // MID 0x11, OID 0x0027, PNM "MT16G ", PRV 0x10, PSN 0x12345678, MDT 0x4F
        val cid = "110027" + "4D5431364720" + "10" + "12345678" + "4FAB"
        val rows = rowsOf(cid, isSd = false)

        assertEquals("0x11", rows["  MID"])
        assertEquals("0x0027", rows["  OID"])
        assertEquals("MT16G ", rows["  Product name"])
        assertEquals("1.0", rows["  PRV"])
        assertEquals("0x12345678", rows["  PSN"])
        assertEquals("04/2015", rows["  Manufacturing date"])
    }

    @Test
    fun `cid decodes real samsung sd card correctly without byte shift`() {
        // Real MicroSD from Redmi Note 8 Pro:
        // MID 0x1B, OID 0x534D ("SM"), PNM "SDU1 ", PRV 1.0, PSN 0x14A12005, MDT 01/2020
        val cid = "1b534d53445531201014a120050141c5"
        val rows = rowsOf(cid, isSd = true)

        assertEquals("0x1B", rows["  MID"])
        assertEquals("0x534D", rows["  OID"])
        assertEquals("SDU1 ", rows["  Product name"])
        assertEquals("1.0", rows["  PRV"])
        assertEquals("0x14A12005", rows["  PSN"])
        assertEquals("01/2020", rows["  Manufacturing date"])
    }

    @Test
    fun `csd v2 decodes 64GB capacity`() {
        // CSD_STRUCTURE=01b, C_SIZE=0x1FFFF -> (0x1FFFF+1) * 512KiB = 64 GiB
        val csd = "40" + "000000000000" + "01FFFFFF" + "0000000000"
        val rows = Info.csdRows(csd).associate { it.key to it.value }

        assertEquals("v2.0", rows["  Structure"])
        assertEquals("64.0 GB", rows["  CSD capacity"])
    }

    @Test
    fun `short or garbage input falls back to raw value instead of throwing`() {
        assertEquals("zz", rowsOf("zz")["CID"])
        assertEquals("nope", Info.csdRows("nope").single().value)
        assertTrue(Info.csdRows("").isNotEmpty())
    }

    @Test
    fun `report alignment puts every colon in identical column including indented sub-rows`() {
        val rows = listOf(
            "CID" to "1B53...",
            "  MID" to "0x1B",
            "  OID" to "0x534D",
            "  Product name" to "SDU1",
            "Capacity" to "29.5 GB"
        )
        val out = com.ciger.info.ReportFormat.aligned(rows)
        val colonIndices = out.map { it.indexOf(':') }.toSet()
        assertEquals(1, colonIndices.size) // every line has ':' at exactly the same index
    }

    @Test
    fun `formatReport aligns colons across multiple sections globally`() {
        val sections = listOf(
            null to listOf(
                "Device" to "Redmi Note 8 Pro",
                "Android Version" to "15"
            ),
            "[STORAGE]" to listOf(
                "Product" to "KM2V8001CM-B707",
                "Capacity" to "128 GB"
            ),
            "[PROCESSOR]" to listOf(
                "Instruction Set Extensions" to "fp asimd aes pmull sha1 sha2 crc32"
            )
        )
        val out = com.ciger.info.ReportFormat.formatReport(sections)
        val linesWithColon = out.lines().filter { it.contains(" : ") }
        val colonIndices = linesWithColon.map { it.indexOf(" : ") }.toSet()
        assertEquals(1, colonIndices.size)
    }

    @Test
    fun `manufacturerName correctly maps known MIDs`() {
        assertEquals("Samsung", Info.manufacturerName("0x15"))
        assertEquals("SK Hynix", Info.manufacturerName("0x90"))
        assertEquals("Micron", Info.manufacturerName("0x13"))
        assertEquals("Kingston", Info.manufacturerName("0x70"))
        assertEquals("SanDisk / Western Digital", Info.manufacturerName("0x45"))
        assertEquals("Toshiba / Kioxia", Info.manufacturerName("0x11"))
    }

    @Test
    fun `isMmcSdCard accurately distinguishes eMMC from SD card`() {
        val blocks = mapOf(
            "/sys/block/mmcblk0" to mapOf("removable" to "0"),
            "/sys/block/mmcblk1" to mapOf("removable" to "1")
        )

        // Internal eMMC on mmc0 with type MMC
        val isSd1 = Info.isMmcSdCard(
            path = "/sys/class/mmc_host/mmc0/mmc0:0001",
            attrs = mapOf("type" to "MMC", "name" to "DG4064"),
            blocks = blocks
        )
        assertEquals(false, isSd1)

        // Device with life_time is definitely internal eMMC
        val isSd2 = Info.isMmcSdCard(
            path = "/sys/block/mmcblk0/device",
            attrs = mapOf("life_time" to "0x01 0x01", "pre_eol_info" to "0x01"),
            blocks = blocks
        )
        assertEquals(false, isSd2)

        // External MicroSD
        val isSd3 = Info.isMmcSdCard(
            path = "/sys/class/mmc_host/mmc1/mmc1:0001",
            attrs = mapOf("type" to "SD", "name" to "SD32G"),
            blocks = blocks
        )
        assertEquals(true, isSd3)
    }

    @Test
    fun `parseExtCsdHex decodes revision pre-eol and lifetime bytes`() {
        // Construct a 540-char (270 bytes) hex string with:
        // Byte 192 (offset 384..385): 0x08 (eMMC 5.1)
        // Byte 267 (offset 534..535): 0x01 (Pre-EOL Normal)
        // Byte 268 (offset 536..537): 0x01 (SLC 0-10% used)
        // Byte 269 (offset 538..539): 0x02 (MLC 10-20% used)
        val sb = StringBuilder("00".repeat(270))
        sb.replace(192 * 2, 192 * 2 + 2, "08")
        sb.replace(267 * 2, 267 * 2 + 2, "01")
        sb.replace(268 * 2, 268 * 2 + 2, "01")
        sb.replace(269 * 2, 269 * 2 + 2, "02")

        val ext = Info.parseExtCsdHex(sb.toString())
        assertTrue(ext != null)
        assertEquals(8, ext?.rev)
        assertEquals("eMMC 5.1", ext?.versionName)
        assertEquals(1, ext?.preEol)
        assertEquals(1, ext?.lifeA)
        assertEquals(2, ext?.lifeB)
    }

    @Test
    fun `parseEmmcHealth calculates correct health percentage and grade`() {
        val attrs = mapOf(
            "name" to "KLMCG4JETD",
            "manfid" to "0x15",
            "life_time" to "0x01 0x01",
            "pre_eol_info" to "0x01",
            "rev" to "8"
        )
        val health = Info.parseEmmcHealth(attrs)
        assertEquals("KLMCG4JETD", health.productName)
        assertEquals("Samsung", health.manufacturer)
        assertEquals("eMMC 5.1", health.storageType)
        assertTrue(health.isHealthReported)
        assertEquals(95, health.healthPercent) // 100 - 10 + 5
        assertEquals("EXCELLENT", health.grade)
        assertEquals("Normal (<80% reserved blocks used)", health.preEolStatus)
    }
}
