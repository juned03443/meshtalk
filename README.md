# MeshTalk

## System & Application Software Type

- **Android Kotlin application** using Jetpack Compose UI framework
- **Mesh networking platform** leveraging Google Nearby Connections API
- **Decentralized chat/communication app** without central server dependency
- **Local-first data storage** with Room SQLite database
- **Cryptography-focused** with elliptic curve key pairs (ECDSA/EC)
- **Background foreground service** for mesh networking (persists in background)
- **Architecture**: Model-View-ViewModel (MVVM) pattern with Compose UI
- **Dependency Injection**: Manual via Application class (MeshTalkApp)

## Features Implemented

- **Mesh networking** via Google Nearby Connections API for device-to-device communication
- **Elliptic curve cryptography** for identity and message signing
- **Room database** (schema version 12) with 8 entities:
  - Message, SeenPacket, QueuedMessage, Contact, ConnectionRequest, BlockedUser, RelayPacket, SignalingOutbox
- **Profile management** with nickname, status, and avatar
- **Onboarding flow**: Splash → Identity Setup → Permissions → Main
- **Navigation tabs**: Chats, Radar (nearby peers), Diagnostics, Settings
- **Glassmorphism UI design** with custom theming (dark/light/system)
- **Battery optimization settings**: scan mode, relay options
- **Contact signaling** system for connection management
- **Message status tracking**: PENDING, SENDING, RELAYED, DELIVERED, FAILED
- **Migration system** for database schema updates (10→11→12)
- **Reset identity and data** functionality
- **Nearby people discovery** and contact request system
- **Foreground service** for mesh networking (does not stop on activity destroy)

## Flaws & Bugs Identified

### Database & Migration Issues
- **Version gap**: Database at v12, migrations only cover 10→11 and 11→12. Earlier versions lost.
- **fallbackToDestructiveMigrationOnDowngrade**: True → downgrading app loses all data
- **Missing migration 9→10**: Not in code, risk data loss from older versions

### Architecture & State Management
- **Application class doing too much**: MeshTalkApp handles identity, DB, mesh, signaling, repos → SRP violation
- **Mutable global state**: nickname, userStatus, avatarUri are mutable vars, not lived data flows
- **Context leakage**: Multiple anonymous listeners capturing `this@MainActivity`
- **Blocking DB ops**: clearAllTables() called without state check

### Networking/Mesh Issues
- **No explicit error handling** for NearbyMeshManager operations
- **Permission handling scattered**: PermissionHelper used inconsistently in multiple places
- **Mesh service lifecycle**: onDestroy comment says "do not stop mesh" but service may still stop
- **No reconnection logic** beyond restartMesh()

### UI/Compose Issues
- **Deprecated API**: useSupportLibrary = true (older approach)
- **Multiple rememberInfiniteTransition** without clear cleanup
- **Fragmented show/hide logic**: showAddContact, showIdentityScreen, showProfileEdit booleans
- **Glass components** rely on HazeState that may not dispose properly
- **Navigation bar** uses hardcoded TealBlob references

### Data & Preferences Issues
- **SharedPreferences everywhere**: 3 preference files (meshtalk_prefs, meshtalk_identity, battery prefs)
- **No encryption for most prefs**: Only private key encrypted at rest
- **Default nickname**: Uses Build.MODEL as fallback → unexpected behavior
- **Profile setup boolean**: profile_setup stored as Boolean, but isFirstLaunch checks onboarding_complete

### Code Quality Issues
- **Duplicate code**: Profile setup logic repeated (IDENTITY_SETUP, PROFILE_SETUP screens)
- **Magic numbers**: Hardcoded dp values, SDK versions, dependency versions
- **Commented-out code**: abiFilters.clear() then immediately adds arm64-v8a
- **No interface abstraction**: Direct Room DAO usage, hard to test/mock
- **Long import lists** and deep package nesting

### Potential Runtime Bugs
- **Race condition** in initIdentityKeys(): Legacy → encrypted key migration timing issues
- **onDestroy** in MainActivity has contradictory comment about not stopping mesh
- **Dependency versions**: Some outdated (compose-bom 2024.10.01, room 2.6.1)

## Workflow (CLI)

