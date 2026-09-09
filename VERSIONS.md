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
