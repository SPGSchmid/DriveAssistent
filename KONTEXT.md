# DriveAssistant – Projektzusammenfassung für KI-Kontext

> Diese Datei fasst alle Entscheidungen, die Architektur und den aktuellen Implementierungsstand
> des DriveAssistant-Projekts zusammen. Sie dient als Kontext-Dokument für zukünftige
> KI-Gespräche zur Weiterentwicklung der App.

---

## Projektziel

Native Android-App (Kotlin) mit **Android Auto**-Unterstützung für einen Außendienstmitarbeiter.
Der Assistent ermöglicht es, während der Fahrt per **Sprache** auf alle relevanten Dienste
zuzugreifen – vollständig **on-device**, kein eigenes Cloud-Backend.

---

## Getroffene Architekturentscheidungen

| Entscheidung | Wert |
|---|---|
| **KI-Modell** | Groq API – `llama-3.1-70b-versatile` (OpenAI-kompatibel) |
| **KI-Endpunkt** | `https://api.groq.com/openai/` |
| **Android Auto** | Android for Cars App Library (`androidx.car.app:app:1.4+`) |
| **Authentifizierung M365** | MSAL for Android (OAuth 2.0, Single Tenant) |
| **Teams-Anrufe** | Deep Link `msteams://call?users=EMAIL` (keine eigene Calling-API) |
| **PLAUD-Export** | Automatischer Datei-Export → FileObserver + WorkManager (on-device) |
| **Distribution** | APK Sideload (kein Play Store Review nötig) |
| **Backend** | Vollständig on-device, kein Cloud-Server |
| **Offline-Cache** | Room Database (Mails, Termine, PLAUD-Import-Status) |

---

## Azure App-Registrierung (Microsoft 365)

| Wert | Inhalt |
|---|---|
| **App-Name** | DriveAssistent |
| **Client ID** | `bbf6be54-3738-4334-8c74-5c8acba0464d` |
| **Tenant ID** | `b3d86b33-c164-4287-b07a-be5763c7e0b7` |
| **Tenant-Typ** | Single Tenant (`AzureADMyOrg`) |
| **Redirect URI** | `msauth://com.driveassistant/2jmj7l5rSw0yVb/vlWAYkK/YBwk=` |
| **Signature Hash** | `2jmj7l5rSw0yVb/vlWAYkK/YBwk=` (Debug-Keystore) |
| **API Permissions** | `User.Read`, `Mail.ReadWrite`, `Calendars.ReadWrite`, `Chat.ReadWrite`, `ChannelMessage.ReadWrite`, `offline_access` – alle mit Admin Consent erteilt ✅ |
| **Konfigurationsdatei** | `app/src/main/res/raw/auth_config.json` |

---

## Credentials – Wo sie einzutragen sind

Alle Secrets werden **zur Laufzeit** in `EncryptedSharedPreferences` gespeichert.
Eingabe erfolgt in der `PhoneActivity` auf dem Smartphone (nicht im Auto-Display).

| Secret | Storage-Key | Eingabe in PhoneActivity |
|---|---|---|
| Groq API Key (`gsk_...`) | `openai_api_key` | Feld "Groq API Key" |
| HubSpot Private App Token (`pat-eu1-...`) | `hubspot_token` | Feld "HubSpot Token" |
| MSAL Access Token | `msal_access_token` | Automatisch nach Login |

**HubSpot Token erstellen:** app.hubspot.com → Einstellungen → Integrationen → Private Apps → App erstellen

---

## Technologie-Stack

| Komponente | Technologie | Version |
|---|---|---|
| Sprache | Kotlin | 1.9+ |
| Min SDK | Android API 23 | – |
| Target SDK | Android API 34 | – |
| Android Auto | `androidx.car.app:app` | 1.4.0 |
| KI | Groq API (OpenAI-kompatibel) | llama-3.1-70b-versatile |
| HTTP-Client | Retrofit 2 + OkHttp | 2.11 / 4.12 |
| Authentifizierung | MSAL for Android | 4.9+ |
| Graph API | Microsoft Graph v1.0 | – |
| CRM | HubSpot API v3 | – |
| Offline-Speicher | Room Database | 2.6.1 |
| Background-Jobs | WorkManager | 2.9.0 |
| Sichere Speicherung | EncryptedSharedPreferences | security-crypto |
| Spracheingabe | Android SpeechRecognizer | native |
| Sprachausgabe | Android TextToSpeech (Deutsch) | native |

