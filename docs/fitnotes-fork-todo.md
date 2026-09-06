# TODO — FitNotes\_Fork

## Codé, en attente de test réel (06/09/2026)

- [ ] **Écarts Prévu/Réalisé** (sujet confirmé par Romain le 05/09/2026, décisions
  d'affichage tranchées le 06/09/2026 via questions posées à Romain) : **codé, livré sur
  l'appareil, pas encore buildé/testé**.
  Décisions retenues (les 3 points posés le 05/09 + le point 3 tranché le 06/09) :
  1. **Prévu** = la valeur écrite par `build_session_import_json` au moment de l'import,
     figée ensuite.
  2. **Réalisé** = la valeur que Romain modifie ensuite dans l'app - les deux coexistent
     désormais pour une série importée au lieu que la modif écrase le prévu.
  3. **Affichage** : "Badge discret + détail au tap" pendant la séance (recommandé,
     retenu) + **écran dédié "Écarts"** pour l'historique (les deux demandés par
     Romain, pas juste l'un ou l'autre).
  Implémentation :
  - `WorkoutSet` (`model/WorkoutSet.java`) gagne deux champs nullables
    `plannedReps`/`plannedWeight`, distincts de `reps`/`weight` (le "réalisé", toujours
    modifiable normalement). Renseignés UNIQUEMENT dans
    `DataStorage.mergeImportedSession()` au moment de la construction du `WorkoutSet` -
    restent `null` pour une saisie manuelle, un import CSV d'historique, ou une série
    déjà sauvegardée avant ce changement (Gson retombe sur `null` pour les champs
    absents des anciennes données). Vérifié que le flux d'édition d'une série
    (`AddExerciseActivity.updateSet()`) ne touche que `reps`/`weight` sur l'objet déjà
    en mémoire - `plannedReps`/`plannedWeight` ne sont donc jamais écrasés par une
    modification ultérieure.
  - `WorkoutSet.hasDiscrepancy()` : vrai si la série a un prévu ET que le réalisé actuel
    en diffère.
  - Badge discret (icône rouge, `ic_error_outline_24px`) ajouté à `workout_set_row.xml`
    (layout partagé), affiché uniquement si `hasDiscrepancy()` - dans `WorkoutSetAdapter`
    (onglet Workout + `DayActivity`) ET `AddExerciseWorkoutSetAdapter` (onglet Sessions).
    Un tap dessus ouvre un dialogue "Prévu / Réalisé" (lecture seule,
    `set_discrepancy_dialog.xml`).
  - Nouvel écran dédié `DiscrepancyHistoryActivity`, accessible depuis l'onglet Charts →
    menu (⋮) → "Ecarts Prevu/Realise" : liste chaque série en écart, toutes séances
    confondues, la plus récente en premier, avec le même dialogue de détail au tap.
  **Reste à faire une fois testé par Romain** : rien de prévu côté code sauf retour
  négatif ; potentiellement exploiter cet historique plus tard depuis
  `workout_engine.py` (mentionné par Romain comme piste, pas demandé formellement).

## Nouvelles demandes (06/09/2026)

