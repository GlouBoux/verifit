# decisions.md — verifit (FitNotes_Fork_Migration)

*Mis à jour 11/09/2026 — voir note en tête de `CLAUDE.md`. Section "Vague IHM et suite"
ajoutée ; le reste du fichier (rédigé par une autre session sans connaissance des
Vagues 3/4/IHM) est conservé tel quel en dessous, toujours valable sur son périmètre.*

## Vague IHM et suite (ajouté 11/09/2026)

- **11/09/2026** — Reskin visuel complet demandé par Romain une fois la Vague 4
  validée/commitée/poussée : *"Je voudrai que l'IHM de l'app soit la plus ressemblante
  possible à celle de Fitnotes."* **Inverse** la précision donnée le 08/09 ("le projet
  vise la parité fonctionnelle, pas un reskin visuel").
- **11/09/2026** — Thème **Light** retenu (pas de Dark) : Vérifit n'a aucune
  infrastructure de thème Dark aujourd'hui (une seule palette `colors.xml`, couleurs en
  dur dans la plupart des layouts) ; un vrai Dark demanderait de retoucher la
  quasi-totalité des layouts. Les couleurs Dark ré-échantillonnées sur les captures
  réelles de Romain (fond `#222222`, carte `#333333`, accent `#33b5e6`...) sont gardées
  en réserve pour un futur chantier Dark.
- **11/09/2026** — **Pas de tiroir de navigation latéral** façon FitNotes : Romain garde
  l'architecture actuelle d'`AddExerciseActivity` (un écran par exercice, déjà identifié
  comme écart d'architecture lors de la Vague 2/Supersets) et se limite au reskin
  (toolbar/couleurs/icônes).
- **11/09/2026** — Ordre de traitement du reskin, écran par écran : IHM-1 (socle
  commun) → IHM-2 (Home + écran de saisie) → IHM-3 (Calendrier) → IHM-4 (Exercices +
  catégories) → IHM-5 (Progress/Records/Stats/Goals) → IHM-6 optionnel (Workout Tools).
  IHM-1/2/3 validés par Romain le 11/09/2026, IHM-4 en cours de démarrage.
- **11/09/2026, retour Romain sur capture d'écran** — badge "Personal Record" (trophée,
  IHM-2) repositionné : chevauchait le texte "reps" une fois chaîné avec les badges
  commentaire/écart. Corrigé en l'ancrant sur une `Guideline` à position fixe plutôt
  que sur un élément voisin dont la largeur varie avec le nombre de chiffres des reps —
  garantit que le badge ne bouge jamais, que ce soit 1 ou 11 reps.
- **11/09/2026** — Dossier du clone de migration déplacé deux fois le même jour
  (réorganisation du Bureau de Romain selon la méthode PARA) : `Desktop\Training\
  FitNotes_Fork_Migration` → `Desktop\Projets\FitNotes_Fork_Migration` → emplacement
  actuel `Desktop\01_Projects\FitNotes_Fork\FitNotes_Fork_Migration`, désormais
  imbriqué dans le dossier du projet actif plutôt que dossier frère.
- **11/09/2026** — Découverte : ce jeu de 5 fichiers (CLAUDE.md/plan.md/todo.md/
  decisions.md/architecture.md) avait été rédigé par une autre session Claude à un
  moment où elle n'avait pas connaissance des Vagues 3/4 ni de la Vague IHM — figé à
  l'état du 07-08/09/2026. Remis à niveau ce jour. Point de vigilance permanent ajouté
  dans `CLAUDE.md` : plusieurs sessions Claude peuvent travailler sur ce dossier à des
  moments différents, toujours vérifier `fitnotes-migration-plan.md` (Projet Claude)
  en premier pour l'état réel.

---

## Choix de dépôt et de portée

- **Confirmé** — `MakisChristou/verifit` retenu comme dépôt de base (Java/Android natif, le plus proche d'un journal de musculation type FitNotes parmi les alternatives étudiées : opengym, Flexify, Lotti, OpenScale, Fast N Fitness).
- **05/09/2026** — Fork perso définitif (`GlouBoux/verifit`), aucune PR vers l'amont envisagée : c'est un projet taillé pour les besoins propres de Romain.
- **04-05/09/2026** — Retrait du backend en ligne `verifit_rs` (compte/sync multi-appareils) : feature abandonnée officiellement, périmètre identifié (~14 fichiers), retrait par étapes prudentes (voir architecture.md). **Statut non suivi par cette session depuis le 05/09/2026.**

## Fonctionnalités : décisions produit notables

- **Import Session (JSON)** retenu comme point d'intégration du générateur de séances Coaching plutôt qu'une rétro-ingénierie du backup Fitnotes — additif, aucune modification Android nécessaire.
- **Commentaire par série** : un vrai commentaire individuel par série (pas partagé entre toutes les séries de l'exercice du jour comme dans le code de base) — pour coller à l'usage FitNotes de Romain et fiabiliser une future migration de données.
- **06/09/2026** — Historique des PR par nombre de reps précis ajouté : jugé comme un manque important par Romain (nécessaire notamment pour le tag `[PR]` du partage de séance).
- **Partage de séance ("Share workout")** : ajout de `WorkoutSet.timestamp` (epoch millis nullable, renseigné à la validation manuelle d'une série) pour supporter la ligne "Time" du rapport, sur exemple réel fourni par Romain.
- **07/09/2026** — Idée d'un onglet "Prévu vs Réalisé" dédié : jugée pas prioritaire par Claude et Romain tant que le badge/dialogue existant (`set_discrepancy_dialog.xml`) n'a pas montré ses limites à l'usage — reste en pause.
- **Charges négatives (exercices assistés/délestés)** : identifiée comme vrai pain point, mais pas urgente — implique de revoir le calcul de volume, les comparaisons de PR, la formule 1RM (Epley) et l'affichage. Romain garde son contournement actuel (poids de référence fictif) en attendant.
- **07/09/2026** — Share Workout : séance entière, pas de sélection fine à la FitNotes.
- **07/09/2026** — Routines : script `workout_engine.py` d'abord, Routine native seulement si le besoin se confirme à l'usage.
- **08/09/2026** — Statistics par période et Goals : deux fonctionnalités entièrement construites de zéro (l'hypothèse initiale d'un écran existant à adapter était fausse pour Statistics).

## Workflow et process

- **05/09/2026** — Claude fournit systématiquement le message de commit prêt à copier-coller à chaque changement validé (Romain trouve leur rédaction pénible).
- **05/09/2026** — Incident : message de commit préparé pour verifit collé par erreur sur le dépôt Coaching (deux projets ouverts en parallèle). Pas de perte de code, corrigé par `git commit --amend`. Point de vigilance permanent depuis.
- Claude ne push jamais vers GitHub et ne fait jamais de changement large difficile à vérifier sans build en un seul coup (ex. retrait complet de `verifit_rs`) — préférence pour des étapes petites et testables individuellement.

## Bugs critiques corrigés

- **05/09/2026** — Bug critique de perte de données à l'Import Session : résolu et confirmé.
- **05/09/2026** — Incident `MainActivity.java` corrompu : leçon retenue pour le workflow (voir `docs/fitnotes-fork-plan.md`).
- **07/09/2026** — Commentaire de la série précédente copié par erreur sur toute nouvelle série : cause = boucle héritée du code de base devenue obsolète depuis l'introduction du commentaire individuel par série. Corrigée, pas encore rebuildée/retestée par Romain au moment de la rédaction initiale de ce fichier.
