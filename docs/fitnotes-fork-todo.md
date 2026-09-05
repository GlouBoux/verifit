# TODO — FitNotes\_Fork

## En cours

- [ ] **Suppression de plusieurs séries en une fois** (retour Romain 05/09/2026) : dans
  `AddExerciseActivity` (l'écran de log d'un exercice), on ne pouvait supprimer qu'une
  série à la fois (long-press → menu popup → Supprimer), fastidieux pour nettoyer
  plusieurs séries d'un coup (ex : import de test à corriger). Ajout d'un mode sélection
  multiple : nouvelle icône "Select" dans la barre d'outils démarre une ActionMode
  (barre contextuelle standard Android) ; taper sur une série la coche/décoche ; l'icône
  Supprimer de la barre contextuelle supprime toute la sélection en une seule
  confirmation. Le long-press existant (Éditer/Supprimer une seule série) n'a pas été
  touché - c'est un ajout, pas un remplacement. **Testé sur l'app par Romain, ça
  fonctionne bien.**

- [ ] **Réorganiser/supprimer plusieurs exercices depuis l'écran du jour** (retour Romain
  05/09/2026, sur l'écran `DayActivity`, atteint via l'icône calendrier) : même mécanique
  de sélection multiple + suppression que pour les séries, plus une poignée de
  réorganisation par glisser-déposer ("comme FitNotes") pour changer l'ordre des
  exercices. Deux mécanismes indépendants (poignée toujours dispo hors sélection
  multiple, sélection multiple = suppression uniquement) - confirmé correspondre à ce que
  voulait Romain.
  Changement plus profond que les précédents : l'ordre d'affichage des exercices d'un
  jour était jusqu'ici toujours alphabétique et jamais mémorisé nulle part (recalculé à
  chaque fois via un tri alphabétique). Il est maintenant mémorisé par jour
  (`WorkoutDay.ExerciseOrder`, nouveau champ) et réordonnable à la main. Vérifié
  compatible avec les anciennes sauvegardes (un ancien fichier sans ce champ retombe sur
  une liste vide, pas sur un crash).
  **Bug trouvé et corrigé (05/09/2026)** : la poignée de glisser-déposer était bien câblée
  mais quasi invisible (teintée `core_grey_02`, un gris presque blanc, sur fond de carte
  clair). Recolorée en `core_grey_55` (même gris que les autres icônes discrètes de
  l'app). **Réorganisation confirmée fonctionnelle par Romain (05/09/2026)** après ce
  correctif.

- [ ] **Même sélection multiple + réorganisation sur l'onglet Workout (accueil)** (retour
  Romain 05/09/2026) : Romain a d'abord essayé de réordonner depuis l'onglet
  accueil - anciennement titré "Verifit", renommé **"Workout"** - avant de découvrir que
  cette fonctionnalité n'existait que sur `DayActivity`. L'app a en fait 3 écrans séparés
  qui affichent chacun "la liste des exercices d'un jour", codés indépendamment (aucun
  n'hérite des autres) : l'onglet Workout (`ViewPagerExerciseAdapter`, dans le carrousel
  swipable de `MainActivity`), l'écran Day/calendrier (`DayExerciseAdapter`, fait
  ci-dessus) et l'historique Sessions (`DiaryExerciseAdapter`, pas encore traité - priorité
  confirmée par Romain : Workout d'abord, Sessions pas demandé pour l'instant). Même
  mécanique reprise à l'identique (checkbox + ActionMode + poignée de glisser-déposer),
  mais plus délicat techniquement car la liste d'exercices est imbriquée dans un
  `ViewPager2` (carrousel horizontal entre les jours) dont les pages (et donc les
  adapters d'exercices) sont recyclées au fil du swipe - contrairement à `DayActivity` où
  un seul adapter vit pour toute la durée de l'écran. L'`ItemTouchHelper` est donc créé
  une seule fois par page recyclée (dans le ViewHolder du carrousel), pas à chaque bind,
  pour éviter d'empiler plusieurs `OnItemTouchListener` sur la même RecyclerView au fil
  des swipes. Le bouton "Select exercises" de la barre d'outils retrouve la page
  actuellement affichée dans le carrousel pour savoir sur quel jour agir. **Confirmé
  fonctionnel par Romain (05/09/2026)** après build.
  **Amélioration ajoutée (05/09/2026, retour Romain)** : en mode sélection, les séries de
  chaque exercice se replient automatiquement (comme FitNotes) - plus facile de
  sélectionner/glisser plusieurs exercices sans avoir à faire défiler le détail de
  chacun. La poignée de glisser-déposer reste aussi disponible PENDANT la sélection
  multiple (avant, les deux modes étaient mutuellement exclusifs). Ce changement a
  nécessité de suivre la sélection par NOM d'exercice plutôt que par position (sinon une
  sélection pointait sur le mauvais exercice après un glisser-déposer pendant qu'une
  sélection était en cours) - fait sur `DayExerciseAdapter` et `ViewPagerExerciseAdapter`.
  **Testé par Romain** : le repli fonctionne bien en passant par le bouton "Select".
  **Bug trouvé et corrigé (05/09/2026)** : en revanche, si on drague directement un
  exercice via la poignée SANS passer par le bouton "Select" (drag "brut", hors sélection
  multiple), les séries ne se repliaient pas - le repli ne dépendait que de
  `selectionMode`, or la poignée reste utilisable même hors sélection multiple. Ajout
  d'un état `dragging` séparé (mis à `true`/`false` par le
  `ItemTouchHelper.Callback.onSelectedChanged()`/`clearView()`, sur `DayActivity` et
  `ViewPagerWorkoutDayAdapter`) qui déclenche le même repli que `selectionMode`,
  indépendamment du mode sélection. Pas encore buildé/testé par Romain.

