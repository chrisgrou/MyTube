# Ιστορικό εκδόσεων — MyTube

## v1.0.0 (αρχική έκδοση)
- Android wrapper (WebView) για `m.youtube.com`, χωρίς toolbar/borders — φαίνεται μόνο η
  σελίδα, edge-to-edge, με persistent cookies ώστε το login να δουλεύει κανονικά.
- Φιλτράρισμα feed: απόκρυψη community posts με εικόνες (όχι βίντεο, όχι Shorts, όχι
  προτεινόμενα βίντεο), με on/off toggle από τις Ρυθμίσεις. Default: ενεργό.
- Cog (⚙) εικονίδιο ενσωματωμένο δίπλα στο λογότυπο του YouTube στο header, που ανοίγει
  τις native Ρυθμίσεις της εφαρμογής (με fallback floating button αν αλλάξει το DOM).
- Ρυθμίσεις: toggle φίλτρου, χειροκίνητος έλεγχος ενημερώσεων μέσω GitHub Releases
  (`chrisgrou/mytube`), λήψη & εγκατάσταση APK, τοπικό ιστορικό εγκαταστάσεων + λίστα
  releases με notes από το GitHub.
- CI workflow (`.github/workflows/release.yml`): build APK + δημιουργία GitHub Release
  αυτόματα σε κάθε tag `vX.Y.Z`.
