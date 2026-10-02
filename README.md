# Ciger ⚡

**Low-Level Device & Silicon Flash Storage Telemetry for Android**

[![Platform](https://img.shields.io/badge/Platform-Android_8.0+_(API_26+)-3DDC84?logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![CI](https://github.com/josskixg/ciger/actions/workflows/ci.yml/badge.svg)](https://github.com/josskixg/ciger/actions/workflows/ci.yml)
[![JEDEC](https://img.shields.io/badge/Standard-JEDEC_JESD84--B51_/_JESD220-black)](https://www.jedec.org)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

**Ciger** is a modern, privacy-focused Android hardware diagnostic tool engineered to decode low-level silicon flash storage registers, real-time battery telemetry, and deep system architecture specs. It directly decodes raw JEDEC eMMC EXT_CSD and UFS health descriptors to give users an accurate assessment of their flash storage wear and device longevity.

---

## ✨ Features

### 💾 1. Silicon Flash Storage Telemetry
- **JEDEC eMMC 5.1 Decoding**:
  - Full decoding of the 512-byte `EXT_CSD` register structure.
  - **Pre-EOL Status (Byte 267)**: Identifies reserved block consumption (`Normal <80%`, `Warning 80-90%`, `Urgent >90%`).
  - **Dual-Tier Wear Leveling (Bytes 268 & 269)**:
    - **Type A**: SLC Endurance / Turbo-Write Cache lifecycle.
    - **Type B**: Main Storage (MLC / TLC / QLC) wear level.
  - **CID Register Breakdown**: Manufacturer ID (MID), OEM ID (OID), Product Name (PNM), Revision (PRV), Serial Number (PSN), and Manufacturing Date (MDT).
- **UFS 2.1 & 3.x Support**:
  - Probes SCSI descriptors, `dump_health_desc`, and system storage daemons (`dumpsys storaged`).
  - Distinguishes internal silicon storage (`/dev/block/sda`, `mmcblk0`) from removable MicroSD cards (`mmcblk1`).
- **Manufacturer Lookup Table**: Resolves JEDEC MIDs to recognized semiconductor fabricators (Samsung, SK Hynix, Micron, Kingston, SanDisk / Western Digital, Kioxia / Toshiba).

### 🔋 2. Live Dynamic Battery Telemetry
- Real-time polling loop tracking battery percentage, charging state, voltage in millivolts (`mV`), and temperature in degrees Celsius (`°C`).
- Hardware battery chemistry readout (`Li-ion`, `Li-poly`) and health status.

### 📱 3. Symmetrical Hardware Overview & Deep Specs
- **Quick Specs Grid**: Equal-sized, responsive hardware cards for Processor, Memory (RAM), Internal Flash, and Display Panel.
- **Categorized Hardware Navigation**: Quick filter tabs for `All`, `Storage`, `eMMC`, `UFS`, `SD Card`, `Processor`, `Memory`, `Display`, `Battery`, `System`, and `Kernel`.
- **System Architecture**: CPU core clustering, frequency governors, instruction sets (ARM64-v8a, NEON, AES, SHA2), Linux kernel version, SELinux enforcement, and bootloader status.

### 📋 4. Globally Aligned Diagnostic Export
- Built-in **"Copy Colon-Aligned Diagnostic Report"** engine.
- Formats every key-value pair across all hardware sections to a single, globally aligned colon column with automated multiline indentation. Perfect for pasting directly into GitHub issues, Telegram messages, or technical bug reports.

<details>
<summary><b>🔍 Sample Exported Diagnostic Report</b> (Click to expand)</summary>

```text
-----------------------------------------------------
 CIGER // LOW-LEVEL DEVICE & SILICON REPORT
 Generated: 2026-10-02 19:30:54
-----------------------------------------------------

Device               : Xiaomi Redmi Note 8 Pro (begonia)
Android Version      : 15 (API 35)
Root Access          : Active (uid 0)
Battery Telemetry    : 42% (Charging, USB Cable, 32.3°C, 3987 mV, Good (Healthy))

[INTERNAL STORAGE (UFS)]
Storage type         : UFS (Universal Flash Storage)
Vendor               : SAMSUNG
Product model        : KM2V8001CM-B707
Firmware revision    : 0600
Capacity             : 119.2 GB
Command queue depth  : 32
SCSI standard        : SCSI Level 7
WWID                 : eui.53414d53554e4700
Health status        : Operational (Active • Normal)
Health descriptor    : Kernel omits debugfs health table

[EXTERNAL MICROSD CARD]
Product name         : SDU1
Manufacturer         : Samsung (0x00001b)
Capacity             : 29.5 GB (CSD v2.0)
Manufacturing date   : 01/2020

[PROCESSOR]
Cores                : 8
Hardware             : MT6785V/CC
CPU0 Max freq        : 2000 MHz
Supported ABIs       : arm64-v8a, armeabi-v7a, armeabi

[MEMORY]
Total RAM            : 5.5 GB
Available RAM        : 1.7 GB (1.2 GB cached)
Swap total           : 3.0 GB (2.2 GB free)

[DISPLAY]
Resolution           : 1080 × 2340 px (440 dpi @ 60 Hz)

[SYSTEM]
Brand / Manufacturer : Redmi / Xiaomi
Model                : Redmi Note 8 Pro (begonia)
Android version      : 15 (API 35)
Security patch       : 2025-04-05
-----------------------------------------------------
```
</details>

### 🛡️ 5. Dual-Mode Operation (Root & Non-Root)
- **Non-Root**: Safe, SELinux-compliant telemetry querying public Android APIs and accessible sysfs files.
- **Root (Magisk / KernelSU / APatch)**: Unlocks privileged direct access to raw flash block devices, EXT_CSD sysfs nodes, and storage debug interfaces.

### 🔒 6. 100% Offline & Private
- Zero internet permissions (`android.permission.INTERNET` is not included in the manifest).
- No third-party analytics, no tracking, and no telemetry data sent outside your device.

---

## 🛠️ Architecture & Tech Stack

```
ciger/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/com/ciger/
│   │   │   │   ├── MainActivity.kt        # Jetpack Compose UI, responsive layout & live polling
│   │   │   │   ├── info/
│   │   │   │   │   ├── Info.kt            # Hardware engine, JEDEC register parser & sysfs probing
│   │   │   │   │   ├── ReportFormat.kt    # Global colon alignment and multiline formatting engine
│   │   │   │   │   └── RootShell.kt       # Privilege execution handler (su process pool)
│   │   │   │   └── ui/
│   │   │   │       ├── Theme.kt           # Material 3 light/dark design system
│   │   │   │       └── Type.kt            # Typography configuration
│   │   │   └── res/                       # Vector assets, launcher icons, colors
│   │   └── test/
│   │       └── kotlin/com/ciger/
│   │           └── EmmcDecodeTest.kt      # Unit tests for CID, CSD, EXT_CSD & report alignment
│   └── build.gradle.kts
├── settings.gradle.kts
└── build.gradle.kts
```

- **Language**: Kotlin 2.0.21
- **UI Framework**: Jetpack Compose with Material Design 3
- **Android Target**: SDK 35 (Android 15)
- **Minimum Android**: SDK 26 (Android 8.0 Oreo)
- **Build System**: Gradle 8.11.1 with Kotlin DSL

---

## 🚀 Building from Source

### Prerequisites
1. **JDK 17** or newer installed and configured in your environment (`JAVA_HOME`).
2. **Android SDK** with Platform 35 and Build-Tools 35.0.0 installed.

### Build Commands

Clone the repository and compile using the Gradle wrapper:

```bash
# Debug build (generates unsigned/debug-signed APK)
./gradlew assembleDebug

# Release build (generates optimized release APK)
./gradlew assembleRelease

# Run unit test suite
./gradlew test
```

The compiled APK will be generated at:
```
app/build/outputs/apk/release/app-release.apk
```

### Installing via ADB

Connect your Android device with USB Debugging enabled:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## 📖 JEDEC Specification Reference

- **JEDEC Standard JESD84-B51**: *Embedded MultiMediaCard (eMMC) Electrical Standard, Standard Capacity 5.1*
  - `EXT_CSD[192]` : CSD Structure & Extended CSD Revision
  - `EXT_CSD[212..215]` : Sector Count (Calculates real raw capacity)
  - `EXT_CSD[267]` : `PRE_EOL_INFO` (Flash memory reserve block state)
  - `EXT_CSD[268]` : `DEVICE_LIFE_TIME_EST_TYP_A` (SLC Endurance block wear in 10% steps)
  - `EXT_CSD[269]` : `DEVICE_LIFE_TIME_EST_TYP_B` (MLC/TLC User data block wear in 10% steps)
- **JEDEC Standard JESD220**: *Universal Flash Storage (UFS) Specification 2.1 / 3.1*
  - Device Descriptor `0x09` (Health Descriptor: `bPreEOLInfo`, `bDeviceLifeTimeEstA`, `bDeviceLifeTimeEstB`)

---

## 📄 License

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at:

```
http://www.apache.org/licenses/LICENSE-2.0
```

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
