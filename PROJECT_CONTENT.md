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

### 3. Πρόσβαση στις Ρυθμίσεις: menu item μέσα στο Settings του YouTube
- Αρχικά δοκιμάστηκε floating cog button πάνω στη σελίδα (είτε "ενσωματωμένο" δίπλα στο
  λογότυπο, είτε floating fallback) — αλλά επικάλυπτε/ενοχλούσε το UI του YouTube (π.χ.
  πάνω στο δικό του search/menu εικονίδιο), οπότε αφαιρέθηκε εντελώς.
- Αντ' αυτού, το injected script (`ensureSettingsMenuItem` στο `FeedScript.kt`) προσθέτει
  μια γραμμή **"MyTube"** μέσα στη δική της σελίδα Ρυθμίσεων του YouTube
  (`m.youtube.com/select_site`, προσβάσιμη μέσω avatar → Settings), δίπλα σε γραμμές όπως
  "General", "History & privacy" κ.λπ. Στοχεύει το πραγματικό container
  `ytm-setting-category-collection-renderer` και προσθέτει ένα `ytm-setting-generic-category`
  με το ίδιο styling/classes ώστε να ταιριάζει οπτικά — επιβεβαιωμένο πάνω σε πραγματικό
  captured DOM αυτής της σελίδας.
- Το κλικ καλεί `window.MyTubeNative.openSettings()` → JavascriptInterface
  (`WebAppInterface.kt`) → ανοίγει native `SettingsActivity`.
- Καμία native επικάλυψη (button) πάνω στο WebView πλέον — πιο "καθαρό" UI, αλλά η
  πρόσβαση περνάει πάντα μέσα από το μενού Ρυθμίσεων του ίδιου του YouTube.

### 4. Update μηχανισμός (GitHub Releases) — ίδιο pattern με `thrylos-news` / `no-algo-fb`
Αντί για semver tags, χρησιμοποιείται το ίδιο μοτίβο με τα άλλα δύο projects του χρήστη:
ένα **σταθερό tag `latest`** που το CI αντικαθιστά σε κάθε push, και ένα versionCode που
προέρχεται απευθείας από τον αριθμό build του CI.

- **`versionCode`** (στο `app/build.gradle.kts`) = `System.getenv("GITHUB_RUN_NUMBER")`
  (fallback `1` για τοπικά builds). Κάθε CI run παίρνει αυξανόμενο, μοναδικό αριθμό.
- **Committed debug keystore** (`keystore/debug.keystore`, στο repo — δεν είναι μυστικό,
  είναι σκόπιμα δημόσιο): χωρίς αυτό, κάθε CI run θα υπέγραφε το APK με νέο, τυχαίο
  debug key (αφού το CI runner δεν έχει `~/.android/debug.keystore` από πριν), και το
  Android θα αρνιόταν να κάνει "update" πάνω από την προηγούμενη εγκατάσταση (θα ζητούσε
  uninstall πρώτα, αφού θα έβλεπε διαφορετική υπογραφή). Το `debug` build type δείχνει σε
  αυτό το keystore ρητά.
- **`UpdateChecker.kt`** (`update/` package): καλεί
  `GET https://api.github.com/repos/chrisgrou/mytube/releases/tags/latest`, διαβάζει το
  πρώτο asset (το APK, ονομασμένο `mytube-<versionCode>.apk`), εξάγει το version code από
  το **όνομα του αρχείου** (regex), και το συγκρίνει με το `BuildConfig.VERSION_CODE`.
- **`UpdateInstaller.kt`**: κατεβάζει το APK σε `cacheDir/updates/` (OkHttp, με progress
  callback), το εκθέτει μέσω `FileProvider` (χρειάζεται ξανά `<provider>` στο Manifest +
  `res/xml/file_paths.xml`), και ανοίγει installer intent.
- Χρειάζεται `REQUEST_INSTALL_PACKAGES` permission· αν ο χρήστης δεν έχει επιτρέψει
  εγκατάσταση από άγνωστες πηγές, τον στέλνουμε στο `ACTION_MANAGE_UNKNOWN_APP_SOURCES`.
- **Έλεγχος μόνο χειροκίνητα** (κουμπί "Έλεγχος για ενημερώσεις" στις Ρυθμίσεις) — καμία
  background/startup κλήση, όπως αποφασίστηκε.
- **Ιστορικό**: το *τοπικό ιστορικό εγκαταστάσεων* (`Prefs.historyEntries()`) καταγράφεται
  αυτόματα από το `MyTubeApp.onCreate()` κάθε φορά που αλλάζει το version code της
  εγκατεστημένης εφαρμογής. Τα release notes του πιο πρόσφατου build (changelog από τα git
  commits) εμφανίζονται inline μετά από κάθε "Έλεγχος για ενημερώσεις".
- **CI**: ένα και μοναδικό `.github/workflows/build.yml`, ίδιο σχήμα με τα άλλα projects:
  - Τρέχει σε **κάθε push** σε οποιοδήποτε branch, σε κάθε pull request, και έχει και
    "Run workflow" κουμπί (manual trigger) στο Actions tab.
  - Χτίζει `assembleDebug` και το ανεβάζει ως **build artifact** (`mytube-debug`) — αυτό
    κατεβάζεις απευθείας από ένα run στο Actions tab, χωρίς να χρειάζεται release/tag.
  - Σε κάθε **push** (όχι σε PR), διαγράφει και ξαναδημιουργεί το prerelease `latest`
    με το APK attached και σημειώσεις από το git log του push — αυτό είναι που "βλέπει"
    ο in-app updater.

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
  MainActivity.kt          - WebView + edge-to-edge + injection hook
  SettingsActivity.kt      - Ρυθμίσεις (filter toggle, updates, ιστορικό)
  WebAppInterface.kt       - JavascriptInterface (window.MyTubeNative)
  FeedScript.kt            - injected JS (filtering + Settings menu item)
  Prefs.kt                 - SharedPreferences wrapper
  MyTubeApp.kt             - Application, καταγραφή τοπικού ιστορικού version
  update/UpdateChecker.kt  - διαβάζει το release "latest" από το GitHub API
  update/UpdateInstaller.kt - download (OkHttp) + FileProvider + install intent
keystore/debug.keystore   - committed debug key (βλ. ενότητα 4 παραπάνω)
.github/workflows/build.yml - CI: build + artifact σε κάθε push, "latest" release σε push
```

## Επόμενα βήματα / ιδέες (δεν έχουν υλοποιηθεί ακόμα)
- Πιθανή προσθήκη badge/notification όταν υπάρχει νέα έκδοση (αν ποτέ αλλάξει η απόφαση
  από "μόνο χειροκίνητα" σε αυτόματο έλεγχο).
- Πραγματικό release keystore αν χρειαστεί ποτέ πιο "σοβαρή" διανομή.

## Debugging notes (χρήσιμο για το μέλλον)
Αρκετά bugs μέχρι τώρα λύθηκαν επειδή ο χρήστης έστειλε **.mht snapshots** πραγματικών
σελίδων του m.youtube.com (File → Save page as, ή share από τον browser). Αυτά περιέχουν
το πλήρες HTML που βλέπει πραγματικά η συσκευή — πολύ πιο αξιόπιστο από το να μαντεύουμε
selectors. Αν κάτι σχετικό με DOM/selectors "σπάσει" ξανά, το πρώτο πράγμα να ζητηθεί είναι
ένα τέτοιο .mht από την οθόνη που έχει πρόβλημα.
