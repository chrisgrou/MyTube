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
