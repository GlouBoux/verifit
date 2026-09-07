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

### Synchronisation delta (06-07/09/2026) : séance du vendredi 04/09

Romain a continué à logger dans FitNotes pendant la phase de test de Vérifit (dont sa
séance du vendredi 04/09, utilisée comme exemple réel pour valider "Share workout" - voir
`docs/fitnotes-fork-todo.md`) et a fourni un nouveau backup
(`FitNotes_Backup_2026_09_07_24_09_43.fitnotes`) pour la resynchroniser avant de basculer
définitivement sur Vérifit.

Point important identifié en lisant `DataStorage.readFile()`/`csvToSets()` : l'import CSV
de Vérifit est un **remplacement complet**, pas un ajout (`sets.clear()` puis reconstruit
tout depuis le CSV - l'app affiche d'ailleurs elle-même l'avertissement "This will
overwrite all saved data" avant de continuer). Un import du seul delta (juste la séance de
vendredi) aurait donc effacé les 3784 séries déjà importées le 04/09. Confirmé avec Romain
qu'aucune vraie série n'avait été loggée directement dans Vérifit entre-temps (seulement
des séries de test créées/supprimées pendant le développement) - un nouveau CSV **combiné**
a donc été généré : les 3784 lignes de l'ancien CSV (identiques, jusqu'au 31/08 inclus) +
43 nouvelles lignes extraites du nouveau backup pour le 04/09 (9 exercices, 8 séries
commentées, aucune exclue) - `verifit_import_combined_2026-09-04.csv`, 3827 séries au
total, prêt à être importé via Réglages → Import CSV (même flux que le 04/09, en confirmant
l'avertissement d'écrasement). **Confirmé par Romain : l'import fonctionne.**

### Script réutilisable de conversion (07/09/2026)

Pour éviter à Romain de redemander cette conversion à chaque fois, généralisation du
script de conversion en un outil autonome qu'il garde en local :
`scripts/convert_fitnotes_to_verifit_csv.py`. Reprend fidèlement toute la logique déjà
validée (`sanitize()`, `fmt_num()`, l'ordre de colonnes Weight-avant-Reps qui compense le
bug de nommage de `DataStorage.csvToSets()`, le filtre `is_complete=1 et unit=0`, le
rattachement des commentaires via `Comment.owner_type_id=1`/`training_log._id`).

