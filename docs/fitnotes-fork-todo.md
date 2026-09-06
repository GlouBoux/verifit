# TODO — FitNotes\_Fork

## Codé, en attente de test réel (06/09/2026)

- [ ] **Timer de repos : ne sonne jamais, et Reset ne fonctionne pas toujours -
  FONCTIONNEL, sonnerie affinée (06/09/2026)** (retour Romain 06/09/2026) : "d'une façon
  générale quand je set un timer, je veux que même téléphone verrouillé, il sonne pour
  me dire que je peux reprendre ma série. Et je dois pouvoir lui faire confiance sur le
  fait de sonner." **Confirmé par Romain après correctif du crash** : "Ok c'est très
  bien ça fonctionne."
  **Root-cause identifiée en lisant `AddExerciseActivity.java`** :
  1. `startTimer()` utilisait un `CountDownTimer` basique, attaché au cycle de vie de
     l'Activity/de l'app. Son `onFinish()` se contentait de remettre
     `TimerRunning = false` et le texte du bouton à "Start" - aucun son, vibration ou
     notification n'était déclenché nulle part, et un `CountDownTimer` classique ne
     tourne de toute façon de façon fiable que tant que l'Activity est au premier plan.
  2. `resetTimer()` était gardé par `if(TimerRunning)` - le reset ne faisait donc RIEN
     si le timer était en pause ou pas encore démarré (seulement s'il tournait
     activement au moment du clic).
  **Codé, livré sur l'appareil, pas encore buildé/testé** :
  - Nouveau `RestTimerReceiver` (`BroadcastReceiver`) : joue un son + vibration +
    affiche une notification à la fin du repos, indépendamment du cycle de vie de
    l'Activity - fonctionne app ouverte, en arrière-plan, ou écran verrouillé. Canal de
    notification dédié (`rest_timer_channel`, importance haute, son de type alarme).
  - `AddExerciseActivity.scheduleTimerAlarm()` programme une alarme système au démarrage
    du timer via `AlarmManager.setAlarmClock()` (plutôt que `setExactAndAllowWhileIdle`) :
    exempte des restrictions Doze/App Standby, traitée par le système comme une vraie
    alarme (petite icône de réveil dans la barre de statut tant qu'elle est programmée).
    `cancelTimerAlarm()` l'annule à la pause/au reset. Le `CountDownTimer` existant ne
    pilote plus que l'affichage du décompte dans l'Activity.
  - `resetTimer()` remet maintenant `TimeLeftInMillis`/l'affichage à zéro
    inconditionnellement (plus seulement si le timer tournait activement) - seul l'arrêt
    du `CountDownTimer` (`pauseTimer()`) reste conditionné à `TimerRunning`, pour éviter
    un NPE sur un minuteur jamais créé.
  - Manifest : permission `VIBRATE`, `RestTimerReceiver` enregistré (`exported=false`).
  **Crash signalé par Romain au premier test (correctif 06/09/2026)** : cliquer sur
  "Start" faisait planter toute l'app (écran blanc, retour sur l'écran Workout du jour
  courant). Cause : `setAlarmClock()` **n'est pas** exempté de la permission
  `SCHEDULE_EXACT_ALARM` contrairement à ce qui était supposé au premier jet (vérifié sur
  `developer.android.com`) - non déclarée dans le Manifest, l'appel levait une
  `SecurityException` non rattrapée qui faisait planter tout le process. Corrigé :
  - Manifest : `SCHEDULE_EXACT_ALARM` déclarée (permission spéciale auto-accordée à
    l'installation vu le `targetSdkVersion` 31 de l'app - pas d'écran de permission
    supplémentaire pour Romain).
  - `scheduleTimerAlarm()` vérifie `canScheduleExactAlarms()` (Android 12+) avant
    d'appeler `setAlarmClock()`, et `try/catch(SecurityException)` par sécurité
    supplémentaire (ex. permission révoquée à la main après coup) - au pire le minuteur
    reste fiable seulement premier plan plutôt que de crasher toute l'app.
    `cancelTimerAlarm()` protégée de même par cohérence.
  **Confirmé fonctionnel par Romain** (crash résolu, sonnerie déclenchée) - reste un
  ajustement de confort signalé dans la foulée :
  - **"Je voudrai que la sonnerie ne perturbe pas. Sur fitnotes ça fait un Tuuut et
    c'est tout. Et c'est bien."** Le premier jet utilisait un son/attribut audio de
    type ALARME (`TYPE_ALARM`/`USAGE_ALARM`, pensé pour rester audible en mode
    silencieux) - qui se traduit sur la plupart des téléphones par une sonnerie longue
    et forte plutôt qu'un simple bip. **Corrigé** : bascule sur le son/attribut de
    notification standard (`TYPE_NOTIFICATION`/`USAGE_NOTIFICATION_EVENT`, plus proche
    du "Tuuut" de FitNotes), vibration ramenée à un seul buzz court (200ms), priorité/
    catégorie de la notification adoucies. Compromis assumé : ne passe plus forcément
    en mode silencieux/Ne pas déranger. Nouvel id de canal (`rest_timer_channel_v2`,
    un canal de notification étant immuable une fois créé sur Android 8+) pour que ce
    changement s'applique même sur le téléphone de Romain qui avait déjà reçu le
    premier jet. Codé, livré sur l'appareil, **pas encore rebuildé/retesté**.