## Fait / validé

- [x] **Bug critique de perte de données à l'Import Session — RÉSOLU ET CONFIRMÉ
  (05/09/2026)** : après avoir recompilé/réinstallé l'app puis importé le JSON de la
  séance de vendredi, Romain ne voyait plus QUE cette séance dans "Sessions" (ex-Diary) -
  tout l'historique précédent avait disparu de l'app.
  **Cause** : `DataStorage.mergeImportedSession()` (le code qui traite un Import
  Session) appelait `setsToEverything()`, qui VIDE `workoutDays` et le RECONSTRUIT
  ENTIÈREMENT à partir de la liste interne `sets` - or `sets` n'est peuplée que par un
  import CSV complet et n'est JAMAIS resynchronisée avec `workoutDays` après un simple
  démarrage de l'app. Un redémarrage à froid laisse donc `sets` vide ; importer une
  séance juste après reconstruisait `workoutDays` à partir de ce `sets` presque vide →
  tout l'historique disparaissait, et ce state tronqué était aussitôt sauvegardé. Bug
  préexistant à cette session (pas introduit par les nouvelles features).
  **Correctif** : `mergeImportedSession()` ajoute maintenant directement les séries
  importées au bon `WorkoutDay` (existant ou nouveau) sans jamais reconstruire
  `workoutDays` depuis `sets`.
  **Confirmé par Romain** : "ça fonctionne et j'ai récupéré mes anciens exo depuis mon
  import, y compris la séance de vendredi." Historique intact, plus rien à
  investiguer côté récupération de données.
- [x] Renommage des onglets (retour Romain 05/09/2026) : titre de l'onglet accueil
  "Verifit" → **"Workout"** (`MainActivity`, l'app garde son nom "Verifit" par ailleurs) ;
  libellé de l'onglet "Diary" → **"Sessions"** (`AndroidManifest.xml`, label de
  `DiaryActivity`).
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
- [x] Calendrier de navigation avec indicateur de jours avec séance (patch \#7) : l'icône
  calendrier de la barre d'outils ouvre un calendrier mensuel fait maison (mois
  précédent/suivant, jour actuellement affiché en surbrillance, point sous chaque jour
  qui a déjà une séance enregistrée, comme sur FitNotes) — **validé par Romain**.
- [x] Commit des changements Android en cours (sélection multiple des séries,
  réorganisation/suppression des exercices sur `DayActivity` et l'onglet Workout,
  correctif du bug de perte de données, correctif de la poignée invisible) — **confirmé
  fait par Romain**.
- [x] **Mixup de message de commit sur le dépôt Coaching (05/09/2026)** : Romain a commité
  du vrai travail (intégration `workout_engine.py`/export JSON, \+553/-50 lignes sur 6
  fichiers) avec le message destiné à un commit Android sans rapport. Pas encore pushé
  au moment du signalement → corrigé par `git commit --amend` avec le bon message.
  **Confirmé corrigé par Romain.**

## En attente de décision / à planifier

- [ ] **Écarts Prévu/Réalisé** (retour Romain 05/09/2026 — mis en pause, reprendre en
  disant "traitons le sujet Écarts Prévu/Réalisé") : FitNotes/verifit ne distingue nulle
  part "ce qui était prévu" de "ce qui a été fait" - une série importée et sa version
  réellement effectuée sont la même unique valeur. Objectif : visualiser l'écart (ex :
  50,0 kg × 7 prévu vs 50,0 kg × 6 réalisé) pour ne plus avoir à écrire à la main un
  commentaire du type "échec d'un 7 RM", et pouvoir un jour ajuster le script à partir de
  ces écarts. Touche le modèle de données (`WorkoutSet`), pas juste l'affichage - 3
  points à trancher avant de coder, déjà posés à Romain :
  1. Ce qui compte comme "prévu" : la valeur écrite par `build_session_import_json` au
     moment de l'import, figée ensuite.
  2. Ce qui compte comme "réalisé" : la valeur que Romain modifie ensuite dans l'app -
     il faut que les deux coexistent pour une série importée, au lieu que la modif
     écrase la valeur prévue.
  3. Où voir l'écart : pendant la séance (série barrée/grisée avec la valeur prévue si
     modifiée) et/ou dans l'historique.
  Options de visualisation concrètes pas encore présentées à Romain (à lui montrer dès
  la reprise du sujet).

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

- [ ] **Réorganisation/suppression multiple sur l'onglet Sessions (ex-Diary)** :
  `DiaryExerciseAdapter` n'a pas encore reçu la mécanique appliquée à `DayActivity` et à
  l'onglet Workout. Explicitement déprioritisé par Romain pour l'instant, mais c'est le
  dernier des 3 écrans "liste d'exercices d'un jour" à ne pas avoir la feature.

## Prochain chantier majeur

- [x] **Intégrer le générateur de séances (`workout_engine.py`)** — livré côté Coaching
  05/09/2026 : `build_session_import_json` génère le fichier au format "Import Session"
  déjà supporté par l'app (aucune modif Android nécessaire), échauffement et holds
  inclus, commentaire de chaque série = l'analyse PR déjà calculée pour le `.md`. Voir
  `SYNC_FITNOTES_APP.md` côté dépôt Coaching pour le détail. En test à l'usage réel par
  Romain.

## Pas urgent

- [ ] Upgrade AGP (actuellement 7.4.2) — délibérément reporté, pas bloquant.

* * *

*Ce fichier est aussi tenu à jour dans le projet Claude "FitNotes\_Fork" ; les deux
copies sont synchronisées à chaque session.*
