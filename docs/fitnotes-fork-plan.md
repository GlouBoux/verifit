# FitNotes\_Fork — dépôt de base et premières fonctionnalités

## Dépôt de base : MakisChristou/verifit (confirmé)

verifit reste le bon choix. Comparé aux alternatives trouvées lors des recherches
(opengym — application web auto\-hébergée, pas un client Android natif ; Flexify et
Lotti — Flutter, pas natif ; OpenScale — suivi du poids/mensurations uniquement, pas un
journal de musculation ; Fast N Fitness — Java mais maintenance incertaine et bien moins
de fonctionnalités) : aucune n'est plus proche de FitNotes qu'un journal de musculation
Android natif.

- Java, Android natif, GPL\-3.0, minSdk 16 / target 31, Gradle.
- UI explicitement inspirée de FitNotes ; a déjà le CRUD des exercices, les exercices
  personnalisés, les commentaires par exercice, l'import/export CSV (local \+ WebDAV), le
  suivi volume/1RM/records, les graphiques, le minuteur de repos.
- Archivé sur GitHub (juin 2025, dernière release mars 2025) — plus d'activité en amont,
  mais ce n'est pas un problème pour un fork qu'on compte maintenir soi\-même.
- Le dépôt embarque aussi un backend de compte en ligne optionnel ("verifit\_rs", une API
  Rust) pour la synchronisation multi\-appareils — **décision prise (04\-05/09/2026) : à
  retirer**, voir section dédiée plus bas.
- **Décidé le 05/09/2026 : on reste définitivement sur le fork perso
  (`GlouBoux/verifit`), pas de PR vers l'amont `MakisChristou/verifit`.** C'est le
  projet de Romain, taillé pour ses propres besoins.

## Fait architectural clé qui conditionne les fonctionnalités

`Exercise` n'a pas d'ID — seulement un `Name` (String) \+ `BodyPart` \+ `favorite`. Toutes
les recherches dans `DataStorage` (`doesExerciseExist`, `editExercise`, `deleteExercise`,
`getExerciseCategory`, ...) comparent les exercices par correspondance exacte de nom.
C'est exactement le problème de convention de nommage décrit au départ : il n'y a rien à
"cloner" au niveau du modèle à part un nom \+ une zone corporelle, et une collision n'est
détectée que par égalité de chaînes, jamais empêchée par une contrainte de schéma.

## Fonctionnalité 1 — Dupliquer un exercice (implémentée, testée sur l'app ✅)

Appui long sur un exercice dans l'onglet Exercises → **Duplicate**. Ouvre la même boîte
de dialogue qu'Edit, pré\-remplie avec `"<nom> (copy)"` et la même zone corporelle que
l'original. Confirmé par Romain : fonctionne bien.

## Fonctionnalité 2 — Import Session (implémentée, testée sur l'app ✅)

