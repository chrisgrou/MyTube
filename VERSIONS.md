# Ιστορικό εκδόσεων — MyTube

## v1.0.0 (αρχική έκδοση)
- Android wrapper (WebView) για `m.youtube.com`, χωρίς toolbar/borders — φαίνεται μόνο η
  σελίδα, edge-to-edge, με persistent cookies ώστε το login να δουλεύει κανονικά.
- Φιλτράρισμα feed: απόκρυψη community posts με εικόνες (όχι βίντεο, όχι Shorts, όχι
  προτεινόμενα βίντεο), με on/off toggle από τις Ρυθμίσεις. Default: ενεργό.
- Cog (⚙) εικονίδιο ενσωματωμένο δίπλα στο λογότυπο του YouTube στο header, που ανοίγει
  τις native Ρυθμίσεις της εφαρμογής (με fallback floating button αν αλλάξει το DOM).
- Ρυθμίσεις: toggle φίλτρου, χειροκίνητος έλεγχος ενημερώσεων, λήψη & εγκατάσταση APK,
  τοπικό ιστορικό εγκαταστάσεων.

## v1.1.0
- Το update mechanism ευθυγραμμίστηκε με το ίδιο pattern που χρησιμοποιείται ήδη στα
  projects `thrylos-news` και `no-algo-fb`: ένα σταθερό prerelease tag `latest` που το CI
  αντικαθιστά σε κάθε push (αντί για semver tags), `versionCode` = αριθμός build του CI
  (`GITHUB_RUN_NUMBER`), committed debug keystore ώστε τα builds να υπογράφονται πάντα ίδια
  (ώστε να γίνεται πραγματικό update πάνω από την προηγούμενη εγκατάσταση, χωρίς uninstall).
- Ένα ενιαίο CI workflow (`.github/workflows/build.yml`): τρέχει σε κάθε push (σε
  οποιοδήποτε branch), σε κάθε pull request, και έχει manual trigger. Ανεβάζει το debug
  APK ως artifact σε κάθε run (κατεβαίνει απευθείας από το Actions tab) και, σε κάθε push,
  ανανεώνει το release `latest` με το APK + αυτόματο changelog από τα commits.

## v1.2.0 (builds #5–#13)
Διορθώσεις μετά από δοκιμές σε πραγματική συσκευή:
- Το πάνω/κάτω μέρος της σελίδας κρυβόταν κάτω από status/navigation bar (edge-to-edge):
  μπαίνει padding στο parent container — το padding πάνω στο ίδιο το WebView δεν δουλεύει.
- Feed filter: τα selectors ήταν λάθος (μαντεψιά). Επιβεβαιώθηκαν με πραγματικό DOM από
  .mht snapshot — `ytm-backstage-post-thread-renderer` μέσα σε `ytm-rich-section-renderer`.
- Το injection γίνεται πλέον σε document-start (`addDocumentStartJavaScript`) αντί για
  onPageFinished — πολύ πιο αξιόπιστο σε SPA.
- Update check: το GitHub API επέστρεφε 403 γιατί έλειπε το `User-Agent` header· επίσης οι
  αποτυχίες ελέγχου εμφανίζονται πλέον ως σφάλμα αντί για ψεύτικο "είσαι ενημερωμένος".
- Pull to refresh: δεν "τρώει" πια το scroll μέσα σε ένθετα panels (π.χ. σχόλια σε Shorts).
- Το floating κουμπί ρυθμίσεων αφαιρέθηκε (ενοχλούσε)· η πρόσβαση γίνεται τώρα από μια
  γραμμή "MyTube" μέσα στη σελίδα Ρυθμίσεων του ίδιου του YouTube.

## v1.3.0
- **Αποκλεισμός διαφημίσεων** (νέο toggle στις Ρυθμίσεις, default ενεργό), σε τρία επίπεδα:
  μπλοκάρισμα ad/tracker domains σε επίπεδο request (όπως ο Brave), αφαίρεση των in-stream
  διαφημίσεων (pre-roll/mid-roll) από το player response πριν το διαβάσει το YouTube, και
  απόκρυψη sponsored αποτελεσμάτων στο feed.

## v1.4.0 — fullscreen playback
- Το fullscreen είναι πλέον πραγματικά fullscreen: κρύβονται status/navigation bar
  (επανέρχονται με swipe).
- Η οθόνη δεν σβήνει πια κατά την αναπαραγωγή (keep-screen-on όσο παίζει βίντεο — ένα
  WebView δεν το κάνει μόνο του όπως ο browser).
- Αυτόματη περιστροφή σε fullscreen ανάλογα με το σχήμα του βίντεο: landscape για κανονικά
  βίντεο, portrait για κάθετα/Shorts. Επαναφορά στην έξοδο.
- Swipe gestures στο fullscreen: αριστερά φωτεινότητα, δεξιά ένταση ήχου, με ένδειξη
  ποσοστού. Τα taps/horizontal drags περνούν κανονικά στον player.
- Το back button βγάζει πρώτα από το fullscreen αντί να κλείνει την εφαρμογή.

