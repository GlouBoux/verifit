# TODO — FitNotes_Fork

## En cours

- [ ] **Valider le correctif "jour conservé"** : `MainActivity` ne recentre plus sur
  aujourd'hui à chaque retour au premier plan (`onRestart()`), elle restaure le jour
  précédemment affiché. Ne couvre que le cas "app réduite puis rouverte" (process
  toujours vivant) — si Android tue le processus en arrière-plan (mémoire faible, longue
  absence), l'app repart de zéro et retombera sur aujourd'hui au prochain lancement. À
  tester par Romain ; si ce cas de figure (kill de process) arrive en pratique et le
  gêne, il faudra persister le dernier jour consulté dans les préférences pour couvrir
  aussi ce cas.
- [ ] **Valider le nouveau sélecteur de date** : l'icône calendrier de la barre d'outils
  (dans l'onglet Verifit) ouvre maintenant un `DatePickerDialog` pré-rempli avec le jour
  affiché, au lieu de juste recentrer sur aujourd'hui — permet de sauter à un jour
  arbitraire sans swiper. Reprend `onDateSet()` qui ouvrait déjà `DayActivity` pour la
  date choisie (code mort jusqu'ici, jamais branché). **Pas encore fait** : indication
  visuelle légère des jours où une séance existe (comme FitNotes) — nécessiterait soit de
  réintroduire une lib de calendrier (on avait justement supprimé
  `material-calendar-view` comme dépendance morte, patch #2 — ironie que ce soit
  peut-être la fonctionnalité prévue à l'origine), soit un calendrier fait main. Pas fait
  cette session, discuté comme "au moins pouvoir changer de jour" en priorité.

## Fait / validé

- [x] Choix du dépôt de base : `MakisChristou/verifit`.
- [x] Feature "Dupliquer un exercice" — codée, testée sur l'app, fonctionne.
- [x] Feature "Import Session" (JSON) — codée, testée sur l'app, fonctionne.
- [x] App qui build et se lance sur émulateur (après correctifs `mavenCentral()` +
  drawable manquant).
- [x] Bug poids/répétitions inversés après import CSV réel — corrigé, confirmé par
  Romain.
- [x] Crash "Import failed: IllegalStateException" sur Import Session — corrigé (patch
  #4). Cause confirmée : fausse manip de Romain (mauvais écran/fichier), pas un vrai bug
  de données.
- [x] Rattrapage de l'historique git côté Romain (fait depuis VSCode) — `git status`
  propre.
- [x] Branche `feature/duplicate-exercise-and-session-import` poussée vers
  `GlouBoux/verifit` par Romain. Pas encore de PR vers l'amont.
- [x] Bug "l'app revient toujours à aujourd'hui" — cause trouvée et corrigée (patch #5) :
  `initViewPager()` recréait l'adapter et resettait la position à chaque
  `onRestart()`. Ne se produisait que quand l'onglet Verifit était au premier plan lors
  de la mise en arrière-plan (confirmé par la description de Romain), jamais depuis
  Diary/Exercises — cohérent avec le fait que ce sont des Activity séparées qui ne
  déclenchent pas le `onRestart()` de `MainActivity`.
- [x] Icône calendrier de la barre d'outils branchée sur un vrai sélecteur de date
  (patch #5) — permet de changer de jour sans swiper.

## En attente de décision / à planifier

- [ ] Ouvrir une PR vers `MakisChristou/verifit` en amont, ou rester sur le fork perso ?
- [ ] **Retirer le backend de compte en ligne `verifit_rs`** : décision prise par Romain
  ("feature abandonnée officiellement"), à faire. Périmètre réel : au moins 14 fichiers
  touchent `verifit_rs`/`verifitrs`/`WorkoutSetsApi`/le mode offline
  (`MainActivity.initActivity()`, `AddExerciseActivity.saveComment()`, `DataStorage`,
  `SettingsActivity`, `ExerciseAdapter`, `BackupService`, la classe `SharedPreferences`
  maison, plus les 3 Activity dédiées `LoginActivity`/`ChangePasswordActivity`/
  `ForgotPasswordActivity` et le package `verifitrs`). Pas de SDK Android disponible côté
  Claude pour compiler et vérifier une suppression à la volée — à faire par étapes
  prudentes (forcer le mode offline en permanence + masquer l'UI de login d'abord,
  suppression effective du code mort ensuite), testées une à une plutôt qu'en un seul
  gros changement non vérifiable avant le prochain build de Romain.
- [ ] Ajouter l'indication visuelle des jours avec séance sur le sélecteur de date
  (voir ci-dessus).

## Prochain chantier majeur

- [ ] **Intégrer le générateur de séances (`workout_engine.py`)** : le format JSON
  "Import Session" est déjà prévu comme point d'intégration ; condition posée par Romain
  — rester facile à faire évoluer tant qu'on est en période de test.

## Pas urgent

- [ ] Upgrade AGP (actuellement 7.4.2) — délibérément reporté, pas bloquant.

---
*Ce fichier est aussi tenu à jour dans le projet Claude "FitNotes_Fork" ; les deux
copies sont synchronisées à chaque session.*