---

## Vollständige Projektstruktur

```
app/src/main/kotlin/com/driveassistant/
│
├── DriveAssistantApp.kt              # Application-Klasse
│                                     # Initialisiert: DB, MSAL, WorkManager (PLAUD-Scan)
│
├── api/                              # Retrofit-Interfaces
│   ├── GraphMailApi.kt               # GET /me/messages, POST reply/sendMail
│   ├── GraphCalendarApi.kt           # GET /me/calendarView, POST /me/events
│   ├── GraphTeamsApi.kt              # GET /me/chats, POST /chats/{id}/messages
│   ├── HubSpotApi.kt                 # POST contacts/search, POST notes
│   └── OpenAiService.kt              # POST /v1/chat/completions (Groq-kompatibel)
│
├── data/
│   ├── model/
│   │   ├── AssistantIntent.kt        # GPT-4 Antwortmodell: intent, params, speech
│   │   ├── ChatRequest.kt            # OpenAI/Groq Request (messages, response_format)
│   │   ├── ChatResponse.kt           # OpenAI/Groq Response
│   │   ├── email/
│   │   │   ├── EmailMessage.kt       # Graph API Mail-Modell
│   │   │   ├── SendMailRequest.kt    # POST /me/sendMail Body
│   │   │   └── ReplyMailRequest.kt   # POST /me/messages/{id}/reply Body
│   │   ├── calendar/
│   │   │   ├── CalendarEvent.kt      # Graph API Kalender-Modell
│   │   │   └── CreateEventRequest.kt # POST /me/events Body
│   │   ├── teams/
│   │   │   ├── TeamsChat.kt          # Chat + Member-Modelle
│   │   │   ├── TeamsMessage.kt       # Message-Modelle
│   │   │   └── SendMessageRequest.kt # POST /chats/{id}/messages Body
│   │   └── hubspot/
│   │       ├── HubSpotContact.kt     # Kontakt-Suchergebnis
│   │       ├── HubSpotDeal.kt        # Deals
│   │       ├── HubSpotNote.kt        # Notiz + Assoziation
│   │       └── HubSpotSearchRequest.kt
│   └── local/                        # Room-Datenbank (Offline-Cache)
│       ├── AppDatabase.kt            # Room DB, Version 3, Singleton
│       ├── CachedEmail.kt            # Entity: gecachte Mails
│       ├── EmailDao.kt
│       ├── CachedCalendarEvent.kt    # Entity: gecachte Termine
│       ├── CalendarEventDao.kt
│       ├── PlaudImportRecord.kt      # Entity: Import-Status (Duplikat-Schutz)
│       └── PlaudImportDao.kt
│
├── service/                          # Business-Logik
│   ├── MainCarAppService.kt          # Android Auto CarAppService
│   ├── DriveAssistantSession.kt      # Android Auto Session
│   ├── AuthCallbacks.kt              # MSAL Callback-Interfaces
│   ├── MsalAuthService.kt            # OAuth 2.0 Login/Logout/TokenRefresh
│   ├── GraphApiClient.kt             # Retrofit-Client für Graph API (Bearer Token)
│   ├── GraphMailService.kt           # E-Mail: lesen, antworten, senden + Cache
│   ├── GraphCalendarService.kt       # Kalender: heute/morgen/nächster + erstellen
│   ├── GraphTeamsService.kt          # Teams: Chats, Nachrichten, Chat-Cache
│   ├── TeamsCallService.kt           # Teams-Anruf via Deep Link
│   ├── NavigationService.kt          # Google Maps Intent + geo:-Fallback
│   ├── HubSpotClient.kt              # Retrofit-Client für HubSpot (Bearer Token)
│   ├── HubSpotService.kt             # CRM: Kontakt suchen, Notiz erstellen
│   ├── PlaudAnalysisService.kt       # Groq analysiert Transkript → JSON
│   ├── PlaudImportService.kt         # Scan + Import-Logik, Duplikat-Schutz
│   ├── OpenAiManager.kt              # Groq API Client + Retry-Logik (Exponential Backoff)
│   ├── SessionContext.kt             # Konversationsgedächtnis + System-Prompt (13 Intents)
│   ├── IntentRouter.kt               # GPT-4 Intent → Aktion-Router (alle Intents verdrahtet)
│   ├── VoiceRecognitionManager.kt    # SpeechRecognizer (de-DE, NoiseSuppressor)
│   └── TtsManager.kt                 # TextToSpeech (Deutsch, QUEUE_FLUSH)
│
├── ui/
│   ├── HomeScreen.kt                 # Android Auto Hauptscreen (Mikrofon-Button)
│   └── PhoneActivity.kt              # Smartphone: Login + API Key Eingabe
│
├── util/
│   └── SecureStorage.kt              # EncryptedSharedPreferences-Wrapper
│
└── worker/
    └── PlaudScanWorker.kt            # WorkManager: PLAUD-Scan alle 15 Min.
```

