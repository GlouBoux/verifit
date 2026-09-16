# Plan de migration de features FitNotes → Vérifit

Ce document propose (1) une stratégie de travail pour mener une migration de grande
ampleur sans perturber l'usage quotidien réel de l'app, et (2) une feuille de route
priorisée des features identifiées dans les 7 docs d'inventaire déjà écrits
(`fitnotes-features-*.md`). Écrit après audit du dépôt réel
(`C:\Users\Brutus\Desktop\Training\FitNotes_Fork\verifit`, module Android
`com.example.verifit`, remote `origin` = `GlouBoux/verifit`, `upstream` =
`MakisChristou/verifit`, branche active `feature/duplicate-exercise-and-session-import`).

## 1. Stratégie de travail : clone séparé, pas une copie manuelle

Ton instinct ("faire une autre copie qu'on fait évoluer") est le bon réflexe, mais pour
la bonne raison qu'il faut préciser : ce n'est pas parce que git te bloquerait, c'est
parce que **tu utilises Vérifit tous les jours pour de vraies séances**, et cette
migration va probablement laisser le code dans des états intermédiaires cassés entre
deux sessions de travail (des features comme les Routines ou les Supersets touchent
plusieurs écrans à la fois). Basculer de branche dans le même dossier voudrait dire que
ton app "quotidienne" devient inutilisable pendant des semaines à chaque fois qu'on est
au milieu d'un chantier.

**Recommandation : un second `git clone` (pas une copie de dossier manuelle), dans un
dossier frère, sur une nouvelle branche.**

Pourquoi un clone plutôt qu'un simple copier-coller du dossier :
- Le clone garde l'historique git complet et reste connecté à `origin`
  (`GlouBoux/verifit`) — tu peux pousser cette branche, la merger plus tard, ou
  l'abandonner proprement sans rien perdre.
