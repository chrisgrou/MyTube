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
5. Αποκλείει διαφημίσεις, όπως κάνει ο Brave (βλ. ενότητα 5 παρακάτω).
6. Μπορεί να γίνει default handler για YouTube links (βλ. ενότητα 7 παρακάτω).
7. Επιλέγει προτιμώμενη ποιότητα βίντεο για κάθε βίντεο αυτόματα (βλ. ενότητα 8 παρακάτω).
8. Συνεχίζει να παίζει ήχο όταν κλειδώνεις την οθόνη ή αλλάζεις εφαρμογή (βλ. ενότητα 9
   παρακάτω). Media notification controls: επόμενο βήμα, όχι ακόμα.

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

### 3. Πρόσβαση στις Ρυθμίσεις: native κουμπί, μέση-δεξιά της οθόνης
Ιστορικό αποτυχιών, με τη σειρά:
1. Cog "ενσωματωμένο" δίπλα στο λογότυπο του YouTube header (injected JS) — δεν
   βρισκόταν ποτέ το header markup.
2. Floating κουμπί πάνω-δεξιά (native ImageButton) — δούλευε, αλλά ο χρήστης το βρήκε
   ενοχλητικό: επικάλυπτε τα δικά του εικονίδια (search/avatar) στο header.
3. Injected "MyTube" row μέσα στη σελίδα Ρυθμίσεων του YouTube (`ytm-settings`,
   προσβάσιμη μέσω avatar → Settings) — δοκιμάστηκε **τρεις** φορές με διαφορετική
   τεχνική κάθε φορά (μέσα σε custom element `ytm-setting-generic-category`· μετά σαν
   απλό `<div>` sibling του `<ytm-settings>`· μετά `position:fixed` κολλημένο στο
   `document.body` με μέγιστο z-index) — και οι τρεις επιβεβαιώθηκαν να δουλεύουν πάνω
   σε captured DOM (.mht) απ' τον χρήστη, αλλά **καμία δεν εμφανίστηκε ποτέ στην
   πραγματική συσκευή**. Πιθανό αίτιο: το πραγματικό DOM στη συσκευή διαφέρει από αυτό
   που δείχνει ένα desktop-captured .mht (πιθανόν διαφορετικό A/B variant ή markup
   έκδοση), αλλά δεν υπήρχε τρόπος να το επιβεβαιώσουμε χωρίς live devtools access στη
   συσκευή. Ο κώδικας αφαιρέθηκε εντελώς (ήταν dead code).

**Τελική λύση**: native `ImageButton` (`buttonSettings` στο `activity_main.xml`), μικρό
(36dp) και ημιδιάφανο (`alpha=0.55`), τοποθετημένο `center_vertical|end` — στη μέση του
δεξιού άκρου της οθόνης, όχι πάνω/κάτω όπου βρίσκεται μόνιμο UI του YouTube (header,
bottom tab bar). Αυτή η θέση δεν έχει ποτέ ανταγωνιστικό στοιχείο του YouTube, οπότε δεν
"ενοχλεί" όπως η πρώτη προσπάθεια, αλλά είναι 100% αξιόπιστο επειδή δεν εξαρτάται καθόλου
από το DOM/markup της σελίδας — μόνο native Android view πάνω από το WebView.

**Εμφανίζεται μόνο στη σελίδα Ρυθμίσεων** (`m.youtube.com/select_site`, επιβεβαιωμένο URL
από captured .mht), όχι παντού — γίνεται καθαρά native, χωρίς JS: το `WebViewClient`
παρακολουθεί το URL του WebView (`onPageFinished` για πλήρες load, `doUpdateVisitedHistory`
για SPA pushState navigation — το YouTube πηγαίνει στο Settings κυρίως έτσι, όχι με πλήρες
reload) και δείχνει/κρύβει το κουμπί ανάλογα (`updateSettingsButtonVisibility` στο
`MainActivity.kt`). Καθόλου εξάρτηση από DOM αυτή τη φορά — μόνο το URL, που είναι σταθερό
και γνωστό.

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

### 5. Αποκλεισμός διαφημίσεων (toggle στις Ρυθμίσεις, default ON)
Τρία επίπεδα, γιατί το YouTube σερβίρει τις διαφημίσεις με τρεις διαφορετικούς τρόπους:

1. **Network level** (`AdBlocker.kt` + `shouldInterceptRequest`): μπλοκάρει requests προς
   ad/tracker domains (doubleclick, googlesyndication, googleadservices, adservice.google.*,
   google-analytics, 2mdn κ.λπ.) και προς συγκεκριμένα ad paths του youtube.com
   (`/pagead/`, `/api/stats/ads`, `/ptracking`). Αυτή είναι η ίδια βασική ιδέα με τον Brave.
   - Η λίστα είναι **σκόπιμα συντηρητική**: `*.googlevideo.com` (τα ίδια τα streams),
     `i.ytimg.com`/`yt3.ggpht.com` (thumbnails) και όλα τα `accounts.google.com` (login)
     ΔΕΝ μπλοκάρονται ποτέ — αν μπουν στη λίστα, σπάει η αναπαραγωγή ή το login.
   - Το `shouldInterceptRequest` καλείται σε background thread για **κάθε** request, οπότε
     το preference διαβάζεται μία φορά σε ένα `@Volatile` field (refresh στο `onResume`),
     ποτέ SharedPreferences μέσα στο hot path.
2. **In-stream ads (pre-roll/mid-roll)** — δεν μπλοκάρονται με URL: το media τους έρχεται
   από τα ίδια googlevideo.com hosts με το κανονικό βίντεο, και οι οδηγίες αναπαραγωγής
   είναι μέσα στο κανονικό `/youtubei/v1/player` response. Οπότε στο `FeedScript.kt`
   αφαιρούνται τα ad fields (`adPlacements`, `playerAds`, `adSlots`,
   `adBreakHeartbeatParams`) **πριν** τα διαβάσει ο κώδικας του YouTube, με τρία patches:
   `JSON.parse` (XHR/inline), `Response.prototype.json` (fetch — είναι native και ΔΕΝ περνά
   από το JSON.parse), και ένα setter στο `window.ytInitialPlayerResponse` (το πρώτο
   response ανατίθεται ως object literal, δεν το πιάνει κανένα από τα άλλα δύο).
   - **Δουλεύει μόνο επειδή** το script τρέχει σε document-start
     (`WebViewCompat.addDocumentStartJavaScript`), πριν από κάθε script της σελίδας.
   - Fallback αν παρ' όλα αυτά παίξει διαφήμιση: auto-skip / fast-forward, αυστηρά
     gated σε ad markers (`.ad-showing` κ.λπ.) ώστε να μη γίνει ποτέ scrub κανονικό βίντεο.
3. **Display ads στο feed**: κρύβονται με γνωστά ad renderer tags ΚΑΙ με heuristic που ψάχνει
   το badge "Sponsored"/"Διαφήμιση" μέσα σε feed item και κρύβει όλο το item. Το heuristic
   υπάρχει επειδή τα ad renderer tags αλλάζουν συχνά — και επειδή σε κανένα από τα .mht
   snapshots που είχαμε δεν έτυχε να υπάρχει διαφήμιση, άρα δεν επιβεβαιώθηκαν με πραγματικό
   DOM (σε αντίθεση με τα community posts). Κάθε item σκανάρεται μία φορά
   (`data-mytube-adscan`) για να μην κοστίζει σε μεγάλο feed.

⚠️ Το YouTube δουλεύει ενεργά ενάντια στα ad blockers. Αν κάποια στιγμή εμφανιστούν ξανά
διαφημίσεις ή μήνυμα τύπου "ad blocker detected", το πιθανότερο είναι ότι άλλαξαν τα ονόματα
των πεδίων/των selectors — ζήτα ένα .mht snapshot και ενημέρωσε το `FeedScript.kt`.

