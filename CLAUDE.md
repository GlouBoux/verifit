# CLAUDE.md — verifit (FitNotes_Fork_Migration)

Contexte à lire en premier pour toute session Claude sur ce dossier.

**⚠️ Mise à jour 11/09/2026** : ce fichier (ainsi que `plan.md`/`todo.md`/`decisions.md`/
`architecture.md`) était resté figé à l'état du 07-08/09/2026 — une autre session
Claude l'avait rédigé sans connaissance des Vagues 3 et 4 (Copy/Move/Comment Workout,
filtres Calendrier, Statistics, Goals) ni du chantier de reskin visuel démarré le
11/09/2026 (voir plus bas). Remis à niveau ce jour à partir du plan de migration tenu
dans le Projet Claude "FitNotes_Fork" (fichier `fitnotes-migration-plan.md`), qui reste
la source de référence la plus à jour et la plus détaillée pour tout ce chantier — ces
5 fichiers locaux n'en restent qu'un résumé.

## Ce qu'est ce projet

Fork personnel (`GlouBoux/verifit`, branche active `feature/fitnotes-parity`) de **verifit**, une app Android native (Java) de suivi de musculation open-source, dont l'UI est déjà "heavily inspired by FitNotes" à la base — pas un fork du code source de FitNotes lui-même. Dépôt de base : `MakisChristou/verifit` (archivé depuis juin 2025, choisi après comparaison avec opengym/Flexify/Lotti/OpenScale/Fast N Fitness — aucun n'était aussi proche d'un journal de musculation Android natif). **Décision actée le 05/09/2026 : on reste définitivement sur ce fork perso, pas de PR vers l'amont.**

**Emplacement (mis à jour 11/09/2026)** : ce dossier de clone vit maintenant sous
`C:\Users\Brutus\Desktop\01_Projects\FitNotes_Fork\FitNotes_Fork_Migration` (Bureau de
Romain réorganisé selon la méthode PARA). Le projet actif d'origine (`verifit/` à la
racine, sans les 5 fichiers Claude File System) est le dossier parent
`01_Projects\FitNotes_Fork` — ce clone de migration y est imbriqué comme sous-dossier,
et non plus dossier frère comme avant le 11/09.

## Objectif produit

Faire converger verifit vers les habitudes de suivi que Romain avait sur FitNotes (commentaire par série, historique de PR par nombre de reps précis, timer de repos fiable, etc.), plus l'intégration du générateur de séances `workout_engine.py` (projet **Coaching**, voir son propre `CLAUDE.md`) via le point d'import JSON "Import Session".

**Chantier en cours (11/09/2026) : reskin visuel complet pour se rapprocher de
FitNotes** ("Vague IHM", insérée après la Vague 4 et avant la Vague 5 — voir `plan.md`).
Inverse une précision donnée à Romain le 08/09 ("le projet vise la parité
fonctionnelle, pas un reskin visuel") : Romain a explicitement demandé un reskin
complet une fois la Vague 4 validée. Thème retenu : **Light** (pas de Dark — aucune
infra de thème n'existe côté Vérifit). Pas de tiroir de navigation latéral façon
FitNotes (Romain a choisi de garder l'architecture actuelle, un écran par exercice).

## Stack

- Java, Android natif, GPL-3.0, minSdk 16 / target 31, Gradle 7.4.2 (AGP), module principal `verifit`.
- Bibliothèques : MPAndroidChart (graphiques), Sardine (WebDAV), Gson (JSON).
- Backend en ligne optionnel `verifit_rs` (API Rust, sync multi-appareils) — feature abandonnée officiellement par Romain, retrait en cours (voir todo.md). **Statut non suivi par cette session (11/09/2026)** — dernière info connue : voir `architecture.md`, à vérifier avec Romain si le retrait a avancé depuis le 05/09.

## Documents de référence existants (ne pas dupliquer)

- **`fitnotes-migration-plan.md`** (Projet Claude "FitNotes_Fork", pas dans ce dépôt) — **la référence la plus à jour** pour tout le chantier Vagues 1 à IHM en cours : détail complet de chaque feature, fichiers touchés, décisions produit, statut de rebuild/test.
- `docs/fitnotes-fork-plan.md` — journal détaillé de chaque fonctionnalité (le pourquoi, l'implémentation, le statut). **Non mis à jour depuis le 08/09/2026** au moment de cette note.
- `docs/fitnotes-fork-todo.md` — suivi coché/pas coché au jour le jour. **Idem, non mis à jour depuis le 08/09/2026.**
- `docs/fitnotes-fork-workflow.md` — **comment on travaille ensemble sur ce projet** (voir ci-dessous, résumé) — toujours valable.
- `docs/session-import-format.md` — format d'échange avec le générateur de séances Coaching.

`plan.md`, `todo.md`, `architecture.md`, `decisions.md` de ce dossier consolident ces documents pour une vue d'ensemble rapide — les docs ci-dessus restent la référence détaillée. **En pratique au 11/09/2026, c'est `fitnotes-migration-plan.md` (Projet Claude) qui est le plus à jour ; les fichiers `docs/fitnotes-fork-*.md` de ce dépôt accusent un retard d'au moins 2 vagues (3 et 4) plus tout le chantier IHM.**

## Workflow à respecter (résumé de docs/fitnotes-fork-workflow.md)

1. Cadrage avant de coder (comportement voulu, priorité, périmètre).
2. Claude modifie le code directement sur la machine de Romain (pont device), livre aussi un patch git pour traçabilité (jamais besoin de l'appliquer manuellement).
3. Romain relit le diff, build + teste sur un vrai téléphone via Android Studio (pas d'émulateur pour les bugs de cycle de vie).
4. Si validé, Romain committe — **Claude fournit toujours le message de commit prêt à copier-coller** (Romain trouve leur rédaction pénible).
5. Claude ne push jamais vers GitHub, ne fait jamais de changement large et difficile à vérifier sans build en un seul coup.
6. **Point de vigilance** : plusieurs dépôts ouverts en parallèle (verifit + Coaching) → toujours vérifier sur quel dépôt on committe avant de coller un message préparé (incident déjà survenu le 05/09/2026, rattrapé par `git commit --amend`).
7. **Point de vigilance (ajouté 11/09/2026)** : plusieurs sessions Claude peuvent travailler sur ce même dossier à des moments différents — toujours relire `fitnotes-migration-plan.md` (Projet Claude) en premier pour l'état réel avant de se fier à ces 5 fichiers locaux, et mettre à jour les DEUX à chaque changement significatif si possible.

## Limite connue

Pas de SDK Android côté Claude : impossible de builder/compiler pour vérifier un changement. Seul Romain peut tester sur émulateur/téléphone réel.

## Build

```
scripts\build_and_log.bat   # build assembleDebug --info, écrit build_log.txt à la racine (stdout+stderr)
```

`./gradlew build` (tâche générique) échoue systématiquement sur `:verifit:processDebugAndroidTestManifest` (espresso-core 3.2.0 trop ancien pour l'API 31) — sans rapport avec les changements du fork, ne bloque ni le bouton Run d'Android Studio ni `assembleDebug`.
