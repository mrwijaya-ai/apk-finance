# Panduan Aktivasi Firebase Lanjutan & Build APK untuk Dibagikan — CoupleCash

## 1. Status Konfigurasi Saat Ini
- **Firebase Project ID**: `gen-lang-client-0915667121`
- **Cloud Firestore Database ID**: `ai-studio-android-coupleca-ec268288-eb12-4847-9cf9-9bc41d2f9903`
- **Aktif Otomatis**: Firebase Authentication (Google Sign-In), Cloud Firestore Real-Time, dan `firestore.rules`.
- **Siap di Kode Client**:
  - `CoupleCashRepository.uploadReceiptPhoto` (upload foto struk dengan metadata `image/*`).
  - `CoupleCashRepository.syncFcmToken()` + `CoupleCashMessagingService` (sinkronisasi token FCM otomatis setelah login & channel notifikasi `couplecash_alerts`).
  - `storage.rules`, `firebase.json`, dan `functions/index.js` (siap deploy).

> **Catatan Lingkungan AI Studio**: Akun CLI Firebase di server build ini tidak memiliki sesi login pribadi Anda sehingga aktivasi **Firebase Storage**, **Firebase Cloud Messaging**, dan deploy **Cloud Functions** perlu dijalankan sekali dari Firebase Console / terminal lokal Anda (dengan akun `mrwijaya42@gmail.com`).

---

## 2. Langkah Aktivasi Firebase Storage, FCM, dan Cloud Functions

### A. Aktifkan Firebase Storage (Foto Struk)
1. Buka [Firebase Console](https://console.firebase.google.com/project/gen-lang-client-0915667121/storage).
2. Klik **Get started** untuk membuat bucket default (`gen-lang-client-0915667121.firebasestorage.app`, sudah cocok dengan `app/google-services.json`).

### B. Pastikan Firebase Cloud Messaging (FCM) Aktif
1. Buka [Firebase Console → Messaging](https://console.firebase.google.com/project/gen-lang-client-0915667121/notification) / Google Cloud Console API library.
2. Pastikan **Firebase Cloud Messaging API (V1)** berstatus **Enabled**.

### C. Deploy Cloud Functions & Storage Rules dari Komputer Anda
Di terminal folder root proyek ini:
```bash
firebase login
firebase use gen-lang-client-0915667121
cd functions && npm install && cd ..
firebase deploy --only functions,storage
```
*(Catatan: Cloud Functions v2 memerlukan project berada di paket **Blaze** di Firebase Console).*

---

## 3. Build APK di Android Studio & Bagikan ke HP Istri

### A. Daftarkan SHA-1 Android Studio Anda (Penting agar Google Sign-In berhasil!)
Jika Anda mengunduh ZIP / clone proyek ke komputer Anda sendiri, Android Studio akan menandatangani Debug APK dengan keystore lokal komputer Anda:
1. Di Android Studio, jalankan tab **Gradle → Tasks → android → signingReport** (atau jalankan `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`).
2. Salin nilai **SHA-1** dan **SHA-256**.
3. Buka [Firebase Console → Project Settings](https://console.firebase.google.com/project/gen-lang-client-0915667121/settings/general), pilih aplikasi Android `com.aistudio.couplecash.xkqmpw`, lalu klik **Add fingerprint**.
4. Unduh ulang `google-services.json` terbaru (atau tetap pakai jika fingerprint sudah ditambahkan — cukup sinkronkan di console) ke folder `app/`.
   *(Jika membangun Signed Release APK/AAB, daftarkan juga SHA-1 keystore release Anda di Firebase Console).*

### B. Build APK di Android Studio
1. Buka folder proyek di **Android Studio**.
2. Tunggu **Gradle Sync** selesai.
3. Pilih menu **Build → Build Bundle(s) / APK(s) → Build APK(s)** (atau jalankan `gradle :app:assembleDebug` di terminal).
4. File APK berada di:
   `app/build/outputs/apk/debug/app-debug.apk`
   (atau gunakan APK yang sudah di-generate via menu **Settings → Download APK** di AI Studio).

### C. Cara Instal & Pairing di HP Suami dan HP Istri
1. Kirim file `app-debug.apk` (atau Release APK) ke HP istri melalui WhatsApp / Google Drive / kabel USB.
2. Instal APK di kedua HP (izinkan *Install from unknown sources* jika diminta).
3. **Di HP Pertama (mis. Suami)**: Masuk dengan akun Google → pilih peran **Suami** → **Buat Buku Kas Bersama Baru** → buka tab **Profil → Bagikan Kode Undangan**.
4. **Di HP Kedua (mis. Istri)**: Masuk dengan akun Google istri (atau akun Google yang sama) → pilih peran **Istri** → **Gabung Kode Pasangan** → masukkan kode → verifikasi nama pemilik → **Konfirmasi & Gabung Sekarang**.
5. Kedua HP kini tersinkron real-time!