```
mesh-talk-workflow:

1. Build & Verify
   ./gradlew assembleDebug
   ./gradlew testDebugUnitTest
   ./gradlew lint

2. Identity Setup
   - Generate/load EC key pair
   - Store private key encrypted at rest
   - Derive human-readable mesh ID
   - Initialize NearbyMeshManager

3. Permission Flow
   - Request: BLUETOOTH, BLUETOOTH_SCAN, BLUETOOTH_ADVERTISE, INTERNET, ACCESS_FINE_LOCATION
   - On grant: Start MeshService foreground
   - On deny: Show rationale, retry or gracefully degrade

4. Mesh Initialization
   - Set local identity (meshTalkId + nickname)
   - Configure MeshRouter with keyPair, DAOs, batteryPrefs
   - Start contact signaling subsystem

5. UI State Management
   - Collect: connectedPeers, conversations, contactsUiState
   - Navigation: Tab selection → show corresponding screen
   - Error states: No peers, no messages, offline

6. Message Flow
   - User types → ChatViewModel.sendMessage()
   - QueuedMessageEntity stored locally
   - NearbyMeshManager sends via connections
   - Status updated: PENDING → SENDING → RELAYED/DELIVERED/FAILED

7. Settings & Preferences
   - Theme mode toggle → prefsRepo.setThemeMode()
   - Scan mode change → batteryPrefsRepo.setScanMode()
   - Relay settings → batteryPrefsRepo.setRelayForOthers()
   - Block user → database.blockedUserDao().unblockUser()

8. Data Reset (if needed)
   - nearbyManager.stopAll()
   - database.clearAllTables()
   - Clear shared preferences
   - Reinitialize keys and restart mesh
```

## README Explained

```markdown
# MeshTalk

## What it is
MeshTalk is an Android application for decentralized mesh networking enabling offline/low-connectivity communication between nearby devices. It uses Google's Nearby Connections API to create a local-area mesh network without requiring internet infrastructure or central servers.

## Architecture
- **Language**: Kotlin 1.9+ with Jetpack Compose UI
- **Database**: Room SQLite (schema version 12)
- **Networking**: Google Nearby Connections API
- **Cryptography**: Elliptic curve (ECDSA) key pairs for identity and signing
- **UI**: Glassmorphism design with dark/light/system theme support

## Key Components

### Application Layer (MeshTalkApp)
- Holds application-level state: nickname, userStatus, avatarUri
- Initializes database, mesh router, contact signaling
- Manages identity keys with encrypted at-rest storage
- Handles profile reset and data migration

### Data Layer
- **Room Database**: 8 entities (Message, SeenPacket, QueuedMessage, Contact, ConnectionRequest, BlockedUser, RelayPacket, SignalingOutbox)
- **DAOs**: Query and mutation interfaces for each entity type
- **Migrations**: Schema versioning from 10→11→12 with SQL statements

### Mesh Networking
- **NearbyMeshManager**: Wraps Google Nearby Connections API
- **MeshRouter**: Routes messages through mesh hops
- **ContactSignaling**: Handles connection requests and signaling payloads
- **BatteryPreferences**: Optimizes scanning/relay behavior

### UI Layer (Jetpack Compose)
- **MainActivity**: Screen flow manager (Splash → Identity → Permissions → Main)
- **Navigation**: Bottom tab bar (Chats, Radar, Diagnostics, Settings)
- **Components**: GlassCard, GlassBubble, MeshAvatar, OnlineStatusIndicator
- **Theming**: MeshTalkTheme with custom colors (TealBlob, PurpleBlob)

## Features
- Decentralized mesh chat without internet
- End-to-end encrypted messages via EC cryptography
- Nearby peer discovery and connection
- Contact request system
- Message status tracking (Pending/Relayed/Delivered/Failed)
- Profile management with avatar
- Battery-optimized scanning modes
- Theme switching (dark/light/system)

## Known Issues
- Database downgrade destroys data (fallbackToDestructiveMigrationOnDowngrade=true)
- Application class has multiple responsibilities
- Scattered permission handling patterns
- Limited error recovery for mesh disconnections
- SharedPreferences used for critical state (consider migration to DataStore)
- Some dependency versions may be outdated

## Building
```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
```

## Development
- Requires Android SDK with Google Play Services
- Minimum SDK: 26 (Android 8.0)
- Target SDK: 34
- Key dependencies: Room, Kotlin Serialization, Coil (image loading), Haze (frosted glass), Play Services Nearby
```