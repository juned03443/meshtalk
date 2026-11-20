# MeshTalk Architecture

## Overview
MeshTalk is an Android Kotlin application using Jetpack Compose for a decentralized mesh networking platform. The system follows a layered architecture with clear separation between UI, application, data, and networking concerns.

## Layered Architecture

### 1. Presentation Layer (UI)
- **Framework**: Jetpack Compose with Material Design 3
- **Architecture**: Composable functions organized by screen
- **Theme**: Glassmorphism design with custom dark/light/system themes
- **Navigation**: Bottom tab bar (Chats, Radar, Diagnostics, Settings)
- **Screen Flow** (MainActivity):
  - SplashScreen → IdentitySetup → Permissions → Main
  - Main contains tabbed interface with 4 sections

### 2. Application Layer
- **Singleton**: MeshTalkApp (extends Application)
- **Responsibilities**:
  - Initialize EC key pair and store encrypted at rest
  - Create and manage RoomDatabase instance
  - Set up NearbyMeshManager, MeshRouter, ContactSignaling
  - Manage profile data (nickname, status, avatarUri)
  - Handle identity reset and data clearance

### 3. Data Layer
- **Database**: Room SQLite with schema version 12
- **8 Entities**:
  - MessageEntity - chat messages with status tracking
  - SeenPacketEntity - deduplication tracking
  - QueuedMessageEntity - outgoing messages waiting to send
  - ContactEntity - known peers
  - ConnectionRequestEntity - pending contact requests
  - BlockedUserEntity - blocked peers
  - RelayPacketEntity - relay-hop message data
  - SignalingOutboxEntity - async signaling payloads
- **12 Abstract DAOs** for each entity type with CRUD operations
- **3 Migrations** documented (10→11, 11→12, plus implicit earlier)

### 4. Networking Layer
- **NearbyMeshManager**: Google Nearby Connections API wrapper
  - Device discovery and connection
  - Connection lifecycle management
  - Message transmission via mesh
- **MeshRouter**: Message routing logic
  - Hop limit configuration (default 4)
  - Peer routing and forwarding
- **ContactSignaling**: Connection signaling system
  - Connection request/acceptance flow
  - Blocked user management

### 5. Cryptography Layer
- **CryptoEngine**: Elliptic curve operations
  - Key pair generation (ECDSA)
  - Private key encryption at rest
  - Public key base64 encoding
  - Human-readable ID derivation

### 6. Repository Layer
- **UserPreferencesRepository**: DataStore/MeshBatteryPreferences
- **ProfileRepository**: Profile data management
- **IdentityRepository**: Key pair lifecycle
- **NearbyPeopleRepository**: Discovered peers management

## Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                        MeshTalkApp                           │
│  (Application - Singleton, Initialization Hub)               │
├─────────────────────────────────────────────────────────────┤
│  ├─ initializes ┬─ MeshTalkDatabase (Room DB)                │
│  ├─ creates     ├─ NearbyMeshManager                         │
│  ├─ keyPair     ├─ MeshRouter                                │
│  ├─ deviceId    ├─ ContactSignaling                          │
│  ├─ nickname    ├─ BatteryPreferencesRepo                    │
│  ├─ userStatus  ├─ ContactRepository                         │
│  ├─ avatarUri   ├─ NearbyPeopleRepository                    │
│  └─ onboarding  └─ ProfileRepository                         │
├─────────────────────────────────────────────────────────────┤
│                         │                                       │
│                         ▼                                       │
│               ┌─────────────────────┐  ┌─────────────────────┐ │
│               │   Data Layer        │  │   Crypto Layer      │ │
│               │  (Room DB - 8 entities)│  │  (CryptoEngine)     │ │
│               └─────────────────────┘  └─────────────────────┘ │
│                         │                                       │
│                         ▼                                       │
│               ┌─────────────────────┐  ┌─────────────────────┐ │
│               │   Repositories      │  │   Mesh Networking   │ │
│               │  (4 repos)          │  │  (NearbyMeshMgr)    │ │
│               └─────────────────────┘  └─────────────────────┘ │
│                         │                                       │
│                         ▼                                       │
│               ┌─────────────────────┐  ┌─────────────────────┐ │
│               │   UI Layer          │  │   Network Layer     │ │
│               │  (Compose UI)       │  │  (Nearby Connections)││
│               └─────────────────────┘  └─────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