## Nouvelles demandes (06/09/2026)

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

- [x] **Onglet Sessions : Undo réel sur "Set Deleted" + réordonnement des séries par
  appui long — VALIDÉ ET POUSSÉ (06/09/2026)** (retour Romain 06/09/2026, en testant le
  point tap = édition ci-dessous) : deux demandes suite à ce test.
  1. Le message "Set Deleted" proposait un bouton "Dismiss" qui ne faisait que fermer le
     message, sans annuler la suppression - Romain voulait un vrai revert.
  2. En recréant une série supprimée (test), elle atterrissait en fin de liste et Romain
     ne pouvait plus la remettre à sa place - aucun réordonnement des séries n'existait
     sur cet écran. Proposé et retenu : l'appui long lance ce réordonnement (drag & drop),
     et la boîte de dialogue Éditer/Supprimer qu'il ouvrait jusqu'ici n'a plus lieu d'être
     (Éditer = le tap simple, Supprimer = le bouton "Delete" du mode édition).
  - `SnackBarWithMessage` gagne `showSnackbarWithUndo()` : le bouton devient "Undo" et
    exécute une action au clic (restaurer la série, même objet donc mêmes
    id/commentaire/valeurs prévues) au lieu de simplement fermer le message. Recrée le
    jour si c'était sa dernière série. `showSnackbar()` existant inchangé pour tous les
    autres messages (Set Updated, Set Added, Comment saved...).
  - Réordonnement par glisser-déposer démarré par appui long sur la ligne (pas de
    poignée dédiée ici, contrairement aux exercices sur `DayActivity`/l'onglet Workout -
    le tap étant déjà pris par l'édition). `WorkoutDay.reorderSetsForExercise()` déplace
    les séries de cet exercice entre elles dans la liste `Sets` du jour, sans perturber
    l'entrelacement avec les séries des autres exercices du même jour.
  - Boîte de dialogue Éditer/Supprimer (`showSetPopupMenu`) supprimée.
  - **Ajustement (retour Romain 06/09/2026, en testant ce point)** : l'Undo remettait la
    série en dernière position au lieu de sa place d'origine. `WorkoutDay.insertSetAt()`
    insère à un index précis au lieu de toujours ajouter en fin de liste comme
    `addSet()` ; `deleteSetLogic()` capture l'index de la série dans `Sets` juste avant
    sa suppression et le transmet à `undoDeleteSet()` pour réinsertion au même endroit.
  Reste en local uniquement (pas de resynchronisation vers l'API `verifit_rs` en mode
  compte en ligne pour l'Undo, comme pour les autres mutations ajoutées depuis).

- [x] **Onglet Sessions : tap sur une série = édition, bouton "Delete" fonctionnel —
  VALIDÉ ET POUSSÉ (06/09/2026)** (retour Romain 06/09/2026) : "Je valide. Commité,
  pushé." Sur l'écran d'édition d'un exercice
  (`AddExerciseActivity`/`AddExerciseWorkoutSetAdapter`, atteint depuis l'onglet
  Sessions), un tap sur une série ne faisait rien d'utile - il fallait rester appuyé
  longtemps pour obtenir un menu Éditer/Supprimer. Comme sur FitNotes : un tap
  sélectionne directement la série, la précharge dans les champs du haut, et fait
  passer le bouton "Save" en "Update".
  - Le tap simple (hors mode sélection multiple) appelle
    `AddExerciseActivity.editSet()` - déjà utilisé par le menu Éditer du long-press,
    donc comportement identique, juste accessible en un tap au lieu de deux étapes.
    L'ancien `updateView()` préremplissait bien les champs mais sans jamais activer le
    mode édition (`isEditMode`) : cliquer "Save" ensuite créait une série en double au
    lieu de mettre à jour celle affichée.
  - **Effet de bord corrigé au passage** : en mode édition, le bouton du bas affiche
    "Delete" mais ne faisait en réalité QUE vider les champs (jamais de vraie
    suppression) - rendu fonctionnel (option choisie par Romain).
  En testant ce correctif, Romain a signalé deux points supplémentaires - voir l'entrée
  Undo + réordonnement ci-dessus.

- [x] **Écarts Prévu/Réalisé — VALIDÉ ET POUSSÉ (06/09/2026)** (sujet confirmé par Romain
  le 05/09/2026, décisions d'affichage tranchées le 06/09/2026 via questions posées à
  Romain) : "Ok ça fonctionne bien. J'ai comité et poussé. Je valide."
  Décisions retenues (les 3 points posés le 05/09 + le point 3 tranché le 06/09) :
  1. **Prévu** = la valeur écrite par `build_session_import_json` au moment de l'import,
     figée ensuite.
  2. **Réalisé** = la valeur que Romain modifie ensuite dans l'app - les deux coexistent
     désormais pour une série importée au lieu que la modif écrase le prévu.
  3. **Affichage** : "Badge discret + détail au tap" pendant la séance + **écran dédié
     "Écarts"** pour l'historique (les deux demandés par Romain).
  Implémentation :
  - `WorkoutSet` (`model/WorkoutSet.java`) gagne deux champs nullables
    `plannedReps`/`plannedWeight`, distincts de `reps`/`weight` (le "réalisé", toujours
    modifiable normalement). Renseignés UNIQUEMENT dans
    `DataStorage.mergeImportedSession()` au moment de la construction du `WorkoutSet` -
    restent `null` pour une saisie manuelle, un import CSV d'historique, ou une série
    déjà sauvegardée avant ce changement.
  - `WorkoutSet.hasDiscrepancy()` : vrai si la série a un prévu ET que le réalisé actuel
    en diffère.
  - Badge discret (icône rouge, `ic_error_outline_24px`) sur `workout_set_row.xml`,
    dans `WorkoutSetAdapter` (onglet Workout + `DayActivity`) ET
    `AddExerciseWorkoutSetAdapter` (onglet Sessions). Un tap dessus ouvre un dialogue
    "Prévu / Réalisé" (lecture seule, `set_discrepancy_dialog.xml`).
  - Nouvel écran dédié `DiscrepancyHistoryActivity`, accessible depuis l'onglet Charts →
    menu (⋮) → "Ecarts Prevu/Realise" : liste chaque série en écart, toutes séances
    confondues, la plus récente en premier.
  Piste évoquée par Romain pour plus tard (pas demandée formellement) : exploiter cet
  historique depuis `workout_engine.py`.

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

- [ ] **Idée : simulation "à blanc" d'une vraie séance (test end-to-end)** (retour Romain
  06/09/2026, à propos du timer de repos - "je ne sais pas s'il faut vraiment le mettre
  en todo") : dérouler tout le process comme une vraie séance (créer le jour, logger des
  séries, lancer le timer de repos, éditer/réordonner/supprimer, etc.) sans la faire
  réellement (pour ne pas perturber l'entraînement de Romain), dans le but de repérer les
  points de friction éventuels avant qu'ils ne se manifestent en conditions réelles. Pas
  tranché comme prioritaire, gardé ici comme piste à évaluer.

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
