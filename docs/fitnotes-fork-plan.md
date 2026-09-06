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

## Fonctionnalité 8 — Onglet Sessions : tap = édition d'une série (implémentée et validée par Romain ✅)

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

**Statut au 06/09/2026 : livré, validé et poussé par Romain** ("Je valide. Commité,
pushé."). En testant, Romain a signalé deux points supplémentaires - voir
Fonctionnalité 9 ci-dessous.

## Fonctionnalité 9 — Undo sur suppression de série + réordonnement par appui long (implémentée et validée le 06/09/2026)

En validant la Fonctionnalité 8, Romain a testé le bouton "Delete" et le message "Set
Deleted" qui l'accompagne, et signalé deux points :

1. Le bouton "Dismiss" du message "Set Deleted" ne faisait que fermer le message, sans
   annuler la suppression - "j'ai bien l'idée de garder le revert".
2. En recréant une série qu'il venait de supprimer (test), elle atterrit en fin de liste
   et il ne peut plus la remettre à sa place d'origine - aucun réordonnement des séries
   n'existait sur cet écran (contrairement aux exercices, voir Fonctionnalité 5). Romain
   propose que l'appui long lance ce réordonnement, rendant la boîte de dialogue
   Éditer/Supprimer obsolète (Éditer = le tap simple depuis la Fonctionnalité 8,
   Supprimer = le bouton "Delete" du mode édition) - il propose de la supprimer.

Implémentation :

- `SnackBarWithMessage` (jusqu'ici un simple wrapper avec un unique bouton "Dismiss" qui
  ne faisait que fermer le message, utilisé pour tous les messages de l'app - Set
  Updated, Set Added, Comment saved...) gagne `showSnackbarWithUndo(message,
  undoAction)` : le bouton devient "Undo" et exécute l'action fournie au clic.
  `showSnackbar(message)` existant est inchangé (délègue en interne avec une action
  vide) - aucun autre appelant impacté.
- `AddExerciseActivity.deleteSetLogic()` utilise ce nouveau bouton pour vraiment
  restaurer la série (`undoDeleteSet()`) : comme la série supprimée n'est qu'un objet
  retiré d'une liste (jamais modifié ni détruit), la réinsérer via `WorkoutDay.addSet()`
  restitue exactement le même id/commentaire/valeurs prévues qu'avant suppression -
  recrée le `WorkoutDay` si c'était sa dernière série (cas nettoyé par
  `deleteSetLogic()` juste avant).
