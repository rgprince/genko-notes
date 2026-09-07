# MMKV — Research Notes for a Future Nimbus Build

> Status: **NOT integrated.** This file is research only, written 2026-09-05 from
> the official MMKV wiki / GitHub / Maven Central. Do NOT add the dependency
> until the coordinates below are approved and verified against
> `Android/libs.versions.toml.txt` (bootstrap re-read rule).

## 1. What MMKV is

Tencent's mmap + protobuf key-value store (powers WeChat). Drop-in
SharedPreferences replacement: synchronous reads with no performance loss,
writes persist immediately (no `apply()`/`sync()` needed), ~50 KB per ABI,
multi-process safe, optional encryption.

## 2. Latest version (researched 2026-09-05)

| Source | Version seen |
|---|---|
| Official wiki (`android_setup`) | `2.4.1` (`implementation 'com.tencent:mmkv:2.4.1'`) |
| GitHub README badge / Maven Central page | `2.4.0` |
| mvnrepository (Dec 2025 entry) | `2.3.0` → newer `2.4.0` available |

**Candidate coordinates for `gradle/libs.versions.toml`:**

```toml
[versions]
mmkv = "2.4.0"   # verify 2.4.1 exists on Maven Central before using it

[libraries]
tencent-mmkv = { group = "com.tencent", name = "mmkv", version.ref = "mmkv" }
```

```kotlin
// app/build.gradle.kts
implementation(libs.tencent.mmkv)
```

## 3. Baseline compatibility (Nimbus: AGP 9.3.2, compileSdk 37, minSdk 26, Kotlin 2.3.0)

- MMKV v2.x requires **minSdk 23+** → Nimbus (26) is fine.
- v2.x is **64-bit only** (no 32-bit `armeabi-v7a` .so). Nimbus ships all ABIs
  today; after adding MMKV, 32-bit devices would crash on load → either accept
  64-bit-only (`ndk { abiFilters += listOf("arm64-v8a", "x86_64") }`) or use the
  `1.3.x` LTS series for 32-bit support.
- Only transitive dep is `androidx.annotation:annotation` — no AGP/Kotlin conflict.
- MMKV ships native `.so` per ABI (~50 KB each, less zipped) — stays under the
  15 MB target, but re-measure the APK after adding.

## 4. Initialization (Application class — Nimbus has none yet)

```kotlin
package com.nimbus.launcher

import android.app.Application
import com.tencent.mmkv.MMKV

class NimbusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(this)   // default dir: files/mmkv
    }
}
```

```xml
<!-- AndroidManifest.xml -->
<application android:name=".NimbusApp" ...>
```

## 5. Reactive Compose settings manager (sketch — mirrors NimbusSettings API)

Keep the **same public API** (`StateFlow`s + setters) so Home/Settings screens
don't change; only the backing store swaps from SharedPreferences to MMKV:

```kotlin
class NimbusSettings(appContext: Context) {
    private val kv = MMKV.mmkvWithID("nimbus")

    private val _layoutStyle = MutableStateFlow(kv.getString("layout_style", "rings") ?: "rings")
    val layoutStyle: StateFlow<String> = _layoutStyle.asStateFlow()

    fun setLayoutStyle(v: String) {
        kv.putString("layout_style", v)   // synchronous, immediate
        _layoutStyle.value = v            // triggers recomposition
    }
    // ... repeat per key: clock_position, clock_theme, icon_scale,
    // icon_shape, theme_mode, dynamic, haptics, pack_seed, wallpaper_uri,
    // usage_prompt_seen
}
```

Alternative: `MMKV-KTX` (`com.github.DylanCaiCoding:MMKV-KTX`, JitPack) gives
`by mmkvString()` delegates + `.asStateFlow()` out of the box — but it adds a
second (JitPack) dependency to verify.

## 6. Migration plan (when approved)

1. One-time import: on first launch after upgrade, copy every key from the
   `nimbus` SharedPreferences file into MMKV, then set `mmkv_imported=true`.
2. Pins (max 5): keep Room as source of truth (ordering + timestamps), or move
   the package-name set into MMKV `putStringSet("pins", ...)` — Room is
   recommended to keep (already tested).
3. Widget IDs: keep Room `widgets` table (positions + spans live there, not just
   IDs) — MMKV can't replace the relational data. Store only a backup copy if
   desired.
4. `allowBackup`: MMKV files under `files/mmkv` are backed up by default; add
   backup rules if widgets/IDs must not roam between devices.

## 7. Risks / open questions

- Native crash surface (mmap corruption) — MMKV has CRC auto-recovery
  (`OnErrorRecover`), acceptable for prefs.
- Encrypted MMKV (`mmkvWithID(..., cryptKey)`) exists if wallpaper URIs / usage
  data ever need it — not needed today.
- Play policy: no special declaration (unlike QUERY_ALL_PACKAGES).
- 32-bit decision (see §3) must be made before merging.

## 8. Approval checklist (before any build-file edit)

- [ ] Re-read `Android/libs.versions.toml.txt` + `compatibility-matrix.txt`
- [ ] Confirm exact version on Maven Central (2.4.0 vs 2.4.1)
- [ ] Decide 64-bit-only vs 1.3.x LTS
- [ ] Add `mmkv` version + `tencent-mmkv` library to the catalog (group/name syntax)
- [ ] `implementation(libs.tencent.mmkv)` in `app/build.gradle.kts`
- [ ] Create `NimbusApp`, register in manifest, migrate keys, push, verify CI