## v1.4.1
- Η πρόσβαση στις Ρυθμίσεις επιστρέφει σε **native κουμπί** (μετά από 3 αποτυχημένες
  προσπάθειες να προστεθεί ως γραμμή μέσα στη σελίδα Ρυθμίσεων του YouTube — δούλευε πάντα
  σε δοκιμές πάνω σε captured DOM, ποτέ στην πραγματική συσκευή). Τοποθετημένο μέση-δεξιά
  της οθόνης, μικρό και ημιδιάφανο, ώστε να μην ενοχλεί όπως η πρώτη (πάνω-δεξιά) εκδοχή.
- Εμφανίζεται **μόνο** στη σελίδα Ρυθμίσεων του YouTube (`/select_site`), όχι παντού πια:
  το native WebViewClient παρακολουθεί το URL, όχι το DOM.

## v1.5.0
- Η εφαρμογή μπορεί να γίνει **default handler** για YouTube links (youtube.com,
  m.youtube.com, www.youtube.com, music.youtube.com, youtu.be) — link από άλλη εφαρμογή,
  SMS, κ.λπ. ανοίγει μέσα στο MyTube αντί για browser/YouTube app. Χωρίς αυτόματη
  επαλήθευση (αυτό απαιτεί αρχείο στο youtube.com που μόνο η Google μπορεί να δημοσιεύσει)
  — ο χρήστης το ενεργοποιεί χειροκίνητα από το "Open with" chooser ή από
  Ρυθμίσεις → Εφαρμογές → MyTube → Set as default.

## v1.6.0
- Νέα ρύθμιση **"Ποιότητα βίντεο"**: επιλέγεις μία σταθερή ποιότητα (144p έως 1080p, ή
  Αυτόματη) και εφαρμόζεται σε κάθε βίντεο που ανοίγεις, μέσω του ίδιου JS API που
  χρησιμοποιεί το YouTube player.

## v1.7.0
- **Background audio**: ο ήχος συνεχίζει να παίζει όταν κλειδώνεις την οθόνη ή αλλάζεις
  εφαρμογή (foreground service κρατάει τη διαδικασία ζωντανή). Δείχνει μια μόνιμη
  notification χωρίς κουμπιά ακόμα — τα media controls (play/pause από τη notification/
  lock screen) είναι ξεχωριστό, επόμενο βήμα.

## v1.7.1
- **Fix background audio**: το v1.7.0 δεν αρκούσε — το YouTube player JS σταματούσε μόνο
  του το βίντεο όταν η σελίδα αναφερόταν ως "hidden" (Page Visibility API), ανεξάρτητα από
  το ότι η διαδικασία έμενε ζωντανή. Προστέθηκε suppression αυτού του API στο injected
  script (pattern δανεισμένο από το `no-algo-fb`): `document.hidden`/`visibilityState`
  πάντα "ορατό", και τα σχετικά events (`visibilitychange`, `pagehide`, `pageshow`, κ.λπ.)
  μπλοκάρονται πριν φτάσουν στο YouTube.

## v1.7.2
- **Fix double-tap-to-seek σε fullscreen**: το `PlayerGestureLayout` (swipe για
  φωτεινότητα/ένταση) "κατάπινε" κάθε `ACTION_MOVE` χωρίς όρους, ακόμα κι όταν δεν γινόταν
  πραγματικό drag — αν το εσωτερικό video view δεν κατανάλωνε ένα tap, αυτό το layout το
  έπιανε αντί γι' αυτό, χαλώντας το timing που χρειάζεται το player για να αναγνωρίσει
  double-tap. Αποτέλεσμα: το double tap για seek +10" έμπαινε σε παύση αντί να συνεχίσει.
  Το layout πλέον αφήνει ανέγγιχτο οτιδήποτε δεν είναι ενεργό κάθετο drag.

## v1.7.3
- **Fix: το βίντεο έμπαινε σε παύση μετά από seek σε fullscreen** — και με double-tap και
  με χειροκίνητο σέρνιμο της μπάρας. Αιτία: κατά το fullscreen το WebView γινόταν
  `View.GONE`, κάτι που κάνει το Chromium να θεωρεί τη σελίδα μη ορατή και να καθυστερεί
  τα δικά της JS timers/callbacks — άρα το JS του YouTube που πρέπει να τρέξει μετά το seek
  για να συνεχίσει η αναπαραγωγή δεν πρόλαβαινε. Το WebView δεν κρύβεται πια (το fullscreen
  video το καλύπτει ούτως ή άλλως οπτικά, δεν χρειαζόταν).
  - ⚠️ Ο χρήστης δοκίμασε το build και το πρόβλημα **παρέμεινε** — άρα δεν ήταν (μόνο) αυτή
    η αιτία. Βλ. v1.7.4.

