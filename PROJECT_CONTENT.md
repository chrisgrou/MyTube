# MyTube — Android wrapper για m.youtube.com

## Τι είναι
Native Android app (Kotlin) που είναι ένας λεπτός wrapper γύρω από `https://m.youtube.com`
μέσα σε ένα `WebView`. Δεν υπάρχει δικό μας UI γύρω από τη σελίδα (χωρίς toolbar/action bar,
edge-to-edge) — φαίνεται μόνο η σελίδα του YouTube, όπως ζητήθηκε.

Repo: `chrisgrou/mytube` (GitHub). Package/applicationId: `com.chrisgrou.mytube`.

## Γιατί υπάρχει (context)
Ο χρήστης θέλει μια εφαρμογή που:
1. Κάνει login κανονικά στο YouTube (μέσω WebView cookies — persistent storage).
2. Φιλτράρει το feed: κρύβει τα **community posts με εικόνες** (π.χ. καρουζέλ εικόνων
   από κανάλια), **χωρίς** να πειράζει τα κανονικά προτεινόμενα βίντεο ή τα Shorts.
   Ο χρήστης έχει κάνει κάτι αντίστοιχο στο project `no-algo-fb` (απόκρυψη posts στο
   Facebook feed) — ίδια λογική/φιλοσοφία εδώ.
3. Έχει ένα cog (⚙) εικονίδιο "ενσωματωμένο" στο header της σελίδας, δίπλα στο λογότυπο
   του YouTube, που ανοίγει native οθόνη ρυθμίσεων της εφαρμογής.
4. Υποστηρίζει ενημερώσεις (updates) μέσω GitHub Releases του ίδιου repo, με ιστορικό,
   παρόμοιο με το πρότυπο του project `thrylos-news`.

## Αρχιτεκτονική / decisions

### 1. WebView wrapper
- `MainActivity` έχει ένα `FrameLayout` με `WebView` + `ProgressBar` (πάνω, λεπτή γραμμή
  progress κατά το loading — το μόνο "δικό μας" ορατό στοιχείο).
- Theme: `Theme.MyTube.Fullscreen` (χωρίς action bar/title, edge-to-edge, μαύρο background
  ίδιο με το dark mode του YouTube ώστε να μην φαίνεται "seam" κατά το πρώτο load).
- Cookies: `CookieManager` με `setAcceptCookie(true)` + `setAcceptThirdPartyCookies(true)`
  ώστε το login (Google account) να δουλεύει και να παραμένει μεταξύ εκκινήσεων.
- User-Agent: ορίζεται σε Chrome mobile UA string (όχι το default WebView UA) γιατί το
  YouTube μπορεί να συμπεριφέρεται διαφορετικά/να εμφανίζει προειδοποιήσεις με άγνωστο UA.
- `onShowCustomView`/`onHideCustomView` στο `WebChromeClient` για fullscreen video playback
  (χρειάζεται ώστε το play σε landscape/fullscreen να δουλεύει σωστά μέσα στο WebView).
- Config changes (rotation) δηλώνονται στο Manifest (`android:configChanges`) ώστε η
  Activity να ΜΗΝ ξαναφτιάχνεται (άρα όχι reload της σελίδας) σε περιστροφή.

### 2. Φιλτράρισμα community image posts
- Υλοποιείται 100% με **injected JavaScript** (`FeedScript.kt`), όχι server-side, αφού
  δεν ελέγχουμε το YouTube backend.
- Selectors στοχεύουν tags/classes που περιέχουν "post-renderer" (community posts), π.χ.
  `ytm-backstage-post-renderer`. Αυτά είναι **διαφορετικά** tags από τα video items
  (`ytm-rich-item-renderer` > video renderer) και τα Shorts (`ytm-reel-shelf-renderer`),
  οπότε ο φίλτρο δεν πειράζει ποτέ video/Shorts — μόνο ό,τι ταιριάζει με post-renderer.
- Toggle on/off από τις Ρυθμίσεις, αποθηκεύεται σε `SharedPreferences` (`Prefs.kt`).
  Default: **ενεργό** (κρύβει τα image posts).
- Λόγω του ότι το YouTube αλλάζει το DOM/markup του χωρίς προειδοποίηση, οι selectors
  είναι best-effort. Αν κάποια στιγμή "σταματήσει" να δουλεύει το φίλτρο, το πιθανότερο
  είναι να χρειάζεται update των selectors στο `FeedScript.kt` (function `applyFilter`).
- Η εφαρμογή τρέχει `MutationObserver` + `setInterval` (1.5s) + listener στο custom event
  `yt-navigate-finish` γιατί το m.youtube.com είναι SPA (δεν γίνεται πλήρες page load σε
  κάθε πλοήγηση μέσα στην εφαρμογή).

### 3. Cog icon στο header
- Το ίδιο injected script (`ensureCogButton`) προσπαθεί να βρει το λογότυπο του YouTube
  στο mobile header και να βάλει το κουμπί ⚙ αμέσως μετά, ώστε να φαίνεται "native".
- **Fallback**: αν δεν βρεθεί το markup του header (π.χ. άλλαξε το YouTube), το κουμπί
  εμφανίζεται σαν floating button πάνω-αριστερά (ίδια οπτική θέση), ώστε να μην χαθεί ποτέ
  η πρόσβαση στις Ρυθμίσεις.
- Το κλικ καλεί `window.MyTubeNative.openSettings()` → JavascriptInterface
  (`WebAppInterface.kt`) → ανοίγει native `SettingsActivity`.
