# plan.md — verifit (FitNotes_Fork_Migration)

*Mis à jour 11/09/2026 — voir la note en tête de `CLAUDE.md` : ce fichier avait été
rédigé par une autre session sans connaissance des Vagues 3/4 ni de la Vague IHM.
Rafraîchi à partir de `fitnotes-migration-plan.md` (Projet Claude), qui reste la
référence la plus détaillée.*

## État actuel

Le fork a rattrapé la quasi-totalité des habitudes de suivi que Romain avait sur
FitNotes (commentaire par série, historique de PR par reps précis, timer de repos
fiable, partage de séance avec totaux, copier/déplacer une séance, commentaire de
séance, filtres et vue liste du Calendrier, Statistics par période, Goals) et
l'intégration avec le générateur de séances Coaching est livrée et en test à l'usage
réel. Vague 4 (Partage et Statistiques) **validée, commitée et poussée par Romain le
11/09/2026**.

## Chantier majeur en cours : Vague IHM — reskin visuel complet

Insérée entre la Vague 4 (validée) et la Vague 5 (chantiers lourds), à la demande
explicite de Romain le 11/09/2026 : rapprocher l'apparence de l'app de celle de
FitNotes (couleurs, typo, icônes, espacements), écran par écran, sans rien retirer des
fonctionnalités déjà ajoutées par le fork. **Thème retenu : Light** (Vérifit n'a aucune
infrastructure de thème Dark). **Pas de tiroir de navigation latéral** façon FitNotes —
Romain garde l'architecture actuelle (un écran par exercice).

Ordre de traitement et statut au 11/09/2026 :
- **IHM-1 (socle visuel commun)** — ✅ codé et **validé par Romain** (coins de cartes
  arrondis, `CardView.Light` redéfini).
- **IHM-2 (Home Screen + écran de saisie)** — ✅ codé et **validé par Romain** : badge
  "Personal Record" (trophée) ajouté sur le Set List, avec correctif d'alignement
  (position fixe via `Guideline`, ne bouge plus entre 1 et 11 reps) ; toolbar/couleurs
  déjà alignées avec FitNotes, rien à changer.
- **IHM-3 (Calendrier)** — ✅ codé et **validé par Romain** : icônes du dialogue
  Calendrier tintées en bleu (`colorPrimary`), boutons Save/Reset du panneau Filter
  passés en `MaterialButton` arrondi, cellule de jour déjà correcte.
- **IHM-4 (Liste d'exercices + catégories)** — 🚧 **prochaine étape (11/09/2026)**,
  Romain vient de valider IHM-2/IHM-3 et de demander de passer à la suite.
- IHM-5 (Progress/Records/Stats/Goals) — pas commencé.
- IHM-6, optionnel (dialogues Workout Tools) — pas commencé.

Détail complet (fichiers touchés, choix de conception, captures de référence FitNotes,
couleurs Dark ré-échantillonnées en réserve pour un futur chantier Dark) : voir
`fitnotes-migration-plan.md` dans le Projet Claude, section "Vague IHM".

## Retrait du backend en ligne `verifit_rs`

Décision actée (feature abandonnée), périmètre large (~14 fichiers). **Statut non
suivi par cette session au 11/09/2026** — dernière info connue au 05/09/2026 (voir
`architecture.md`/`decisions.md`) : approche par étapes prudentes envisagée (forcer
l'offline + masquer l'UI de login, puis retirer le code mort). À vérifier avec Romain
si ce chantier a avancé en parallèle de la Vague IHM.

## Prochain focus

1. **IHM-4** : reskin de la liste d'exercices et des catégories (`ExercisesActivity`/
   `ExerciseAdapter`), en cours de démarrage.
2. Valider par un test réel "à blanc" les items codés-mais-pas-rebuildés qui
   s'accumulent depuis plusieurs vagues (voir `todo.md`).
3. Poursuivre IHM-5/IHM-6 une fois IHM-4 validé, puis reprendre la Vague 5 (Routines/
   `workout_engine.py`, Progress Graphs, Exercise Types avancés).

## Reporté / pas prioritaire

- Onglet "Prévu vs Réalisé" dédié — en pause tant que le besoin ne se confirme pas à l'usage.
- Charges négatives (exercices assistés) — pain point réel mais contournement acceptable en attendant.
- Upgrade AGP.
- Routines natives façon FitNotes — script `workout_engine.py` d'abord (voir Vague 5).

## Lien avec Coaching

L'intégration du générateur de séances est **le point de couplage principal** entre les deux projets : toute évolution du format `session-import-format.md` doit rester coordonnée avec `architecture.md`/`decisions.md` du projet Coaching, en gardant à l'esprit la condition explicite de Romain — rester facile à faire évoluer, pas de couplage rigide tant que l'intégration est en période de test.