## v1.7.4 (διαγνωστικό, όχι fix ακόμα)
- Προστέθηκε προσωρινό logging γύρω από τα video events (`seeking`/`seeked`/`pause`/`play`/
  `playing`/`waiting`/`stalled`/`canplay`/`suspend`) με timestamp, `paused`, `currentTime`,
  `readyState`, `networkState` — φαίνονται στο Logcat με tag `MyTubeWebView`. Σκοπός: να
  καταγραφεί τι ακριβώς συμβαίνει (ποιο event δεν έρχεται/έρχεται λάθος) την επόμενη φορά
  που αναπαραχθεί το πρόβλημα, ώστε το επόμενο fix να είναι βασισμένο σε δεδομένα, όχι
  εικασία. Θα αφαιρεθεί μόλις βρεθεί η πραγματική αιτία.

## v1.7.5 (διαγνωστικό)
- Ο χρήστης δεν έχει τρόπο να δει Logcat (χωρίς υπολογιστή/adb), οπότε το ίδιο debug log
  φτάνει πλέον και σε ένα buffer μέσα στην εφαρμογή. Νέα ενότητα "Debug log" στις Ρυθμίσεις
  με κουμπί **"Αντιγραφή debug log"**: αντιγράφει τις τελευταίες ~300 γραμμές στο clipboard,
  έτοιμες για paste. Προσωρινό, θα αφαιρεθεί μαζί με το υπόλοιπο diagnostic logging.

## v1.7.6
- **Πραγματικό fix (πιθανολογείται) για την παύση μετά από seek**: από τα logs φάνηκε ότι το
  πρόβλημα δεν είναι fullscreen-specific (συνέβαινε και εκτός fullscreen) — άρα τα δύο
  προηγούμενα fixes (v1.7.2, v1.7.3) ήταν άσχετα με αυτό (παρέμειναν όμως, ήταν σωστά για ό,τι
  διόρθωναν). Τα logs έδειξαν ότι το YouTube καθυστερεί σταθερά ~1.5-2.2" να καλέσει ξανά
  play() μετά από ένα seek, ενώ το βίντεο είναι ήδη πλήρως buffered (`readyState=4`). Αντί να
  ψάξουμε γιατί καθυστερεί το ίδιο το YouTube (πιθανό δικό του ad-eligibility check), το
  injected script θυμάται αν το βίντεο έπαιζε πριν το seek και, αν παραμένει σε παύση για
  >600ms μετά το `seeked`, καλεί το ίδιο `video.play()`.

## v1.7.7
- Το grace period πριν το nudge μετά από seek μειώθηκε από 600ms σε **150ms** — τα logs
  έδειξαν ότι το βίντεο είναι έτοιμο (canplay) σχεδόν αμέσως μετά το seeked.
- **Αυτόματο fullscreen όταν γυρίζεις την οθόνη σε landscape** ενώ παίζει ένα landscape
  βίντεο (όχι Short/κάθετο) — ίδια συμπεριφορά με πραγματικό mobile browser. Best-effort:
  το Fullscreen API κανονικά απαιτεί user gesture, και μια περιστροφή συσκευής ίσως δεν
  μετράει πάντα ως τέτοιο μέσα σε WebView — αν αρνηθεί, απλά δεν συμβαίνει τίποτα (silent).

## v1.7.8
- **Fix: "πατάω play και παίζει για μια στιγμή, μετά ξαναμπαίνει σε παύση"** — άσχετο με το
  seek, νέο debug log το αποκάλυψε. Αιτία: κάθε `playing` event (όχι μόνο το πρώτο για ένα
  βίντεο — και σε resume, buffering recovery, αλλαγή ποιότητας) ξανακαλούσε
  `startForegroundService` στο `PlaybackService`, που ξαναζητούσε audio focus
  (`AUDIOFOCUS_GAIN`) κάθε φορά. Αυτό το επαναλαμβανόμενο request "έκλεβε" audio focus από
  το ίδιο το WebView player, που έχανε focus και αυτο-παυσάριζε μέσα σε λίγα ms. Fix σε δύο
  επίπεδα: το `MainActivity` δεν ξαναστέλνει το service αν ήδη τρέχει, και το ίδιο το
  `PlaybackService` δεν ξαναζητάει audio focus αν το έχει ήδη.

## v1.7.9
- **Το v1.7.8 δεν αρκούσε** — ο χρήστης επιβεβαίωσε ότι το πρόβλημα επιμένει, με νέο log που
  έδειξε ότι συμβαίνει σε **κάθε** πραγματικό play/pause κύκλο, όχι μόνο σε redundant
  κλήσεις. Πραγματική αιτία: το `PlaybackService` ζητούσε το **δικό του, ξεχωριστό** audio
  focus (`AUDIOFOCUS_GAIN`), το οποίο συγκρούεται με αυτό που ήδη κρατάει το ίδιο το WebView
  για το playing video element — σε κάθε νόμιμο resume (μετά από κάθε πραγματική παύση το
  service σταματούσε/άφηνε το focus, οπότε το επόμενο play το ζητούσε ξανά, διακόπτοντας το
  ήδη κατεχόμενο focus του player). Fix: το service **δεν ζητάει καθόλου δικό του audio
  focus πια** — αυτό είναι ήδη δουλειά του Chromium/WebView για το πραγματικό media element,
  δεν χρειαζόταν διπλασιασμό.
