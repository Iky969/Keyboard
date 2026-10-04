# ⚡ Terminal Keyboard for Android

[![Build & Release APK](https://github.com/Iky969/Keyboard/actions/workflows/apk.yml/badge.svg)](https://github.com/Iky969/Keyboard/actions/workflows/apk.yml)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Terminal Keyboard** adalah keyboard sistem (IME) Android native berkinerja tinggi yang dirancang khusus untuk para pengembang, sysadmin, dan pengguna **Termux**, **JuiceSSH**, **Termius**, **ConnectBot**, **Neovim/Vim**, **Nano**, serta emulator terminal Linux.

Keyboard ini mengirimkan **Hardware KeyEvent** otentik dari sistem Android dengan modifier lengkap (`Ctrl`, `Alt`, `Shift`), tombol panah DPAD arah, `Esc`, `Tab`, tombol fungsi `F1–F12`, fitur **Gelembung Melayang (Floating Bubble)**, serta tombol **Clipboard Paste** langsung.

---

## 📸 Preview Tata Letak & Antarmuka

### 1. Baris Atas Terminal (Top Terminal Bar)
```
[ESC] [TAB] [CTRL] [ALT] [📋] [💡] [▲] [▼] [◀] [▶] [Fn] [>_]
```
- **ESC** : Keluar dari mode edit (Vim/Nano), membatalkan prompt input.
- **TAB** : Autocomplete otomatis nama file, folder, atau perintah bash/zsh.
- **CTRL** : Modifier kontrol Linux (Tekan 1x untuk sticky •, tekan 2x cepat untuk lock 🔒).
- **ALT** : Modifier Meta/Alt (Tekan 1x untuk sticky •, tekan 2x cepat untuk lock 🔒).
- **📋 (Paste)** : Tempelkan teks langsung dari clipboard sistem ke terminal dengan satu ketukan.
- **💡 (Guide)** : Buka panel pop-up panduan pintasan terminal dan tombol aksi cepat (`Ctrl+C`, `Ctrl+V`, `Ctrl+Z`, `Ctrl+D`, `Ctrl+L`).
- **▲ ▼ ◀ ▶** : Navigasi riwayat perintah sebelumnya dan penggeseran kursor per karakter.
- **Fn** : Beralih ke layer tombol fungsi (`F1–F12`), `Home`, `End`, `PgUp`, `PgDn`, `Ins`, `Del`, `|`, `~`.
- **>_** : Buka bilah pintasan perintah cepat (`clear`, `ls -la`, `git status`, `sudo`, `nano`, dll).

### 2. Layar Utama QWERTY (Lengkap dengan Tanda Baca)
```
[q] [w] [e] [r] [t] [y] [u] [i] [o] [p]
[a] [s] [d] [f] [g] [h] [j] [k] [l] [;]
[⇧] [z] [x] [c] [v] [b] [n] [m] [,] [.] [⌫]
[?123] [:] [/] [     SPACE     ] [-] ['] [↵ ENTER] [🌐]
```
- **Presisi Huruf Kecil & Huruf Besar** : Teks pada tombol secara akurat menampilkan huruf kecil saat Shift tidak aktif, dan otomatis berubah ke huruf besar saat Shift aktif (`⇧`) atau Caps Lock (`⇪`).
- **Titik Koma (`;`)** : Berada langsung di ujung baris kedua di sebelah tombol `l` (sama seperti keyboard PC standar).
- **Koma (`,`) & Titik (`.`)** : Berada langsung di baris ketiga di sebelah kanan tombol `m`.
- **Titik Dua (`:`), Slash (`/`), Minus (`-`), Petik (`'`)** : Berada langsung di baris bawah di sekitar spasi tanpa perlu berpindah ke layer simbol!

### 3. Layer Simbol Coding & Pemrograman (`?123`)
```
[1] [2] [3] [4] [5] [6] [7] [8] [9] [0]
[`] [!] [@] [#] [$] [%] [^] [&] [*] [_]
[+] [=] [\] [{] [}] [[] []] [<] [>] [~]
[ABC] [;] [|] ["] ['] [  SPACE  ] [⌫] [↵]
```

### 4. Layer Tombol Fungsi & Navigasi (`Fn`)
```
[F1]  [F2]  [F3]  [F4]  [F5]  [F6]
[F7]  [F8]  [F9]  [F10] [F11] [F12]
[HOME] [END] [PGUP] [PGDN] [INS] [DEL]
[ABC] [|] [~] [ESC] [TAB] [SPACE] [↵ ENTER] [● SEL]
```

---

## 🌟 Fitur Unggulan

### 🫧 1. Gelembung Melayang (Floating Bubble Overlay)
Seringkali saat menjalankan aplikasi terminal, emulator, atau game, layar tidak menyediakan kolom teks yang dapat difokuskan sehingga keyboard bawaan Android tidak mau muncul.
- **Solusi**: Fitur **Gelembung Melayang** menampilkan tombol overlay kecil `>_` yang melayang di atas semua aplikasi.
- **Bisa Digeser**: Gelembung dapat diseret bebas ke sudut layar mana saja.
- **Keluarkan Kapan Saja**: Cukup sentuh gelembung untuk memunculkan keyboard terminal secara instan di mana pun, dan sentuh lagi untuk menutupnya.
- **Aman & Terkontrol**: Fitur ini **tidak langsung aktif** secara default, melainkan harus diaktifkan secara manual melalui sakelar di aplikasi utama dengan izin *Display over other apps*.

### 📋 2. Tombol Paste Clipboard Sekali Sentuh
- Tidak perlu repot menahan layar untuk mencari menu "Paste".
- Cukup tekan tombol **`📋`** di baris teratas keyboard, teks dari clipboard HP Anda akan langsung tertulis ke terminal.

### 💡 3. Panel Panduan & Tombol Aksi Cepat (Quick Guide)
- Tekan tombol **`💡`** di bar atas untuk memunculkan panduan interaktif lengkap dengan cheatsheet shortcut terminal.
- Tersedia tombol sekali tekan langsung: `[Ctrl+C]`, `[📋 Paste]`, `[Ctrl+Z]`, `[Ctrl+D]`, dan `[Ctrl+L]`.

### 🔒 4. Sticky & Locked Modifiers
- **Ketuk 1x**: Modifier aktif untuk 1 tombol berikutnya (ditandai dengan titik `CTRL •`).
- **Ketuk 2x cepat**: Modifier terkunci (ditandai dengan ikon gembok `CTRL 🔒`, `ALT 🔒`, `⇪ CAPS`).
- **Ketuk 3x**: Melepas kuncian.

---

## ⌨️ Panduan Pintasan Terminal (Terminal Cheatsheet)

| Pintasan | Fungsi dalam Shell / Linux Terminal |
| :--- | :--- |
| **`Ctrl + C`** | **SIGINT**: Membatalkan / menghentikan proses atau script yang sedang berjalan |
| **`Ctrl + V`** | **Paste**: Menempelkan teks dari clipboard (atau klik tombol **📋**) |
| **`Ctrl + Z`** | **SIGTSTP**: Menjeda proses ke background (ketik `fg` di shell untuk melanjutkan) |
| **`Ctrl + D`** | **EOF / Logout**: Menutup sesi terminal, exit dari SSH, atau keluar dari shell |
| **`Ctrl + L`** | **Clear**: Membersihkan tampilan layar terminal (sama dengan command `clear`) |
| **`Ctrl + A`** | Pindah kursor langsung ke awal baris (*Home*) |
| **`Ctrl + E`** | Pindah kursor langsung ke ujung baris (*End*) |
| **`Ctrl + U`** | Menghapus seluruh baris teks sebelum posisi kursor |
| **`Ctrl + K`** | Menghapus seluruh teks setelah posisi kursor hingga ujung baris |
| **`Ctrl + W`** | Menghapus satu kata sebelum kursor |
| **`Ctrl + R`** | Mencari riwayat perintah sebelumnya (*reverse-i-search*) |
| **`Tab`** | Autocomplete otomatis nama file, direktori, dan perintah |
| **`Esc`** | Keluar dari mode Insert di Vim/Neovim atau membatalkan dialog |
| **`▲` / `▼`** | Menjelajahi riwayat perintah shell sebelumnya / berikutnya |

---

## 🚀 Cara Memasang & Mengaktifkan di Android

1. **Instal APK**:
   Unduh file `app-debug.apk` terbaru dari tab [Releases](../../releases) repositori ini dan pasang di HP Anda.
2. **Buka Aplikasi Terminal Keyboard**:
   - Tekan tombol **`1. Aktifkan di Pengaturan Sistem`** -> Hidupkan sakelar pada **Terminal Keypad IME**.
   - Tekan tombol **`2. Pilih Terminal Keypad IME`** -> Pilih **Terminal Keypad IME** sebagai keyboard aktif Anda.
3. **Uji Coba di Playground**:
   Ketik dan coba tombol `Esc`, `Tab`, `Ctrl+C`, tombol panah, serta tanda baca di area playground interaktif yang telah disediakan.
4. **Aktifkan Gelembung Melayang (Opsional)**:
   Nyalakan sakelar **Gelembung Melayang** pada kartu di dalam aplikasi, izinkan opsi *Tampilkan di atas aplikasi lain*. Sekarang Anda bisa memanggil keyboard kapan saja di atas Termux atau game!

---

## 🛠️ Kompilasi & Build APK Sendiri

### Melalui GitHub Actions (Otomatis)
Setiap kali ada pembaruan kode yang di-push ke branch `main` atau `master`, GitHub Actions akan otomatis mengompilasi kode dan mengunggah APK siap pakai ke bagian **Releases**.
- Workflow: `.github/workflows/apk.yml`
- Menggunakan JDK 21 dan Android SDK 36.

### Melalui Komputer Lokal (Gradle)
Pastikan Anda memiliki JDK 17 atau 21 terpasang:
```bash
# Clone repositori
git clone https://github.com/Iky969/Keyboard.git
cd Keyboard

# Berikan izin eksekusi untuk gradlew
chmod +x gradlew

# Kompilasi Debug APK
./gradlew assembleDebug

# Lokasi file APK yang dihasilkan:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 Lisensi
Didistribusikan di bawah lisensi MIT. Silakan gunakan, modifikasi, dan bagikan secara bebas.