Ouvrir un jour (`DayActivity`) → barre d'outils → **Import Session** → choisir un fichier
`.json` → ses séries sont ajoutées à ce jour, de façon additive. Schéma documenté dans
`docs/session-import-format.md`. C'est le point d'intégration prévu pour le générateur de
séances (workout\_engine.py), prochain chantier majeur (voir tout en bas). **Statut :
validé, fonctionne** (le crash "IllegalStateException" croisé en route était une fausse
manip, corrigé quand même côté robustesse — patch \#4).

## Fonctionnalité 3 — Commentaire par série (implémentée, validée par Romain ✅)

Le modèle `WorkoutSet` a toujours eu un champ `comment` individuel par série, mais la
seule UI existante pour en éditer un ("Exercise Comments" dans `AddExerciseActivity` →
`saveComment()`) applique le même texte à **toutes** les séries de l'exercice du jour —
donc dans les faits, un seul commentaire partagé, pas un vrai commentaire par série.
Romain veut ce comportement pour deux raisons : il l'a sur FitNotes, et ça permettra de
récupérer fidèlement ces commentaires si/quand il migre plus de données.

Implémenté (patch \#6) en restant additif, sans toucher à la fonctionnalité existante :

- `workout_set_row.xml` : petite icône (réutilise `ic_comment_24px`), visible uniquement
  si la série a un commentaire — repérable d'un coup d'œil, y compris en relisant des
  données importées.
- `WorkoutSetAdapter` : un appui long sur une série ouvre un dialogue (réutilise
  `add_exercise_comment_dialog.xml`, même look que le dialogue existant) pour voir/
  éditer/effacer le commentaire de **cette série précise**, indépendamment des autres
  séries du même exercice. L'appui court continue d'ouvrir le dialogue de stats existant
  (volume, 1RM), inchangé.

Non fait délibérément : pas de synchronisation vers l'API en ligne `verifit_rs` pour
cette nouvelle mutation (comme pour les autres fonctionnalités ajoutées sur cette
branche) — de toute façon sans objet vu que `verifit_rs` est voué à disparaître.

**Statut au 05/09/2026 : livré, validé par Romain.**

## Bugs corrigés en cours de route (dépôt de base, sans rapport avec les fonctionnalités)

- **Dépendances mortes** (patch \#2) : `jcenter()` (fermé depuis 2022) sans
  `mavenCentral()` en repli ; deux dépendances non utilisées supprimées.
- **Drawable manquant** (patch \#3) : `@drawable/background_transparent`, bug latent du
  dépôt d'origine (présent aussi sur `master`).
- **Crash Import Session** (patch \#4) : `SessionImporter` ne rattrapait que
  `JsonSyntaxException` alors que Gson lève directement une `IllegalStateException`
  quand un fichier n'a pas la forme JSON attendue.
- **L'app revient toujours à aujourd'hui** (patch \#5) : `MainActivity.initViewPager()`
  recréait l'adapter du ViewPager et resettait sa position sur aujourd'hui à chaque
  `onRestart()` (déclenché en réduisant l'app pendant que l'onglet Verifit était affiché).
  Corrigé : la position précédente est mémorisée et restaurée. **Validé par Romain.**
  Limite connue, acceptée : ne couvre que le cas "app réduite" (process vivant), pas un
  kill de process par Android.
- **Icône calendrier inerte** (patch \#5) : ne faisait que recentrer sur aujourd'hui —
  jamais de sélecteur de date, malgré tout le code déjà en place
  (`DatePickerDialog.OnDateSetListener`, `onDateSet()` fonctionnel) mais jamais branché
  à un `.show()`. Corrigé : ouvre maintenant ce sélecteur, pré\-rempli avec le jour
  affiché. **Validé par Romain.** Indication visuelle des jours avec séance : voir
  "Fonctionnalité 4" plus haut — le sélecteur basique d'Android a ensuite été remplacé
  par un calendrier fait maison (patch \#7) pour pouvoir l'afficher.

## Incident : MainActivity.java corrompu (05/09/2026) — leçon pour le workflow

Après le patch \#5, un build a échoué avec des erreurs de syntaxe absurdes
("class, interface, or enum expected" dès la ligne 1). Cause : `MainActivity.java`
contenait littéralement le **texte du patch git** (`From ... Subject: [PATCH] ... diff --git ...`) au lieu du vrai code Java — vraisemblablement une tentative
d'"application" manuelle du patch (copier\-coller de son contenu dans le fichier) plutôt
qu'une commande git. Corrigé en renvoyant le vrai fichier depuis la copie de référence
saine côté Claude. **Rappel ajouté dans `docs/fitnotes-fork-workflow.md` : les patches
`000X-....patch` sont uniquement pour l'archive git, jamais à ouvrir ni coller dans un
fichier source — les fichiers sont de toute façon déjà à jour sur la machine de Romain
à chaque session.**

La branche `feature/duplicate-exercise-and-session-import` a maintenant 7 commits de
fonctionnalités/correctifs (\+ quelques commits de documentation/outillage). Livrés en
patches `0001` à `0007` (`git am`\-compatibles, pour archive uniquement) et tous appliqués
directement sur la machine de Romain via le pont avec son appareil. **Poussée vers
`GlouBoux/verifit` par Romain ; pas de PR vers l'amont (décidé, voir plus haut).**

## Import de la vraie base FitNotes (04/09/2026)

Backup FitNotes réel (`FitNotes_Backup_2026_09_03_14_06_11.fitnotes`, une base SQLite)
converti en CSV compatible verifit via script Python : **3784 séries** / 369 exercices /
186 jours (28/10/2024 → 31/08/2026), 823 séries avec commentaire. Deux catégories de
séries exclues (verifit n'a pas de notion de temps/distance) : 469 incomplètes/
planifiées, 177 basées sur durée/distance plutôt que poids×répétitions.

Bug trouvé et corrigé en route : poids et répétitions inversés après le premier import,
causé par un ordre de colonnes CSV incompatible avec un bug de nommage compensatoire dans
`DataStorage.csvToSets()`. Corrigé côté script de conversion (alignement sur l'ordre
natif de verifit) — **confirmé par Romain : le ré\-import fonctionne correctement.**

## Chantier : retirer le backend en ligne verifit\_rs (décidé le 05/09/2026)

Romain confirme : à retirer, cette fonctionnalité est de toute façon abandonnée
officiellement côté upstream. Périmètre réel du changement (recherché mais pas encore
fait) : au moins 14 fichiers référencent `verifit_rs`/`verifitrs`/`WorkoutSetsApi` ou le
mode offline qui leur est lié —
`MainActivity.initActivity()` (fetch au démarrage si pas en mode offline/cache),
`AddExerciseActivity.saveComment()` (branche selon le mode offline pour savoir s'il faut
appeler l'API ou pas), `DataStorage`, `SettingsActivity`, `ExerciseAdapter`,
`BackupService`, la classe maison `SharedPreferences` (`isOfflineMode()`,
`enableOfflineMode()`, ...), plus tout le package `verifitrs` (`WorkoutSetsApi`,
`UsersApi`, `ExercisesApi`, `ResponseLoginUser`) et les 3 Activity dédiées
(`LoginActivity`, `ChangePasswordActivity`, `ForgotPasswordActivity`).

Pas de SDK Android disponible côté Claude pour compiler et vérifier une suppression à la
volée (seul Romain peut builder, dans Android Studio) — un changement de cette ampleur
mérite d'être fait par étapes vérifiables plutôt qu'en un seul gros commit non testable
avant le prochain build : d'abord forcer le mode offline en permanence et masquer les
entrées UI de login/compte (risque faible, réversible), puis supprimer le code mort
correspondant une fois le premier changement validé par Romain. À planifier pour une
prochaine session dédiée.

## Fonctionnalité 4 — Calendrier de navigation avec indicateur de jours avec séance (implémentée, à valider par Romain)

Priorisé par Romain juste après le commentaire par série (05/09/2026), plutôt que
reporté comme envisagé initialement.

Le sélecteur de date basique d'Android (`DatePickerDialog`, branché en patch \#5) n'a
aucun moyen de décorer des jours individuels — impossible d'y afficher un indicateur
par jour. Implémenté (patch \#7) : un calendrier mensuel fait maison
(`CalendarPickerDialog`), qui remplace entièrement le `DatePickerDialog` sur l'icône
calendrier de la barre d'outils :

- Grille de 42 cases (6 semaines, dimanche en premier), navigation mois précédent/
  suivant, jour actuellement affiché entouré d'un cercle.
- Un petit point apparaît sous chaque jour qui a déjà une séance enregistrée
  (`DataStorage.getDays()`), comme sur FitNotes.
- Choisir un jour ouvre `DayActivity` pour cette date, exactement comme avant.

Pas de nouvelle dépendance Gradle : une lib de calendrier (`material-calendar-view`)
avait déjà été retirée plus tôt comme dépendance morte, cassée depuis la fermeture de
JCenter (voir plus bas). La réintroduire (à une version plus récente) et apprendre son
API sans pouvoir compiler pour vérifier semblait plus risqué qu'une grille "fait main"
volontairement simple.

**Statut au 05/09/2026 : livré, validé par Romain.**

## Fonctionnalité 5 — Sélection multiple et réorganisation des exercices (implémentée, validée ✅)

Demande de Romain (05/09/2026) : partout où l'app affiche une sélection de séries ou une
liste d'exercices d'un jour (hors onglet Exercises), pouvoir supprimer en masse ou
réordonner par glisser-déposer, "comme sur FitNotes".

Trois écrans distincts affichent chacun "la liste des exercices d'un jour", codés
indépendamment (aucun n'hérite des autres) :

- **AddExerciseActivity** (log d'un exercice) : sélection multiple des séries
  (`AddExerciseWorkoutSetAdapter`) - suppression en masse via ActionMode. Pas de
  réorganisation ici (les séries d'un exercice n'ont pas d'ordre à changer).
- **DayActivity** (écran du jour, atteint via l'icône calendrier) : sélection multiple +
  suppression + glisser-déposer des exercices (`DayExerciseAdapter`). Nouveau champ
  `WorkoutDay.ExerciseOrder` pour mémoriser l'ordre par jour (avant : toujours
  alphabétique, jamais mémorisé).
- **Onglet Workout** (accueil, `ViewPagerExerciseAdapter` dans le carrousel `ViewPager2`
  de `MainActivity`) : même mécanique, plus délicate techniquement car les pages du
  carrousel (et leurs adapters d'exercices) sont recyclées au fil du swipe -
  l'`ItemTouchHelper` est donc créé une seule fois par ViewHolder de page recyclée
  (constructeur), jamais recréé à chaque bind, pour éviter d'empiler des
  `OnItemTouchListener` sur la même RecyclerView.

Amélioration demandée dans la foulée ("comme FitNotes") : replier automatiquement les
séries de chaque exercice pendant une sélection multiple ou un glisser-déposer, pour
mieux visualiser/sélectionner en masse. Deux bugs trouvés en la construisant :

1. Le repli ne se déclenchait qu'en passant par le bouton "Select" (mode sélection),
   jamais lors d'un glisser-déposer démarré directement via la poignée.
2. Une fois corrigé, le repli disparaissait tout seul une seconde après la fin du geste
   de drag (le premier correctif utilisait un simple booléen remis à `false` en fin de
   geste). Attendu par Romain : rester replié jusqu'à un tap manuel pour rouvrir - remplacé
   par un suivi persistant par nom d'exercice (`collapsedExerciseNames`, même mécanique
   que la sélection).

Sélection et repli sont tous les deux suivis par **nom d'exercice** plutôt que par
position, dans `DayExerciseAdapter` et `ViewPagerExerciseAdapter` : la poignée de
réorganisation reste active pendant la sélection multiple, donc un suivi par position
deviendrait faux dès qu'un glisser-déposer change l'ordre pendant qu'une sélection est en
cours.

**Statut au 05/09/2026 : livré, validé par Romain sur les trois écrans concernés**
(Sessions/`DiaryExerciseAdapter` explicitement laissé de côté pour l'instant, à la
demande de Romain).

## Fonctionnalité 6 — Commentaire par série : icône cliquable, écran Sessions, inversion tap/appui long (implémentée, validée ✅)

Suite logique de la Fonctionnalité 3 (patch \#6), affinée par les retours d'usage réel de
Romain (05/09/2026) :

- L'icône de commentaire sur une série (onglet Workout, `WorkoutSetAdapter`) était
  visible mais pas cliquable directement - il fallait deviner qu'un long-press sur toute
  la carte l'ouvrait.
- En creusant, l'écran ouvert en tapant un exercice d'une séance passée (onglet
  Sessions → `AddExerciseActivity`, dont l'onglet porte le nom de l'exercice) s'est
  révélé n'avoir AUCUN commentaire par série (`AddExerciseWorkoutSetAdapter` n'avait
  jamais reçu cette feature du patch \#6), alors que Romain en a besoin là aussi. Ajouté :
  même icône, même dialogue voir/éditer/effacer, en réutilisant `workout_set_row.xml`
  (le bon layout, avec l'id `set_comment_indicator` déjà présent) et le même mécanisme
  que `WorkoutSetAdapter`.
- Après un premier essai (icône cliquable en plus du tap/long-press existants), retour
  d'usage de Romain : c'est le commentaire qui l'intéresse au quotidien, pas les stats
  (reps/charge/volume/1RM) - **inversion complète** dans `WorkoutSetAdapter` (partagé par
  l'onglet Workout et `DayActivity`) : tap court → commentaire, long-press → stats.
  `AddExerciseWorkoutSetAdapter` (écran de log actif) n'est pas concerné, son tap/
  long-press ont un usage différent (sélectionner une série à éditer / menu
  Éditer-Supprimer).

**Statut au 05/09/2026 : livré, validé et commité par Romain** ("c'est validé et
comité").

## Fonctionnalité 7 — Écarts Prévu/Réalisé (implémentée et validée par Romain ✅)

Sujet annoncé par Romain en fin de session le 05/09/2026 comme prochain chantier.
FitNotes/verifit ne distingue nulle part "ce qui était prévu" de "ce qui a été fait" -
une série importée et sa version réellement effectuée sont la même unique valeur.
Objectif : visualiser l'écart (ex : 50,0 kg × 7 prévu vs 50,0 kg × 6 réalisé) pour ne
plus avoir à écrire à la main un commentaire du type "échec d'un 7 RM", et pouvoir un
jour ajuster le script à partir de ces écarts.

Trois points tranchés avec Romain avant de coder (via questions posées le 06/09/2026) :

1. **Prévu** = la valeur écrite par `build_session_import_json` au moment de l'import,
   figée ensuite.
2. **Réalisé** = la valeur que Romain modifie ensuite dans l'app - les deux coexistent
   pour une série importée, la modification n'écrase plus la valeur prévue.
3. **Affichage** : "badge discret + détail au tap" pendant la séance (option
   recommandée, retenue) **et** un écran dédié "Écarts" pour l'historique complet -
   Romain a demandé les deux, pas l'un ou l'autre.

Implémentation :

- `WorkoutSet` (`model/WorkoutSet.java`) gagne `plannedReps`/`plannedWeight` (nullables),
  séparés de `reps`/`weight` (le "réalisé"). Seul `DataStorage.mergeImportedSession()`
  les renseigne, au moment de construire chaque `WorkoutSet` importé - toute autre
  origine (saisie manuelle dans `AddExerciseActivity`, import CSV d'historique, séries
  déjà sauvegardées avant ce changement) les laisse à `null`, Gson retombant sur cette
  valeur par défaut pour les anciennes données sérialisées sans ces champs. Vérifié que
  `AddExerciseActivity.updateSet()` (flux d'édition d'une série) ne modifie que
  `reps`/`weight` sur l'objet déjà en mémoire, jamais `plannedReps`/`plannedWeight` -
  aucun risque que la modification du réalisé efface le prévu.
- `WorkoutSet.hasDiscrepancy()` compare prévu/réalisé (vrai seulement si un prévu existe
  ET diffère du réalisé actuel).
- Badge discret (icône `ic_error_outline_24px`, teinte rouge) ajouté au layout partagé
  `workout_set_row.xml`, visible uniquement si `hasDiscrepancy()` - câblé dans
  `WorkoutSetAdapter` (onglet Workout + `DayActivity`) et `AddExerciseWorkoutSetAdapter`
  (onglet Sessions), les deux endroits qui peuvent afficher une série importée. Un tap
  dessus ouvre `set_discrepancy_dialog.xml` (lecture seule : "Prévu : ... / Réalisé :
  ...").
- Nouvel écran `DiscrepancyHistoryActivity`, accessible depuis l'onglet Charts → menu
  (⋮) → "Ecarts Prevu/Realise" (nouvel item ajouté à `charts_activity_menu.xml`, à côté
  de "Personal Records" qui suit le même schéma de navigation). Parcourt tous les
  `WorkoutDay` enregistrés, ne garde que les séries en écart, triées par date
  décroissante (les dates étant au format `yyyy-MM-dd`, un simple tri de chaînes suffit)
  - même dialogue de détail au tap sur une ligne.

**Statut au 06/09/2026 : livré, validé et poussé par Romain** ("Ok ça fonctionne bien.
J'ai comité et poussé. Je valide."). Piste évoquée par Romain pour plus tard (pas
demandée formellement) : exploiter cet historique depuis `workout_engine.py`.

## Fonctionnalité 8 — Onglet Sessions : tap = édition d'une série (implémentée 06/09/2026, pas encore testée)

En validant la Fonctionnalité 7, Romain a signalé un problème d'UX séparé sur l'écran
d'édition d'un exercice (`AddExerciseActivity`/`AddExerciseWorkoutSetAdapter`, atteint
depuis l'onglet Sessions) : un tap sur une série ne faisait rien de visible - il fallait
un appui long pour obtenir un menu Éditer/Supprimer. Voulu, comme sur FitNotes : un tap
sélectionne directement la série pour édition (champs du haut préremplis, bouton "Save"
→ "Update").

En creusant, le tap appelait déjà un `updateView()` qui préremplissait bien les champs,
mais sans jamais activer `isEditMode` - cliquer "Save" ensuite créait donc une série en
double au lieu de mettre à jour celle affichée, d'où l'impression que "le tap ne fait
rien". Remplacé par un appel direct à `AddExerciseActivity.editSet()` (déjà utilisé par
le menu Éditer du long-press) ; `updateView()` supprimé (dead code).

**Effet de bord trouvé et corrigé au passage** : en mode édition, le bouton du bas
affiche "Delete" mais ne faisait en réalité QUE vider les champs de saisie, jamais une
vraie suppression - bug préexistant invisible tant que ce mode n'était atteint que via
le long-press (peu emprunté). Le tap devenant le chemin principal pour sélectionner une
série, ce bouton trompeur allait devenir bien plus visible. Question posée à Romain :
le rendre fonctionnel, ou garder "Clear" même en édition. **Choix de Romain : le rendre
fonctionnel** - `clickClear()` déclenche maintenant une vraie suppression (avec
confirmation, comme le menu Supprimer existant) quand le bouton affiche "Delete", et
`deleteSetLogic()` sort proprement du mode édition (champs vidés, bouton remis à
"Save") une fois la suppression effectuée - sans quoi un "Save" ultérieur aurait recréé
une série avec les valeurs de celle qu'on vient d'effacer.

**Statut au 06/09/2026 : codé et livré sur la machine de Romain, pas encore
buildé/testé.**

## Incident : bug critique de perte de données à l'Import Session (05/09/2026)

Romain a signalé, après avoir recompilé/réinstallé l'app puis importé le JSON de la
séance de vendredi, ne plus voir QUE cette séance dans "Sessions" - tout l'historique
précédent avait disparu de l'app. Signal fort qui a immédiatement pris le pas sur le
reste du travail en cours ce jour-là.

**Cause** : `DataStorage.mergeImportedSession()` (le code de l'Import Session)
appelait `setsToEverything()`, qui vide `workoutDays` et le reconstruit ENTIÈREMENT à
partir de la liste interne `sets` - or `sets` n'est peuplée que par un import CSV
complet et n'est jamais resynchronisée avec `workoutDays` après un simple démarrage de
l'app (`loadWorkoutData()` charge directement dans `workoutDays`, sans passer par
`sets`). Un redémarrage à froid (recompilation + réinstallation) laisse donc `sets`
vide ; importer une séance juste après reconstruisait `workoutDays` à partir de ce
`sets` presque vide → tout l'historique disparaissait, et ce state tronqué était
aussitôt sauvegardé. Bug préexistant (la feature Import Session et son commentaire
"additive, ne touche jamais l'historique" datent d'avant cette session) - une trace de
la même fragilité de `sets` était même déjà documentée dans `CalendarPickerDialog.java`
(patch \#7).

**Correctif** : `mergeImportedSession()` ajoute maintenant directement les séries
importées au bon `WorkoutDay` (existant ou nouveau) sans jamais reconstruire
`workoutDays` depuis `sets`. `calculatePersonalRecords()` a été vérifiée comme
n'itérant que sur `workoutDays`, donc non affectée par cette divergence.

**Confirmé par Romain** après rebuild : "ça fonctionne et j'ai récupéré mes anciens exo
depuis mon import, y compris la séance de vendredi." Historique intact.

## Prochain chantier majeur : intégrer le générateur de séances

`workout_engine.py` — outil Python qui calcule des feuilles de route d'entraînement et
des charges (ancrage S1, pivotement RM, contraintes de delta minimum). Le format JSON
"Import Session" est déjà pensé comme point d'intégration. Condition explicite de
Romain : le script doit rester facile à faire évoluer tant qu'on est en période de
test — éviter un couplage trop rigide entre les deux projets pour l'instant.

## Questions ouvertes pour la prochaine session

1. **Retour de test attendu sur le tap = édition (Fonctionnalité 8 ci-dessus)** — codé le
   06/09/2026, pas encore buildé ni testé par Romain sur l'appareil.
2. **Timer de repos** (retour Romain 06/09/2026, voir `docs/fitnotes-fork-todo.md`,
   section "Nouvelles demandes") : ne sonne jamais à la fin, Reset ne fonctionne pas
   toujours - marqué prioritaire par Romain, mais mis de côté le 06/09/2026 au profit des
   Écarts Prévu/Réalisé puis du correctif Sessions ci-dessus. Root-cause déjà identifiée,
   pas encore corrigé.
3. Étapes précises pour retirer verifit\_rs (voir section dédiée) — par où commencer ?
4. Si le kill de process par Android (pas juste la mise en arrière\-plan) s'avère gênant
   en pratique pour la conservation du jour affiché, ajouter une vraie persistance
   (SharedPreferences) du dernier jour consulté.

Voir aussi `docs/fitnotes-fork-todo.md` pour le suivi au jour le jour.

* * *

*Ce fichier est aussi tenu à jour dans le projet Claude "FitNotes\_Fork".*