- [ ] **PRIORITAIRE - Timer de repos : ne sonne jamais, et Reset ne fonctionne pas
  toujours** (retour Romain 06/09/2026) : "d'une façon générale quand je set un timer,
  je veux que même téléphone verrouillé, il sonne pour me dire que je peux reprendre ma
  série. Et je dois pouvoir lui faire confiance sur le fait de sonner."
  **Root-cause déjà identifiée en lisant `AddExerciseActivity.java`** (pas encore
  corrigé) :
  1. `startTimer()` utilise un `CountDownTimer` (`android.os.CountDownTimer`) tout ce
     qu'il y a de plus basique, attaché au cycle de vie de l'Activity/de l'app. Son
     `onFinish()` se contente de remettre `TimerRunning = false` et le texte du bouton à
     "Start" - **aucun son, vibration ou notification n'est déclenché nulle part**. Ce
     n'est pas "sonne parfois mal" : ça n'a jamais été implémenté, ni dans ce fork ni
     dans le dépôt de base. Un `CountDownTimer` classique ne tourne de toute façon de
     façon fiable que tant que l'Activity est au premier plan - téléphone verrouillé ou
     app en arrière-plan, Android peut throttle/tuer le Handler sous-jacent (Doze mode),
     donc même en ajoutant juste un son dans `onFinish()`, rien ne garantit qu'il se
     déclenche à l'heure si le téléphone est verrouillé.
  2. `resetTimer()` est gardé par `if(TimerRunning)` - le reset ne fait donc RIEN si le
     timer est en pause ou n'a pas encore démarré (seulement s'il tourne activement au
     moment du clic). C'est probablement le bug concret que Romain observe : il met le
     timer en pause, clique Reset, et rien ne se passe car `TimerRunning` est déjà à
     `false` à ce moment-là.
  **Pour un vrai "je peux lui faire confiance" (téléphone verrouillé inclus)**, il faudra
  a minima : corriger la garde de `resetTimer()` (reset doit fonctionner qu'il tourne,
  soit en pause, à tout moment) ; remplacer le `CountDownTimer` lié à l'Activity par un
  mécanisme qui survit à l'écran verrouillé/l'app en arrière-plan (`AlarmManager` avec
  alarme exacte, ou un `Service` en foreground avec notification) ; déclencher un son
  (+ idéalement vibration/notification à écran verrouillé) à la fin, avec un canal de
  notification dédié pour que le son soit fiable même en mode Ne pas déranger/silencieux
  selon les réglages système. Pas encore commencé.

- [ ] **Partager une séance ("Share workout")** (retour Romain 06/09/2026) : fonctionnalité
  qu'il avait sur FitNotes - génère un rapport texte de la séance affichée, partageable
  vers d'autres apps (Discord principalement dans son usage actuel) via le sélecteur de
  partage standard Android, ou copiable en texte brut pour coller où il veut. **Pas
  prioritaire pour l'instant** ("l'app n'est pas encore prête") mais Romain pense s'en
  servir assez vite. Pas encore investigué côté implémentation (probablement un
  `Intent.ACTION_SEND` texte/plain généré à partir des exercices/séries du jour affiché,
  depuis `DayActivity` et/ou `AddExerciseActivity`).

- [ ] **Générer un programme ("Routine")** (retour Romain 06/09/2026) : équivalent de la
  fonctionnalité "Routines" de FitNotes - sélectionner un ensemble d'exercices (dans le
  cas de Romain : organisés en cycles et en jours volume/force), cliquer "Generate
  workout" et ça pré-remplit le jour avec la séance vierge (structure d'exercices, pas
  encore de séries loggées). Dans notre cas, Romain imagine que ça déclencherait
  directement son script `workout_engine.py` pour proposer une séance. Idée
  d'amélioration évoquée (pas tranchée, "à voir comment faire") : rendre le script plus
  souple en allant chercher automatiquement dans son tableau de PR les exercices
  sélectionnés. **Pas prioritaire** ("même si génial") car son script fait déjà ça
  aujourd'hui "sans intelligence additionnelle" via le flux manuel Import Session
  existant - ceci ne serait qu'une couche d'automatisation/UX par-dessus un processus qui
  fonctionne déjà. À rapprocher du sujet Écarts Prévu/Réalisé et de l'intégration
  `workout_engine.py` déjà livrée (voir `docs/fitnotes-fork-plan.md`).

## Fait / validé

- [x] **Suppression de plusieurs séries en une fois** (retour Romain 05/09/2026) : dans
  `AddExerciseActivity` (l'écran de log d'un exercice), on ne pouvait supprimer qu'une
  série à la fois (long-press → menu popup → Supprimer), fastidieux pour nettoyer
  plusieurs séries d'un coup (ex : import de test à corriger). Ajout d'un mode sélection
  multiple : nouvelle icône "Select" dans la barre d'outils démarre une ActionMode
  (barre contextuelle standard Android) ; taper sur une série la coche/décoche ; l'icône
  Supprimer de la barre contextuelle supprime toute la sélection en une seule
  confirmation. Le long-press existant (Éditer/Supprimer une seule série) n'a pas été
  touché - c'est un ajout, pas un remplacement. **Testé sur l'app par Romain, ça
  fonctionne bien.**