### 6. Fullscreen playback
- `onShowCustomView`/`onHideCustomView`: το player view μπαίνει σε ένα `PlayerGestureLayout`
  (custom `FrameLayout`) πάνω στο decor view, κρύβονται τα system bars (immersive, με
  `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`) και η οθόνη γυρίζει ανάλογα με το **σχήμα του
  βίντεο**: `SENSOR_LANDSCAPE` για κανονικά, `SENSOR_PORTRAIT` για κάθετα/Shorts (αλλιώς
  ένα κάθετο βίντεο θα γύριζε άσκοπα στο πλάι). Στην έξοδο επανέρχονται όλα.
  - Το σχήμα το αναφέρει το injected script (`loadedmetadata`/`playing`/`resize`) και
    εφαρμόζεται αμέσως από την τελευταία τιμή ώστε να μη φαίνεται flip· αμέσως μετά γίνεται
    επιβεβαίωση με `evaluateJavascript` (`window.__mytubeVideoIsPortrait`) γιατί η
    cached τιμή μπορεί να προήλθε από άλλο video της σελίδας (π.χ. autoplay preview στο
    feed). Η επιλογή προτιμά βίντεο που παίζει, μετά το μεγαλύτερο.
  - Δουλεύει χωρίς recreation της Activity επειδή το Manifest έχει ήδη
    `configChanges="orientation|screenSize|..."`.
