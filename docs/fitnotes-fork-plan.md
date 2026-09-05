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

## Fait architectural clé qui conditionne les deux fonctionnalités

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
`.json` → ses séries sont ajoutées à ce jour, de façon additive (contrairement à l'import
CSV existant qui est une restauration complète et destructrice). Schéma documenté dans
`docs/session-import-format.md`. C'est le point d'intégration prévu pour le générateur de
séances (workout_engine.py), prochain chantier majeur (voir tout en bas).

**Statut : validé, fonctionne.** Un crash "Import failed: IllegalStateException"
rencontré en cours de route s'est révélé être une fausse manip de Romain (mauvais
fichier/écran) plutôt qu'un vrai bug de données ; corrigé quand même côté robustesse
(patch #4, `SessionImporter` ne rattrapait que `JsonSyntaxException` alors que Gson lève
directement une `IllegalStateException` quand un fichier n'a pas la forme JSON attendue —
ça affiche maintenant un message d'erreur propre au lieu de planter).

## Bugs corrigés en cours de route (dépôt de base, sans rapport avec les 2 fonctionnalités)

- **Dépendances mortes** (patch #2) : `jcenter()` (fermé depuis 2022) sans
  `mavenCentral()` en repli ; deux dépendances non utilisées supprimées.
- **Drawable manquant** (patch #3) : `@drawable/background_transparent`, bug latent du
  dépôt d'origine (présent aussi sur `master`).
- **Crash Import Session** (patch #4) : voir ci-dessus.
- **L'app revient toujours à aujourd'hui** (patch #5, 05/09/2026) : `MainActivity.
  initViewPager()` recréait l'adapter du ViewPager et resettait sa position sur
  aujourd'hui à **chaque** appel, y compris depuis `onRestart()` — déclenché à chaque
  retour au premier plan après avoir réduit l'app. Romain a précisé que ça n'arrivait que
  lorsque l'onglet **Verifit** (le ViewPager lui-même) était affiché au moment de réduire
  l'app, jamais depuis Diary ou Exercises — cohérent, ces deux derniers sont des Activity
  séparées qui ne déclenchent pas le `onRestart()` de `MainActivity`. Corrigé : la
  position précédente est maintenant mémorisée et restaurée après le rafraîchissement des
  données, au lieu d'être écrasée par un retour systématique à aujourd'hui. Limite
  connue : ne couvre que le cas "app réduite, process toujours vivant" ; si Android tue
  le process en arrière-plan, l'app repartira de zéro au prochain lancement (pas de
  persistance du dernier jour consulté sur disque) — à ajouter plus tard seulement si ça
  gêne réellement Romain en pratique.
- **Icône calendrier inerte** (patch #5) : l'icône de la barre d'outils dans l'onglet
  Verifit (`R.id.home` dans `onOptionsItemSelected`) ne faisait que recentrer sur
  aujourd'hui — jamais de sélecteur de date, alors que `MainActivity` implémente déjà
  `DatePickerDialog.OnDateSetListener` avec un `onDateSet()` fonctionnel (ouvre
  `DayActivity` à la date choisie) jamais branché à un `.show()` : du code mort resté en
  place depuis le dépôt d'origine. Romain a demandé cette fonctionnalité en comparant à
  FitNotes ("au moins pouvoir changer de jour comme ça plutôt que de swiper"). Corrigé :
  l'icône ouvre maintenant ce `DatePickerDialog`, pré-rempli avec le jour actuellement
  affiché. **Pas fait** : l'indication visuelle légère des jours où une séance existe
  (le petit "+" ou la coche que montre FitNotes sur son calendrier) — nécessiterait soit
  de réintroduire une lib de calendrier tierce (ironiquement, on avait supprimé
  `material-calendar-view` comme dépendance morte au patch #2 — c'était peut-être prévu
  pour ça à l'origine), soit un composant calendrier fait main. Priorité de Romain était
  "au moins" pouvoir changer de jour, donc reporté.

La branche `feature/duplicate-exercise-and-session-import` a maintenant 5 commits
(2 fonctionnalités + 3 correctifs de bugs). Livrés en patches `0001` à `0005`
(`git am`-compatibles) et tous appliqués directement sur la machine de Romain via le pont
avec son appareil. **Poussée vers `GlouBoux/verifit` par Romain** (pas encore de PR vers
l'amont `MakisChristou/verifit`).

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

## Deux chantiers UX toujours ouverts (identifiés le 04/09/2026, pas encore traités)

1. **Commentaire par série, pas juste par exercice.** Le modèle `WorkoutSet` a bien un
   champ `comment` individuel par série, mais la seule UI existante ("Exercise Comments"
   dans `AddExerciseActivity` → `saveComment()`) applique le même texte à toutes les
   séries de l'exercice du jour. Confirmé par Romain. Il faudra une UI par ligne de
   série (bouton sur chaque `workout_set_row.xml`, via `WorkoutSetAdapter`).
2. Indication visuelle des jours avec séance sur le sélecteur de date (voir patch #5
   ci-dessus).

## Prochain chantier majeur : intégrer le générateur de séances

`workout_engine.py` — outil Python qui calcule des feuilles de route d'entraînement et
des charges (ancrage S1, pivotement RM, contraintes de delta minimum). Le format JSON
"Import Session" est déjà pensé comme point d'intégration. Condition explicite de
Romain : le script doit rester facile à faire évoluer tant qu'on est en période de
test — éviter un couplage trop rigide entre les deux projets pour l'instant.

## Questions ouvertes pour la prochaine session

1. Étapes précises pour retirer verifit_rs (voir section dédiée) — par où commencer ?
2. Ouvrir une PR vers `MakisChristou/verifit` en amont, ou rester sur le fork perso ?
3. `workout_engine.py` peut-il produire directement le JSON du format "Import Session" ?
4. Si le kill de process par Android (pas juste la mise en arrière-plan) s'avère gênant
   en pratique pour la conservation du jour affiché, ajouter une vraie persistance
   (SharedPreferences) du dernier jour consulté.

Voir aussi `docs/fitnotes-fork-todo.md` pour le suivi au jour le jour.

---
*Ce fichier est aussi tenu à jour dans le projet Claude "FitNotes_Fork" ; les deux
copies sont synchronisées à chaque session.*
