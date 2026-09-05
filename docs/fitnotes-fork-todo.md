# TODO — FitNotes\_Fork

## En cours

- [ ] **Valider le calendrier de navigation avec indicateur de jours avec séance**
  (patch \#7) : l'icône calendrier de la barre d'outils ouvre maintenant un calendrier
  mensuel fait maison (mois précédent/suivant, jour actuellement affiché en surbrillance)
  au lieu du sélecteur de date basique d'Android, qui ne permettait pas cet affichage.
  Un petit point apparaît sous chaque jour qui a déjà une séance enregistrée, comme sur
  FitNotes. À tester par Romain.

## Fait / validé

- [x] Choix du dépôt de base : `MakisChristou/verifit`. **Décidé : on reste sur le fork
  perso (`GlouBoux/verifit`), pas de PR vers l'amont `MakisChristou/verifit`.**
- [x] Feature "Dupliquer un exercice" — codée, testée sur l'app, fonctionne.
- [x] Feature "Import Session" (JSON) — codée, testée sur l'app, fonctionne.
- [x] App qui build et se lance (émulateur et téléphone).
- [x] Bug poids/répétitions inversés après import CSV réel — corrigé, confirmé par
  Romain.
- [x] Crash "Import failed: IllegalStateException" sur Import Session — corrigé (patch
  \#4).
- [x] Bug "l'app revient toujours à aujourd'hui" — corrigé (patch \#5), **validé par
  Romain**.
- [x] Icône calendrier de la barre d'outils branchée sur un vrai sélecteur de date
  (patch \#5) — **validé par Romain**.
- [x] Rattrapage de l'historique git côté Romain, gitignore de `build_log.txt`, script
  `scripts/build_and_log.bat` pour partager un log de build facilement.
- [x] Commentaire par série (patch \#6) — **validé par Romain**.

## En attente de décision / à planifier

- [ ] **Retirer le backend de compte en ligne `verifit_rs`** : décision prise par Romain
  ("feature abandonnée officiellement"), à faire. Périmètre réel : au moins 14 fichiers
  touchent `verifit_rs`/`verifitrs`/`WorkoutSetsApi`/le mode offline
  (`MainActivity.initActivity()`, `AddExerciseActivity.saveComment()`, `DataStorage`,
  `SettingsActivity`, `ExerciseAdapter`, `BackupService`, la classe `SharedPreferences`
  maison, plus les 3 Activity dédiées `LoginActivity`/`ChangePasswordActivity`/
  `ForgotPasswordActivity` et le package `verifitrs`). Pas de SDK Android disponible côté
  Claude pour compiler et vérifier une suppression à la volée — à faire par étapes
  prudentes (forcer le mode offline en permanence \+ masquer l'UI de login d'abord,
  suppression effective du code mort ensuite), testées une à une.

## Prochain chantier majeur

- [ ] **Intégrer le générateur de séances (`workout_engine.py`)** : le format JSON
  "Import Session" est déjà prévu comme point d'intégration ; condition posée par Romain
  — rester facile à faire évoluer tant qu'on est en période de test.

## Pas urgent

- [ ] Upgrade AGP (actuellement 7.4.2) — délibérément reporté, pas bloquant.

* * *

*Ce fichier est aussi tenu à jour dans le projet Claude "FitNotes\_Fork" ; les deux
copies sont synchronisées à chaque session.*
