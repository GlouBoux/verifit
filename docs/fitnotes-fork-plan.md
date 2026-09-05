# FitNotes_Fork — dépôt de base et premières fonctionnalités

## Dépôt de base : MakisChristou/verifit (confirmé)

verifit reste le bon choix. Comparé aux alternatives trouvées lors des recherches
(opengym — application web auto-hébergée, pas un client Android natif ; Flexify et
Lotti — Flutter, pas natif ; OpenScale — suivi du poids/mensurations uniquement, pas un
journal de musculation ; Fast N Fitness — Java mais maintenance incertaine et bien moins
de fonctionnalités) : aucune n'est plus proche de FitNotes qu'un journal de musculation
Android natif.

- Java, Android natif, GPL-3.0, minSdk 16 / target 31, Gradle.
- UI explicitement inspirée de FitNotes ; a déjà le CRUD des exercices, les exercices
  personnalisés, les commentaires par exercice, l'import/export CSV (local + WebDAV), le
  suivi volume/1RM/records, les graphiques, le minuteur de repos.
- Archivé sur GitHub (juin 2025, dernière release mars 2025) — plus d'activité en amont,
  mais ce n'est pas un problème pour un fork qu'on compte maintenir soi-même.
- Le dépôt embarque aussi un backend de compte en ligne optionnel ("verifit_rs", une API
  Rust) pour la synchronisation multi-appareils — **décision prise (04-05/09/2026) : à
  retirer**, voir section dédiée plus bas.
- **Décidé le 05/09/2026 : on reste définitivement sur le fork perso
  (`GlouBoux/verifit`), pas de PR vers l'amont `MakisChristou/verifit`.** C'est le
  projet de Romain, taillé pour ses propres besoins.

## Fait architectural clé qui conditionne les fonctionnalités

`Exercise` n'a pas d'ID — seulement un `Name` (String) + `BodyPart` + `favorite`. Toutes
les recherches dans `DataStorage` (`doesExerciseExist`, `editExercise`, `deleteExercise`,
`getExerciseCategory`, ...) comparent les exercices par correspondance exacte de nom.
C'est exactement le problème de convention de nommage décrit au départ : il n'y a rien à
"cloner" au niveau du modèle à part un nom + une zone corporelle, et une collision n'est
détectée que par égalité de chaînes, jamais empêchée par une contrainte de schéma.

## Fonctionnalité 1 — Dupliquer un exercice (implémentée, testée sur l'app ✅)

Appui long sur un exercice dans l'onglet Exercises → **Duplicate**. Ouvre la même boîte
de dialogue qu'Edit, pré-remplie avec `"<nom> (copy)"` et la même zone corporelle que
l'original. Confirmé par Romain : fonctionne bien.

## Fonctionnalité 2 — Import Session (implémentée, testée sur l'app ✅)