Choix de conception clé, dicté par le point ci-dessus (l'import CSV Vérifit est un
remplacement complet, pas un ajout) : le script convertit par défaut **tout** l'historique
du backup à chaque exécution, jamais un delta seul - c'est ce qui le rend sûr à relancer
autant de fois que voulu sans que Romain ait à suivre une date de coupure. Une option
`--since AAAA-MM-JJ` existe pour un usage avancé (fusion manuelle d'un delta avec un CSV
existant, comme fait ponctuellement ci-dessus) mais le script avertit explicitement (dans
sa docstring et à l'écran) de ne jamais importer son résultat directement dans Vérifit.

Usage : `python scripts\convert_fitnotes_to_verifit_csv.py MonBackup.fitnotes` (génère le
CSV à côté du backup) - le fichier `.csv` obtenu doit ensuite être transféré sur le
téléphone puis importé via Réglages → Import CSV, en confirmant l'avertissement
d'écrasement.

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

## Fonctionnalité 10 — Timer de repos fiable (fonctionnelle et VALIDÉE le 06/09/2026, bip dédié + volume + durée réglables inclus)

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

**Statut au 06/09/2026 : fonctionnalité entièrement VALIDÉE, COMMITÉE ET POUSSÉE par
Romain** ("ok. validé, comité, pushé") - fiabilité, sonnerie discrète, bip dédié, volume
et durée réglables, y compris le correctif d'alignement du bouton Reset dans
`timer_dialog.xml` (ancré par erreur sous l'ancien curseur Volume au lieu du nouveau
curseur Durée après l'ajout de ce dernier).

## Fonctionnalité 11 — Historique des PR par nombre de reps (v2 codée le 06/09/2026, pas encore testée)

Née en préparant l'export de séance (Fonctionnalité "Share workout", cf. Questions
ouvertes) : reproduire le tag `[PR]` par série du rapport FitNotes exigeait de savoir
précisément ce que FitNotes entend par "PR". Réponse de Romain :

**"Un PR c'est un record (Personal Record) pour ce rep range (reps) pour ce poids
(kgs). Ici mon record pour 43 reps = 47.5 (avant c'était moins du coup). Fitnotes garde
un historique de PR pour chaque exercice (oups ça veut dire que Verifit ne l'a
probablement pas et c'est important)."**

Vérification faite en lisant `DataStorage.calculatePersonalRecords()` : Romain avait
raison, c'était un vrai manque. Le système existant (`volumePRs`, `maxRepsPRs`,
`maxWeightPRs`, `actualOneRepMaxPRs`, `estimatedOneRMPRs`, tous des
`HashMap<String, Double>` par exercice) ne track que des records **globaux**, un seul
scalaire par exercice et par métrique, tous nombres de reps confondus - jamais de table
"pour ce nombre de reps exact, quel est le poids le plus lourd jamais soulevé". C'est
d'ailleurs cohérent avec l'exemple fourni par Romain : sur `Assisted Floor Shoulder
stand`, DEUX séries du même jour sont tagées `[PR]` (47.5kg x 43 reps ET 40kg x 52
reps) - impossible avec un simple "poids max du jour", mais cohérent avec un record
par nombre de reps distinct.

Romain a choisi de construire un vrai écran d'historique (plutôt qu'un calcul interne
limité à l'export). Une v1 (record = meilleur poids pour EXACTEMENT N reps, liste
plate avec badge "Actuel") a été codée et livrée en premier - Romain n'a pas encore eu
le temps de la tester qu'elle a été remplacée par la v2 ci-dessous, suite à deux
screenshots du popup "Personal Record History" de FitNotes et à ce retour :

**"On va même aller plus loin en trackant comme sur ce screenshot : quand on clique sur
un RM sur cet écran, ça en ouvre un autre avec current record, previous record et la
date (l'historique quoi). Les PRs sont également déduis (20 kgs pour 8 reps est
également un PR pour 7 reps s'il n'y a pas de valeur. transitivité). [...] les PR
déduits sont grisés/non mis en avant."**

L'algorithme exact a été reconstitué par rétro-ingénierie à partir des deux
screenshots (la table 1RM à 8RM, puis le détail du "5 RM" : record actuel 49.0kg
31/08, précédents 46.5kg 21/08 étiqueté "6 RM" et 30.0kg 17/08 étiqueté "5 RM") et
validé en retraçant à la main les 8 lignes visibles avant d'être codé :

- **Transitivité** : le record pour N reps = le poids max jamais soulevé sur une série
  d'AU MOINS N reps (pas seulement N reps exactement) - réussir R reps prouve qu'on
  pouvait aussi en faire moins. Un évènement est "déduit" quand le nombre de reps réel
  de sa série source diffère de la case qu'il occupe (grisé dans l'affichage), "réel"
  sinon (mis en avant) - reproduit exactement le rendu du screenshot.
- `DataStorage.calculateRepRangeHistory(String exerciseName)` : parcourt tout
  l'historique de l'exercice trié chronologiquement (dates `"yyyy-MM-dd"`, tri de
  chaînes suffisant) et construit une `TreeMap<Integer reps, ArrayList<RepRangePREvent>>`
  - pour chaque nombre de reps, la liste chronologique de TOUS les évènements
  (transitifs compris) qui ont établi un nouveau record à cette case, chacun portant
  poids, date, nombre de reps source réel et indicateur "déduit" (le dernier de la
  liste = record actuel). Calculé à la volée à chaque appel, aucun nouveau champ
  persisté sur `WorkoutSet` - contrairement à l'horodatage décidé pour la ligne "Time"
  de l'export (voir Questions ouvertes), qui lui nécessite un vrai champ stocké.
  Exclut les séries à 0 reps (retour Romain : "ça n'a pas de sens").
- `RepRangePREvent` (nouvelle classe) : `{weight, date, sourceReps, deduced,
  sourceSet}` - un évènement de record pour une case donnée.
- `DataStorage.getRepRangePRSets(String exerciseName)` : aplatit cette table en un
  `HashSet<WorkoutSet>` (comparaison par référence, `WorkoutSet` ne redéfinit pas
  `equals`/`hashCode`, évènements réels uniquement pour ne pas compter deux fois la
  même série) - conçu pour être réutilisé tel quel comme source de vérité unique du tag
  `[PR]` dans le générateur d'export, afin que l'écran et l'export s'accordent toujours.