---

## Room-Datenbank

**Datei:** `AppDatabase.kt`, Version **3**

| Entity | Tabelle | Zweck |
|---|---|---|
| `CachedEmail` | `cached_emails` | Offline-Cache ungelesener Mails (24h TTL) |
| `CachedCalendarEvent` | `cached_calendar_events` | Offline-Cache Kalendertermine |
| `PlaudImportRecord` | `plaud_imports` | Import-Status (verhindert Doppel-Import) |

`fallbackToDestructiveMigration()` aktiv – bei Schema-Änderungen wird DB neu aufgebaut (ok, da nur Cache).

---

## GPT-4 / Groq Intent-System

**System-Prompt** in `SessionContext.kt` definiert 13 Intents:

| Intent | Aktion | Implementiert |
|---|---|---|
| `READ_EMAILS` | Ungelesene Mails vorlesen | ✅ Phase 1 |
| `REPLY_EMAIL` | Mail beantworten | ✅ Phase 1 |
| `COMPOSE_EMAIL` | Neue Mail senden | ✅ Phase 1 |
| `GET_CALENDAR` | Termine abfragen (heute/morgen/nächster) | ✅ Phase 2 |
| `CREATE_EVENT` | Termin erstellen | ✅ Phase 2 |
| `NAVIGATE` | Google Maps Navigation starten | ✅ Phase 2 |
| `TEAMS_MESSAGE` | Teams-Nachricht senden | ✅ Phase 2 |
| `TEAMS_CALL` | Teams-Anruf via Deep Link | ✅ Phase 2 |
| `HUBSPOT_LOOKUP` | CRM-Kontakt nachschlagen | ✅ Phase 3 |
| `CREATE_NOTE` | HubSpot-Notiz erstellen | ✅ Phase 3 |
| `IMPORT_PLAUD` | PLAUD-Transkript importieren | ✅ Phase 3 |
| `GENERAL_RESPONSE` | Allgemeine Antwort ohne Aktion | ✅ alle Phasen |

**Antwortformat:**
```json
{"intent": "READ_EMAILS", "params": {"key": "value"}, "speech": "Ich lese deine Mails vor."}
```

---

## PLAUD-Import-Flow

```
Neue Transkript-Datei in /Download/PLAUD/
    ↓  PlaudScanWorker (WorkManager alle 15 Min.)
PlaudImportService.importFile()
    ↓
PlaudAnalysisService (Groq llama-3.1-70b)
    → contactName, summary, actionItems, sentiment
    ↓
HubSpotService.searchContact(contactName)
    ↓
HubSpotService.createNote(contactId, formatierte Notiz)
    ↓
PlaudImportRecord in Room (Duplikat-Schutz via filePath PrimaryKey)
    ↓
Android Notification: "X neue Transkripte importiert"
```