- **Gestures**: το `PlayerGestureLayout` κάνει intercept **μόνο** κάθετα drags πέρα από το
  touch slop (και μόνο όταν το |dy| υπερτερεί σαφώς του |dx|) — έτσι taps (play/pause,
  εμφάνιση controls) και οριζόντια drags (seek) φτάνουν κανονικά στον player από κάτω.
  Αριστερό μισό = φωτεινότητα (`window.attributes.screenBrightness`), δεξί = ένταση
  (`AudioManager`, με flag 0 ώστε να μη βγαίνει το system volume UI — δείχνουμε δικό μας).
  - Η ένταση συσσωρεύεται ως float μέσα στο gesture, γιατί το stream volume είναι πολύ
    χοντρικό (0..15) και αλλιώς μικρά drags στρογγυλοποιούνται στο τίποτα.
  - Η φωτεινότητα επιστρέφει στο system default (`BRIGHTNESS_OVERRIDE_NONE`) στην έξοδο.
  - **Bug (v1.7.2)**: το `onTouchEvent` επέστρεφε `true` χωρίς όρους για κάθε `ACTION_MOVE`
    (το `if (dragging)` καθόριζε μόνο αν θα καλούνταν το `onVerticalDrag`, όχι το `return`).
    Αν το εσωτερικό video view δεν κατανάλωνε ένα tap-event μόνο του, το event έπεφτε πίσω
    στο `onTouchEvent` του γονέα και "καταπινόταν" εκεί χωρίς λόγο — αυτό χαλούσε το timing
    ενός double-tap (π.χ. double tap για seek +10": ο χρήστης το ανέφερε ως το βίντεο να
    μπαίνει σε παύση αντί να συνεχίσει). Διορθώθηκε ώστε το layout να μην καταναλώνει τίποτα
    εκτός αν υπάρχει ήδη ενεργό κάθετο drag (`if (!dragging) return false` στην αρχή).
  - **Bug (v1.7.3, ξεχωριστό)**: μετά το παραπάνω fix, ο χρήστης ανέφερε ότι seek +10" ΚΑΙ
    χειροκίνητο σέρνιμο της μπάρας σε fullscreen δούλευαν (το seek γινόταν), αλλά το βίντεο
    έμενε σε παύση. Αιτία: το `enterFullscreen` έκανε `webView.visibility = View.GONE`. Το
    Chromium συσχετίζει την ορατότητα του View με το αν θεωρεί τη σελίδα visible/foreground
    και throttle-άρει timers/JS callbacks όταν όχι — άρα το δικό του JS logic του YouTube
    που πρέπει να τρέξει μετά το `seeked` event για να καλέσει ξανά `play()` καθυστερούσε ή
    δεν έτρεχε καθόλου. Λύση: το WebView μένει `VISIBLE` σε όλη τη διάρκεια του fullscreen —
    δεν χρειαζόταν να κρύβεται έτσι κι αλλιώς, αφού το αδιαφανές fullscreen container το
    καλύπτει πλήρως οπτικά.
  - ⚠️ **Ο χρήστης δοκίμασε αυτό το build και το πρόβλημα παρέμεινε** ("πάλι κάνει παύση") —
    άρα η θεωρία περί throttling λόγω `View.GONE` ήταν λάθος ή τουλάχιστον ανεπαρκής. Αντί
    για τρίτη μαντεψιά, προστέθηκε **προσωρινό diagnostic logging** γύρω από τα video events
    (`seeking`/`seeked`/`pause`/`play`/`playing`/`waiting`/`stalled`/`canplay`/`suspend`) στο
    `FeedScript.kt`, με timestamp + `paused`/`currentTime`/`readyState`/`networkState` του
    video element — μέσω `console.log('MyTube[seekdebug] ...')`, ήδη προωθείται σε Logcat με
    tag `MyTubeWebView` από το υπάρχον `onConsoleMessage`. **Επόμενο βήμα**: ο χρήστης να
    αναπαράγει το πρόβλημα ενώ κάποιος βλέπει/καταγράφει το logcat (π.χ. `adb logcat -s
    MyTubeWebView` από υπολογιστή, ή κάποια logcat-viewer εφαρμογή στο ίδιο το κινητό), ώστε
    να φανεί ποιο ακριβώς event λείπει/έρχεται λάθος πριν αποφασιστεί το πραγματικό fix. Να
    αφαιρεθεί το logging μόλις βρεθεί η αιτία.
  - Ο χρήστης δεν έχει τρόπο να δει Logcat στη συσκευή του — δεν υπάρχει adb/υπολογιστής
    διαθέσιμος. Λύση (v1.7.5): οι ίδιες γραμμές φτάνουν πλέον και σε ένα in-memory ring
    buffer στο native (`DebugLog.kt`, τελευταίες ~300 γραμμές), μέσω νέας JS bridge method
    `MyTubeNative.logDebug(message)`. Το `SettingsActivity` έχει μια προσωρινή ενότητα
    "Debug log" με κουμπί που αντιγράφει το buffer στο clipboard — ο χρήστης το κάνει paste
    κατευθείαν στη συνομιλία. Όλο αυτό (DebugLog.kt, το bridge method, το section στο
    settings layout) είναι προσωρινό και θα αφαιρεθεί μαζί με το υπόλοιπο diagnostic
    logging μόλις βρεθεί η πραγματική αιτία του seek-pause bug.
  - **Το debug log αποκάλυψε τα πραγματικά δεδομένα (v1.7.6)**: ο χρήστης ανέφερε ότι το
    πρόβλημα συμβαίνει **και εκτός fullscreen** — άρα καμία σχέση με `PlayerGestureLayout`
    ή με `webView.visibility` (και τα δύο μόνο σε fullscreen τρέχουν). Το log έδειξε το
    πραγματικό μοτίβο: `seeking` → `pause` (φυσιολογικό, άμεσο) → `seeked` με
    `readyState=4` (ήδη πλήρως buffered) → αλλά το `play` event από το ίδιο το YouTube
    έρχεται **σταθερά ~1.5-2.2 δευτερόλεπτα αργότερα**, όχι αμέσως. Αυτή η καθυστέρηση
    (πιθανό δικό του ad-eligibility/analytics check στο YouTube, όχι κάτι δικό μας) είναι
    αυτό που ο χρήστης αντιλαμβάνεται ως "μπαίνει σε παύση". Fix: το injected script
    θυμάται αν το βίντεο έπαιζε πριν το seek (`mtShouldBePlaying`, ενημερώνεται μόνο από
    πραγματικό `playing`/`ended`, όχι από το ενδιάμεσο `pause` του ίδιου του seek) και, αν
    μετά το `seeked` παραμένει σε παύση πάνω από 600ms, καλεί το ίδιο `video.play()`. Δεν
    διορθώνει την υποκείμενη αιτία του YouTube — απλά δεν την περιμένει.
- **Keep screen on**: ένα WebView δεν κρατάει την οθόνη ξύπνια όπως ο browser, οπότε η οθόνη
  σκοτείνιαζε στη μέση του βίντεο. Το injected script ακούει `play`/`playing`/`pause`/`ended`
  **σε capture phase** (τα media events δεν κάνουν bubble) και το native βάζει/βγάζει
  `FLAG_KEEP_SCREEN_ON`. Καλύπτει και inline και fullscreen αναπαραγωγή.
- Το back button βγάζει πρώτα από fullscreen (`leaveFullscreenIfActive`) πριν πάει σε
  `webView.goBack()` ή έξοδο από την εφαρμογή.

