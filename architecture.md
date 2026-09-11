# architecture.md — verifit (FitNotes_Fork_Migration)

*Mis à jour 11/09/2026 — voir note en tête de `CLAUDE.md`.*

## Positionnement du dépôt

- Origin : `https://github.com/GlouBoux/verifit.git` — branches locales : `master`, `feature/duplicate-exercise-and-session-import`, `feature/fitnotes-parity` (active, `HEAD`).
- Upstream (lecture seule, jamais de PR envisagée) : `https://github.com/MakisChristou/verifit.git`.
- **Emplacement sur disque (mis à jour 11/09/2026)** : `C:\Users\Brutus\Desktop\
  01_Projects\FitNotes_Fork\FitNotes_Fork_Migration` — déplacé deux fois le 11/09/2026
  suite à la réorganisation du Bureau de Romain (méthode PARA). Le dossier parent
  `01_Projects\FitNotes_Fork` contient à sa racine un `verifit/` sans `.git` : c'est
  l'**ancien clone de travail** antérieur à la mise en place du dépôt git propre
  (contient son propre sous-dossier `verifit/` et un import CSV Fitnotes,
  `verifit_import_from_fitnotes.csv`), gardé comme référence/archive. **Ce clone-ci
  (`FitNotes_Fork_Migration`, imbriqué dedans) est le dépôt actif** — c'est ici que
  vivent les 5 fichiers Claude File System de ce projet.

## Structure du dépôt

| Chemin | Rôle |
|---|---|
| `verifit/` | Module Android principal (code source Java, resources). |
| `build.gradle`, `settings.gradle`, `gradle.properties`, `gradlew(.bat)` | Build Gradle, AGP 7.4.2. |
| `docs/` | Documentation du fork : `fitnotes-fork-plan.md`, `fitnotes-fork-todo.md`, `fitnotes-fork-workflow.md`, `session-import-format.md`, `sample_session.json`. **Non mis à jour depuis le 08/09/2026** (voir CLAUDE.md). |
| `scripts/` | `build_and_log.bat` (build + log partageable), `convert_fitnotes_to_verifit_csv.py` (script de conversion FitNotes → verifit réutilisable). |
| `metadata/` | Assets F-Droid/store : `logo/icon.svg`, `screenshots/`. |
| `0001-duplicate-exercise-and-session-import.patch` | Patch racine (fonctionnalités 1-2). Les patches suivants (`0002`-`0008`) vivent dans `docs/` (traçabilité, voir `fitnotes-fork-workflow.md`). |
| `LICENSE` | GPL-3.0 (héritée du dépôt de base). |

## Modèle de données clé

`Exercise` n'a pas d'identifiant stable : seulement `Name` (String) + `BodyPart` + `favorite`. Toute recherche (`doesExerciseExist`, `editExercise`, `deleteExercise`, `getExerciseCategory`...) compare par correspondance exacte de nom. Conséquence directe : le format d'import de séance (`session-import-format.md`) matche aussi par nom exact, et une collision de nom n'est jamais empêchée par le schéma, seulement détectée par égalité de chaînes.

`WorkoutSet` ne redéfinit pas `equals()`/`hashCode()` — toute comparaison (ex. détection
des séries Personal Record via `DataStorage.getRepRangePRSets()`) se fait par référence
d'objet, pas par valeur.

## Fonctionnalités livrées sur ce fork (mis à jour 11/09/2026, détail complet dans `fitnotes-migration-plan.md` du Projet Claude)

Dupliquer un exercice · Import Session (JSON) · Commentaire par série (avec icône, écran Sessions) · Calendrier de navigation avec indicateur de jours avec séance (Category Dots multicolores, vue Liste, filtres Catégorie/Exercice) · Sélection multiple/réorganisation d'exercices (Day, Workout) · Écarts Prévu/Réalisé (badge + historique) · Copier une séance / Copier la séance précédente / Déplacer une séance · Commentaire de séance entière · Onglet Sessions : édition/suppression par tap · Undo sur suppression de série + réordonnement · Timer de repos fiable (bip dédié, volume/durée réglables, Auto Start) + barre persistante · Chrono de séance entière · Historique des PR par nombre de reps précis · Badge "Personal Record" (trophée) sur le Set List · Chrono "Workout Time" · Favorite Exercises + Show Exercise Details · Category Colours (palette fixe) · Avertissement de suppression en cascade · Épuration IHM (barre de chrono, boutons conditionnels, Toolbar Settings) · Partage de séance ("Share workout", totaux Volume/Sets inclus) · Statistics par période (par exercice) · Goals (cible + progression par exercice) · **Reskin visuel en cours (Vague IHM)** : coins de cartes arrondis (`CardView.Light`), icônes/toolbar teintées `colorPrimary` sur Home Screen/écran de saisie/Calendrier.

## Intégration avec Coaching (workout_engine.py)

`docs/session-import-format.md` documente le format JSON consommé par `DayActivity → Import Session` : additif (ne touche jamais l'historique déjà loggé), matching par nom d'exercice exact, pas de déduplication en cas de ré-import du même fichier. C'est le point d'intégration déjà utilisé par `build_session_import_json()` côté projet **Coaching** — voir son `architecture.md`. Aucune modification du code Android n'a été nécessaire côté verifit pour cette intégration.

## Chantier de retrait en cours : `verifit_rs`

Backend Rust optionnel pour compte en ligne / sync multi-appareils, **abandonné officiellement** par décision de Romain (04-05/09/2026). Périmètre identifié : au moins 14 fichiers touchent `verifit_rs`/`verifitrs`/`WorkoutSetsApi`/le mode offline (`MainActivity.initActivity()`, `AddExerciseActivity.saveComment()`, `DataStorage`, `SettingsActivity`, `ExerciseAdapter`, `BackupService`, la classe `SharedPreferences` maison, `LoginActivity`/`ChangePasswordActivity`/`ForgotPasswordActivity`, package `verifitrs`). Approche prudente par étapes (forcer le mode offline + masquer l'UI de login d'abord, retrait du code mort ensuite) car pas de SDK Android côté Claude pour vérifier une suppression à la volée. **Statut d'avancement non suivi par cette session depuis le 05/09/2026** — à vérifier avec Romain.

## Limitations connues

- Pas de SDK Android côté Claude : aucun build/compile possible depuis Claude, seulement lecture/écriture de code + patch. Romain reste seul capable de tester sur appareil réel.
- `./gradlew build` échoue sur la tâche de test instrumenté (espresso-core 3.2.0 vs API 31) — sans impact sur `assembleDebug`/le bouton Run.