**Unterstützte Verzeichnisse:** `/storage/emulated/0/Download/PLAUD`, `/storage/emulated/0/PLAUD`, `/storage/emulated/0/Download`
**Unterstützte Dateitypen:** `.txt`, `.md`, `.json`

---

## Ausstehende Phasen

### Phase 4 – Spotify & Google Maps

**Sub-Task 4.1: Spotify-Steuerung ✅ Implementiert**
- `SpotifyService.kt` implementiert (Intent-basiert, kein SDK erforderlich)
- `PLAY_MUSIC`-Intent im `IntentRouter.kt` vollständig verdrahtet
- Unterstützte Befehle: Suche nach Künstler/Playlist/Album, Pause, Weiter, Next, Previous
- Fallback: falls Spotify nicht installiert → Play Store wird geöffnet

**Sub-Task 4.2: Google Maps Navigation**
- `NavigationService.kt` ist bereits implementiert (Intent-basiert)
- Erweiterung: Adresse aus Kalender-Termin extrahieren → Navigation
- Erweiterung: Adresse aus HubSpot-Kontakt extrahieren → Navigation
- *(NavigationService.kt existiert bereits für direkte Adress-Eingabe)*

### Phase 5 – Sicherheit & Build (noch nicht implementiert)

**Sub-Task 5.1: Sicherheit & Datenschutz**
- ProGuard/R8 für Release-Build konfigurieren
- Certificate Pinning für kritische Endpunkte
- Datenschutz-Hinweis beim ersten App-Start

**Sub-Task 5.2: APK Build & Sideload ✅ Implementiert**
- `keystore.properties` Template erstellt (liegt im Projektverzeichnis, **nicht** in Git einchecken!)
- `app/build.gradle.kts` für signierten Release konfiguriert (Signing-Config aus `keystore.properties`)
- `proguard-rules.pro` um WorkManager-, Coroutines-, Security- und Spotify-Regeln erweitert
- **Nächste Schritte für den Nutzer:**
  1. `keytool -genkey -v -keystore driveassistant-release.keystore -alias driveassistant -keyalg RSA -keysize 2048 -validity 10000`
  2. Keystore-Datei in `DriveAssistant/` ablegen
  3. `keystore.properties` mit echten Passwörtern befüllen
  4. Release-Hash ermitteln und in Azure eintragen
  5. `./gradlew assembleRelease` → signierte APK unter `app/build/outputs/apk/release/`
  6. APK per USB auf Gerät übertragen → "Unbekannte Quellen" aktivieren → installieren

---

## Nächste Schritte (sofort umsetzbar)

1. **Android Studio öffnen** → Projekt unter `lab6-build_challenge/DriveAssistant/` importieren
2. **App auf Smartphone installieren** (Debug-Build via Android Studio)
3. **PhoneActivity öffnen** → Microsoft Login durchführen
4. **Groq API Key eintragen** (von console.groq.com)
5. **HubSpot Private App Token eintragen** (von app.hubspot.com)
6. **Android Auto Simulator (DHU)** starten und testen
7. **Phase 4** implementieren: Spotify-Steuerung

---

## Bekannte offene Punkte

| Punkt | Details |
|---|---|
| Spotify-Integration | ✅ Implementiert – `SpotifyService.kt` + `PLAY_MUSIC`-Intent im Router |
| Release-Keystore | Noch nicht erstellt – Signature Hash für Release-Build muss in Azure nachgetragen werden |
| READ_MEDIA_AUDIO Permission | Für Android 13+ (API 33+) zusätzlich zu READ_EXTERNAL_STORAGE nötig |
| Teams-Benachrichtigungen | Polling alle 60s als Methode in GraphTeamsService definiert, aber WorkManager-Job fehlt noch |
| PLAUD-Verzeichnis | Exakter Exportpfad der PLAUD-App auf dem Gerät muss ggf. angepasst werden |

---

## Planfile-Referenz

Vollständiger Planfile mit Sub-Task-Status:
`lab6-build_challenge/vertrieb-ki-assistent-plan.md`

Alle Sub-Tasks Phase 1–3 sind abgeschlossen (`[x] done`).
Phase 4 und Phase 5 sind noch offen (`[ ] pending`).