- Écran `RepRangeRecordsActivity` (+ `RepRangeHistoryAdapter`, `RepRangeHistoryRow`) :
  pour un exercice, une ligne par nombre de reps (triées du plus petit au plus grand,
  table type "rep-max" 1RM/2RM/3RM...) montrant le record ACTUEL, grisé si déduit.
  **Cliquer une ligne ouvre une popup** (`rep_range_history_dialog.xml`, inspirée du
  motif `set_discrepancy_dialog.xml` déjà présent dans le code - conteneurs
  `LinearLayout` peuplés dynamiquement plutôt qu'un `RecyclerView` imbriqué) avec le
  record actuel puis les records précédents (du plus récent au plus ancien), chaque
  ligne précédente affichant le nombre de reps RÉEL de sa série source (et non celui de
  la case consultée) et sa date. Le bouton "Graph" du screenshot FitNotes n'a
  volontairement pas été reproduit (non demandé). Accessible via le même point d'accès
  qu'avant (menu contextuel "Historique par nombre de reps" en long-press sur une carte
  de `PersonalRecordsActivity`, et icône trophée dans la fiche de l'exercice
  `AddExerciseActivity` - cf. item TODO correspondant).
- `rep_range_history_entry_row.xml` simplifiée en une seule ligne unifiée (reps/poids/
  date), réutilisée à la fois pour la liste principale et pour chaque ligne de la
  popup - le badge "Actuel" de la v1 a disparu (remplacé par le placement en section
  dans la popup). `rep_range_history_header_row.xml` (créé pour la v1) n'est plus
  utilisé, supprimé du dépôt.

**Statut au 06/09/2026 : v2 codée et livrée sur la machine de Romain, pas encore
buildée/testée** (comme d'habitude, pas de SDK Android côté Claude - vérifié
uniquement par lecture de code, équilibrage accolades/parenthèses, et bonne formation
XML). Reste à utiliser `getRepRangePRSets()` dans le générateur d'export une fois
celui-ci écrit.

## Fonctionnalité 12 — Chrono de séance : dialogue "Workout Time", correctif démarrage manuel (07/09/2026)

Suite au premier test réel de la Fonctionnalité "Chrono de la séance entière" (codée le
06/09/2026, voir `docs/fitnotes-fork-todo.md`), sur une vraie séance de sport
(importée en JSON via `generate_workout`, loggée à la salle).