## Data Flow Diagrams

### Message Sending Flow
```
User Types
    │
    ▼
ChatViewModel.sendMessage()
    │
    ▼
QueuedMessageEntity stored in Room DB
    │
    ▼
NearbyMeshManager.sendMessage()
    │
    ▼
MeshRouter.routeMessage() → hops via connected peers
    │
    ▼
Message transmitted over Nearby Connections
    │
    ▼
Receiver gets message → MessageEntity created
    │
    ▼
Status updated: PENDING → SENDING → RELAYED/DELIVERED/FAILED
    │
    ▼
UI recomposes with new message bubble
```

### Permission & Mesh Initialization Flow
```
MainActivity.onCreate()
    │
    ▼
UserPreferencesRepository theme/prefs collect
    │
    ▼
ThemeMode determined (SYSTEM/DARK/LIGHT)
    │
    ▼
Check PermissionHelper.hasAllPermissions()
    │
    ├─ If granted: startMeshServices() → MeshService.start()
    │         │
    │         ▼
    │     NearbyMeshManager.setLocalIdentity(meshTalkId, nickname)
    │
    └─ If denied: show PermissionScreen → requestPermissions()
           │
           ▼
           On grant: proceed to startMeshServices()
           On deny: show toast, stay on permission screen
```

### Screen Flow State Machine
```
+----------------+     +----------------+     +----------------+
|   SplashScreen   |     |IdentitySetup   |     |   Permission   |
+--------+--------+     +------+--------+     +--------+---------+
         |                   |                |
         |                   |                |
         |                   |                |
         v                   v                v
+----------------+   if !profile_setup   +----------------+
|   Main (tabs)  | <─── if !hasPerms    |   Main (tabs)  |
+----------------+   else             +----------------+
```

## Dependency Injection Wiring

```
MeshTalkApp
  ├─ creates: database = MeshTalkDatabase.getInstance(this)
  ├─ creates: nearbyManager = NearbyMeshManager(this)
  ├─ creates: meshRouter = MeshRouter(...)
  ├─ creates: contactSignaling = ContactSignaling(...)
  ├─ creates: contactRepository = ContactRepository(...)
  ├─ creates: nearbyPeopleRepository = NearbyPeopleRepository(...)
  └─ provides: keyPair, meshTalkId, deviceId, nickname, userStatus, avatarUri
```

## Database Schema (Version 12)

| Entity | Key Fields | Purpose |
|--------|-----------|---------|
| MessageEntity | messageId, peerId, content, timestamp, isOutgoing, status | Core chat messages |
| SeenPacketEntity | messageId, timestamp | Deduplication - track seen messages |
| QueuedMessageEntity | messageId, content, timestamp, retryCount | Outgoing messages waiting to send |
| ContactEntity | meshTalkId, displayName, identifier, keyChanged | Known peers |
| ConnectionRequestEntity | fromMeshId, toMeshId, status, lastSentAt, sendAttempts | Pending contacts |
| BlockedUserEntity | meshTalkId, blockedAt | Blocked peers list |
| RelayPacketEntity | hopCount, payload, timestamp | Multi-hop routing data |
| SignalingOutboxEntity | id, requestId, type, targetMeshId, payloadBytes, attempts, createdAt, nextAttemptAt, expiresAt | Async signaling payloads |

Migrations Applied:
- 10→11: Added keyChanged to contacts, lastSentAt/sendAttempts to connection_requests, dropped old index, created new non-unique index, created signaling_outbox table
- 11→12: Dropped emergency_sos, groups, group_members, user_reports tables (cleanup)

## Key Constants & Configurations

### Network
- MIN_SDK: 26 (Android 8.0)
- TARGET_SDK: 34
- Nearby Connections API version: 19.5.1
- Bluetooth permissions: SCAN, ADVERTISE, CONNECT

### Database
- Schema version: 12
- Export schema: false
- Destructive migration on downgrade: true (known issue)

### UI
- Glassmorphism theme colors: TealBlob, PurpleBlob
- Default hop limit: 4
- Battery scan modes: BALANCED, LOW_POWER, AGGRESSIVE
- Relay settings: enabled, onlyWhileCharging

### Cryptography
- Curve: EC (secp256r1 or similar)
- Key storage: Encrypted at rest with CryptoEngine
- ID derivation: CryptoEngine.deriveHumanId(keyPair.public.encoded)