Ouvrir un jour (`DayActivity`) → barre d'outils → **Import Session** → choisir un fichier
`.json` → ses séries sont ajoutées à ce jour, de façon additive. Schéma documenté dans
`docs/session-import-format.md`. C'est le point d'intégration prévu pour le générateur de
séances (workout_engine.py), prochain chantier majeur (voir tout en bas). **Statut :
validé, fonctionne** (le crash "IllegalStateException" croisé en route était une fausse
manip, corrigé quand même côté robustesse — patch #4).

## Fonctionnalité 3 — Commentaire par série (implémentée, à valider par Romain)

Le modèle `WorkoutSet` a toujours eu un champ `comment` individuel par série, mais la
seule UI existante pour en éditer un ("Exercise Comments" dans `AddExerciseActivity` →
`saveComment()`) applique le même texte à **toutes** les séries de l'exercice du jour —
donc dans les faits, un seul commentaire partagé, pas un vrai commentaire par série.
Romain veut ce comportement pour deux raisons : il l'a sur FitNotes, et ça permettra de
récupérer fidèlement ces commentaires si/quand il migre plus de données.

Implémenté (patch #6) en restant additif, sans toucher à la fonctionnalité existante :
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

**Statut au 05/09/2026 : livré, pas encore testé par Romain.**

## Bugs corrigés en cours de route (dépôt de base, sans rapport avec les fonctionnalités)

- **Dépendances mortes** (patch #2) : `jcenter()` (fermé depuis 2022) sans
  `mavenCentral()` en repli ; deux dépendances non utilisées supprimées.
- **Drawable manquant** (patch #3) : `@drawable/background_transparent`, bug latent du
  dépôt d'origine (présent aussi sur `master`).
- **Crash Import Session** (patch #4) : `SessionImporter` ne rattrapait que
  `JsonSyntaxException` alors que Gson lève directement une `IllegalStateException`
  quand un fichier n'a pas la forme JSON attendue.
- **L'app revient toujours à aujourd'hui** (patch #5) : `MainActivity.initViewPager()`
  recréait l'adapter du ViewPager et resettait sa position sur aujourd'hui à chaque
  `onRestart()` (déclenché en réduisant l'app pendant que l'onglet Verifit était affiché).
  Corrigé : la position précédente est mémorisée et restaurée. **Validé par Romain.**
  Limite connue, acceptée : ne couvre que le cas "app réduite" (process vivant), pas un
  kill de process par Android.
- **Icône calendrier inerte** (patch #5) : ne faisait que recentrer sur aujourd'hui —
  jamais de sélecteur de date, malgré tout le code déjà en place
  (`DatePickerDialog.OnDateSetListener`, `onDateSet()` fonctionnel) mais jamais branché
  à un `.show()`. Corrigé : ouvre maintenant ce sélecteur, pré-rempli avec le jour
  affiché. **Validé par Romain.** Pas fait : indication visuelle des jours avec séance
  (nécessiterait de réintroduire une lib de calendrier ou un composant fait main) —
  reporté, priorité de Romain était de pouvoir changer de jour, pas l'indication
  visuelle.

## Incident : MainActivity.java corrompu (05/09/2026) — leçon pour le workflow

Après le patch #5, un build a échoué avec des erreurs de syntaxe absurdes
("class, interface, or enum expected" dès la ligne 1). Cause : `MainActivity.java`
contenait littéralement le **texte du patch git** (`From ... Subject: [PATCH] ...
diff --git ...`) au lieu du vrai code Java — vraisemblablement une tentative
d'"application" manuelle du patch (copier-coller de son contenu dans le fichier) plutôt
qu'une commande git. Corrigé en renvoyant le vrai fichier depuis la copie de référence
saine côté Claude. **Rappel ajouté dans `docs/fitnotes-fork-workflow.md` : les patches
`000X-....patch` sont uniquement pour l'archive git, jamais à ouvrir ni coller dans un
fichier source — les fichiers sont de toute façon déjà à jour sur la machine de Romain
à chaque session.**

La branche `feature/duplicate-exercise-and-session-import` a maintenant 6 commits de
fonctionnalités/correctifs (+ quelques commits de documentation/outillage). Livrés en
patches `0001` à `0006` (`git am`-compatibles, pour archive uniquement) et tous appliqués
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
natif de verifit) — **confirmé par Romain : le ré-import fonctionne correctement.**

## Chantier : retirer le backend en ligne verifit_rs (décidé le 05/09/2026)

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

## Prochain chantier majeur : intégrer le générateur de séances

`workout_engine.py` — outil Python qui calcule des feuilles de route d'entraînement et
des charges (ancrage S1, pivotement RM, contraintes de delta minimum). Le format JSON
"Import Session" est déjà pensé comme point d'intégration. Condition explicite de
Romain : le script doit rester facile à faire évoluer tant qu'on est en période de
test — éviter un couplage trop rigide entre les deux projets pour l'instant.

## Questions ouvertes pour la prochaine session

1. Confirmation de Romain que "commentaire par série" fonctionne comme voulu (appui
   long sur une série).
2. Étapes précises pour retirer verifit_rs (voir section dédiée) — par où commencer ?
3. `workout_engine.py` peut-il produire directement le JSON du format "Import Session" ?
4. Ajouter l'indication visuelle des jours avec séance sur le sélecteur de date, si
   Romain le souhaite toujours après avoir utilisé la version actuelle.
5. Si le kill de process par Android (pas juste la mise en arrière-plan) s'avère gênant
   en pratique pour la conservation du jour affiché, ajouter une vraie persistance
   (SharedPreferences) du dernier jour consulté.

Voir aussi `docs/fitnotes-fork-todo.md` pour le suivi au jour le jour.

---
*Ce fichier est aussi tenu à jour dans le projet Claude "FitNotes_Fork".*