- [x] **Réorganiser/supprimer plusieurs exercices depuis l'écran du jour** (retour Romain
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

- [x] **Même sélection multiple + réorganisation sur l'onglet Workout (accueil)** (retour
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
  **Bug \#1 trouvé et corrigé (05/09/2026)** : en revanche, si on drague directement un
  exercice via la poignée SANS passer par le bouton "Select" (drag "brut", hors sélection
  multiple), les séries ne se repliaient pas - le repli ne dépendait que de
  `selectionMode`, or la poignée reste utilisable même hors sélection multiple.
  **Bug \#2 trouvé et corrigé (05/09/2026, retour Romain après test)** : le premier
  correctif du bug \#1 (un simple booléen `dragging`, remis à `false` à la fin du geste
  dans `clearView()`) faisait bien réapparaître le repli au début du drag, mais Romain a
  vu tout se ré-déplier tout seul dès qu'il relâchait - "collapse pendant une seconde
  avant de s'expand à nouveau". Ce qu'il voulait : que ça reste replié après le drag,
  jusqu'à un tap manuel pour rouvrir. Remplacé par un suivi persistant par NOM
  (`collapsedExerciseNames`, même mécanique que la sélection) : `setDragging(true)`
  replie et mémorise toutes les séries au début du geste, `setDragging(false)` (fin du
  geste) ne les rouvre plus - elles ne se rouvrent qu'en tapant dessus (comportement
  identique au repli manuel classique). Sur l'onglet Workout, comme le tap sur une série
  naviguait jusqu'ici toujours directement vers l'écran de log (pas de repli manuel
  préexistant sur cet écran contrairement à `DayActivity`), le tap sur une série repliée
  la déplie d'abord au lieu de naviguer - il faut retaper pour naviguer une fois dépliée.
  **Confirmé fonctionnel par Romain (05/09/2026)** après build : "c'est validé".

- [x] **Icône de commentaire cliquable, puis inversion tap court/appui long** (retour
  Romain 05/09/2026) : sur l'onglet Workout, l'icône de commentaire sur une série
  (ajoutée au patch \#6) était visible mais pas cliquable - il fallait deviner qu'un
  long-press sur toute la carte l'ouvrait.
  **Manque plus large trouvé en même temps** : sur l'onglet Sessions, ouvrir un exercice
  d'une séance passée mène à `AddExerciseActivity` (l'onglet porte le nom de l'exercice),
  dont la liste de séries (`AddExerciseWorkoutSetAdapter`) n'avait ICI aucune icône de
  commentaire du tout, ni aucun moyen de voir/éditer un commentaire par série - alors que
  Romain en a besoin sur cet écran-là aussi (comme sur FitNotes). Ajouté : même icône,
  même dialogue voir/éditer/effacer, réutilisant `workout_set_row.xml` (déjà le bon
  layout, l'id `set_comment_indicator` existait déjà dedans) et le même mécanisme que
  `WorkoutSetAdapter`.
  **Amélioration UX ajoutée dans la foulée (retour Romain après test)** : le tap court
  ouvrait les stats (reps/charge/volume/1RM) et le long-press le commentaire - dans
  l'ordre inverse de ce que Romain utilise réellement (c'est le commentaire qui
  l'intéresse au quotidien, pas les stats). Inversé dans `WorkoutSetAdapter` (partagé par
  l'onglet Workout et l'écran `DayActivity`, même composant) : tap court → voir/éditer le
  commentaire, long-press → stats de la série. L'icône de commentaire n'a plus son propre
  `OnClickListener` séparé (redondant maintenant que le tap sur toute la carte fait la
  même chose) - elle reste un simple indicateur visuel (visible seulement si la série a
  un commentaire). `AddExerciseWorkoutSetAdapter` (écran de log actif, atteint aussi
  depuis Sessions) n'est PAS concerné par cette inversion - son tap sert à sélectionner
  la série à éditer et son long-press ouvre déjà un menu Éditer/Supprimer, un usage
  différent.
  **Confirmé fonctionnel par Romain (05/09/2026)** : "c'est validé et comité".

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
