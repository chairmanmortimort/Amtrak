# Light Phone III: A Complete Guide to the Platform, SDK, and Transit Tools

A presentation-style report explaining everything from zero assumed knowledge.

---

## Part 1: What is the Light Phone III?

### The Philosophy

The Light Phone III is a "minimalist" phone made by Light, a company that asks one question:

> What if a phone did less?

Where other smartphones try to do *everything* — social media, games, 1000+ apps — the Light Phone III embraces radical simplicity. It makes calls, sends texts, tells time, and runs **tools** (Light calls them "tools" instead of "apps"). The philosophy is called **"Light" ethos**:

- **Intentional interaction**: Every action should serve a clear purpose
- **Monochrome everything**: The display is black and white — no color, no emoji
- **Touch screen**: A capacitive touch screen handles all input (taps, swipes)
- **D-pad navigation**: Physical directional pad + center select button for menu navigation
- **No distractions**: No notifications, no social media, no AI assistants

### The Hardware

| Feature | Light Phone III |
|---|---|
| Display | 1080 × 1240 pixels (NOT 1080×2400 — that's a common mistake) |
| Touch input | Full capacitive touch screen |
| D-pad | 4-direction pad + center select button for menu navigation |
| OS | Android 14 (API 34), heavily customized as "LightOS" |
| No Google Services | Zero Google Play Services, Firebase, or GMS APIs |

### Why This Matters for Code

The constraints of the hardware directly shape how code is written:

1. **Small screen**: Only 1080×1240 pixels — that's ~6.5" at 360 DPI if it were a modern phone, but the text rendering uses a custom grid system (more on this later)
2. **Touch + D-pad input**: Tap interactions are primary; D-pad (UP/DOWN/LEFT/RIGHT + CENTER) provides keyboard-style navigation as fallback — useful for navigating menus and interacting with `LightTextInputEditor`
3. **No color**: Colors are just "dark mode" or "light mode" — monochrome. Emoji are explicitly forbidden because they break the aesthetic
4. **No Google**: Can't use Firebase, Google Maps, Google Play Services, or any Google-dependent libraries

---

## Part 2: The Light Phone SDK Architecture

### What is an SDK?

An SDK (Software Development Kit) is a bundle of code that lets you build apps for a platform without starting from zero. The Light Phone SDK provides pre-built building blocks that match the Light Phone's design language.

### Project Structure

The SDK is organized into two main layers:

```
light-sdk/
├── sdk/                    # The core SDK (what Light provides)
│   ├── client/             # Base classes (LightActivity, LightViewModel, LightScreen)
│   └── ui/                 # UI components (LightText, LightTopBar, LightBottomBar, etc.)
├── examples/               # Official sample tools (Weather, Authenticator, UI-Demo)
│   ├── weather/            # Weather tool (5 files)
│   ├── authenticator/      # TOTP 2FA tool (18 files)
│   ├── ui-demo/            # Component showcase
│   ├── cdta/               # Community: CDTA bus tracker
│   └── wikipedia/          # Community: Wikipedia browser
└── tool/                   # Community: Amtrak train tracker
```

### The Three-Layer Architecture

Every Light Phone tool follows a three-layer architecture:

```
┌──────────────────────────────────────────────────┐
│  PRESENTATION LAYER                              │
│  (What you see on screen)                        │
│  • Screen classes (HomeScreen, DetailScreen)     │
│  • Composable functions (Jetpack Compose)        │
│  • Light UI components (LightText, LightTopBar)  │
├──────────────────────────────────────────────────┤
│  VIEWMODEL LAYER                                 │
│  (Business logic + state)                        │
│  • LightViewModel subclass                       │
│  • StateFlow for UI state                        │
│  • CoroutineExceptionHandler for errors          │
│  • DataStore for persistent data                 │
├──────────────────────────────────────────────────┤
│  DATA/API LAYER                                  │
│  (Getting data from the internet)                │
│  • Api classes (WeatherApi, AmtrakApi, etc.)     │
│  • Data models (Serializable data classes)       │
│  • Ktor HTTP client (the network library)        │
└──────────────────────────────────────────────────┘
```

### The Navigation Model

#### What is a "Screen"?

A Screen in the Light Phone SDK is a full-screen page. Think of it like a "card" in a deck of cards — you flip through them. Each screen is a Kotlin class extending `LightScreen`:

```kotlin
class HomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, AmtrakViewModel>(sealedActivity) {
    
    override fun Content() {
        // This is where you draw the screen
        LightTopBar(center = LightTopBarCenter.Text("Amtrak"))
        // ... more UI
    }
}
```

#### How Navigation Works

Navigation is a stack (like a back stack of cards):

- `navigateTo(::SomeScreen)` — pushes a new screen on top
- `goBack()` — pops the current screen
- `onScreenShow()` — called when a screen becomes visible (like "viewDidAppear" in iOS)

#### The Top Bar

Every screen typically has a `LightTopBar` at the top:

```
┌──────────────────────────────────┐
│ ←   [Center Title]    ⚙         │  ← LightTopBar
│                                  │
│  Main Content Here               │
│                                  │
│                                  │
│                                  │
│  ▢  ⃝  ◯                         │  ← LightBottomBar
└──────────────────────────────────┘
```

The top bar has:
- **Left button** (usually ← BACK)
- **Center text** (the screen title — e.g., "Amtrak", "CDTA", "Wikipedia")
- **Right button** (contextual — usually ⚙ SETTINGS)

The bottom bar has up to 3 icon buttons for primary navigation.

### The State Management Pattern

#### What is a ViewModel?

A ViewModel is a Kotlin class that holds data and business logic. It survives configuration changes (like screen rotation) and lives independently of the UI. Think of it as the "brain" behind a screen.

In the Light Phone SDK, ViewModels extend `LightViewModel`:

```kotlin
class AmtrakViewModel(
    private val dataStore: DataStore<Preferences>,
) : LightViewModel<Unit>() {
    
    // State that the UI observes
    private val _homeState = MutableStateFlow<HomeState>(HomeState.Loading)
    val homeState: StateFlow<HomeState> = _homeState.asStateFlow()
    
    // Error state
    private val _errorModal = MutableStateFlow<String?>(null)
    val errorModal: StateFlow<String?> = _errorModal.asStateFlow()
}
```

#### What is StateFlow?

`StateFlow` is a Kotlin Coroutines primitive for observable state. The UI "collects" (listens to) state changes:

```kotlin
// In the Screen's Content() function:
val state by viewModel.homeState.collectAsState()
// When homeState changes, Content() automatically re-runs
```

#### What is a Sealed Class?

A sealed class is Kotlin's way of making a type that can be exactly one of several variants:

```kotlin
sealed class HomeState {
    object Loading : HomeState()      // "show a loading spinner"
    data class Trains(val trains: List<TrainDisplay>) : HomeState()  // "show the list"
    data class Error(val message: String) : HomeState()  // "show an error"
}
```

This is better than nullable fields or boolean flags because the compiler forces you to handle every case.

### The Data Flow Pattern

```
User taps button
    ↓
ViewModel calls API in background coroutine
    ↓
API returns Result (Success or Failure)
    ↓
ViewModel updates StateFlow
    ↓
UI collects new state → re-renders
```

#### Coroutines, Dispatchers, and Exception Handlers

- **Coroutines** = Kotlin's lightweight threading system. They're cheap to create and cancel.
- **Dispatchers.IO** = a background thread for network/file I/O
- **Dispatchers.Main** = the UI thread (where Compose re-renders)
- **CoroutineExceptionHandler** = catches errors in coroutines so the app doesn't crash

```kotlin
viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
    api.fetchTrains().fold(
        onSuccess = { result -> setState(HomeState.Trains(result)) },
        onFailure = { error -> showError(error) },
    )
}
```

### DataStore: Persistence Without SQLite

Light Phone tools persist small amounts of data (last search, cached results) using `DataStore` — a modern Android library that stores key-value pairs. It's accessed via `lightContext.dataStore` in the ViewModel.

```kotlin
// Saving data
dataStore.edit { prefs ->
    prefs[LAST_SEARCH_QUERY] = query
}

// Reading data
val prefs = dataStore.data.first()
val query = prefs[LAST_SEARCH_QUERY]
```

---

## Part 3: The Design System

### Typography Scale

The Light Phone uses a fixed typography scale (from `LightTheme.kt`):

| Variant | Font Size | Use Case |
|---|---|---|
| Title | 115sp | Screen titles (rare) |
| Subtitle | 52sp | Subtitles, section labels |
| Heading | 38sp | Section headings in content |
| Subheading | 30sp | Menu items, primary labels |
| Copy | 30sp | Body text |
| Button | 30sp | Button labels (uppercase) |
| Paragraph | 24.5sp | Article paragraphs |
| Detail | 20sp | Subtext, timestamps |
| Fine | 15sp | Top bar titles, fine print |
| Superfine | ~13sp | Very fine print |
| Micro | 12sp | Tiny labels |

Note: These sizes are scaled by `designVerticalPxToSp()` which multiplies by `screenHeightDp / 600`. At 1240dp height, a 30sp font renders at ~62px actual size.

### Grid System

The Light Phone uses a fixed grid: **27 × 31 units**. One grid unit = `screenWidth / 27` dp horizontally, `screenHeight / 31` dp vertically.

```kotlin
// 1 grid unit of horizontal padding
Modifier.padding(horizontal = 1f.gridUnitsAsDp())

// 2.5 grid units of vertical spacing  
Modifier.padding(top = 2.5f.verticalGridUnitsAsDp())
```

### Colors

Monochrome only — no color. The entire system is:

- **Dark mode**: Black background, white text, gray(0xFFBBBBBB) for secondary
- **Light mode**: White background, black text, gray(0xFF666666) for secondary

Toggle between them via `LightThemeController.colors`.

### Available Icons

The SDK ships with ~60 monochrome icon drawables (all white). Examples:
- `LightIcons.SEARCH`, `LightIcons.SETTINGS`, `LightIcons.BACK`
- `LightIcons.DIRECTIONS_TRAIN`, `LightIcons.MAP`, `LightIcons.LOOP`
- `LightIcons.REFRESH`, `LightIcons.FAST_FORWARD` (now unused — see below)

### Key UI Components

| Component | Purpose |
|---|---|
| `LightTopBar` | Header with title + up to 3 buttons (left, center, right) |
| `LightBottomBar` | Footer with 3 icon buttons for primary navigation |
| `LightText` | Text rendering with typography variants |
| `LightScrollView` | Scrollable container (single-column, with scrollbar) |
| `LightTextInputEditor` | Full-screen text input with on-screen keyboard + D-pad support |
| `LightFullscreenModal` | Full-screen error/information overlay |
| `LightGrid` | Grid-based layout helpers |

---

## Part 4: The Three Custom Tools

### Tool 1: Amtrak (Rail Transit)

#### Interface Walkthrough

```
┌──────────────────────────────────┐
│ Trains    ←       ⏧              │  ← Top bar: show trains vs. stations
│                                  │
│ Brightline                       │  ← Train route
│ Miami → West Palm Beach          │  ← Route direction  
│ Stop: Fort Lauderdale · On time  │  ← Current stop + delay status
│                                  │
│ Brightline                       │
│ Miami → Orlando                  │
│ Stop: Orlando · On time          │
│                                  │
│ Ocean                            │
│ Petit Rocher → Petit Rocher      │
│ Stop: Petit Rocher · On time     │
│                                  │
│ 🚄  🗺  🔍                         │  ← Bottom bar: trains, stations, search
└──────────────────────────────────┘
```

**Navigation**: TAP to select items, D-pad (UP/DOWN to move between items, CENTER to activate) for menu navigation.

**Key Design Decisions**:
- **Top bar right button**: Shows/hides train/station toggle
- **Bottom bar**: 3 icons — Trains (list), Map (stations), Search (? icon)
- **Disruption status**: Uses `!!` text prefix (not ⚠️ emoji, which was removed for monochrome compliance)
- **Local search**: Searches cached trains/stations offline (no API call needed)

#### Code Architecture

**ViewModel** (`AmtrakViewModel.kt`) — 440 lines, the brain:
- Holds 4 StateFlows: `homeState`, `trainDetailState`, `stationDetailState`, `searchState`, `errorModal`
- `init { loadFromCacheOrFetch() }` — loads immediately on creation
- `onScreenShow()` — refreshes data if cache is older than 5 minutes
- Cache: trains/stations serialized as JSON in DataStore, max age = 5 minutes
- Network fallback: if API fails, falls back to cached data
- Time filtering: station detail shows only upcoming trains (comparing to `Clock.System.now()`)

**Models** (`AmtrakModels.kt`):
- `TrainData` — raw API response (train number, route, stations list, positions)
- `TrainDisplay` — UI-ready train (sorted by train number, computed display strings)
- `StationTrain` — per-stop train info (arrival/departure times, status)

**API** (`AmtrakApi.kt`):
- Uses Ktor HTTP client
- Three endpoints: `fetchTrain(trainId)`, `fetchAllTrains()`, `fetchAllStations()`
- Returns `Result<T>` (Kotlin's built-in success/failure wrapper)

**Repository** (`AmtrakRepository.kt`):
- Simple singleton holding `selectedTrainId` and `selectedStationCode`
- This replaced a fragile `NavigationArgs` pattern (lessons learned from SDK conventions)

**Screens**:
- `HomeScreen.kt` — top bar + bottom bar + LazyColumn of trains/stations
- `TrainDetailScreen.kt` — scrollable list of stops for a train
- `StationDetailScreen.kt` — shows upcoming trains at a station
- `SearchScreen.kt` — local text search with `LightTextInputEditor`

#### What Changed (from SDK lessons)

1. **Repository singleton**: Replaced `NavigationArgs` with `AmtrakRepository` singleton — matches Authenticator's `TotpAccountRepository` pattern
2. **CoroutineExceptionHandler**: All `viewModelScope.launch` calls wrapped with it — matches Weather pattern
3. **DataStore caching**: 5-minute cache with stale-while-revalidate — matches Weather's `loadStoredState` pattern
4. **Minimum loading display**: 1-second minimum to prevent jarring flash — matches Weather's `awaitMinimumLoading` pattern
5. **LightTextInputEditor**: Search uses the SDK's native text input (not Android's EditText) — matches SDK convention
6. **Emoji removal**: Replaced ⚠️ with `!!` text — monochrome compliance
7. **Error modal**: Uses `LightFullscreenModal` — matches CDTA's existing pattern

---

### Tool 2: CDTA (Bus Transit)

#### Interface Walkthrough

```
┌──────────────────────────────────┐
│ CDTA   ←       ☰  [search bar]   │  ← Top bar + search field
│                                  │
│ 1  Central Avenue                │  ← Route number + name
│ 10 Western Avenue                │
│ 12 Washington Avenue             │
│ 13 New Scotland Avenue           │
│ 18 Delaware Avenue               │
│                                  │
│ 🚌  🔍  ⓘ                          │  ← Bottom bar: routes, search, info
└──────────────────────────────────┘
```

**Navigation**: TAP routes to see details. D-pad for menu navigation as fallback.
- Route list sorted alphabetically
- Tap a route → see direction + stop list
- Tap a stop → see upcoming departures (computed from GTFS schedule)
- Search bar uses `LightTextInputEditor` with on-screen keyboard + D-pad support

#### Code Architecture

**ViewModel** (`CdtaViewModel.kt`):
- `onScreenShow()` override: restores last viewed stop from DataStore — matches the `restoreSavedWeather` pattern from Weather
- `MIN_LOADING_DISPLAY = 1.seconds` — minimum loading duration
- `apiExceptionHandler` — catches all coroutine errors
- `GtftRepository` — wraps GTFS data loading from bundled JSON assets
- `getEffectiveTimeSec()` — uses `java.util.Calendar` (not `kotlinx-datetime`, since CDTA doesn't depend on it)

**Models** (`CdtaModels.kt`):
- `Route` — route short name, long name, description
- `Stop` — stop code, name, lat/lon, wheelchair accessibility
- `StopTime` — scheduled arrival/departure times
- Time formatting: `formatTime()` converts epoch seconds to local time strings (e.g., "3:45 PM")

**GTFS Parsing** (`gtfs/` package):
- `GtfsParser.kt` — loads `stop_times.txt`, `stops.txt`, `trips.txt`, `routes.txt` from assets
- `GtfsModels.kt` — raw GTFS entities matching the CSV columns

**Screens** (`HomeScreen.kt`):
- `HomeContent` — top bar + search + LazyColumn of routes
- `SearchContent` — `LightTextInputEditor` for finding routes/stops
- `RouteDetailContent` — shows route description and stop list
- `StopDetailContent` — shows upcoming departures for a stop

#### Key Insight: GTFS Schedule Position Estimation

CDTA uses GTFS static data (scheduled times) rather than real-time API predictions. The tool calculates which trips are "upcoming" by:
1. Finding the current time via `Calendar.getInstance()`
2. Filtering `StopTime` entries where `arrivalTime >= now`
3. Grouping by trip and showing the next 3-5 departures

This is the same pattern used in the `gtfs-schedule-position-estimation` skill — it's a deterministic approach that works without real-time data.

---

### Tool 3: Wikipedia

#### Interface Walkthrough

```
┌──────────────────────────────────┐
│ Wikipedia ←         ⚙            │  ← Top bar + settings button
│ Search articles or discover...   │  ← Description
│                                  │
│ 🔍  Search                      │  ← Menu item
│ 🔄  Random Article              │  ← Menu item
│                                  │
│                                  │
│                                  │
│                                  │
└──────────────────────────────────┘
```

After searching:

```
┌──────────────────────────────────┐
│ Wikipedia ←         ⚙            │
│                                  │
├──────────────────────────────────┤
│ Search field [_________] SEARCH  │  ← LightTextInputEditor full screen
└──────────────────────────────────┘
```

Search results:

```
┌──────────────────────────────────┐
│ Wikipedia ←         ⚙            │
│                                  │
├──────────────────────────────────┤
│ Search results for "Quantum..."  │
│                                  │
│ Quantum Physics                  │
│ Quantum Mechanics                │
│ Quantum Entanglement             │
│                                  │
└──────────────────────────────────┘
```

Article view:

```
┌──────────────────────────────────┐
│ Quantum Physics ←        ⚙       │
│ Physics of the quantum world...  │  ← description
│                                  │
│ Quantum mechanics is a...        │  ← extract (plain text)
│                                  │
│ == Concepts ==                  │  ← section header (rendered as Heading)
│ ...                              │
│                                  │
│ == History ==                   │
│ ...                              │
│                                  │
│ §§ Related Articles §§           │  ← at ~85% scroll position
│ • Quantum computing              │
│ • Wave particle duality          │
└──────────────────────────────────┘
```

**Key Features**:
- Public API — no authentication needed
- Article content rendered as plain text (no images, no complex formatting)
- Section headers (`== Section ==`) detected and rendered as headings
- Terminal sections (References, Sources, External links, etc.) automatically stripped
- Related Articles shown at bottom of article
- **Forward arrow removed** — was causing confusing partial-scroll behavior

#### Code Architecture

**ViewModel** (`WikipediaViewModel.kt`) — 251 lines:
- Single `WikipediaUiState` with a `mode` field (sealed class with 6 variants: Home, SearchInput, Search, Loading, Article, About)
- `CoroutineExceptionHandler` catches all API errors
- `MIN_LOADING_DISPLAY = 1.seconds` — prevents jarring loading flash
- DataStore saves `LAST_SEARCH_QUERY` and `LAST_ARTICLE_TITLE` keys
- `onScreenShow()` — reads DataStore (passive restore — doesn't auto-navigate)
- `dismissError()` — inlined state update (fixed: was calling a suspend function)

**ArticleContent** (`WikipediaArticleScreen.kt`) — the article rendering:
- **Terminal section stripping**: filters out sections like "References", "Sources", "External links", "Further reading", "See also", "Footnotes"
- **Section detection**: parses `== Section ==` headers from Wikipedia's plain text extract (requires `exsectionformat` to be NOT set — was changed from the original)
- **Related Articles fallback**: when no next section exists, scrolls to 85% of max scroll position where Related Articles appear
- **Forward arrow removed**: the `LightIcons.FAST_FORWARD` top-right button was causing confusing behavior — estimated scroll positions didn't match actual rendered heights, causing tiny jumps. Removed entirely for cleaner UX.
- **Description placement**: article description shown between title and extract, not as part of extract

**API** (`WikipediaApi.kt`):
- `fetchSummary(title)` — gets article description + thumbnail from REST API
- `fetchExtract(title)` — gets plain text extract (with `== Section ==` headers — `exsectionformat=plain` was **removed** because it stripped the section headers, making section detection fail)
- `fetchLinks(title)` — gets related article titles for the "Related Articles" section
- `fetchRandomTitle()` — gets a random article title from `https://en.wikipedia.org/api/rest_v1/page/random/summary`
- `search(query)` — searches articles via the REST API

**Models** (`WikipediaModels.kt`):
- `WikiExtract` — JSON response from the summary API (title, description, extract, thumbnail)
- `WikiSearchResult` — title + snippet from search results
- `WikiSummary` — used in `fetchSummary`

#### Key SDK Lessons Applied

1. **LightTextInputEditor replaces AndroidView+EditText**: The original Wikipedia used Android's `EditText` wrapped in `AndroidView`. This was replaced with Light's `LightTextInputEditor` because:
   - `EditText` doesn't work properly on the LP3's touch-only interface
   - `LightTextInputEditor` handles on-screen keyboard and D-pad navigation natively
   - The `editorKey` parameter forces a `TextFieldState` reset when navigating back to the screen (prevents stale text) — same pattern as Amtrak SearchScreen.

2. **Removed `exsectionformat=plain`**: The Wikipedia REST API's `exsectionformat=plain` parameter strips `==` markers from section headers. Without them, the regex `^(=+)\\s*(.+?)\\s*=+$` in `ArticleBody` can't detect sections. Removing this parameter allowed `== Section ==` headers to flow through.

3. **Removed forward arrow**: The original "fast forward" button used estimated pixel positions for sections. These estimates (28dp for paragraphs, 38dp for headings) didn't match actual rendered heights, causing the scroll to jump erratically.

4. **CoroutineExceptionHandler pattern**: Matches Weather — catches cancellation exceptions, surfaces human-readable error messages to `LightFullscreenModal`.

5. **DataStore for persistence**: Saves last search query and article title, matching Weather's `restoreSavedWeather` pattern.

---

## Part 5: How Each Tool Differs

| Aspect | Amtrak | CDTA | Wikipedia |
|---|---|---|---|
| **Data source** | Real-time API (Trains.gov) | Static GTFS files (bundled) | Public REST API (Wikipedia) |
| **Needs network?** | Yes (always) | No (after initial load) | Yes (always) |
| **Uses search input?** | Yes (local search) | Yes (find routes/stops) | Yes (search articles) |
| **Has detail screens?** | Yes (train detail, station detail) | Yes (stop detail) | Yes (article view) |
| **Cache strategy** | 5-min JSON cache in DataStore | Load GTFS at init (no cache needed) | None (always fresh API call) |
| **Navigation pattern** | Repository singleton + navTo | Simple navTo + DataStore restore | Single-screen mode enum |
| **Text input** | LightTextInputEditor (search) | LightTextInputEditor (search) | LightTextInputEditor (search) |
| **Error display** | LightFullscreenModal | LightFullscreenModal | LightFullscreenModal |
| **Data format** | JSON via Ktor + kotlinx.serialization | CSV parsing → internal models | JSON via Ktor + kotlinx.serialization |
| **Location aware** | Yes (train lat/lon, map links) | No | No |
| **Time aware** | Yes (scheduled vs. estimated) | Yes (GTFS schedule) | No |

### Architecture Complexity

```
Amtrak:  4 screens + 1 API + 1 models + 1 repository + 1 ViewModel  = 9 files
CDTA:    1 main screen (with 4 content modes) + 1 API + 1 models + 1 GTFS package + 1 ViewModel = 8 files
Wikipedia: 3 screens + 1 API + 2 models + 1 ViewModel = 7 files
```

---

## Part 6: How the Apps Were Built

### Common Patterns Across All Three

Every tool follows this exact blueprint:

```
1. @InitialScreen class XxxHomeScreen : LightScreen<Unit, XxxViewModel>
2.     override val viewModelClass = XxxViewModel::class.java
3.     override fun createViewModel() = XxxViewModel(lightContext.dataStore)
4.     @Composable
5.     override fun Content() { ... observes stateFlows ... }
6.
7. class XxxViewModel(dataStore) : LightViewModel<Unit>
8.     private val _state = MutableStateFlow(...)
9.     val state: StateFlow<...> = _state.asStateFlow()
10.    override fun onScreenShow(...) { refresh logic }
11.    private val apiExceptionHandler = CoroutineExceptionHandler { ... }
```

### The Build Process

All tools build via Gradle Kotlin DSL:

```bash
# Mac (JDK 21 required — JDK 26 breaks the Android SDK)
export JAVA_HOME=/usr/local/opt/openjdk@21
./gradlew :examples:wikipedia:assembleDebug --no-daemon --offline

# APK output
examples/wikipedia/build/outputs/apk/debug/wikipedia-debug.apk
```

### The Deployment Process

```bash
# Install to emulator
adb -s emulator-5554 install -r wikipedia-debug.apk

# Launch
adb -s emulator-5554 shell am start -n \
  com.thelightphone.wikipedia/com.thelightphone.sdk.LightActivity

# For real LP3 device: change serverPackage in lighttool.toml
# from "com.thelightphone.sdk.emulator" to "com.lightos"
```

### Emulator Setup

The Light Phone III emulator is configured as:
- **Screen size**: 1080 × 1240 pixels (NOT 1080×2400)
- **Density**: 420 DPI
- **Server package**: `com.thelightphone.sdk.emulator` (in `lighttool.toml`)

The config file lives at:
```
~/.android/avd/lp3test2.avd/config.ini
```

Key display settings:
```ini
hw.lcd.width = 1080
hw.lcd.height = 1240
hw.lcd.density = 420
```

---

## Part 7: SDK Conventions and What We Learned

### From the Weather Tool

Weather is the gold standard. Key patterns we copied:

1. **`CoroutineExceptionHandler`** — every `viewModelScope.launch` is wrapped with it
2. **`MIN_LOADING_DISPLAY = 1.seconds`** — prevents loading flash
3. **`onScreenShow`** — refreshes data when screen appears
4. **`skipRefreshOnNextScreenShow`** — prevents refresh after user explicitly navigates
5. **DataStore restore** — falls back to saved data when API fails
6. **`awaitMinimumLoading(startTime)`** — helper function extracted into a pattern

### From the Authenticator Tool

Authenticator is the most complex sample. Key patterns:

1. **Repository singleton** — `TotpAccountRepository` for navigation state (we copied this in Amtrak)
2. **Room database** — SQLite ORM wrapped in DAO pattern (not applicable to our transit tools, but shows the pattern for persistent data)
3. **QR scanner integration** — uses `LightQrCodeScanner` for adding accounts
4. **Result callback navigation** — `navigateTo(screenFactory) { result -> ... }` pattern

### From the UI-Demo Tool

UI-Demo is a showcase of all SDK components. Key learnings:

1. **`LightText`** variants — use `Subheading` for menu items, `Detail` for secondary text
2. **`LightTextInputEditor`** — the proper way to do text input (not Android `EditText`)
3. **`LightScrollView`** — always use this instead of raw Compose `ScrollState`
4. **Grid units** — always use `gridUnitsAsDp()`, never hardcode dp/px
5. **Icon sizing** — buttons use `button.sizeUnits.gridUnitsAsDp()` for consistent sizing

### What We Got Wrong (and Fixed)

| Problem | Fix | Source |
|---|---|---|
| Wikipedia used Android `EditText` via `AndroidView` | Replaced with `LightTextInputEditor` using `editorKey` | UI-Demo + Amtrak pattern |
| `exsectionformat=plain` stripped `== Section ==` headers | Removed the parameter from API call | Direct fix |
| Forward arrow jumped to wrong sections | Removed entirely — estimates unreliable | User feedback |
| `dismissError()` called suspend function | Inlined the state update | Direct fix |
| `editorKey` used `kotlin.hashCode()` | Changed to `System.nanoTime()` | Amtrak pattern |
| Emoji ⚠️ in Amtrak status display | Replaced with `!!` text | Light Phone monochrome ethos |
| Emulator set to 1080×2400 resolution | Fixed to 1080×1240 | README spec |

---

## Part 8: File Guide

### Amtrak Tool (9 source files)

```
tool/src/main/kotlin/com/thelightphone/amtrak/
├── ToolEntryPoint.kt          # SDK entry point (~30 lines)
├── AmtrakApi.kt               # HTTP client for Trains.gov API (~50 lines)
├── AmtrakModels.kt            # Data classes for API responses (~120 lines)
├── AmtrakRepository.kt        # Singleton for navigation state (~30 lines)
├── AmtrakViewModel.kt         # Business logic + state (440 lines)
├── HomeScreen.kt              # Train/station list + bottom bar (252 lines)
├── TrainDetailScreen.kt       # Train stop schedule view (~180 lines)
├── StationDetailScreen.kt     # Station arrival board (~200 lines)
└── SearchScreen.kt            # Local train/station search (~300 lines)
```

### CDTA Tool (8 source files)

```
examples/cdta/src/main/kotlin/com/thelightphone/cdta/
├── ToolEntryPoint.kt          # SDK entry point (~25 lines)
├── CdtaModels.kt              # Data classes + formatting helpers (~180 lines)
├── CdtaPreferences.kt         # DataStore key definitions (~15 lines)
├── CdtaViewModel.kt           # Business logic + GTFS loading (310 lines)
├── GtfsRepository.kt          # GTFS data loading from assets (~300 lines)
├── HomeScreen.kt              # Routes list + search + stop detail (776 lines)
└── gtfs/
    ├── GtfsModels.kt          # Raw GTFS entity classes (~80 lines)
    └── GtfsParser.kt          # CSV → models parser (~150 lines)
```

### Wikipedia Tool (7 source files)

```
examples/wikipedia/src/main/kotlin/com/thelightphone/wikipedia/
├── WikipediaEntryPoint.kt      # SDK entry point (~25 lines)
├── WikipediaApi.kt             # Wikipedia REST API client (~120 lines)
├── WikipediaModels.kt          # Data classes (~100 lines)
├── WikipediaSearchResults.kt   # Search result row UI (~130 lines)
├── WikipediaViewModel.kt       # State machine + API orchestration (251 lines)
├── WikipediaHomeScreen.kt      # Home + search input + results list (236 lines)
└── WikipediaArticleScreen.kt   # Article rendering + section parsing (~150 lines, was 297 lines before forward arrow removal)
```

---

## Part 9: Verification Results

### Build Status (Mac, JDK 21)

| Tool | Compile | Assemble Debug |
|---|---|---|
| Amtrak | ✅ BUILD SUCCESSFUL | ✅ |
| CDTA | ✅ BUILD SUCCESSFUL | ✅ |
| Wikipedia | ✅ BUILD SUCCESSFUL | ✅ |

### Test Status

| Tool | Unit Tests | Test Result |
|---|---|---|
| Amtrak | ❌ No tests | — |
| CDTA | ✅ `CdtaModelsTest`, `GtfsRepositoryContractTest` | ✅ PASS |
| Wikipedia | ❌ No tests | — |

### Emulator Testing (1080×1240)

| Tool | Launched | Home Screen | Navigation |
|---|---|---|---|
| Amtrak | ✅ | ✅ Shows train list | ✅ Tap + D-pad navigation |
| CDTA | ✅ | ✅ Shows route list | ✅ Tap + D-pad navigation |
| Wikipedia | ✅ | ✅ Shows search menu | ✅ Menu items work |

### Network Notes

- The LP3 physical device has a SIM card and works without issues
- The emulator **does not** have network access (WiFi/ethernet not bridged to host)
- API calls fail on emulator: "Wikipedia requires a network connection. Please insert a data sim or connect to wi-fi."
- This is expected — network testing must be done on physical LP3 hardware

---

## Part 10: Recommendations for GitHub Publication

### What to Include

1. **README.md** — Project overview, build instructions, architecture
2. **LICENSE** — MIT or Apache 2.0 (matches Light SDK)
3. **lighttool.toml** — Tool metadata (already present in each tool)
4. **`.gitignore`** — Ignore build outputs, `.gradle/`, `*.apk`

### What to Redact

1. **No personal info** — verified no PII in source code
2. **No API keys** — all three tools use public APIs (no authentication)
3. **No Tailscale IPs** — verified none in source
4. **Emulator config** — `serverPackage = "com.thelightphone.sdk.emulator"` should be commented with instructions to switch to `com.lightos` for real LP3

### Code Quality Checklist

- [x] All `@Composable` functions are private when not part of the API surface
- [x] ViewModel uses `viewModelScope` (not `GlobalScope` or `lifecycleScope`)
- [x] API errors have human-readable messages (not raw exception text)
- [x] No `System.out.println` or debug logging
- [x] No hardcoded strings in UI — all in resource-less top-level constants where possible
- [x] State flows are `.asStateFlow()` (not exposed as MutableStateFlow)
- [ ] Missing: unit tests for Wikipedia and Amtrak ViewModels
- [ ] Missing: README.md for each tool

This document serves as both a reference for how the tools work and a record of the SDK conventions they follow.