- Une copie de dossier sans `.git` perd la connexion à l'historique ; une copie AVEC
  `.git` recopié est en fait un clone fait à la main, mais sans le filet de sécurité
  (tu peux facilement oublier un fichier, ou copier un état avec des changements non
  committés sans t'en rendre compte).
- `git diff`/`git log` entre les deux dossiers restent lisibles et comparables.

Concrètement :

```bash
cd C:\Users\Brutus\Desktop\Training
git clone https://github.com/GlouBoux/verifit.git FitNotes_Fork_Migration
cd FitNotes_Fork_Migration
git checkout feature/duplicate-exercise-and-session-import
git checkout -b feature/fitnotes-parity
```

(Le nouveau clone part de la même branche que ton travail actuel — pas de `master`,
qui est resté au point de fork d'origine et n'a jamais reçu tes commits.)

Astuce pratique pour Android, pour pouvoir garder les DEUX apps installées sur ton
téléphone en même temps (l'app "réelle" pour continuer à logger tes séances, l'app de
migration pour tester sans risque) :

- Dans `verifit/build.gradle` **du clone de migration uniquement**, change
  `applicationId "com.whatever.verifit"` en `applicationId "com.whatever.verifit.dev"`.
  Android traite ça comme une app complètement différente (données séparées, icône
  séparée) — aucun risque d'écraser tes vraies données en testant.
- Pense aussi à changer le nom affiché (`app_name` dans `strings.xml`) en quelque chose
  comme "Vérifit DEV" pour ne pas confondre les deux icônes sur ton launcher.
  ✅ **Fait** (07/09/2026) — `applicationId` et nom d'app changés sur le clone de
  migration, les deux apps peuvent tourner en parallèle sur ton téléphone.
- L'app de migration démarrera avec une base vide. Si tu veux tester avec des données
  réalistes, réimporte simplement un des CSV que tu as déjà (`verifit_import_from_fitnotes.csv`
  ou `verifit_import_combined_2026-09-04.csv`) via le flux d'import existant.

Le reste du workflow ne change pas (voir `fitnotes-fork-workflow.md`) : Claude modifie
le code sur ta machine via le pont, tu builds/testes depuis Android Studio, tu commits
avec le message fourni. La seule différence est le dossier de travail actif pendant la
durée de ce chantier.

**Quand une vague ci-dessous est validée sur le clone de migration**, le commit peut
être cherry-pické ou mergé vers ta branche de travail habituelle
(`feature/duplicate-exercise-and-session-import` dans `FitNotes_Fork`) dès que tu veux
qu'elle devienne "réelle" — pas besoin d'attendre que TOUT le chantier soit fini pour en
profiter au quotidien. Quand le clone de migration a rattrapé/dépassé ton dossier
habituel, tu peux basculer dessus comme nouveau dossier principal et abandonner
l'ancien.

## 2. Feuille de route par vagues

Priorisation par rapport valeur/effort, en s'appuyant sur ce qui est déjà solide côté
Vérifit (transitivité des PR, chrono de séance, sélection multiple, minuteur de repos
avancé) plutôt que de tout reprendre à zéro. Chaque item renvoie au doc d'inventaire
source pour le détail du comportement FitNotes de référence.

### Vague 1 — Petits gains isolés, fondations pour la suite

Peu de fichiers touchés chacun, aucune dépendance entre eux, bon échauffement sur le
nouveau dossier de travail.

1. **Auto Start du Rest Timer** (`fitnotes-features-workout-tools.md`) — l'infra du
   minuteur (son, volume, durée réglables) existe déjà et est solide ; il ne manque que
   le déclenchement automatique à la création d'une série. Répond directement à ta
   demande en attente dans `fitnotes-fork-todo.md`.
2. **Favorite Exercises** + **Show Exercise Details** (Workout Count / Last Used Date)
   (`fitnotes-features-exercises.md`) — deux ajouts de confort sur la liste d'exercices
   existante, pas de nouvelle logique métier complexe.
3. **Category Colours** — ✅ **codé, adapté au périmètre réel (07/09/2026)** :
   découverte en l'attaquant, il n'existe **aucun écran "liste de catégories" côté
   Vérifit** — les catégories sont un simple tableau figé dans le code
   (`R.array.Categories`/`strings.xml` : Chest/Back/Shoulders/Biceps/Triceps/Legs/Abs),
   pas de données éditables. Construire tout l'écran de gestion (Add/Edit/Delete/
   Reorder Category, `fitnotes-features-exercises.md` section 10) aurait dépassé le
   calibrage "petit gain isolé" de cette vague, donc **scope réduit à l'essentiel qui
   sert la Vague 3** : nouvelle classe `CategoryColours.java` (palette fixe, une
   couleur par catégorie) + point de couleur affiché à côté de la catégorie dans la
   liste d'exercices (`exercise_row.xml`/`ExerciseAdapter.java`) pour valider la
   palette visuellement. Pas de personnalisation utilisateur des couleurs pour
   l'instant (juste la palette fixe) — à revoir si Romain veut un vrai écran de gestion
   des catégories plus tard, indépendamment du Calendrier.