**Bug trouvé et corrigé** : le chrono ne pouvait pas être démarré manuellement pour une
séance dont les séries arrivaient par import JSON (`AddExerciseActivity.addSet*()`, les
seuls endroits qui démarraient le chrono jusque-là, ne sont jamais appelés par l'import).
Romain devait "logger une série bidon puis la supprimer" pour débloquer le bouton.
Corrigé dans `AddExerciseActivity` et `DayActivity` : le bouton Start/Stop/Resume est
actif dès qu'un `WorkoutDay` existe, quelle que soit son origine.

**Comportement Stop → Resume clarifié** : Romain a remarqué que le temps d'arrêt entre un
Stop et un Resume est inclus dans la durée totale affichée/exportée ("je ne sais même pas
si c'est ce que je veux"). Question posée explicitement (avec option recommandée) :
confirmé qu'il veut garder ce comportement - aucun changement de code nécessaire, c'était
le comportement voulu depuis le début (voir le commentaire sur
`WorkoutDay.SessionStartTimestamp`).

**Nouveau dialogue "Workout Time"** : Romain a fourni 5 captures d'écran de l'IHM
équivalente sur FitNotes ("Analyse els et propose moi un design similaire / feature
similaire"). Proposition faite et confirmée par Romain ("Dialogue complet façon
FitNotes") : dialogue ouvert en tapant la barre de chrono (le bouton Start/Stop/Resume
garde son action rapide inchangée), affichant Start Time / End Time / Duration, un
bouton Stop/Resume Timer, et un menu "..." avec Settings (réglage "Auto Start" seul -
pas d'"Auto Stop", faute de signal fiable de fin de séance dans cette app) et Cancel
Timer (annule le chrono en cours, confirmation demandée, ne touche jamais aux séries
déjà loggées).

Nouveaux fichiers : `workout_time_dialog.xml`, `workout_time_settings_dialog.xml`,
`workout_time_dialog_menu.xml`. `WorkoutReportGenerator.formatDateHeader()` élargie de
`private` à `public static` pour être réutilisée par le dialogue (même format de date
français que le rapport "Share workout").

**Statut au 07/09/2026 : codé, vérifié côté Claude (équilibre accolades/parenthèses,
XML bien formé), livré sur la machine de Romain - pas encore rebuildé/testé.**

Par ailleurs, le rapport "Share workout" lui-même (Fonctionnalité déjà validée, voir
plus haut) a été reconfirmé correct sur cette même vraie séance (10 exercices, tags
`[PR]` et commentaires par série corrects) - aucun bug relevé.

## Fonctionnalité 13 — Épuration de l'IHM : barre de chrono, boutons conditionnels, surbrillance (07/09/2026)

Suite directe de la Fonctionnalité 12 ci-dessus, une fois le dialogue "Workout Time"
validé par Romain. Trois demandes distinctes en une fois :

1. **Barre de chrono allégée** : icône course à pied et bouton Stop/Resume retirés de
   la barre persistante (`session_timer_bar`) - ne reste que le décompte, centré,
   comme sur FitNotes. Le tap sur la barre ouvre le dialogue "Workout Time" (seul
   contrôle Stop/Resume restant).
2. **Save/Update et Clear/Delete masqués tant que Weight et Reps ne sont pas
   renseignés** : un `TextWatcher` commun sur les deux champs pilote leur visibilité
   (`View.GONE`, le `RecyclerView` remonte combler l'espace) - couvre automatiquement
   tout `setText()` programmatique (+/-, Clear, Delete, pré-remplissage d'édition).
3. **Question ouverte de Romain** ("comment gérer les boutons quand je clique sur une
   série déjà loggée, je n'ai pas d'idée") : proposition de Claude retenue - un retap
   sur la ligne déjà sélectionnée désélectionne (`AddExerciseActivity.cancelEditSet()`)
   plutôt que d'obliger à Update/Delete pour sortir du mode édition.
4. **Surbrillance de la ligne en cours d'édition** (`AddExerciseWorkoutSetAdapter`,
   nouvelle couleur `row_highlight`) - répond à "je ne sais pas sur quelle ligne je me
   trouve", et se combine avec le retap ci-dessus pour une boucle complète
   sélection/désélection visuellement claire.

Romain a aussi demandé confirmation que la fonctionnalité "série loggée vs série
réalisée" n'avait pas été abandonnée - clarifié : c'est la Fonctionnalité 7 "Écarts
Prévu/Réalisé" (voir plus haut), déjà livrée et validée le 06/09/2026, toujours en
place. La surbrillance de ligne ajoutée ici est complémentaire, pas un remplacement.

**Bug trouvé au premier test réel et corrigé** : la surbrillance ("point 4")
disparaissait après une fraction de seconde. Cause identifiée : `cardview_set` est
coloré via `android:backgroundTint` dans le layout (pas `app:cardBackgroundColor`) -
le premier essai appelait `CardView.setCardBackgroundColor()`, une API différente qui
reste recouverte par ce `backgroundTint` statique dès qu'Android réévalue l'état du
drawable. Corrigé en passant par `ViewCompat.setBackgroundTintList()` (compat-safe
pour le `minSdk 16` du projet, `View.setBackgroundTintList()` natif exigeant l'API 21)
- même mécanisme que le XML, plus de concurrence entre deux systèmes de coloration.

**Statut au 07/09/2026 : codé, correctif livré, vérifié côté Claude (équilibre
accolades/parenthèses, XML bien formé) - pas encore retesté par Romain.**

## Fonctionnalité 14 — Titre de l'exercice tronqué : items de la barre d'outils passés en overflow (07/09/2026)

Suite au test de la Fonctionnalité 13 ci-dessus, Romain a signalé un pain point déjà
connu sur FitNotes : "je vois le nom de l'exercice en cours. Mais il est tronqué donc
je ne sais pas sur quel exo je me trouve [...] cet affichage devrait être prioritaire
(surtout par rapport à la liste d'icones. je n'aime pas les liste d'icone en générale)
[...] là j'ai 2 lettres."

**Cause** : les 6 items de `add_exercise_activity_menu.xml` (Historique, Graph,
Personal Records, Timer, Comments, Select sets) étaient tous en
`app:showAsAction="always"` - 6 icônes forcées dans l'ActionBar, ne laissant presque
plus de largeur au titre (`AddExerciseActivity.initActivity()`,
`getSupportActionBar().setTitle(exercise_name)`).

**Correctif** : les 6 items passés en `app:showAsAction="never"` - ils rejoignent le
menu overflow "..." standard (une seule icône), le titre récupère toute la largeur
disponible. Effet de bord détecté en cours de route : 4 des 6 items n'avaient jamais eu
d'`android:title` (invisible tant qu'affichés en icône seule dans l'ActionBar) - le
menu overflow affichant ses entrées par texte et non par icône, elles seraient restées
vides. Titres ajoutés à partir de l'action de chaque item dans
`onOptionsItemSelected()`. Seul fichier modifié, aucun changement Java nécessaire.

Romain a aussi soumis une deuxième idée dans le même message, présentée comme
facultative : un onglet/section visible pendant la séance montrant directement
prévu/réalisé + le commentaire généré par `workout_engine.py` (le gain théorique en
%), plutôt que de devoir taper sur le badge d'écart série par série. Avis donné : ne
pas dupliquer l'écran existant (Fonctionnalité 7), mais enrichir le dialogue
`set_discrepancy_dialog.xml` déjà ouvert par le badge en y ajoutant le commentaire du
script - une vue d'ensemble par séance (sous-ensemble filtré de
`DiscrepancyHistoryActivity`) resterait possible en plus si Romain confirme en avoir
besoin après avoir testé la version enrichie du dialogue. Pas codé, en attente de
décision (voir `docs/fitnotes-fork-todo.md`).

**Statut au 07/09/2026 : codé, livré, vérifié côté Claude (XML bien formé) - pas encore
rebuildé/retesté par Romain.**

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

1. **Fonctionnalité 10 (timer de repos) validée, commitée et poussée par Romain le
   06/09/2026** ("ok. validé, comité, pushé"), y compris le bip dédié, le volume et la
   durée réglables, et le correctif d'alignement du bouton Reset dans
   `timer_dialog.xml` (était resté ancré sous l'ancien curseur Volume au lieu du
   nouveau curseur Durée). Plus rien en attente de test sur ce sujet pour l'instant.
2. **Nouvelle demande à scoper (06/09/2026, pas codée)** : démarrage/arrêt automatique
   du timer de repos (au premier/dernier série de l'exercice, "ce que fait fitnotes"),
   puis piste plus lointaine de suivi du temps de repos réellement pris entre deux
   séries consécutives (indicateur de repos insuffisant) - voir
   `docs/fitnotes-fork-todo.md`, section "Nouvelles demandes", pour le détail et les
   points à clarifier avec Romain avant de coder (notamment ce que "dernière série"
   signifie en pratique, l'app ne connaissant pas à l'avance le nombre de séries
   prévues pour un exercice).
3. **Simulation "à blanc" d'une vraie séance (test end-to-end) - Romain a annoncé le
   06/09/2026 vouloir s'y mettre** (résultat/points de friction attendus à la prochaine
   session) - voir `docs/fitnotes-fork-todo.md`, section "Codé, en attente de test réel".
4. **Export/partage de séance ("Share workout") - codé, pas encore testé (06/09/2026)** :
   Romain a fourni un exemple réel de rapport FitNotes. Deux points bloquants
   identifiés en l'étudiant, tous deux résolus avec Romain : (a) la ligne "Time" exige
   un horodatage par série que Verifit ne trackait pas - décision de Romain : l'ajouter
   - **codé et validé par Romain** (`WorkoutSet.timestamp`, epoch millis nullable,
   renseigné uniquement à la validation manuelle d'une nouvelle série) ; (b) le tag
   `[PR]` par série exige un historique de records **par nombre de reps précis**, que
   Verifit ne trackait pas non plus (confirmé comme un manque important par Romain) -
   **codé**, voir Fonctionnalité "Historique des PR par nombre de reps" ci-dessous.
   Le générateur de rapport lui-même (`WorkoutReportGenerator`) et son déclencheur de
   partage (menu "Share workout" dans `DayActivity`, `Intent.ACTION_SEND`) sont
   maintenant **codés** - voir `docs/fitnotes-fork-todo.md`, section "Nouvelles
   demandes", item "Partager une séance", pour le détail complet, y compris les deux
   points laissés à l'appréciation de Claude (format de date, minutes non paddées).
   **Pas encore testé/rebuild par Romain.**
5. Étapes précises pour retirer verifit\_rs (voir section dédiée) — par où commencer ?
6. Si le kill de process par Android (pas juste la mise en arrière\-plan) s'avère gênant
   en pratique pour la conservation du jour affiché, ajouter une vraie persistance
   (SharedPreferences) du dernier jour consulté.

Voir aussi `docs/fitnotes-fork-todo.md` pour le suivi au jour le jour.

* * *

*Ce fichier est aussi tenu à jour dans le projet Claude "FitNotes\_Fork".*
