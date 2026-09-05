# TODO — FitNotes_Fork

## En cours

- [ ] **Valider "commentaire par série"** : appui long sur une série (dans l'écran d'un
  jour) ouvre maintenant un dialogue pour voir/éditer/effacer le commentaire de cette
  série précise, indépendamment des autres séries du même exercice. Un petit icône
  apparaît sur la série si elle a un commentaire. N'affecte pas la fonctionnalité
  existante "Exercise Comments" (qui applique un commentaire à toutes les séries de
  l'exercice du jour d'un coup) — les deux coexistent. À tester par Romain.
- [ ] Ajouter l'indication visuelle des jours avec séance sur le sélecteur de date
  (discuté, pas fait — voir plan).

## Fait / validé

- [x] Choix du dépôt de base : `MakisChristou/verifit`. **Décidé : on reste sur le fork
  perso (`GlouBoux/verifit`), pas de PR vers l'amont `MakisChristou/verifit`.**
- [x] Feature "Dupliquer un exercice" — codée, testée sur l'app, fonctionne.
- [x] Feature "Import Session" (JSON) — codée, testée sur l'app, fonctionne.
- [x] App qui build et se lance (émulateur et téléphone).
- [x] Bug poids/répétitions inversés après import CSV réel — corrigé, confirmé par
  Romain.
- [x] Crash "Import failed: IllegalStateException" sur Import Session — corrigé (patch
  #4).
- [x] Bug "l'app revient toujours à aujourd'hui" — corrigé (patch #5), **validé par
  Romain**.
- [x] Icône calendrier de la barre d'outils branchée sur un vrai sélecteur de date
  (patch #5) — **validé par Romain**.
- [x] Rattrapage de l'historique git côté Romain, gitignore de `build_log.txt`, script
  `scripts/build_and_log.bat` pour partager un log de build facilement.
- [x] Commentaire par série (patch #6) — voir "En cours" ci-dessus pour le statut de
  validation.

## En attente de décision / à planifier

- [ ] **Retirer le backend de compte en ligne `verifit_rs`** : décision prise par Romain
  ("feature abandonnée officiellement"), à faire. Périmètre réel : au moins 14 fichiers
  touchent `verifit_rs`/`verifitrs`/`WorkoutSetsApi`/le mode offline
  (`MainActivity.initActivity()`, `AddExerciseActivity.saveComment()`, `DataStorage`,
  `SettingsActivity`, `ExerciseAdapter`, `BackupService`, la classe `SharedPreferences`
  maison, plus les 3 Activity dédiées `LoginActivity`/`ChangePasswordActivity`/
  `ForgotPasswordActivity` et le package `verifitrs`). Pas de SDK Android disponible côté
  Claude pour compiler et vérifier une suppression à la volée — à faire par étapes
  prudentes (forcer le mode offline en permanence + masquer l'UI de login d'abord,
  suppression effective du code mort ensuite), testées une à une.

## Prochain chantier majeur

- [ ] **Intégrer le générateur de séances (`workout_engine.py`)** : le format JSON
  "Import Session" est déjà prévu comme point d'intégration ; condition posée par Romain
  — rester facile à faire évoluer tant qu'on est en période de test.

## Pas urgent

- [ ] Upgrade AGP (actuellement 7.4.2) — délibérément reporté, pas bloquant.

---
*Ce fichier est aussi tenu à jour dans le projet Claude "FitNotes_Fork" ; les deux
copies sont synchronisées à chaque session.*