### 7. Default handler για YouTube links
- Intent-filter με `ACTION_VIEW` + `BROWSABLE` για τα hosts youtube.com, www.youtube.com,
  m.youtube.com, music.youtube.com, youtu.be (σχήμα ίδιο με το `no-algo-fb` για
  facebook.com links).
- **Χωρίς `android:autoVerify`**: αυτό απαιτεί ένα `assetlinks.json` αρχείο hosted στο
  `https://youtube.com/.well-known/assetlinks.json` που μόνο η ίδια η Google μπορεί να
  δημοσιεύσει — δεν μπορούμε να το κάνουμε auto-verified default. Χωρίς αυτό, το Android
  δείχνει την εφαρμογή στο "Open with" chooser όταν πατηθεί ένα YouTube link· ο χρήστης
  μπορεί να την κάνει μόνιμο default είτε επιλέγοντάς την εκεί (με "Always"), είτε από
  Ρυθμίσεις Android → Εφαρμογές → MyTube → Set as default → Add link.
- `launchMode` άλλαξε από `singleTop` σε **`singleTask`**: αν η εφαρμογή τρέχει ήδη και
  πατηθεί ένα YouTube link αλλού, θέλουμε να ξαναχρησιμοποιηθεί το ίδιο instance (όχι νέο
  πάνω από το παλιό) — το νέο URL φτάνει μέσω `onNewIntent`, όχι νέο `onCreate`.
- `onNewIntent`/`youTubeUrlFrom(intent)` στο `MainActivity.kt`: αν το intent είναι
  `ACTION_VIEW` με http(s) data URI, φορτώνεται απευθείας στο WebView (καλύπτει και cold
  start μέσω link, και ήδη-τρέχουσα εφαρμογή).

### 8. Προτιμώμενη ποιότητα βίντεο
- Το YouTube player εκθέτει το ίδιο JS API που χρησιμοποιεί και το επίσημο IFrame Player
  API (`setPlaybackQuality`/`setPlaybackQualityRange`) πάνω στο element `#movie_player`
  (class `ytp-mweb-player`) — επιβεβαιωμένο ότι υπάρχει στο πραγματικό captured DOM μιας
  σελίδας βίντεο.
- Ρύθμιση στο Settings (`buttonVideoQuality` → `AlertDialog` με single-choice list) που
  αποθηκεύει ένα από τα `VideoQuality` ids (`auto`, `hd1080`, `hd720`, `large`=480p,
  `medium`=360p, `small`=240p, `tiny`=144p — τα ίδια strings που περιμένει το YouTube API).
  Default: `auto` (δεν κάνει τίποτα, αφήνει το YouTube να διαλέξει μόνο του).
- Το injected script (`applyPreferredQuality` στο `FeedScript.kt`) καλεί
  `getPreferredVideoQuality()` από το bridge και εφαρμόζει τη ρύθμιση σε κάθε `video`
  element **μία φορά ανά βίντεο** (στο `loadedmetadata`/`playing`, με guard attribute
  `data-mytube-quality-applied` ώστε αν ο χρήστης αλλάξει χειροκίνητα την ποιότητα μέσα
  στο ίδιο βίντεο μετά, να μην το "παλέψουμε" ξανά).
- ⚠️ Δεν έχει επιβεβαιωθεί σε πραγματική συσκευή ότι το `setPlaybackQuality` πράγματι
  αλλάζει την ποιότητα στο mobile web player (το API elements υπάρχουν, αλλά δεν είδαμε
  live behavior) — αν δεν πιάσει, το επόμενο βήμα θα ήταν να ελεγχθεί
  `player.getAvailableQualityLevels()` σε πραγματική συσκευή.

### 9. Background audio (χωρίς media notification controls ακόμα)
Ζητήθηκε ρητά σε δύο βήματα από τον χρήστη: πρώτα background audio, μετά media notification
controls (ξεχωριστό, μελλοντικό task).

- **Το πρόβλημα**: ένα απλό WebView-wrapper app δεν κρατάει τον ήχο να παίζει όταν ο χρήστης
  κλειδώνει την οθόνη ή αλλάζει εφαρμογή — το Android σταματάει/σκοτώνει τη διαδικασία
  (process) του backgrounded app μετά από λίγο, ό,τι κι αν κάνει η ίδια η σελίδα/JS.