4. **Avertissement de suppression en cascade** — ✅ **codé, pas encore rebuildé/retesté
   (07/09/2026)** : le message de `delete_exercise_dialog.xml` est désormais rempli
   dynamiquement ("Supprimer 'X' ? N séries sur M séances seront supprimées
   définitivement, avec leur historique et les records personnels associés.") au lieu
   du texte générique d'origine. Scope réduit à la suppression d'exercice (seule
   opération de suppression en cascade qui existe réellement côté Vérifit
   aujourd'hui — pas de suppression de catégorie possible, puisqu'aucune gestion de
   catégorie n'existe, voir item Category Colours ci-dessus ; pas de Goals à mentionner
   non plus, feature pas encore construite, vague 4).
5. **Cloner un exercice** — ✅ **déjà fait, découvert le 07/09/2026** : en préparant
   cet item j'ai trouvé que "Duplicate Exercise" existe déjà entièrement dans
   `ExerciseAdapter.java` (menu "⋮" → Duplicate, nom pré-rempli avec suffixe
   "(copy)", changement de catégorie possible, vérification anti-collision de nom) —
   c'est très probablement le sujet de la branche `feature/duplicate-exercise-and-
   session-import` déjà active. Rien à coder, item retiré de la liste de travail.

**Todo à ne pas perdre** : Romain a mentionné d'autres pain points déjà remontés sur
Vérifit dont il ne se souvient plus sur le moment (07/09/2026) — à lister ici dès qu'ils
reviennent, plutôt que de les chercher activement pour l'instant.

### Vague 2 — Supersets (fondation pour Mark Sets Complete et les Routines)

5. **Supersets** (`fitnotes-features-workout-tracking.md`) — ✅ **codé, pas encore
   rebuildé/retesté (07/09/2026)**. Grouper des exercices, barre colorée dans la liste
   du jour, enchaînement automatique après validation d'une série, groupe qui boucle sur
   le premier exercice après le dernier. Voir le détail (fichiers touchés, découverte
   d'architecture, choix de conception) dans la section 4 ci-dessous.
   ⚠️ **Priorité revue (07/09/2026, retour Romain)** : usage réel limité — Romain
   supersette déjà quelques exercices "à la main" sans difficulté. Reste codé (c'est
   fait), mais ne conditionne plus la priorité des items suivants.
   **Correctif (07/09/2026)** : en implémentant la suite, découvert que la barre
   colorée et les actions Group/Ungroup n'existaient que sur `DayActivity` (écran
   ouvert depuis le Calendrier) — pas sur le vrai écran d'accueil quotidien
   (`MainActivity`/carrousel `ViewPager2`, onglet "Workout"), qui a son propre
   adapter (`ViewPagerExerciseAdapter`/`ViewPagerWorkoutDayAdapter`). Pire : comme les
   deux écrans partagent `exercise_selection_action_menu.xml`, les boutons
   Group/Ungroup apparaissaient déjà sur l'écran d'accueil mais ne faisaient rien.
   Corrigé en dupliquant la logique (barre colorée + Group/Ungroup) sur
   `ViewPagerExerciseAdapter`/`MainActivity`, comme le reste des fonctionnalités de
   sélection multiple qui existaient déjà en double sur ces deux écrans.

### Vague 3 — Séance : ce qui touche le Home Screen et le Calendrier

6. **Mark Sets Complete** (`fitnotes-features-workout-tracking.md`) — ⏸️ **repoussé
   (07/09/2026, retour Romain)** : surtout pensé pour une séance pré-remplie (Routine ou
   Copy Previous Workout), or Romain n'utilise pas de Routine native (voir vague 5, item
   15). Retiré de l'ordre de travail actif ; à reconsidérer une fois Copy Previous
   Workout en place (item 7 ci-dessous), qui rendra l'usage réel plus clair.
7. **Copy Workout / Copy Previous Workout** — ✅ **codé, pas encore rebuildé/retesté
   (07/09/2026)**. Voir le détail dans la section 4 ci-dessous.
8. **Move a Workout** — ✅ **codé en même temps que Copy Workout (07/09/2026)**, mêmes
   fichiers/mécanisme (voir section 4).
9. **Comment a Workout** — ✅ **codé, pas encore rebuildé/retesté (07/09/2026)**
   (commentaire de séance entière, distinct du commentaire par série déjà existant sur
   chaque série). Voir le détail dans la section 4 ci-dessous.
10. **Calendar : Category Dots multicolores + List View** (`fitnotes-features-calendar.md`)
    — le calendrier actuel (patch #7) affiche déjà un point par jour ; ce lot ajoute la
    couleur par catégorie (dépend de la vague 1, item 3) et la vue liste alternative.
11. **Calendar : Category Filter + Exercise Filter** (`fitnotes-features-calendar.md`)
    — probablement le plus utile de tout le lot Calendrier vu ton historique importé
    (~3800 séries) : retrouver "la séance où j'ai fait 100kg x 5 au bench" sans scroller
    manuellement.

### Vague 4 — Partage et Statistiques

12. **Share Workout** (`fitnotes-features-home-screen.md`) — ✅ **tranché** (07/09/2026) :
    Romain garde le partage de la séance entière, pas de sélection fine à la FitNotes.
    Donc pas d'écran de sélection à construire ; à voir seulement si les lignes
    optionnelles Total Volume/Total Sets valent le coup d'être ajoutées à l'export
    actuel.
13. **Statistics par période** (`fitnotes-features-progress-tracking.md`) — ⚠️ **révisé
    après lecture du code** : ce n'est pas un manque complet. Le dialogue
    `exercise_history_stats_dialog.xml` (+ `ExercisePersonalStats.java`) affiche déjà
    Total Sets/Reps/Volume, Max Weight/Reps/Set Volume et l'Estimated 1RM — mais en
    "depuis toujours" fixe, sans sélecteur de période. Le vrai delta avec FitNotes est
    donc **juste le sélecteur Period (Workout/Week/Month/Year/All/Custom)** au-dessus
    d'un écran qui existe déjà largement — effort plus faible que prévu initialement.
14. **Goals** (`fitnotes-features-progress-tracking.md`) — cible + barre de progression
    par exercice. Nouveau modèle de données simple, UI simple, mais entièrement à
    créer (pas de socle existant identifié).

### Vague 5 — Chantiers lourds, à ne lancer qu'une fois le reste stabilisé

15. **Routines** (`fitnotes-features-routines.md`) — ✅ **tranché** (07/09/2026) : on
    avance d'abord sur `workout_engine.py` (le script), la Routine native façon FitNotes
    (structure Days/exercices, séries prédéfinies, écran d'édition dédié) ne sera
    implémentée que si le besoin s'en fait vraiment sentir après usage du script.
    Chantier donc **retiré de la feuille de route active** pour l'instant ; à
    ré-envisager plus tard si le script montre ses limites.
16. **Progress Graphs, parité complète** (`fitnotes-features-progress-tracking.md`) —
    MPAndroidChart est déjà une dépendance et `activity_charts.xml` (22 Ko) existe déjà :
    à auditer précisément AVANT d'estimer l'effort (probablement partiellement couvert,
    comme pour Statistics ci-dessus). Ne pas supposer que les 12 types de graphique
    manquent tous.
17. **Exercise Types avancés + Weight Unit par exercice** (Supporter)
    (`fitnotes-features-exercises.md`) — à rapprocher de la discussion déjà en pause sur
    les charges négatives/délestées dans `fitnotes-fork-todo.md` : plutôt qu'un chantier
    isolé, ça vaut le coup de trancher les deux sujets ensemble (même zone de code :
    modèle de série, formules de volume/1RM, affichage).

## 3. Points tranchés (07/09/2026)

Les trois points ouverts de la version précédente sont réglés :

- **Share Workout** (vague 4, item 12) : séance entière, pas de sélection fine. Voir
  vague 4 mise à jour.
- **Routines** (vague 5, item 15) : script `workout_engine.py` d'abord, Routine native
  seulement si le besoin se confirme à l'usage. Voir vague 5 mise à jour.
- **"Analysis"** : Romain a partagé les captures d'écran de son FitNotes Supporter.
  Ce n'est pas une feature isolée mais tout un écran d'analytics à onglets, plus riche
  que ce que les docs d'inventaire laissaient supposer :
  - **Workouts** : graphique temporel avec sélecteur (Volume Per Workout, etc.), filtres
    de période (1m/3m/6m/1y/all) et ligne de tendance — recoupe la vague 5, item 16
    (Progress Graphs).
  - **Breakdown** : donut chart de répartition (ex. "Number Of Sets By Category") avec
    sélecteur de période (Week/Month/...) et plage de dates — **nouveau**, pas couvert
    par les docs d'inventaire existants ; proche dans l'esprit de la vague 4 item 13
    (Statistics) mais en vue agrégée multi-catégories plutôt que par exercice.
  - **Exercises** : mêmes graphiques que ci-dessus mais par exercice (ex. Estimated 1RM
    dans le temps) — recoupe vague 5 item 16 et vague 4 item 13.
  - **Goals** : barres de progression par exercice/objectif — recoupe directement vague
    4 item 14 (Goals), déjà identifiée comme feature à créer de zéro.
  - **Records** : tableau croisé RM (1RM à ~20RM) par variante d'exercice — **nouveau**,
    plus détaillé que le `personal_record_dialog.xml`/`rep_range_history_dialog.xml`
    déjà existants côté Vérifit (qui donnent la PR par rep range mais pas sous forme de
    tableau croisé multi-exercices).
  Romain a dit explicitement qu'il donnera les détails d'implémentation au moment de
  s'y atteler plutôt que de tout spécifier maintenant — donc pas de nouvelle vague créée
  pour l'instant, ce point sert de mémo pour quand on attaquera les vagues 4/5.

## 4. Statut

✅ **Copy Workout / Copy Previous Workout / Move a Workout codés le 07/09/2026** sur le
clone de migration — **pas encore rebuildés/retestés**.
- **Copy Workout** (menu "...") : ouvre le calendrier existant (`CalendarPickerDialog`)
  pour choisir le jour source, puis une liste à cocher (tout coché par défaut, "Select
  All" implicite) des EXERCICES de ce jour — copie les séries des exercices cochés vers
  le jour actuellement affiché.
- **Copy Previous Workout** (menu "...") : même chose mais saute le choix du calendrier
  — reprend directement le jour avec des séries le plus récent avant celui affiché
  (`DataStorage.getMostRecentWorkoutDateBefore()`).
- **Move Workout** (menu "...") : identique à Copy Workout, puis retire les séries
  copiées du jour source (et supprime ce jour s'il ne reste plus rien).
- **Scope réduit par rapport à FitNotes** : sélection par EXERCICE ENTIER (case à cocher
  standard Android), pas par série individuelle avec bouton "Edit" avant validation —
  un écran dédié pour ce niveau de détail (comme le vrai Training Screen de FitNotes)
  aurait été disproportionné par rapport au besoin réel ("je refais une séance déjà
  loggée"/"je me suis trompé de date"). Chaque série copiée est un `WorkoutSet` tout
  neuf (nouvelle date, pas d'id/timestamp/valeurs "prévues" repris de l'original) —
  jamais partagé entre les deux jours. Fichiers touchés : `DataStorage.java` (nouvelles
  méthodes `copySetsToDay()`/`removeExerciseSetsFromDay()`/
  `getMostRecentWorkoutDateBefore()`), `MainActivity.java`, `DayActivity.java`,
  `main_activity_menu.xml`, `day_activity_menu.xml`.

✅ **Comment a Workout codé le 07/09/2026** sur le clone de migration — **pas encore
rebuildé/retesté**. Nouveau champ `Comment` sur `WorkoutDay` (même logique défensive que
`ExerciseOrder`/`SupersetGroups` pour les anciennes sauvegardes). Action "Comment
Workout" dans le menu "..." de l'écran d'accueil (`MainActivity`) ET de `DayActivity`
(les deux écrans où on peut consulter/loguer une séance) — ouvre un dialogue simple
(champ texte, pré-rempli si un commentaire existe déjà). Affiché en italique gris
au-dessus de la liste d'exercices sur les deux écrans (masqué si vide), et repris dans
le texte du "Share Workout" existant juste sous l'en-tête de date. Si le dialogue est
ouvert sur un jour sans aucune série encore loggée, un `WorkoutDay` "coquille vide" est
créé pour porter le commentaire — supprimé automatiquement si finalement laissé vide
(annulation ou sauvegarde d'un texte vide), pour ne pas polluer les données avec des
jours fantômes. Fichiers touchés : `WorkoutDay.java`, `WorkoutReportGenerator.java`,
`MainActivity.java`, `DayActivity.java`, `ViewPagerWorkoutDayAdapter.java`,
`main_activity_menu.xml`, `day_activity_menu.xml`, `item_view_pager.xml`,
`activity_day.xml`.

✅ **Vague 2 (Supersets) codée le 07/09/2026** sur le clone de migration — **pas encore
rebuildée/retestée**. Une découverte d'architecture a orienté plusieurs choix, détaillée
ci-dessous car elle s'écarte du comportement FitNotes de référence sur un point précis
(l'enchaînement automatique) :

- **Découverte** : FitNotes permet de changer d'exercice pendant une séance sans changer
  d'écran, via son "Navigation Panel" (tiroir latéral). Vérifit n'a **aucun équivalent** —
  `AddExerciseActivity` est un écran par exercice, sans mécanisme de bascule interne
  (vérifié par recherche exhaustive de ViewPager/Spinner/bouton "exercice suivant" —
  aucun trouvé). L'enchaînement automatique ne peut donc pas être un simple
  rafraîchissement en place ; il relance une nouvelle instance de l'écran sur l'exercice
  suivant (`finish()` puis `startActivity()`, sans animation de transition pour rester
  aussi immédiat que possible) — visuellement un changement d'écran très bref plutôt
  qu'une bascule invisible comme sur FitNotes, mais fonctionnellement équivalent (la
  pile de retour ne grossit pas : chaque exercice du superset remplace le précédent).
- **Modèle de données** : nouvelle classe `SupersetGroup` (id, nom optionnel, couleur,
  liste ordonnée de noms d'exercices, `AutoAdvance` bool) rattachée à `WorkoutDay` (comme
  `ExerciseOrder` déjà existant) plutôt qu'à `WorkoutExercise` (classe de stats/agrégats
  recalculée à chaque `UpdateData()`, pas un bon endroit pour stocker de la
  configuration). Auto-nettoyage dans `UpdateData()` : un exercice qui disparaît du jour
  sort de son groupe, un groupe à moins de 2 membres est dissous — même logique
  défensive que `ExerciseOrder` pour rester compatible avec les ~3800 séries déjà
  loggées (absent des anciennes sauvegardes → Gson retombe sur liste vide).
- **UI** : réutilise la sélection multiple déjà existante dans `DayActivity` (celle qui
  servait à la suppression groupée) — deux nouvelles actions "Group"/"Ungroup" dans la
  barre d'action contextuelle. "Group" sur une sélection qui inclut un exercice déjà
  groupé sert à renommer/reconfigurer ce groupe plutôt que d'en créer un second. Pas de
  sélecteur de couleur manuel en v1 (couleur assignée automatiquement dans une palette
  de 8, `SupersetColours`) — jugé secondaire par rapport au cœur de la feature ; à
  ajouter plus tard si le besoin s'en fait sentir.
- **Réglage par groupe, pas global** : case "Automatically move to next exercise after
  each set" dans le dialogue de création, **cochée par défaut**. Choix délibéré de la
  mettre par groupe plutôt qu'un réglage global unique dans Settings — au cas où ce
  changement d'écran systématique ne conviendrait pas en pratique pour certains
  enchaînements, sans devoir désactiver l'auto-advance partout. **Point à valider par
  toi à l'usage réel** : si le changement d'écran (même sans animation) casse le rythme
  pendant une séance, dis-le-moi et je peux basculer sur un comportement moins
  intrusif (ex. un bandeau/notification "Superset : passer à X ?" à valider d'un tap au
  lieu d'un saut automatique).
- Fichiers touchés : `SupersetGroup.java` (nouveau), `SupersetColours.java` (nouveau),
  `WorkoutDay.java`, `DayExerciseAdapter.java`, `DayActivity.java`,
  `AddExerciseActivity.java`, `day_exercise_row.xml`, `exercise_selection_action_menu.xml`.

✅ **Vague 1 entièrement codée le 07/09/2026** sur le clone de migration
(`applicationId` `com.whatever.verifit.dev`) — **pas encore rebuildée/retestée par
Romain**, qui a délibérément choisi de tester en une seule passe à la fin de la vague
plutôt qu'après chaque item, pour avancer plus vite pendant sa fenêtre d'utilisation.
5 items sur 5 : Auto Start Rest Timer (codé), Favorite Exercises + Show Exercise
Details (codé), Category Colours (codé, scope réduit — voir item 3), Avertissement de
suppression en cascade (codé, scope réduit — voir item 4), Cloner un exercice (déjà
présent avant même de commencer, découverte).

- [x] **Auto Start du Rest Timer — codé, pas encore rebuildé/retesté (07/09/2026)** :
  démarre automatiquement le minuteur de repos à chaque nouvelle série loggée (dans
  `addSetExistingWorkoutDay()`/`addSetNewWorkoutDay()`, seuls appelants de
  `updateViewAndShowMessage()`), avec la durée déjà configurée — redémarre le décompte
  à zéro même si un repos était déjà en cours. Nouveau réglage **Auto Start**
  (`CheckBox` dans la boîte de dialogue "Timer", `timer_dialog.xml`), **désactivé par
  défaut**. Fonctionne même si la boîte de dialogue "Timer" n'a jamais été ouverte
  (réutilise `loadTimerDurationFromPrefs()`/`resetTimer()`/`startTimer()`, déjà conçus
  pour ça — même mécanique que la barre de chrono de séance persistante). Pas de
  réglage "Auto Stop" : FitNotes lui-même n'en propose pas (même ambiguïté déjà notée
  pour le chrono de séance — pas de signal fiable de "dernière série"). Fichiers
  touchés : `AddExerciseActivity.java`, `timer_dialog.xml`. **Pas de patch git généré
  cette fois** (pas d'accès à `git diff` depuis l'environnement Claude sur ce nouveau
  clone) — les fichiers sont à jour sur disque, `git diff`/`git status` chez toi
  suffisent pour revoir le changement avant de tester.

- [x] **Favorite Exercises + Show Exercise Details — codé, pas encore rebuildé/retesté
  (07/09/2026)** : le champ `favorite` existait déjà (mort) dans `Exercise.java`/
  `DataStorage.setFavoriteExercise()` — juste manquant côté IHM. Ajouté : étoile bleue
  sur la ligne d'un exercice favori (`exercise_row.xml`), item "Favorite"/"Unfavorite"
  dans le menu "⋮" (libellé dynamique selon l'état), tri automatique favoris en tête de
  liste (stable sinon). Côté détails : nouveau réglage "Show Details" dans le menu de
  la barre d'outils (persisté, masqué par défaut) qui affiche sous chaque exercice
  "X séances - il y a Y jours" (nouvelles méthodes `DataStorage.getExerciseWorkoutCount()`/
  `getExerciseLastUsedDate()`). Adapté à l'IHM Vérifit existante (liste plate avec
  recherche) plutôt qu'à la navigation par catégorie de FitNotes — pas de "catégorie
  Favorites" séparée puisque Vérifit n'a pas cette étape de navigation. Fichiers
  touchés : `Exercise.java` (inchangé), `DataStorage.java`, `ExerciseAdapter.java`,
  `ExercisesActivity.java`, `exercise_row.xml`, `exercises_activity_menu.xml`,
  `exercises_activity_floating_context_menu.xml`.

## 5. Suivi

`fitnotes-fork-todo.md` reste le fil d'actualité (bugs, retours de test, demandes
ponctuelles) — il est déjà volumineux (72 Ko), donc ce plan de migration vit dans son
propre fichier plutôt que d'y être fusionné. Une ligne de pointeur vers ce document a
été ajoutée dans `fitnotes-fork-todo.md` (section "Prochain chantier majeur"). À chaque
vague validée sur le clone de migration, on coche l'item ici ET on ajoute l'entrée
détaillée habituelle dans `fitnotes-fork-todo.md`/`fitnotes-fork-plan.md` une fois
mergée dans ton dossier de travail principal.

*Ce fichier est aussi tenu à jour dans le projet Claude "FitNotes_Fork".*
