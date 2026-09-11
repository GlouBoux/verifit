# todo.md — verifit (FitNotes_Fork_Migration)

*Mis à jour 11/09/2026 — voir note en tête de `CLAUDE.md`. Consolidé depuis
`fitnotes-migration-plan.md` (Projet Claude), la référence la plus à jour. `docs/
fitnotes-fork-todo.md` (suivi détaillé au jour le jour dans ce dépôt) accuse un retard
d'au moins les Vagues 3/4 et toute la Vague IHM — à rafraîchir séparément si besoin.*

## Vague IHM (reskin visuel) — en cours

- [x] IHM-1 : socle visuel commun (coins de cartes arrondis, `CardView.Light`) —
  **validé par Romain**.
- [x] IHM-2 : badge "Personal Record" (trophée) sur le Set List + correctif
  d'alignement (position fixe via `Guideline`) + toolbar/couleurs Home Screen/écran de
  saisie (déjà alignées) — **validé par Romain (11/09/2026)**.
- [x] IHM-3 : icônes du dialogue Calendrier tintées `colorPrimary`, boutons Save/Reset
  du panneau Filter en `MaterialButton` arrondi, cellule de jour déjà correcte —
  **validé par Romain (11/09/2026)**.
- [ ] IHM-4 : Liste d'exercices + catégories — **prochaine étape, démarrage
  11/09/2026**.
- [ ] IHM-5 : Progress/Records/Stats/Goals — pas commencé.
- [ ] IHM-6 (optionnel) : dialogues Workout Tools — pas commencé.

## Codé, en attente de rebuild/test réel par Romain (hors Vague IHM déjà validée)

- [ ] Popup "Set Added/Updated/Deleted+Undo/Restored" déplacée en haut de l'écran (variantes `AtTop` ajoutées à côté des méthodes existantes, écrans concernés seulement).
- [ ] Correctif : commentaire de la série précédente qui se copiait par erreur sur toute nouvelle série.
- [ ] Toolbar Settings : 2e volet (épinglage d'icônes) livré, pas encore rebuildé/retesté (1er correctif de troncature du nom d'exercice déjà validé/poussé).
- [ ] Vague 1 (Auto Start Rest Timer, Favorite Exercises + Show Exercise Details, Category Colours scope réduit, avertissement de suppression en cascade) — codée le 07/09, Romain a choisi de tester en une seule passe.
- [ ] Vague 2 (Supersets) — codée le 07/09, priorité revue par Romain (usage réel limité, reste codé).
- [ ] Vague 3 (Copy/Move/Comment Workout, Calendar Category Dots + List View + Filters) — codée le 07-08/09.

## Fait / validé

- [x] Vague 4 (Share Workout totaux, Statistics par période, Goals) — **validée,
  commitée et poussée par Romain le 11/09/2026**.
- [x] Import Session, Dupliquer un exercice, Commentaire par série, Timer de repos,
  Chrono de séance, Écarts Prévu/Réalisé, Sélection multiple/réorganisation,
  Historique des PR par reps, Script de conversion FitNotes → verifit, Intégration
  Coaching (`build_session_import_json`).

## En attente de décision / à planifier

- [ ] Onglet/section "Prévu vs Réalisé" visible pendant la séance — en pause, à réévaluer si le besoin se confirme à l'usage.
- [ ] Charges négatives pour exercices assistés/délestés — pas urgent, contournement actuel conservé.
- [ ] Simulation "à blanc" d'une vraie séance (test end-to-end) — annoncée par Romain, toujours à faire.
- [ ] Retirer le backend `verifit_rs` — décision prise, **statut non suivi par cette session depuis le 05/09/2026**, à vérifier avec Romain.
- [ ] Réorganisation/suppression multiple sur l'onglet Sessions (ex-Diary) — déprioritisé.
- [ ] Démarrage/arrêt automatique du timer de repos + suivi du repos réellement pris — à scoper.
- [ ] Générer un programme ("Routine") native — en pause, script `workout_engine.py` d'abord (Vague 5).
- [ ] Progress Graphs, parité complète (Vague 5, item 16) — à auditer avant d'estimer l'effort.
- [ ] Exercise Types avancés + Weight Unit par exercice (Vague 5, item 17).
- [ ] Écran "Analysis" à onglets (Workouts/Breakdown/Exercises/Goals/Records) — mémo pour plus tard, Romain donnera les détails au moment de s'y atteler.

## Pas urgent

- [ ] Upgrade AGP (actuellement 7.4.2) — reporté délibérément, pas bloquant.