- **Η λύση**: `PlaybackService.kt`, ένα minimal foreground `Service` (`foregroundServiceType
  ="mediaPlayback"`). Ξεκινάει/σταματάει από το `MainActivity` (`setPlaybackServiceRunning`)
  μέσα στο ήδη υπάρχον `onVideoPlayingChanged` callback (το ίδιο σήμα play/pause που ήδη
  χρησιμοποιούσαμε για το `FLAG_KEEP_SCREEN_ON`).
  - Το foreground service είναι αυτό που εμποδίζει το Android να σκοτώσει τη διαδικασία —
    δεν κάνουμε τίποτα άλλο "μαγικό" στο ίδιο το WebView. Το Chromium engine που τρέχει το
    YouTube ήδη εξαιρεί (δεν throttle-άρει) tabs που παίζουν ήχο, οπότε η ίδια η αναπαραγωγή
    συνεχίζεται φυσιολογικά όσο η διαδικασία μένει ζωντανή.
  - Απαιτεί μια μόνιμη (ongoing) notification όσο τρέχει — υποχρεωτικό από το ίδιο το
    Android για foreground services, χωρίς κουμπιά προς το παρόν (θα προστεθούν με το media
    notification controls task). Tap πάνω της ανοίγει την εφαρμογή.
  - Ζητάει `POST_NOTIFICATIONS` permission μία φορά στο `onCreate` (Android 13+) — αν δεν
    δοθεί, το service συνεχίζει να τρέχει κανονικά (ο ήχος συνεχίζεται), απλά χωρίς ορατή
    notification.
  - Ζητάει audio focus (`AudioManager`, `AUDIOFOCUS_GAIN`) ώστε να συμπεριφέρεται σωστά με
    άλλες εφαρμογές ήχου (π.χ. duck/pause), αλλά δεν διαχειρίζεται ακόμα το reaction σε
    audio focus loss (θα μπει μαζί με το media session στο επόμενο task).