- Réordonnement par glisser-déposer sur `AddExerciseWorkoutSetAdapter`/
  `AddExerciseActivity`, avec un `ItemTouchHelper` calqué sur celui de `DayActivity`
  (Fonctionnalité 5), à une différence près, voulue par Romain : le drag démarre par un
  appui long directement sur la ligne (`isLongPressDragEnabled() = true`, désactivé
  pendant le mode sélection multiple), pas via une poignée dédiée - le tap étant déjà
  pris par l'édition, une poignée séparée n'aurait pas eu de sens ici. Persistance via
  la nouvelle `WorkoutDay.reorderSetsForExercise()` : contrairement à `ExerciseOrder`
  (une liste d'ordre dédiée), les séries n'ont pas de champ d'ordre séparé - leur ordre
  d'affichage vient directement de leur position dans la liste `Sets` du jour, qui
  mélange les séries de TOUS les exercices. La méthode retrouve donc les index de
  `Sets` appartenant à cet exercice et y réécrit le nouvel ordre à ces mêmes positions,
  ce qui déplace ces séries entre elles sans perturber leur entrelacement avec celles
  des autres exercices.
- Boîte de dialogue Éditer/Supprimer (`showSetPopupMenu`, menu
  `set_add_exercise_activity_menu.xml`) supprimée du code (fichier de menu laissé en
  place, inoffensif mais inutilisé).

Non fait délibérément, comme pour les autres mutations locales ajoutées depuis
(commentaires, Écarts Prévu/Réalisé) : pas de resynchronisation de l'Undo vers l'API en
ligne `verifit_rs` en mode compte - de toute façon vouée à disparaître.

**Ajustement (retour Romain 06/09/2026, en testant ce point)** : l'Undo restituait bien
le même objet (id/commentaire/valeurs prévues intacts) mais le réinsérait toujours en
fin de `Sets` via `addSet()`, au lieu de sa position d'origine parmi les séries de son
exercice. `WorkoutDay.insertSetAt(index, set)` (nouveau) insère à un index précis, bornée
aux limites actuelles de la liste (undo tardif après d'autres changements). Le point
d'insertion est capturé par `deleteSetLogic()` (indexOf de la série dans `Sets`, juste
avant l'appel à `removeSet()`) et transmis à `undoDeleteSet()`.

**Statut au 06/09/2026 : livré, validé et poussé par Romain** ("validé et pushé.").

## Fonctionnalité 10 — Timer de repos fiable (fonctionnelle et validée le 06/09/2026 ; bip dédié + volume + durée réglables en cours de test)

Signalé "PRIORITAIRE" par Romain le 06/09/2026 mais mis de côté à plusieurs reprises au
profit d'autres demandes ce jour-là : "d'une façon générale quand je set un timer, je
veux que même téléphone verrouillé, il sonne pour me dire que je peux reprendre ma
série. Et je dois pouvoir lui faire confiance sur le fait de sonner." Deux problèmes
distincts identifiés en lisant `AddExerciseActivity.java` :

1. Le `CountDownTimer` (`android.os.CountDownTimer`) utilisé pour le minuteur ne
   déclenchait aucun son/vibration/notification dans son `onFinish()` - jamais
   implémenté, ni dans ce fork ni dans le dépôt de base. De plus, un `CountDownTimer`
   classique est un simple `Handler` attaché au cycle de vie de l'Activity : Android
   peut le throttle ou le tuer (Doze, arrière-plan, kill de process), donc même en lui
   ajoutant un son, rien ne garantirait qu'il se déclenche à l'heure écran verrouillé.
2. `resetTimer()` était entièrement gardé par `if(TimerRunning)` : le reset ne faisait
   RIEN si le timer était en pause ou pas encore démarré.

Implémentation :

- Nouveau `RestTimerReceiver` (`BroadcastReceiver`, package `com.example.verifit`) :
  joue un son + vibration + affiche une notification (canal dédié
  `rest_timer_channel`, importance haute, son de type `TYPE_ALARM` avec les
  `AudioAttributes` correspondants) à la fin du repos. Totalement indépendant du cycle
  de vie de l'Activity - se déclenche que l'app soit ouverte, en arrière-plan, ou
  l'écran verrouillé.
- `AddExerciseActivity.scheduleTimerAlarm()` programme, au démarrage du timer, une
  alarme système via `AlarmManager.setAlarmClock()` plutôt que
  `setExactAndAllowWhileIdle()` : `setAlarmClock()` est traité par le système comme une
  vraie alarme (type "réveil"), ce qui l'exempte des restrictions Doze/App Standby -
  au prix d'une petite icône de réveil dans la barre de statut tant qu'elle est
  programmée (comportement voulu : gage de fiabilité visible). `cancelTimerAlarm()`
  annule cette alarme à la pause ou au reset (no-op sans effet si aucune n'est en
  attente). `PendingIntent` construits avec `FLAG_IMMUTABLE` à partir d'Android 6
  (requis à partir d'Android 12). Le `CountDownTimer` existant est conservé tel quel,
  mais ne pilote plus QUE l'affichage du décompte dans l'Activity - la fiabilité de la
  sonnerie vient entièrement de l'alarme système, désormais découplée du cycle de vie
  de l'Activity.
- `resetTimer()` remet maintenant `TimeLeftInMillis`/le texte affiché à zéro de façon
  inconditionnelle. Seul l'arrêt effectif du `CountDownTimer` (via `pauseTimer()`, qui
  appelle `countDownTimer.cancel()`) reste conditionné à `TimerRunning`, pour éviter un
  NPE si le timer n'a jamais été démarré (`countDownTimer` alors `null`).
- `AndroidManifest.xml` : permission `VIBRATE` ajoutée, `RestTimerReceiver` enregistré
  (`exported=false` - ne peut être déclenché que par l'app elle-même via son
  `PendingIntent`). Pas de permission `POST_NOTIFICATIONS` nécessaire : le
  `targetSdkVersion` du module (31) est sous le seuil (33) à partir duquel Android exige
  cette permission runtime pour les notifications.

Limite assumée : si le process de l'Activity est tué pendant que le timer tourne
(champs `countDownTimer`/`TimerRunning`/`TimeLeftInMillis` non persistés), rouvrir
`AddExerciseActivity` recrée une instance avec des valeurs par défaut, sans lien avec
l'alarme système déjà programmée - mais cette dernière reste indépendante du cycle de
vie de l'Activity et sonnera de toute façon à l'heure prévue, ce qui couvre le besoin
exprimé par Romain (fiabilité de la sonnerie, pas forcément cohérence de l'affichage
dans ce cas limite).

**Crash signalé par Romain au premier test, corrigé le 06/09/2026** : cliquer sur
"Start" faisait planter toute l'app (écran blanc, retour sur l'écran Workout du jour
courant plutôt que celui d'où il était parti - signe d'un kill de process complet, pas
juste d'une Activity). Cause identifiée via recherche documentaire (`developer.android.com`,
page "Schedule exact alarms are denied by default") : contrairement à ce qui était
supposé lors de la première implémentation, `setAlarmClock()` **n'est pas** exempté de
la permission `SCHEDULE_EXACT_ALARM` - seule la variante `OnAlarmListener` de ces API
en est dispensée. La permission n'étant pas déclarée dans le Manifest, l'appel levait
une `SecurityException` non rattrapée qui faisait planter tout le process au premier
appel de `scheduleTimerAlarm()`.

Corrigé :

- `AndroidManifest.xml` déclare `android.permission.SCHEDULE_EXACT_ALARM` - permission
  "spéciale" (catégorie Alarmes et rappels), auto-accordée à l'installation sans écran
  de permission pour les apps dont le `targetSdkVersion` est ≤ 31 (cas de ce module,
  confirmé documentairement pour Android 14 : le refus par défaut ne s'applique qu'aux
  apps ciblant l'API 33+).
- `scheduleTimerAlarm()` vérifie `alarmManager.canScheduleExactAlarms()` (Android 12+
  uniquement, la méthode n'existe pas en dessous) avant d'appeler `setAlarmClock()`, et
  englobe l'appel dans un `try/catch(SecurityException)` par sécurité supplémentaire
  (permission révoquée à la main par l'utilisateur après coup, comportement propre à
  certains fabricants...) : dans le pire cas, le minuteur reste fiable uniquement
  premier plan (comportement d'avant ce chantier), plutôt que de faire planter toute
  l'app. `cancelTimerAlarm()` protégée de la même façon par cohérence.

**Confirmé fonctionnel par Romain après ce correctif** : "Ok c'est très bien ça
fonctionne." Signalé dans la foulée, un ajustement de confort plutôt qu'un bug :

**"Je voudrai que la sonnerie ne perturbe pas. Sur fitnotes ça fait un Tuuut et c'est
tout. Et c'est bien."** Le premier jet utilisait volontairement un son et un attribut
audio de type ALARME (`RingtoneManager.TYPE_ALARM`/`AudioAttributes.USAGE_ALARM`),
pensés pour rester audibles même en mode silencieux/Ne pas déranger - mais qui se
traduisent sur la plupart des téléphones par une sonnerie d'alarme longue et forte,
loin du bref "Tuuut" de FitNotes.

Corrigé :

- `RestTimerReceiver` bascule sur le son et l'attribut audio de notification standard
  (`TYPE_NOTIFICATION`/`USAGE_NOTIFICATION_EVENT`) plutôt que ceux d'alarme - compromis
  assumé : la notification ne passera plus forcément en mode silencieux/Ne pas
  déranger, en échange d'un signal beaucoup plus discret, conforme à ce que Romain a
  validé sur FitNotes.
- Vibration ramenée à un seul buzz court (200 ms) au lieu du double-buzz plus long
  (500/250/500 ms) du premier jet.
- Priorité et catégorie de la notification adoucies (`PRIORITY_DEFAULT`/
  `CATEGORY_REMINDER` au lieu de `PRIORITY_HIGH`/`CATEGORY_ALARM`).
- Nouvel identifiant de canal de notification (`rest_timer_channel_v2`) : un canal est
  immuable une fois créé sur Android 8+ (son/vibration ne peuvent plus être changés
  après coup) - sans ce changement d'id, le téléphone de Romain aurait gardé
  indéfiniment le son/la vibration d'alarme du premier jet malgré la mise à jour du
  code.

**Confirmé par Romain après ce correctif** : "ça fonctionne et c'est ok. Je l'ai
commité." Nouveau retour dans la foulée, sur le même sujet :

**"Je préférerais avoir un son de notification différent [...] pendant mon
entraînement j'ai besoin de pouvoir entendre ce son à quelques mètres de distance avec
la musique de ma salle de muscu en fond [...] il faut que je puisse discerner le son
spécifique me disant que je peux reprendre ma série et ajuster le volume suivant le
bruit ambiant du jour."** Deux besoins distincts, tous deux présents sur FitNotes selon
Romain (qui expose un réglage de volume, mais pas de type de son) :

1. Un son **spécifique et reconnaissable**, pas le son de notification générique du
   téléphone (potentiellement partagé avec d'autres apps, et pas assez distinctif).
2. Un **volume réglable depuis l'app**, indépendant du volume "notifications" du
   système - insuffisant en salle de sport avec de la musique ambiante.

Implémentation :

- Nouveau `res/raw/rest_timer_beep.wav` : bip synthétisé (généré par script, pas un
  fichier audio tiers) - ~350 ms, 880 Hz, fondu entrée/sortie de 20 ms pour éviter tout
  "clic". Son fixe et reconnaissable, indépendant des réglages de sonnerie du
  téléphone.
- `RestTimerReceiver.playRestTimerBeep()` joue ce bip directement via `MediaPlayer`
  plutôt que de le déléguer au son du canal de notification (qui ne peut pas être
  réglé en volume depuis l'app - seulement via le volume système "notifications").
  Diffusé sur le flux **ALARME** (`AudioAttributes.USAGE_ALARM`) : le flux le plus
  fort du téléphone, et qui reste audible même en mode silencieux/Ne pas déranger -
  pertinent pour le cas d'usage salle de sport. Le volume effectif est
  `MediaPlayer.setVolume(gain, gain)` avec `gain` = réglage choisi dans l'app (0-100%)
  / 100 - un gain logiciel appliqué par-dessus le volume système du flux alarme (donc
  un plafond côté téléphone reste possible, mais l'appli peut toujours réduire en
  dessous pour les jours calmes).
  - `onReceive()` appelle `goAsync()` et ne libère le `PendingResult` qu'à la fin
    effective de la lecture (`onCompletion`/`onError` du `MediaPlayer`, ou un filet de
    sécurité de 3s) : sans ça, rien ne garantissait que le process reste vivant assez
    longtemps pour terminer de jouer le son si l'app avait déjà été tuée en
    arrière-plan au moment où l'alarme système se déclenche.
  - La notification système (`postNotification()`) reste affichée (visuel + tap pour
    rouvrir l'app + vibration courte) mais devient **silencieuse** côté canal
    (`channel.setSound(null, null)`) pour éviter un double signal sonore avec le bip
    joué séparément. Nouvel id de canal (`rest_timer_channel_v3`) pour que ce
    changement s'applique aussi sur le téléphone de Romain, déjà passé par `_v2`.
- Nouveau réglage **Volume** (`SeekBar` 0-100%, `timer_dialog.xml`) sous le réglage de
  durée existant. `AddExerciseActivity.loadVolume()`/`saveVolume()` le persistent dans
  les mêmes `SharedPreferences` ("shared preferences") que la durée du minuteur
  (`loadSeconds()`/`saveSeconds()`), sous la clé `RestTimerReceiver.VOLUME_PREF_KEY`
  (constante partagée pour éviter toute divergence entre l'écriture côté Activity et
  la lecture côté Receiver). Persisté uniquement à `onStopTrackingTouch` (pas à chaque
  pixel de déplacement du curseur). Défaut 100% - le besoin exprimé par Romain est
  d'être entendu par-dessus la musique de la salle, donc mieux vaut un défaut fort
  qu'on baisse au besoin plutôt que l'inverse.

**Erreur de compilation signalée par Romain au build suivant, corrigée le 06/09/2026** :
`compileDebugJavaWithJavac` échouait sur `AddExerciseActivity.loadVolume()`/
`saveVolume()` - `RestTimerReceiver.VOLUME_PREF_KEY` et `DEFAULT_VOLUME_PERCENT`
avaient été déclarées sans modificateur d'accès (donc `package-private`), alors que
`AddExerciseActivity` vit dans le package `com.example.verifit.ui`, différent de celui
de `RestTimerReceiver` (`com.example.verifit`) - inaccessibles depuis l'extérieur du
package. Corrigé en ajoutant `public` aux deux constantes. Erreur purement locale à
l'ajout du volume réglable, sans lien avec le reste de la Fonctionnalité 10 (bip dédié,
fiabilité de l'alarme...).

**Nouveau retour de Romain, avant même d'avoir rebuild la correction ci-dessus** : "Le
bruit, le bip est ok mais un peu court. Possible que tu le rendes 2 fois plus long ? ou
m'offrir la possibilité de l'éditer dans l'app ? (si pas trop violent comme feature)".
Choix fait en faveur de l'option "éditable dans l'app", jugée pas plus complexe qu'un
simple doublement de la durée fixe et plus utile sur la durée :

- Le bip n'est plus un fichier `.wav` fixe (`res/raw/rest_timer_beep.wav`, supprimé) :
  `RestTimerReceiver.generateBeepSamples(int durationMs)` synthétise directement en
  mémoire les échantillons PCM (même sinusoïde 880 Hz + fondu entrée/sortie de 20 ms
  qu'avant, mais durée désormais paramétrable) et les joue via `AudioTrack` en mode
  `MODE_STATIC` (au lieu de `MediaPlayer` sur un asset). Toujours diffusé sur le flux
  ALARME avec un gain logiciel proportionnel au réglage Volume, et toujours protégé par
  `goAsync()` + filet de sécurité (porté à 4 s) + détection de fin de lecture via
  `setNotificationMarkerPosition()`/`setPlaybackPositionUpdateListener()` (équivalent
  audio de `onCompletion()` pour un `AudioTrack`).
- Nouveau réglage **Durée du bip** (`SeekBar`, `timer_dialog.xml`) sous le réglage de
  Volume : 150 ms à 2000 ms, défaut 700 ms (exactement le double de l'ancienne durée
  fixe de 350 ms). Le curseur va de 0 à 1850 (`MAX_DURATION_MS - MIN_DURATION_MS`)
  plutôt que d'utiliser `SeekBar.setMin()` (API 26+ seulement) pour rester compatible
  avec d'anciennes versions d'Android ; `AddExerciseActivity` fait la conversion
  (`+ MIN_DURATION_MS`) à la lecture comme à l'écriture. Persisté par
  `loadDuration()`/`saveDuration()`, même principe et mêmes `SharedPreferences` que le
  Volume, sous la clé `RestTimerReceiver.DURATION_PREF_KEY`.

**Statut au 06/09/2026 : fonctionnalité principale (fiabilité + sonnerie discrète)
confirmée et poussée par Romain ; bip dédié + volume + durée réglables codés et livrés
sur sa machine (erreur de compilation corrigée), pas encore rebuildés/retestés** (pas de
SDK Android ni d'émulateur côté Claude - vérifié uniquement par lecture de code et
équilibrage accolades/parenthèses côté Java, bonne formation XML côté layout). La mise
en page ajoutée à `timer_dialog.xml` (deux labels + deux curseurs, boutons Start/Reset
repoussés en conséquence) n'a pas pu être vérifiée visuellement.

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

1. **Retour de test attendu sur le bip dédié + volume + durée réglables du timer de
   repos (Fonctionnalité 10 ci-dessus)** — fiabilité et sonnerie discrète déjà
   confirmées et poussées par Romain ; le bip synthétisé dédié et les curseurs de
   volume et de durée dans `timer_dialog.xml` sont codés le 06/09/2026 mais pas encore
   buildés ni testés sur l'appareil (mise en page notamment, jamais vérifiée
   visuellement côté Claude - tester en particulier une durée très courte et très
   longue pour vérifier l'absence de clic/distorsion en fin de bip).
2. **Nouvelle demande à scoper (06/09/2026, pas codée)** : démarrage/arrêt automatique
   du timer de repos (au premier/dernier série de l'exercice, "ce que fait fitnotes"),
   puis piste plus lointaine de suivi du temps de repos réellement pris entre deux
   séries consécutives (indicateur de repos insuffisant) - voir
   `docs/fitnotes-fork-todo.md`, section "Nouvelles demandes", pour le détail et les
   points à clarifier avec Romain avant de coder (notamment ce que "dernière série"
   signifie en pratique, l'app ne connaissant pas à l'avance le nombre de séries
   prévues pour un exercice).
3. **Idée évoquée par Romain (06/09/2026, pas tranchée)** : une simulation "à blanc"
   d'une vraie séance (test end-to-end manuel, sans faire réellement la séance) pour
   repérer les points de friction avant qu'ils ne se manifestent en conditions réelles -
   voir `docs/fitnotes-fork-todo.md`, section "En attente de décision / à planifier".
4. Étapes précises pour retirer verifit\_rs (voir section dédiée) — par où commencer ?
5. Si le kill de process par Android (pas juste la mise en arrière\-plan) s'avère gênant
   en pratique pour la conservation du jour affiché, ajouter une vraie persistance
   (SharedPreferences) du dernier jour consulté.

Voir aussi `docs/fitnotes-fork-todo.md` pour le suivi au jour le jour.

* * *

*Ce fichier est aussi tenu à jour dans le projet Claude "FitNotes\_Fork".*