- ⚠️ Δεν έχει γίνει live test πάνω στο πραγματικό DOM του m.youtube.com (δεν υπάρχει
  πρόσβαση internet προς youtube.com μέσα στο περιβάλλον όπου γράφτηκε ο κώδικας). Θέλει
  οπτικό έλεγχο σε πραγματική συσκευή/emulator και πιθανή μικρορύθμιση των selectors/θέσης.

### 4. Update μηχανισμός (GitHub Releases)
- `UpdateManager.kt`: καλεί `GET https://api.github.com/repos/chrisgrou/mytube/releases`
  (public API, χωρίς token), παίρνει τη λίστα releases, συγκρίνει το `tag_name` του πιο
  πρόσφατου με το `versionName` της εφαρμογής (dotted numeric comparison).
- Αν υπάρχει release με asset `.apk`, το κουμπί "Λήψη & εγκατάσταση" το κατεβάζει μέσω
  `DownloadManager` (destination: app-specific external files dir — δεν χρειάζεται storage
  permission) και μετά ανοίγει installer intent (`ACTION_VIEW` με το APK mime type).
- Χρειάζεται `REQUEST_INSTALL_PACKAGES` permission· αν ο χρήστης δεν έχει επιτρέψει
  εγκατάσταση από άγνωστες πηγές για το app, τον στέλνουμε στο σχετικό system settings
  screen (`ACTION_MANAGE_UNKNOWN_APP_SOURCES`).
- **Έλεγχος μόνο χειροκίνητα** (κουμπί στις Ρυθμίσεις) — καμία background/startup κλήση,
  όπως αποφασίστηκε.
- **Ιστορικό**: δύο διαφορετικά "ιστορικά" εμφανίζονται στις Ρυθμίσεις:
  - *Τοπικό ιστορικό εγκαταστάσεων* (`Prefs.historyEntries()`): καταγράφεται αυτόματα από
    το `MyTubeApp.onCreate()` κάθε φορά που αλλάζει το version code της εγκατεστημένης
    εφαρμογής (δηλαδή μετά από κάθε πραγματική ενημέρωση σε αυτή τη συσκευή).
  - *Releases από GitHub* (remote λίστα με release notes) — φαίνεται μετά από κάθε
    "Έλεγχος για ενημερώσεις".
- **CI**: `.github/workflows/release.yml` χτίζει αυτόματα APK (`assembleRelease`) και
  δημιουργεί GitHub Release με το APK attached κάθε φορά που γίνεται push ένα tag
  `vX.Y.Z`. Αυτό είναι το "τροφοδοτικό" του update μηχανισμού — χωρίς αυτό δεν θα υπάρχουν
  releases/APK να κατέβει η εφαρμογή.
  - Το release build type υπογράφεται με το **debug signing config** (όχι δικό μας
    keystore) ώστε το CI να μπορεί να παράγει εγκαταστάσιμο APK χωρίς μυστικά (secrets).
    Αυτό είναι μια χαλαρή λύση, βολική για προσωπική χρήση/sideloading· **δεν είναι**
    κατάλληλη λύση αν ποτέ μπει σε Play Store (θα χρειαστεί πραγματικό release keystore).

## Περιορισμός στο περιβάλλον όπου γράφτηκε ο κώδικας
Το sandbox αυτής της συνεδρίας **δεν έχει πρόσβαση σε `dl.google.com`** (Google's Maven
repository), το proxy το μπλοκάρει σκόπιμα (403). Το Android Gradle Plugin (AGP) βρίσκεται
ΜΟΝΟ εκεί, όχι στο Maven Central — άρα **δεν μπόρεσα να κάνω πραγματικό Gradle build /
compile εδώ**. Ο κώδικας ελέγχθηκε προσεκτικά "με το μάτι" (imports, ids, τύποι),
αλλά το πρώτο πραγματικό build πρέπει να γίνει είτε:
- τοπικά σε Android Studio (έχει πλήρη πρόσβαση internet), ή
- μέσω του GitHub Actions workflow (`release.yml`) που φτιάξαμε, πατώντας ένα tag.

Αν προκύψει compile error στο πρώτο πραγματικό build, είναι πιθανότερο να είναι κάτι
μικρό (π.χ. έκδοση εξάρτησης) παρά λάθος λογικής.

## Δομή project
```
app/src/main/java/com/chrisgrou/mytube/
  MainActivity.kt        - WebView + edge-to-edge + injection hook
  SettingsActivity.kt     - Ρυθμίσεις (filter toggle, updates, ιστορικό)
  WebAppInterface.kt      - JavascriptInterface (window.MyTubeNative)
  FeedScript.kt           - injected JS (filtering + cog button)
  UpdateManager.kt        - GitHub Releases API + download
  Prefs.kt                - SharedPreferences wrapper
  MyTubeApp.kt            - Application, καταγραφή τοπικού ιστορικού version
.github/workflows/release.yml - CI build + GitHub Release στο tag push
```

## Επόμενα βήματα / ιδέες (δεν έχουν υλοποιηθεί ακόμα)
- Live testing σε πραγματική συσκευή/emulator για να επιβεβαιωθούν/διορθωθούν οι
  selectors του header (cog button) και του feed filter.
- Πιθανή προσθήκη badge/notification όταν υπάρχει νέα έκδοση (αν ποτέ αλλάξει η απόφαση
  από "μόνο χειροκίνητα" σε αυτόματο έλεγχο).
- Πραγματικό release keystore αν χρειαστεί ποτέ πιο "σοβαρή" διανομή.