- ⚠️ **Δοκιμάστηκε σε πραγματική συσκευή (build #22) και ΔΕΝ δούλεψε** — ο χρήστης ανέφερε
  "δεν συνεχίζει". Αιτία (διάγνωση, βλ. ενότητα 10): το `PlaybackService` κρατάει ζωντανή τη
  διαδικασία, αλλά αυτό από μόνο του δεν αρκεί — το ίδιο το YouTube player JS σταματάει το
  βίντεο μόλις η σελίδα αναφερθεί ως "hidden" μέσω του Page Visibility API.
- Πιθανό μελλοντικό σημείο τριβής (άσχετο με το παραπάνω): aggressive battery optimization
  από κάποια OEM (Xiaomi/Huawei/κ.λπ.) μπορεί να σκοτώσει και foreground services αν δεν
  εξαιρεθεί η εφαρμογή χειροκίνητα από τον χρήστη· δεν ζητάμε "Ignore battery optimizations"
  αυτόματα (πιο invasive permission prompt).

### 10. Background audio fix — Page Visibility API suppression ("resume guard")
Μετά το build #22 ο χρήστης ανέφερε ότι ο ήχος δεν συνεχίζει στο background παρόλο που το
`PlaybackService` (ενότητα 9) κρατάει τη διαδικασία ζωντανή.

- **Η πραγματική αιτία**: το `PlaybackService` λύνει μόνο το "μην σκοτώσει το Android τη
  διαδικασία". Δεν λύνει ότι το ίδιο το web player JS του YouTube παρακολουθεί το Page
  Visibility API (`document.hidden`/`visibilitychange`) και σταματάει μόνο του το βίντεο
  μόλις η σελίδα αναφερθεί ως κρυμμένη — ίδια συμπεριφορά με σχεδόν κάθε site με video player
  (εξοικονόμηση μπαταρίας/bandwidth όταν το tab δεν είναι ορατό).
- **Η λύση**: προστέθηκε στο `FeedScript.kt` ένα "resume guard" pattern, προσαρμοσμένο από το
  ήδη αποδεδειγμένο `resume_guard.js` του project `no-algo-fb` (εκεί λύνει ανάλογο πρόβλημα).
  - `document.hidden`/`webkitHidden`/`visibilityState`/`webkitVisibilityState` γίνονται pinned
    μέσω `Object.defineProperty` ώστε να αναφέρουν πάντα "ορατό".
  - Τα events `visibilitychange`, `webkitvisibilitychange`, `pagehide`, `pageshow`, `freeze`,
    `resume` παγιδεύονται σε **capture phase πάνω στο `window`** — το DOM περνάει από εκεί
    πριν φτάσει σε οποιονδήποτε listener πάνω στο `document` (όπου πραγματικά γίνεται dispatch
    το `visibilitychange`, και όπου ζει ο listener του ίδιου του YouTube) — άρα η σειρά
    καταγραφής (registration order) δεν έχει σημασία, μόνο η σειρά διέλευσης (capture πριν
    bubble/target).
  - Επιπλέον γίνεται override το `EventTarget.prototype.addEventListener` ώστε νέες
    εγγραφές για αυτά τα events να αγνοούνται σιωπηλά — καλύπτει events που στοχεύουν
    απευθείας το `window` (εκεί ένας capturing window listener δεν προλαβαίνει να είναι
    "πριν" γιατί ΕΙΝΑΙ ο listener του window).
  - Μπαίνει αμέσως μετά το `isAdBlockEnabled()` στο injected script, πριν το τμήμα
    αφαίρεσης διαφημιών — τρέχει σε document-start, άρα πριν προλάβει να τρέξει οποιοδήποτε
    δικό του YouTube script.
- ⚠️ **Δεν έχει επιβεβαιωθεί ακόμα σε πραγματική συσκευή** — επόμενο βήμα είναι ο χρήστης να
  ξαναδοκιμάσει background audio μετά από αυτό το build.

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

### 11. Μικρές βελτιώσεις μετά το seek-pause fix
- Το grace period του nudge (ενότητα 10 / v1.7.6) μειώθηκε από 600ms σε 150ms κατόπιν
  αιτήματος — τα ίδια logs έδειξαν ότι το `canplay` έρχεται σχεδόν αμέσως μετά το `seeked`,
  οπότε δεν χρειαζόταν τόσο μεγάλο περιθώριο πριν το native `video.play()`.
- **Αυτόματο fullscreen σε rotation (v1.7.7)**: `MainActivity.onConfigurationChanged`
  (χρειάστηκε γιατί το manifest έχει ήδη `configChanges="orientation|..."`, άρα δεν
  ξαναδημιουργείται η Activity σε rotation) καλεί
  `window.__mytubeEnterFullscreenIfLandscapeVideo()` όταν η συσκευή γυρίσει σε landscape
  ενώ δεν είμαστε ήδη σε fullscreen (`customView == null`). Η JS function βρίσκει το
  ενεργό video (ίδιο helper `findActiveVideo()` με το `__mytubeVideoIsPortrait`), και αν
  παίζει και είναι landscape-shaped (όχι Short/κάθετο), καλεί `video.requestFullscreen()`.
  - ⚠️ **Άγνωστο αν θα δουλέψει σε πραγματική συσκευή**: το Fullscreen API κανονικά
    απαιτεί "user gesture" (transient activation) — μια περιστροφή hardware ίσως δεν
    μετράει ως τέτοιο μέσα σε ένα τρίτο-μέρους WebView (σε αντίθεση με το πραγματικό Chrome
    app, που μπορεί να έχει ειδική μεταχείριση). Αν το browser αρνηθεί το request,
    αποτυγχάνει σιωπηλά (καμία ένδειξη σφάλματος) — απλό επόμενο βήμα αν δεν δουλέψει.

### 12. Fix: "play" σταματούσε το βίντεο σχεδόν αμέσως (v1.7.8)
Άσχετο με το seek bug — νέο debug log (ίδιο μηχανισμό, ενότητα 10) το αποκάλυψε: ο χρήστης
πατούσε play ενώ το βίντεο ήταν σε παύση, το βίντεο έπαιζε για ελάχιστα ms
(`play`→`playing`→`pause` μέσα σε 30-100ms, χωρίς κανένα `seeking` ενδιάμεσα), επαναλαμβανόμενα.

- **Αιτία**: `MainActivity.setPlaybackServiceRunning(true)` καλούνταν χωρίς όρους σε **κάθε**
  `playing` event — όχι μόνο στο πρώτο για ένα βίντεο, αλλά και σε κάθε resume από παύση,
  ανάκαμψη από buffering, επανεφαρμογή ποιότητας, κ.λπ. Κάθε τέτοια κλήση ξανάτρεχε
  `startForegroundService` → `PlaybackService.onStartCommand` → `requestAudioFocus()`, που
  έφτιαχνε ένα **καινούργιο** `AudioFocusRequest` κάθε φορά. Αυτό το επαναλαμβανόμενο request
  "διέκοπτε" το ήδη κατεχόμενο audio focus του ίδιου του WebView player (που το YouTube
  video element κρατάει φυσιολογικά όσο παίζει ήχο), προκαλώντας στο Chromium loss-of-focus
  reaction — δηλαδή αυτο-παύση του video element μέσα σε λίγα ms.
- **Fix, δύο επίπεδα**:
  1. `MainActivity`: νέο flag `playbackServiceRunning` — το `setPlaybackServiceRunning` πια
     δεν καλεί `startForegroundService`/`stopService` αν η ζητούμενη κατάσταση είναι ήδη η
     τρέχουσα (no-op όταν το request είναι redundant).
  2. `PlaybackService.requestAudioFocus()`: idempotent guard, δεν ξαναζητάει focus αν
     `audioFocusRequest != null` (ήδη το κατέχει).
- Πιθανώς προϋπήρχε από το v1.7.0 (background audio) — απλά δεν είχε αναφερθεί/εντοπιστεί
  πριν αποκτήσουμε το debug-log εργαλείο.

### 13. v1.7.8 δεν αρκούσε — το service δεν έπρεπε να ζητάει δικό του audio focus καθόλου
Ο χρήστης επιβεβαίωσε ότι το πρόβλημα της ενότητας 12 επέμενε μετά το fix, με νέο log που
έδειξε καθαρά ότι συμβαίνει σε **κάθε** πραγματικό play/pause κύκλο (όχι μόνο redundant
κλήσεις — αυτές το v1.7.8 τις είχε ήδη σταματήσει). Αυτό αποκάλυψε ότι η αρχική θεωρία ήταν
ημιτελής: το πρόβλημα δεν ήταν το πλεονάζον request, ήταν η **ίδια η ύπαρξη** ενός δεύτερου,
ανεξάρτητου audio-focus holder μέσα στην ίδια εφαρμογή.

- Κάθε φορά που ο χρήστης πατάει pause, το `MainActivity.setPlaybackServiceRunning(false)`
  σταματάει το service — που στο `onDestroy` άφηνε (abandon) το focus του. Στο επόμενο play,
  το service ξεκινούσε ξανά και ζητούσε ξανά focus (`AUDIOFOCUS_GAIN`) — αυτό είναι απόλυτα
  φυσιολογική/αναμενόμενη ροή για ΕΝΑ πραγματικό play/pause, όχι bug από μόνο του. Το
  πρόβλημα ήταν ότι αυτό το request διέκοπτε το ήδη κατεχόμενο focus του ίδιου του WebView
  player (που το Chromium κρατάει μόνο του για το ενεργό `<video>`), κάνοντάς το να χάνει
  focus και να αυτο-παυσάρει.
- **Fix**: αφαιρέθηκε εντελώς η λογική audio focus (`requestAudioFocus`/`abandonAudioFocus`,
  το `AudioFocusRequest` field) από το `PlaybackService`. Ο αρχικός σκοπός της ("να κάνουν
  duck/pause άλλες εφαρμογές ήχου") ήταν προληπτικός/μη ζητημένος ρητά — και ούτως ή άλλως
  ήδη καλύπτεται από το δικό του focus handling του Chromium/WebView για το playing video,
  οπότε δεν χρειαζόταν διπλό μηχανισμό. Το service πλέον κάνει μόνο ό,τι χρειάζεται στην
  πραγματικότητα: κρατάει τη διαδικασία ζωντανή μέσω του foreground notification.
- Το guard από το v1.7.8 (δεν ξαναστέλνει service αν ήδη τρέχει στην ίδια κατάσταση)
  παραμένει — ήταν σωστό ως προστασία από redundant κλήσεις, απλά δεν ήταν η πλήρης λύση.
