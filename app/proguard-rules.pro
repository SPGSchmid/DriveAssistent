# ── Android for Cars App Library ──────────────────────────────────────────────
-keep class androidx.car.app.** { *; }

# ── Retrofit / OkHttp / Gson ──────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ── MSAL ──────────────────────────────────────────────────────────────────────
-keep class com.microsoft.identity.** { *; }
-keep class com.microsoft.aad.** { *; }

# ── Room ──────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# ── Data-Klassen (JSON-Mapping) ───────────────────────────────────────────────
-keep class com.driveassistant.data.model.** { *; }
-keep class com.driveassistant.data.remote.dto.** { *; }

# ── WorkManager ───────────────────────────────────────────────────────────────
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Kotlin Coroutines ─────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ── Security (EncryptedSharedPreferences) ─────────────────────────────────────
-keep class androidx.security.crypto.** { *; }

# ── Spotify Intent (keine SDK-Abhängigkeit, aber Paketname darf nicht entfernt werden) ──
-keep class com.spotify.** { *; }
