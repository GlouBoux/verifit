# TODO — FitNotes\_Fork

## Codé, en attente de test réel (06/09/2026)

- [x] **Bug : crash en supprimant la dernière série d'un exercice/jour - VALIDÉ,
  COMMITÉ ET POUSSÉ PAR ROMAIN (06/09/2026)** (retour Romain 06/09/2026) : "quand je suis sur un
  workout donné, si je supprime la dernière ligne du dernier exercice ça fait planter
  l'app (pas violemment mais j'ai du relancer car il n'y avait plus rien de visible sur
  l'app)". Repro exacte : ouvrir un exercice d'un jour donné (icône crayon depuis
  `DayActivity`), taper sur sa dernière série restante pour passer en mode édition, puis
  "Delete".
  Cause identifiée par lecture de code (pas de logcat disponible côté Claude pour
  confirmer à 100% - à surveiller si le crash revient malgré ce correctif) :
  `AddExerciseActivity.deleteSetLogic()` (le flux tap-sur-une-série -> mode édition ->
  "Delete") appelait `WorkoutDay.removeSet()`, dont le seul garde-fou contre le cas
  "suppression de l'unique série restante du jour" était un `assert` Java - **jamais
  actif en production sur Android** (les asserts sont désactivés par défaut, y compris
  en debug). Supprimer la dernière série vidait donc `Sets` sans aucune protection
  réelle. Fait notable : toutes les AUTRES suppressions de l'app (sélection multiple
  dans `AddExerciseActivity`, suppression d'exercices dans `DayActivity`) avaient déjà
  été écrites avec `WorkoutDay.removeSets()` (sans assert, avec vérification explicite
  du jour vidé) précisément pour éviter ce cas - seule cette suppression série-par-série
  ne l'avait pas été.
  Correctif : `deleteSetLogic()` utilise maintenant `removeSets()` (comme toutes les
  autres suppressions) et vérifie explicitement si le jour est vidé pour le retirer de
  `DataStorage`. `WorkoutDay.removeSet()` elle-même a été mise à jour pour déléguer à
  `removeSets()` plutôt que de garder son assert dangereux - elle n'a d'ailleurs plus
  aucun appelant dans le code après ce correctif. Vérifié côté Claude (équilibre
  accolades/parenthèses). **Confirmé par Romain : "Ok ça fonctionne. validé, comité,
  pushé."**

- [x] **Timer de repos : ne sonne jamais, et Reset ne fonctionne pas toujours -
  VALIDÉ, COMMITÉ ET POUSSÉ PAR ROMAIN (06/09/2026)** (retour Romain 06/09/2026) : "d'une façon
  générale quand je set un timer, je veux que même téléphone verrouillé, il sonne pour
  me dire que je peux reprendre ma série. Et je dois pouvoir lui faire confiance sur le
  fait de sonner." **Confirmé par Romain après correctif du crash** : "Ok c'est très
  bien ça fonctionne."
  **Root-cause identifiée en lisant `AddExerciseActivity.java`** :
  1. `startTimer()` utilisait un `CountDownTimer` basique, attaché au cycle de vie de
     l'Activity/de l'app. Son `onFinish()` se contentait de remettre
     `TimerRunning = false` et le texte du bouton à "Start" - aucun son, vibration ou
     notification n'était déclenché nulle part, et un `CountDownTimer` classique ne
     tourne de toute façon de façon fiable que tant que l'Activity est au premier plan.
  2. `resetTimer()` était gardé par `if(TimerRunning)` - le reset ne faisait donc RIEN
     si le timer était en pause ou pas encore démarré (seulement s'il tournait
     activement au moment du clic).
  **Codé, livré sur l'appareil, pas encore buildé/testé** :
  - Nouveau `RestTimerReceiver` (`BroadcastReceiver`) : joue un son + vibration +
    affiche une notification à la fin du repos, indépendamment du cycle de vie de
    l'Activity - fonctionne app ouverte, en arrière-plan, ou écran verrouillé. Canal de
    notification dédié (`rest_timer_channel`, importance haute, son de type alarme).
  - `AddExerciseActivity.scheduleTimerAlarm()` programme une alarme système au démarrage
    du timer via `AlarmManager.setAlarmClock()` (plutôt que `setExactAndAllowWhileIdle`) :
    exempte des restrictions Doze/App Standby, traitée par le système comme une vraie
    alarme (petite icône de réveil dans la barre de statut tant qu'elle est programmée).
    `cancelTimerAlarm()` l'annule à la pause/au reset. Le `CountDownTimer` existant ne
    pilote plus que l'affichage du décompte dans l'Activity.
  - `resetTimer()` remet maintenant `TimeLeftInMillis`/l'affichage à zéro
    inconditionnellement (plus seulement si le timer tournait activement) - seul l'arrêt
    du `CountDownTimer` (`pauseTimer()`) reste conditionné à `TimerRunning`, pour éviter
    un NPE sur un minuteur jamais créé.
  - Manifest : permission `VIBRATE`, `RestTimerReceiver` enregistré (`exported=false`).
  **Crash signalé par Romain au premier test (correctif 06/09/2026)** : cliquer sur
  "Start" faisait planter toute l'app (écran blanc, retour sur l'écran Workout du jour
  courant). Cause : `setAlarmClock()` **n'est pas** exempté de la permission
  `SCHEDULE_EXACT_ALARM` contrairement à ce qui était supposé au premier jet (vérifié sur
  `developer.android.com`) - non déclarée dans le Manifest, l'appel levait une
  `SecurityException` non rattrapée qui faisait planter tout le process. Corrigé :
  - Manifest : `SCHEDULE_EXACT_ALARM` déclarée (permission spéciale auto-accordée à
    l'installation vu le `targetSdkVersion` 31 de l'app - pas d'écran de permission
    supplémentaire pour Romain).
  - `scheduleTimerAlarm()` vérifie `canScheduleExactAlarms()` (Android 12+) avant
    d'appeler `setAlarmClock()`, et `try/catch(SecurityException)` par sécurité
    supplémentaire (ex. permission révoquée à la main après coup) - au pire le minuteur
    reste fiable seulement premier plan plutôt que de crasher toute l'app.
    `cancelTimerAlarm()` protégée de même par cohérence.
  **Confirmé fonctionnel par Romain** (crash résolu, sonnerie déclenchée) - reste un
  ajustement de confort signalé dans la foulée :
  - **"Je voudrai que la sonnerie ne perturbe pas. Sur fitnotes ça fait un Tuuut et
    c'est tout. Et c'est bien."** Le premier jet utilisait un son/attribut audio de
    type ALARME (`TYPE_ALARM`/`USAGE_ALARM`, pensé pour rester audible en mode
    silencieux) - qui se traduit sur la plupart des téléphones par une sonnerie longue
    et forte plutôt qu'un simple bip. **Corrigé** : bascule sur le son/attribut de
    notification standard (`TYPE_NOTIFICATION`/`USAGE_NOTIFICATION_EVENT`, plus proche
    du "Tuuut" de FitNotes), vibration ramenée à un seul buzz court (200ms), priorité/
    catégorie de la notification adoucies. Nouvel id de canal (`rest_timer_channel_v2`).
    **Confirmé par Romain** : "ça fonctionne et c'est ok. Je l'ai commité."
  **Nouveau retour de Romain dans la foulée** : "je préférerais avoir un son de
  notification différent [...] pendant mon entraînement j'ai besoin de pouvoir
  entendre ce son à quelques mètres de distance avec la musique de ma salle de muscu
  en fond [...] il faut que je puisse discerner le son spécifique me disant que je
  peux reprendre ma série et ajuster le volume suivant le bruit ambiant du jour"
  (comme sur FitNotes, qui expose un réglage de volume mais pas de type de son).
  **Codé, livré sur l'appareil, pas encore rebuildé/retesté** :
  - Nouveau `res/raw/rest_timer_beep.wav` : bip synthétisé dédié (~350ms, 880Hz, fondu
    entrée/sortie) - reconnaissable et distinct du son de notification générique du
    téléphone (qui peut être partagé avec d'autres apps).
  - `RestTimerReceiver.playRestTimerBeep()` joue ce bip via `MediaPlayer` sur le flux
    ALARME (le plus fort du téléphone, audible même en mode silencieux/Ne pas
    déranger - pertinent pour la salle de sport), avec un gain
    (`MediaPlayer.setVolume()`) proportionnel au réglage choisi dans l'app plutôt que
    subi via le volume "notifications" du système. `goAsync()` + filet de sécurité
    pour laisser le temps au son de jouer même si l'app était déjà tuée en
    arrière-plan au moment où l'alarme se déclenche.
  - La notification système reste affichée (visuel + vibration courte) mais devient
    silencieuse (le bip est joué séparément ci-dessus) - encore un nouvel id de canal
    (`rest_timer_channel_v3`, un canal étant immuable une fois créé) pour appliquer ce
    changement même sur le téléphone de Romain.
  - Nouveau réglage **Volume** (curseur 0-100%) ajouté dans la boîte de dialogue du
    minuteur (`timer_dialog.xml`), sous le réglage de durée. Persisté par
    `AddExerciseActivity.loadVolume()`/`saveVolume()` dans les mêmes
    `SharedPreferences` que la durée du minuteur, lu par `RestTimerReceiver` au moment
    de sonner. Défaut 100% (le besoin exprimé est d'entendre le bip par-dessus la
    musique de la salle de sport - plutôt réduire le volume les jours calmes que
    l'inverse).
  Mise en page de la boîte de dialogue **non vérifiée visuellement** (pas d'environnement
  de build/émulateur côté Claude) - à valider par Romain à l'usage, en plus du son/volume
  eux-mêmes.
  **Erreur de compilation signalée par Romain (corrigée 06/09/2026)** :
  `compileDebugJavaWithJavac` échouait - `RestTimerReceiver.VOLUME_PREF_KEY` et
  `DEFAULT_VOLUME_PERCENT` n'étaient pas `public`, donc inaccessibles depuis
  `AddExerciseActivity` (package `com.example.verifit.ui`, différent du package de
  `RestTimerReceiver`). Corrigé en ajoutant `public` aux deux constantes. Pas testé sur
  l'appareil suite à ce correctif.
  **Nouveau retour de Romain** : "Le bruit, le bip est ok mais un peu court. Possible
  que tu le rendes 2 fois plus long ? ou m'offrir la possibilité de l'éditer dans
  l'app ? (si pas trop violent comme feature)".
  **Codé, livré sur l'appareil, pas encore rebuildé/retesté** :
  - Le bip n'est plus un fichier `.wav` fixe : `RestTimerReceiver` le synthétise
    maintenant à la volée (`generateBeepSamples()`, même sinusoïde 880Hz + fondu
    entrée/sortie qu'avant, mais durée paramétrable) et le joue via `AudioTrack` en
    mode `MODE_STATIC` (au lieu de `MediaPlayer` sur un asset `res/raw`). Le fichier
    `res/raw/rest_timer_beep.wav`, devenu inutile, a été supprimé.
  - Nouveau réglage **Durée du bip** (curseur 150ms-2000ms, défaut 700ms = 2x l'ancienne
    durée de 350ms) ajouté dans `timer_dialog.xml`, sous le réglage de Volume. Persisté
    par `AddExerciseActivity.loadDuration()`/`saveDuration()` dans les mêmes
    `SharedPreferences` que le volume, lu par `RestTimerReceiver` au moment de sonner.
  Mise en page **non vérifiée visuellement** (toujours pas d'environnement de build/
  émulateur côté Claude) - à valider par Romain à l'usage, en plus du volume/de la
  durée eux-mêmes. À tester en particulier : un réglage très court et très long (2000ms)
  pour vérifier l'absence de "clic"/distorsion en fin de son, et reconfirmer que le
  crash et le réglage de volume fonctionnent toujours.
  **Bug d'alignement signalé par Romain (corrigé 06/09/2026)** : le bouton Reset
  apparaissait plus haut que le bouton Start dans le dialogue du minuteur - le curseur
  Durée du bip venait d'être inséré entre le curseur Volume et les boutons, mais seul le
  bouton Start avait été réancré dessous ; Reset était resté ancré sous l'ancien
  curseur Volume. Corrigé en réancrant `bt_close` (Reset) sous `sb_beep_duration`
  comme `bt_start`.
  **VALIDÉ, COMMITÉ ET POUSSÉ PAR ROMAIN** : "ok. validé, comité, pushé." Fonctionnalité
  10 (timer de repos) entièrement close.

- [x] **Barre de minuteur de REPOS persistante sur l'écran de saisie - CONSTRUITE PUIS
  RETIRÉE, MALENTENDU (06/09/2026)** : suite au retour "il faudrait que le timer de
  workout soit visible [...] ça m'a traumatisé sur fitnotes", une barre persistante
  (icône alarme, `MM:SS`, Start/Pause, Reset) avait été construite pour le minuteur de
  REPOS entre les séries (Fonctionnalité 10). **Romain a clarifié qu'il parlait en
  réalité du chrono de la SÉANCE ENTIÈRE** (voir item "Chrono de la séance entière"
  plus haut) : "on ne s'est pas compris [...] je voulais parler du timer interne de
  toute la séance [...] celle qui sera envoyé dans le rapport de séance". Cette barre
  de repos étant donc un pur artefact du malentendu, Romain a demandé son retrait :
  "enlève l'écran avec le start refresh, c'est l'erreur d'incompréhension sur le timer
  qui est resté. Je ne veux voir que le timer icône course à pied." **Retirée** :
  `activity_add_exercise.xml` ne contient plus que la barre du chrono de session
  (icône course à pied) ; `AddExerciseActivity` a perdu les champs/listeners/méthodes
  spécifiques à cette barre de repos (`tv_inline_timer`, `bt_inline_timer_toggle`,
  `bt_inline_timer_reset`). Le minuteur de repos lui-même (Fonctionnalité 10) est
  intact et reste utilisable comme avant via la boîte de dialogue "Timer" du menu -
  seule la barre persistante ajoutée par erreur a disparu.

- [ ] **Chrono de la séance entière, contrôlable (Start auto / Stop-Resume manuel) -
  CODÉ, PAS ENCORE TESTÉ (06/09/2026)** (retour Romain 06/09/2026, après un
  malentendu sur la barre de minuteur de repos ci-dessus) : "on ne s'est pas compris.
  Je voulais parler du timer interne de toute la séance. Depuis combien de temps je
  fais ma séance. Celle qui sera envoyé dans le rapport de séance (le share). Comme je
  ne sais pas ni ne peut controler ce timer il faut que je puisse y accéder (dans
  fitnotes il y avait toujoours une checkbox à coté d'une série [...] lorsqu'on coche
  la première série ça lance le timer de la séance et quand toutes les séries sont
  cochés alors le timer s'arrête). Pas obligé de reproduire cet UX mais le timer lui je
  dois pouvoir le controler."
  Jusqu'ici, la ligne "Time" de l'export de séance (voir item "Partager une séance"
  plus bas) était calculée à partir du plus petit/plus grand horodatage de série
  (`WorkoutSet.timestamp`) - un calcul entièrement implicite, sans aucun affichage ni
  contrôle possible pendant la séance. Trois décisions prises avec Romain (questions
  posées explicitement, chacune avec une option recommandée) :
  1. **Emplacement** : chrono visible et contrôlable à la fois sur l'écran de saisie
     (`AddExerciseActivity`, à côté du minuteur de repos) et sur la vue d'ensemble du
     jour (`DayActivity`) - même chrono partagé (au niveau du `WorkoutDay`), affiché
     aux deux endroits.
  2. **Source du rapport** : ce chrono manuel devient la **seule** source de la ligne
     "Time" exportée - remplace entièrement l'ancien calcul par horodatages de séries
     (pas de repli automatique dessus).
  3. **Démarrage** : automatique à la première série loggée de la journée (pas
     d'action manuelle requise pour démarrer), arrêt uniquement manuel (bouton Stop) -
     pas de reproduction du mécanisme de cases à cocher de FitNotes (non demandé).
  **Codé, livré sur l'appareil, pas encore rebuildé/testé** :
  - `WorkoutDay` : nouveaux champs `SessionStartTimestamp`/`SessionEndTimestamp`
    (epoch millis, `null` par défaut pour les jours déjà sauvegardés avant cet ajout -
    la ligne "Time" est alors omise à l'export, comme avant que
    `WorkoutSet.timestamp` n'existe) + `isSessionTimerRunning()`.
  - Nouvelle barre affichée en haut des deux écrans (icône course à pied, décompte
    `HH:mm:ss`, bouton Stop/Resume) - fond légèrement plus foncé que la barre du
    minuteur de repos pour bien les distinguer visuellement. Bouton désactivé tant
    qu'aucune série n'a encore été loggée ce jour-là (rien à démarrer/arrêter
    manuellement).
  - `AddExerciseActivity.addSetExistingWorkoutDay()`/`addSetNewWorkoutDay()` démarrent
    (ou reprennent, si le chrono avait été arrêté manuellement) le chrono à chaque
    nouvelle série loggée - reprise automatique et silencieuse plutôt que de laisser un
    chrono "arrêté" pendant qu'un entraînement continue visiblement.
  - Nouvelle classe partagée `SessionTimerTicker` (package `com.example.verifit`) :
    fait défiler l'affichage `HH:mm:ss` chaque seconde sur les deux écrans - lecture
    seule, ne modifie jamais le `WorkoutDay` (le démarrage/l'arrêt reste géré par
    chaque écran, car impliquant une sauvegarde immédiate des données).
  - Sauvegarde immédiate (`DataStorage.saveWorkoutData()`) à chaque changement d'état
    du chrono (démarrage, reprise, Stop manuel) plutôt que de compter sur le flag
    `autoBackupRequired` différé utilisé pour l'ajout de séries - pour ne jamais perdre
    ce chrono si l'app est tuée juste après.
  - `WorkoutReportGenerator.buildTimeLine()` réécrite pour lire exclusivement
    `WorkoutDay.SessionStartTimestamp`/`SessionEndTimestamp` (plus aucune lecture des
    horodatages de série). Si le chrono n'a pas encore été arrêté au moment du
    partage, l'heure de fin prise est "maintenant" (instantané de la séance en cours).
  - `WorkoutSet.timestamp` (champ par série) reste renseigné mais n'est plus utilisé
    par l'export - conservé pour un usage futur éventuel (ex. mesurer le repos
    réellement pris entre deux séries, idée déjà notée plus bas dans ce document).
  Vérifié côté Claude (équilibre accolades/parenthèses de tous les fichiers Java
  touchés, XML bien formé des deux layouts). **Pas encore testé/rebuild par Romain** -
  en particulier : l'affichage réel des deux barres à l'écran (pas d'environnement de
  build/émulateur côté Claude), la reprise automatique après un Stop manuel suivi
  d'une nouvelle série, et le format de la ligne "Time" du rapport avec cette nouvelle
  source (devrait être identique en apparence, seul le calcul sous-jacent a changé).

- [x] **Historique des PR par nombre de reps - v2 VALIDÉE, COMMITÉE ET POUSSÉE PAR ROMAIN (06/09/2026)**
  (retour Romain, à propos de la définition exacte d'un PR pour l'export de séance) :
  "Un PR c'est un record (Personal Record) pour ce rep range (reps) pour ce poids
  (kgs). Ici mon record pour 43 reps = 47.5 (avant c'était moins du coup). Fitnotes
  garde un historique de PR pour chaque exercice (oups ça veut dire que Verifit ne l'a
  probablement pas et c'est important)". Confirmé en lisant le code : Verifit ne
  trackait que des records **globaux** par exercice (poids max et reps max tous
  nombres de reps confondus, volume, 1RM) - aucune table "pour N reps précis, quel est
  le poids max jamais soulevé", qui est la vraie définition d'un PR selon Romain (et le
  point bloquant pour tagger correctement `[PR]` dans l'export de séance). Manque
  confirmé comme important par Romain, qui a choisi de construire un vrai écran plutôt
  qu'un simple calcul interne à l'export.
  Une v1 (simple : record = meilleur poids pour EXACTEMENT N reps, liste plate) a été
  codée puis remplacée avant même que Romain ne la teste, suite à deux screenshots du
  popup "Personal Record History" de FitNotes et à ce retour : "On va même aller plus
  loin en trackant comme sur ce screenshot : quand on clique sur un RM sur cet écran, ça
  en ouvre un autre avec current record, previous record et la date (l'historique quoi).
  Les PRs sont également déduis (20 kgs pour 8 reps est également un PR pour 7 reps s'il
  n'y a pas de valeur. transitivité). [...] les PR déduits sont grisés/non mis en avant."
  L'algorithme a été reconstitué par rétro-ingénierie à partir des deux screenshots
  fournis (table 1RM à 8RM + détail du "5 RM") et validé point par point (poids/date/
  couleur) sur les 8 lignes visibles avant d'être codé - v2 :
  - **Transitivité** : le record pour N reps = le poids max jamais soulevé sur une série
    d'AU MOINS N reps (pas seulement N reps exactement), puisque réussir R reps prouve
    qu'on pouvait aussi en faire moins. Un évènement est "déduit" quand la série source a
    un nombre de reps différent de la case qu'il occupe (ex : une série de 43 reps établit
    aussi un record déduit pour 42, 41... reps si rien de mieux n'existe) ; "réel" quand
    reps source = reps de la case. Les déduits sont affichés grisés, les réels mis en
    avant - exactement le rendu du screenshot FitNotes.
  - `DataStorage.calculateRepRangeHistory(exerciseName)` : recalculée à la volée (aucun
    nouveau champ persisté sur `WorkoutSet`), retourne `TreeMap<Integer,
    ArrayList<RepRangePREvent>>` - pour chaque nombre de reps, tout l'historique
    chronologique des évènements (pas juste le record actuel), chacun portant son poids,
    sa date, son nombre de reps source réel et s'il est déduit.
  - `DataStorage.getRepRangePRSets(exerciseName)` : aplatit cette table en un
    `HashSet<WorkoutSet>` (comparaison par référence, évènements réels uniquement pour ne
    pas compter deux fois la même série) - source de vérité unique destinée à être
    réutilisée telle quelle pour le tag `[PR]` de l'export de séance (voir item "Partager
    une séance" plus bas), afin de garantir la même règle entre l'écran et l'export.
  - Écran `RepRangeRecordsActivity` (+ `RepRangeHistoryAdapter`, `RepRangeHistoryRow`,
    `RepRangePREvent`) : une ligne par nombre de reps (triées du plus petit au plus
    grand, table type "rep-max"), montrant le record ACTUEL, grisé si déduit. **Cliquer
    une ligne ouvre désormais une popup** "Personal Record History"
    (`rep_range_history_dialog.xml`) avec le record actuel puis les records précédents
    (du plus récent au plus ancien), chaque ligne précédente affichant le nombre de reps
    RÉEL de sa série source (ex "6 RM" pour un évènement déduit apparaissant dans le
    détail du "5 RM") et sa date - comportement calqué sur le screenshot fourni. Le
    bouton "Graph" visible dans le screenshot FitNotes n'a volontairement pas été
    reproduit (non demandé par Romain). Accessible via le même point d'accès qu'avant
    (menu "Historique par nombre de reps" en long-press sur une carte dans Personal
    Records, et icône trophée dans la fiche de l'exercice - voir plus bas).
  Vérification faite côté Claude (pas de build/émulateur disponible) : équilibre des
  accolades/parenthèses de tous les fichiers Java touchés et bonne formation XML des
  layouts - OK. **Pas encore testé/rebuild par Romain.** Fichier
  `rep_range_history_header_row.xml` (créé pour la v1, plus utilisé par la v2) supprimé
  du dépôt - à supprimer manuellement sur ta machine si tu veux nettoyer (`git rm`/`git
  add` s'en chargera au commit, il ne sera pas recréé).
  **Retour de Romain après avoir vu l'écran (v1)** : "j'aime bien. Même si un peu
  difficile d'accès. Ce que j'aimerai c'est pouvoir y accèder depuis la fiche de
  l'éxercice en question. Quand j'ajoute une série sur cet exo, il faut que j'ai une
  icone (par exemple un petit trophée comme sur fitnotes qui m'améne vers mon tableau de
  PR)." - screenshot de l'écran "Records" de FitNotes fourni comme inspiration (onglets
  Records/Stats/Goals, filtres Type/Period/Date, table 1RM/2RM/3RM...). **Point d'accès
  ajouté** : icône trophée (`ic_emoji_events_24px`, déjà présente dans les ressources -
  même icône que le badge PR utilisé ailleurs dans l'app) dans la barre d'outils de
  `AddExerciseActivity` (la fiche de l'exercice), ouvrant `RepRangeRecordsActivity` pour
  l'exercice affiché. Les filtres Type/Period/Date et les onglets Records/Stats/Goals de
  FitNotes n'ont volontairement pas été reproduits pour l'instant (Romain n'a demandé que
  le point d'accès) - à revoir si besoin plus tard.

## Nouvelles demandes (06/09/2026)

- [ ] **Démarrage/arrêt automatique du timer de repos, puis suivi du repos réellement
  pris** (retour Romain 06/09/2026, note "Todo" - pas encore scopé/codé) : "ajouter
  start timer quand on valide la première série. Terminer automatiquement ce timer
  quand on termine la dernière série. Ce que fait fitnotes." Étape suivante envisagée
  par Romain : "on pourra aller plus loin et ajouter des timer entre chaque série
  histoire de tracker le repos que je prends (ou plutôt le temps que je mets à valider
  2 séries consécutives). Ça peut donner une indication si je ne prends pas assez de
  repos par exemple." Deux volets distincts, à clarifier/scoper avant de coder :
  1. **Auto start/stop** : démarrer le timer de repos (Fonctionnalité 10) sans action
     manuelle dès qu'une série est validée, l'arrêter à la fin de l'exercice - à
     préciser avec Romain ce que "dernière série" signifie exactement en pratique
     (l'app ne sait pas à l'avance combien de séries sont prévues pour un exercice) :
     probablement soit "quand on quitte l'écran de l'exercice", soit "le prochain
     `startTimer()` remplace/annule implicitement celui en cours" (comportement FitNotes
     probable - le repos se relance à chaque nouvelle série tant qu'on reste sur
     l'exercice).
  2. **Tracker le repos réel** (piste, pas encore de spec) : mesurer le temps écoulé
     entre deux séries consécutives validées (indépendamment du minuteur lui-même,
     qu'il ait été utilisé ou non) et le stocker/afficher comme indicateur - pourrait
     rejoindre le sujet Écarts Prévu/Réalisé (donnée supplémentaire par série) ou une
     vue dédiée. Utilité évoquée : détecter un repos insuffisant entre les séries.

- [x] **Partager une séance ("Share workout") - VALIDÉ, COMMITÉ ET POUSSÉ PAR ROMAIN (06/09/2026)** :
  fonctionnalité que Romain avait sur FitNotes - génère un rapport texte de la séance
  affichée, partageable vers d'autres apps (Discord principalement dans son usage
  actuel) via le sélecteur de partage standard Android. Romain a fourni un exemple réel
  (sa séance du 04/09/2026), reproduit à l'identique. Format observé :
  ```
  FitNotes Workout - vendredi 4th septembre 2026
  Time: 17:43 – 22:15 (4h 31m)
  ** Nom de l'exercice **
  - 40.0 kgs x 4 reps
  - 50.0 kgs x 2 reps [Échec d'un 7. <commentaire libre de la série>]
  - 41.0 kgs x 14 reps [PR]
  ** Exercice suivant **
  ...
  ```
  Deux points bloquants résolus avec Romain avant de pouvoir générer le rapport :
  1. **Ligne "Time"** : nouveau champ `WorkoutSet.timestamp`, codé et validé par Romain
     ("Ok ça fonctionne. validé, comité, pushé.") - voir plus bas, item clos.
  2. **Tag `[PR]` par série** : `DataStorage.getRepRangePRSets()`, voir l'item
     "Historique des PR par nombre de reps" ci-dessus.
  Nouvelle classe `WorkoutReportGenerator` (package `com.example.verifit`) :
  - En-tête `"FitNotes Workout - <date>"` : date formatée en français propre
    ("vendredi 4 septembre 2026", première lettre en majuscule) plutôt que le format
    bizarre observé côté FitNotes ("vendredi 4th septembre 2026", mélange d'ordinal
    anglais et de mois français, probablement un bug de locale FitNotes) - Romain ne
    s'étant pas prononcé sur ce point, décision prise par Claude comme convenu.
  - Ligne `"Time: HH:mm – HH:mm (XhYm)"` (avec le vrai tiret cadratin – du template) :
    à l'origine calculée à partir du plus petit et du plus grand `WorkoutSet.timestamp`
    du jour, **omise entièrement** si aucune série n'avait d'horodatage connu.
    **Remplacée depuis (voir item "Chrono de la séance entière" plus haut, retour
    Romain 06/09/2026 : "je ne sais ni ne peux controler ce timer [...] il faut que je
    puisse y accéder")** : cette ligne se base désormais exclusivement sur le chrono de
    séance manuel `WorkoutDay.SessionStartTimestamp`/`SessionEndTimestamp`, contrôlable
    par Romain (Start auto à la première série, Stop/Resume manuel) - toujours omise
    si ce chrono n'a jamais été démarré. Minutes de la durée non paddées à 2 chiffres
    (`"4h 5m"`, pas `"4h 05m"` - c'est une durée, pas une heure d'horloge) - à ajuster
    si Romain préfère le padding.
  - Une section `"** Nom **"` par exercice, dans l'ordre d'apparition du jour
    (`WorkoutDay.getExercises()`, qui respecte déjà `ExerciseOrder` - même ordre que
    l'écran `DayActivity`).
  - Une ligne `"- <poids> kgs x <reps> reps"` par série, avec annotation entre crochets
    quand pertinente : `"[PR]"`, `"[<commentaire>]"`, ou `"[PR. <commentaire>]"` quand
    les deux sont présents (PR en premier, comme dans l'exemple de Romain).
  Déclencheur : nouvel item de menu "Share workout" dans `DayActivity`
  (`day_activity_menu.xml`, icône déjà présente `ic_share_24px`) - ouvre le sélecteur de
  partage standard Android (`Intent.ACTION_SEND`, `text/plain`). Le sélecteur système
  (Android 10+) propose aussi une action "Copier" intégrée, ce qui couvre le besoin
  "copiable en texte brut" sans bouton dédié supplémentaire côté app - à ajouter
  explicitement si Romain le souhaite quand même après avoir testé.
  Vérifié côté Claude (équilibre accolades/parenthèses, XML bien formé).
  **Confirmé par Romain avec deux exemples réels** ("Ok c'est bien voici l'output (j'en
  ai mis 2. un historique, un nouvellement créé)") : une séance historique/importée sans
  aucune série horodatée (ligne "Time" correctement absente), une séance avec une série
  nouvellement créée (ligne "Time: 22:31 – 22:31 (0h 0m)" correctement calculée, tag
  `[PR]` correct) - aucun bug relevé sur les deux exemples.

- [ ] **Générer un programme ("Routine")** (retour Romain 06/09/2026) : équivalent de la
  fonctionnalité "Routines" de FitNotes - sélectionner un ensemble d'exercices (dans le
  cas de Romain : organisés en cycles et en jours volume/force), cliquer "Generate
  workout" et ça pré-remplit le jour avec la séance vierge (structure d'exercices, pas
  encore de séries loggées). Dans notre cas, Romain imagine que ça déclencherait
  directement son script `workout_engine.py` pour proposer une séance. Idée
  d'amélioration évoquée (pas tranchée, "à voir comment faire") : rendre le script plus
  souple en allant chercher automatiquement dans son tableau de PR les exercices
  sélectionnés. **Pas prioritaire** ("même si génial") car son script fait déjà ça
  aujourd'hui "sans intelligence additionnelle" via le flux manuel Import Session
  existant - ceci ne serait qu'une couche d'automatisation/UX par-dessus un processus qui
  fonctionne déjà. À rapprocher du sujet Écarts Prévu/Réalisé et de l'intégration
  `workout_engine.py` déjà livrée (voir `docs/fitnotes-fork-plan.md`).

## Fait / validé

- [x] **Onglet Sessions : Undo réel sur "Set Deleted" + réordonnement des séries par
  appui long — VALIDÉ ET POUSSÉ (06/09/2026)** (retour Romain 06/09/2026, en testant le
  point tap = édition ci-dessous) : deux demandes suite à ce test.
  1. Le message "Set Deleted" proposait un bouton "Dismiss" qui ne faisait que fermer le
     message, sans annuler la suppression - Romain voulait un vrai revert.
  2. En recréant une série supprimée (test), elle atterrissait en fin de liste et Romain
     ne pouvait plus la remettre à sa place - aucun réordonnement des séries n'existait
     sur cet écran. Proposé et retenu : l'appui long lance ce réordonnement (drag & drop),
     et la boîte de dialogue Éditer/Supprimer qu'il ouvrait jusqu'ici n'a plus lieu d'être
     (Éditer = le tap simple, Supprimer = le bouton "Delete" du mode édition).
  - `SnackBarWithMessage` gagne `showSnackbarWithUndo()` : le bouton devient "Undo" et
    exécute une action au clic (restaurer la série, même objet donc mêmes
    id/commentaire/valeurs prévues) au lieu de simplement fermer le message. Recrée le
    jour si c'était sa dernière série. `showSnackbar()` existant inchangé pour tous les
    autres messages (Set Updated, Set Added, Comment saved...).
  - Réordonnement par glisser-déposer démarré par appui long sur la ligne (pas de
    poignée dédiée ici, contrairement aux exercices sur `DayActivity`/l'onglet Workout -
    le tap étant déjà pris par l'édition). `WorkoutDay.reorderSetsForExercise()` déplace
    les séries de cet exercice entre elles dans la liste `Sets` du jour, sans perturber
    l'entrelacement avec les séries des autres exercices du même jour.
  - Boîte de dialogue Éditer/Supprimer (`showSetPopupMenu`) supprimée.
  - **Ajustement (retour Romain 06/09/2026, en testant ce point)** : l'Undo remettait la
    série en dernière position au lieu de sa place d'origine. `WorkoutDay.insertSetAt()`
    insère à un index précis au lieu de toujours ajouter en fin de liste comme
    `addSet()` ; `deleteSetLogic()` capture l'index de la série dans `Sets` juste avant
    sa suppression et le transmet à `undoDeleteSet()` pour réinsertion au même endroit.
  Reste en local uniquement (pas de resynchronisation vers l'API `verifit_rs` en mode
  compte en ligne pour l'Undo, comme pour les autres mutations ajoutées depuis).

- [x] **Onglet Sessions : tap sur une série = édition, bouton "Delete" fonctionnel —
  VALIDÉ ET POUSSÉ (06/09/2026)** (retour Romain 06/09/2026) : "Je valide. Commité,
  pushé." Sur l'écran d'édition d'un exercice
  (`AddExerciseActivity`/`AddExerciseWorkoutSetAdapter`, atteint depuis l'onglet
  Sessions), un tap sur une série ne faisait rien d'utile - il fallait rester appuyé
  longtemps pour obtenir un menu Éditer/Supprimer. Comme sur FitNotes : un tap
  sélectionne directement la série, la précharge dans les champs du haut, et fait
  passer le bouton "Save" en "Update".
  - Le tap simple (hors mode sélection multiple) appelle
    `AddExerciseActivity.editSet()` - déjà utilisé par le menu Éditer du long-press,
    donc comportement identique, juste accessible en un tap au lieu de deux étapes.
    L'ancien `updateView()` préremplissait bien les champs mais sans jamais activer le
    mode édition (`isEditMode`) : cliquer "Save" ensuite créait une série en double au
    lieu de mettre à jour celle affichée.
  - **Effet de bord corrigé au passage** : en mode édition, le bouton du bas affiche
    "Delete" mais ne faisait en réalité QUE vider les champs (jamais de vraie
    suppression) - rendu fonctionnel (option choisie par Romain).
  En testant ce correctif, Romain a signalé deux points supplémentaires - voir l'entrée
  Undo + réordonnement ci-dessus.

- [x] **Écarts Prévu/Réalisé — VALIDÉ ET POUSSÉ (06/09/2026)** (sujet confirmé par Romain
  le 05/09/2026, décisions d'affichage tranchées le 06/09/2026 via questions posées à
  Romain) : "Ok ça fonctionne bien. J'ai comité et poussé. Je valide."
  Décisions retenues (les 3 points posés le 05/09 + le point 3 tranché le 06/09) :
  1. **Prévu** = la valeur écrite par `build_session_import_json` au moment de l'import,
     figée ensuite.
  2. **Réalisé** = la valeur que Romain modifie ensuite dans l'app - les deux coexistent
     désormais pour une série importée au lieu que la modif écrase le prévu.
  3. **Affichage** : "Badge discret + détail au tap" pendant la séance + **écran dédié
     "Écarts"** pour l'historique (les deux demandés par Romain).
  Implémentation :
  - `WorkoutSet` (`model/WorkoutSet.java`) gagne deux champs nullables
    `plannedReps`/`plannedWeight`, distincts de `reps`/`weight` (le "réalisé", toujours
    modifiable normalement). Renseignés UNIQUEMENT dans
    `DataStorage.mergeImportedSession()` au moment de la construction du `WorkoutSet` -
    restent `null` pour une saisie manuelle, un import CSV d'historique, ou une série
    déjà sauvegardée avant ce changement.
  - `WorkoutSet.hasDiscrepancy()` : vrai si la série a un prévu ET que le réalisé actuel
    en diffère.
  - Badge discret (icône rouge, `ic_error_outline_24px`) sur `workout_set_row.xml`,
    dans `WorkoutSetAdapter` (onglet Workout + `DayActivity`) ET
    `AddExerciseWorkoutSetAdapter` (onglet Sessions). Un tap dessus ouvre un dialogue
    "Prévu / Réalisé" (lecture seule, `set_discrepancy_dialog.xml`).
  - Nouvel écran dédié `DiscrepancyHistoryActivity`, accessible depuis l'onglet Charts →
    menu (⋮) → "Ecarts Prevu/Realise" : liste chaque série en écart, toutes séances
    confondues, la plus récente en premier.
  Piste évoquée par Romain pour plus tard (pas demandée formellement) : exploiter cet
  historique depuis `workout_engine.py`.

- [x] **Suppression de plusieurs séries en une fois** (retour Romain 05/09/2026) : dans
  `AddExerciseActivity` (l'écran de log d'un exercice), on ne pouvait supprimer qu'une
  série à la fois (long-press → menu popup → Supprimer), fastidieux pour nettoyer
  plusieurs séries d'un coup (ex : import de test à corriger). Ajout d'un mode sélection
  multiple : nouvelle icône "Select" dans la barre d'outils démarre une ActionMode
  (barre contextuelle standard Android) ; taper sur une série la coche/décoche ; l'icône
  Supprimer de la barre contextuelle supprime toute la sélection en une seule
  confirmation. Le long-press existant (Éditer/Supprimer une seule série) n'a pas été
  touché - c'est un ajout, pas un remplacement. **Testé sur l'app par Romain, ça
  fonctionne bien.**

- [x] **Réorganiser/supprimer plusieurs exercices depuis l'écran du jour** (retour Romain
  05/09/2026, sur l'écran `DayActivity`, atteint via l'icône calendrier) : même mécanique
  de sélection multiple + suppression que pour les séries, plus une poignée de
  réorganisation par glisser-déposer ("comme FitNotes") pour changer l'ordre des
  exercices. Deux mécanismes indépendants (poignée toujours dispo hors sélection
  multiple, sélection multiple = suppression uniquement) - confirmé correspondre à ce que
  voulait Romain.
  Changement plus profond que les précédents : l'ordre d'affichage des exercices d'un
  jour était jusqu'ici toujours alphabétique et jamais mémorisé nulle part (recalculé à
  chaque fois via un tri alphabétique). Il est maintenant mémorisé par jour
  (`WorkoutDay.ExerciseOrder`, nouveau champ) et réordonnable à la main. Vérifié
  compatible avec les anciennes sauvegardes (un ancien fichier sans ce champ retombe sur
  une liste vide, pas sur un crash).
  **Bug trouvé et corrigé (05/09/2026)** : la poignée de glisser-déposer était bien câblée
  mais quasi invisible (teintée `core_grey_02`, un gris presque blanc, sur fond de carte
  clair). Recolorée en `core_grey_55` (même gris que les autres icônes discrètes de
  l'app). **Réorganisation confirmée fonctionnelle par Romain (05/09/2026)** après ce
  correctif.

- [x] **Même sélection multiple + réorganisation sur l'onglet Workout (accueil)** (retour
  Romain 05/09/2026) : Romain a d'abord essayé de réordonner depuis l'onglet
  accueil - anciennement titré "Verifit", renommé **"Workout"** - avant de découvrir que
  cette fonctionnalité n'existait que sur `DayActivity`. L'app a en fait 3 écrans séparés
  qui affichent chacun "la liste des exercices d'un jour", codés indépendamment (aucun
  n'hérite des autres) : l'onglet Workout (`ViewPagerExerciseAdapter`, dans le carrousel
  swipable de `MainActivity`), l'écran Day/calendrier (`DayExerciseAdapter`, fait
  ci-dessus) et l'historique Sessions (`DiaryExerciseAdapter`, pas encore traité - priorité
  confirmée par Romain : Workout d'abord, Sessions pas demandé pour l'instant). Même
  mécanique reprise à l'identique (checkbox + ActionMode + poignée de glisser-déposer),
  mais plus délicat techniquement car la liste d'exercices est imbriquée dans un
  `ViewPager2` (carrousel horizontal entre les jours) dont les pages (et donc les
  adapters d'exercices) sont recyclées au fil du swipe - contrairement à `DayActivity` où
  un seul adapter vit pour toute la durée de l'écran. L'`ItemTouchHelper` est donc créé
  une seule fois par page recyclée (dans le ViewHolder du carrousel), pas à chaque bind,
  pour éviter d'empiler plusieurs `OnItemTouchListener` sur la même RecyclerView au fil
  des swipes. Le bouton "Select exercises" de la barre d'outils retrouve la page
  actuellement affichée dans le carrousel pour savoir sur quel jour agir. **Confirmé
  fonctionnel par Romain (05/09/2026)** après build.
  **Amélioration ajoutée (05/09/2026, retour Romain)** : en mode sélection, les séries de
  chaque exercice se replient automatiquement (comme FitNotes) - plus facile de
  sélectionner/glisser plusieurs exercices sans avoir à faire défiler le détail de
  chacun. La poignée de glisser-déposer reste aussi disponible PENDANT la sélection
  multiple (avant, les deux modes étaient mutuellement exclusifs). Ce changement a
  nécessité de suivre la sélection par NOM d'exercice plutôt que par position (sinon une
  sélection pointait sur le mauvais exercice après un glisser-déposer pendant qu'une
  sélection était en cours) - fait sur `DayExerciseAdapter` et `ViewPagerExerciseAdapter`.
  **Testé par Romain** : le repli fonctionne bien en passant par le bouton "Select".
  **Bug \#1 trouvé et corrigé (05/09/2026)** : en revanche, si on drague directement un
  exercice via la poignée SANS passer par le bouton "Select" (drag "brut", hors sélection
  multiple), les séries ne se repliaient pas - le repli ne dépendait que de
  `selectionMode`, or la poignée reste utilisable même hors sélection multiple.
  **Bug \#2 trouvé et corrigé (05/09/2026, retour Romain après test)** : le premier
  correctif du bug \#1 (un simple booléen `dragging`, remis à `false` à la fin du geste
  dans `clearView()`) faisait bien réapparaître le repli au début du drag, mais Romain a
  vu tout se ré-déplier tout seul dès qu'il relâchait - "collapse pendant une seconde
  avant de s'expand à nouveau". Ce qu'il voulait : que ça reste replié après le drag,
  jusqu'à un tap manuel pour rouvrir. Remplacé par un suivi persistant par NOM
  (`collapsedExerciseNames`, même mécanique que la sélection) : `setDragging(true)`
  replie et mémorise toutes les séries au début du geste, `setDragging(false)` (fin du
  geste) ne les rouvre plus - elles ne se rouvrent qu'en tapant dessus (comportement
  identique au repli manuel classique). Sur l'onglet Workout, comme le tap sur une série
  naviguait jusqu'ici toujours directement vers l'écran de log (pas de repli manuel
  préexistant sur cet écran contrairement à `DayActivity`), le tap sur une série repliée
  la déplie d'abord au lieu de naviguer - il faut retaper pour naviguer une fois dépliée.
  **Confirmé fonctionnel par Romain (05/09/2026)** après build : "c'est validé".

- [x] **Icône de commentaire cliquable, puis inversion tap court/appui long** (retour
  Romain 05/09/2026) : sur l'onglet Workout, l'icône de commentaire sur une série
  (ajoutée au patch \#6) était visible mais pas cliquable - il fallait deviner qu'un
  long-press sur toute la carte l'ouvrait.
  **Manque plus large trouvé en même temps** : sur l'onglet Sessions, ouvrir un exercice
  d'une séance passée mène à `AddExerciseActivity` (l'onglet porte le nom de l'exercice),
  dont la liste de séries (`AddExerciseWorkoutSetAdapter`) n'avait ICI aucune icône de
  commentaire du tout, ni aucun moyen de voir/éditer un commentaire par série - alors que
  Romain en a besoin sur cet écran-là aussi (comme sur FitNotes). Ajouté : même icône,
  même dialogue voir/éditer/effacer, réutilisant `workout_set_row.xml` (déjà le bon
  layout, l'id `set_comment_indicator` existait déjà dedans) et le même mécanisme que
  `WorkoutSetAdapter`.
  **Amélioration UX ajoutée dans la foulée (retour Romain après test)** : le tap court
  ouvrait les stats (reps/charge/volume/1RM) et le long-press le commentaire - dans
  l'ordre inverse de ce que Romain utilise réellement (c'est le commentaire qui
  l'intéresse au quotidien, pas les stats). Inversé dans `WorkoutSetAdapter` (partagé par
  l'onglet Workout et l'écran `DayActivity`, même composant) : tap court → voir/éditer le
  commentaire, long-press → stats de la série. L'icône de commentaire n'a plus son propre
  `OnClickListener` séparé (redondant maintenant que le tap sur toute la carte fait la
  même chose) - elle reste un simple indicateur visuel (visible seulement si la série a
  un commentaire). `AddExerciseWorkoutSetAdapter` (écran de log actif, atteint aussi
  depuis Sessions) n'est PAS concerné par cette inversion - son tap sert à sélectionner
  la série à éditer et son long-press ouvre déjà un menu Éditer/Supprimer, un usage
  différent.
  **Confirmé fonctionnel par Romain (05/09/2026)** : "c'est validé et comité".

- [x] **Bug critique de perte de données à l'Import Session — RÉSOLU ET CONFIRMÉ
  (05/09/2026)** : après avoir recompilé/réinstallé l'app puis importé le JSON de la
  séance de vendredi, Romain ne voyait plus QUE cette séance dans "Sessions" (ex-Diary) -
  tout l'historique précédent avait disparu de l'app.
  **Cause** : `DataStorage.mergeImportedSession()` (le code qui traite un Import
  Session) appelait `setsToEverything()`, qui VIDE `workoutDays` et le RECONSTRUIT
  ENTIÈREMENT à partir de la liste interne `sets` - or `sets` n'est peuplée que par un
  import CSV complet et n'est JAMAIS resynchronisée avec `workoutDays` après un simple
  démarrage de l'app. Un redémarrage à froid laisse donc `sets` vide ; importer une
  séance juste après reconstruisait `workoutDays` à partir de ce `sets` presque vide →
  tout l'historique disparaissait, et ce state tronqué était aussitôt sauvegardé. Bug
  préexistant à cette session (pas introduit par les nouvelles features).
  **Correctif** : `mergeImportedSession()` ajoute maintenant directement les séries
  importées au bon `WorkoutDay` (existant ou nouveau) sans jamais reconstruire
  `workoutDays` depuis `sets`.
  **Confirmé par Romain** : "ça fonctionne et j'ai récupéré mes anciens exo depuis mon
  import, y compris la séance de vendredi." Historique intact, plus rien à
  investiguer côté récupération de données.
- [x] Renommage des onglets (retour Romain 05/09/2026) : titre de l'onglet accueil
  "Verifit" → **"Workout"** (`MainActivity`, l'app garde son nom "Verifit" par ailleurs) ;
  libellé de l'onglet "Diary" → **"Sessions"** (`AndroidManifest.xml`, label de
  `DiaryActivity`).
- [x] Choix du dépôt de base : `MakisChristou/verifit`. **Décidé : on reste sur le fork
  perso (`GlouBoux/verifit`), pas de PR vers l'amont `MakisChristou/verifit`.**
- [x] Feature "Dupliquer un exercice" — codée, testée sur l'app, fonctionne.
- [x] Feature "Import Session" (JSON) — codée, testée sur l'app, fonctionne.
- [x] App qui build et se lance (émulateur et téléphone).
- [x] Bug poids/répétitions inversés après import CSV réel — corrigé, confirmé par
  Romain.
- [x] Crash "Import failed: IllegalStateException" sur Import Session — corrigé (patch
  \#4).
- [x] Bug "l'app revient toujours à aujourd'hui" — corrigé (patch \#5), **validé par
  Romain**.
- [x] Icône calendrier de la barre d'outils branchée sur un vrai sélecteur de date
  (patch \#5) — **validé par Romain**.
- [x] Rattrapage de l'historique git côté Romain, gitignore de `build_log.txt`, script
  `scripts/build_and_log.bat` pour partager un log de build facilement.
- [x] Commentaire par série (patch \#6) — **validé par Romain**.
- [x] Calendrier de navigation avec indicateur de jours avec séance (patch \#7) : l'icône
  calendrier de la barre d'outils ouvre un calendrier mensuel fait maison (mois
  précédent/suivant, jour actuellement affiché en surbrillance, point sous chaque jour
  qui a déjà une séance enregistrée, comme sur FitNotes) — **validé par Romain**.
- [x] Commit des changements Android en cours (sélection multiple des séries,
  réorganisation/suppression des exercices sur `DayActivity` et l'onglet Workout,
  correctif du bug de perte de données, correctif de la poignée invisible) — **confirmé
  fait par Romain**.
- [x] **Mixup de message de commit sur le dépôt Coaching (05/09/2026)** : Romain a commité
  du vrai travail (intégration `workout_engine.py`/export JSON, \+553/-50 lignes sur 6
  fichiers) avec le message destiné à un commit Android sans rapport. Pas encore pushé
  au moment du signalement → corrigé par `git commit --amend` avec le bon message.
  **Confirmé corrigé par Romain.**
- [x] **Synchronisation FitNotes → Vérifit avant transition définitive (06-07/09/2026)** :
  import d'un backup FitNotes réel (SQLite) en CSV compatible Vérifit - 3784 séries de
  l'historique complet, puis un CSV combiné (\+43 séries pour rattraper la séance du
  vendredi 04/09) une fois découvert que l'import CSV de Vérifit remplace entièrement les
  données (pas un ajout). **Confirmé par Romain : l'import fonctionne.** Voir
  `docs/fitnotes-fork-plan.md` pour le détail technique.
- [x] **Script de conversion FitNotes → Vérifit réutilisable en local (07/09/2026)**
  (retour Romain : "pour éviter que je te demande ça à chaque fois est ce que tu peux me
  faire un script que je garderai en local") : `scripts/convert_fitnotes_to_verifit_csv.py`,
  reprend la logique de conversion déjà validée, convertit par défaut tout l'historique du
  backup à chaque exécution (jamais un delta seul, puisque l'import Vérifit remplace tout -
  voir ci-dessus) pour rester sûr à relancer à volonté. Option `--since` pour usage avancé
  uniquement, avec avertissement explicite de ne pas l'importer telle quelle.

## En attente de décision / à planifier

- [ ] **Autoriser des charges négatives (exercices délestés/assistés)** (retour Romain
  06/09/2026, gros pain point identifié sur FitNotes) : "j'ai des exercices ou je me
  deleste (assisted) et je suis obligé de faire un truc naze pour tracker mes PRs :
  définir mon poids comme étant de 70 kilos, et set 50 kgs x 1 rep si j'ai réussi à
  faire une rep délesté de 20 kilos. Si Verifit autorisait le tracking de charge
  négative il n'y aurait pas de problème (ex : -15 x 5 reps, -10 x 3 reps)". Permettrait
  de logger directement l'assistance/le délestage en négatif plutôt que de bidouiller
  un faux poids de référence. **Complication identifiée par Romain lui-même** : "0
  <-- ok là on aura un souci pour dire une rep au poids du corps" - un poids à 0kg
  redeviendrait ambigu (poids du corps sans charge ajoutée, ou juste "0 de charge
  négative ajoutée" ?) une fois que le négatif devient un cas normal, à distinguer
  proprement. D'autres impacts à étudier avant de coder : le calcul du volume
  (`reps * weight`, qui deviendrait négatif ou nul), les comparaisons de records (`>`
  utilisé partout pour détecter un nouveau PR, y compris dans
  `calculateRepRangeHistory()` ajouté cette session - sens à revalider avec du
  négatif), les formules de 1RM (Epley), l'affichage (`"X kgs"` avec un signe moins),
  et l'export CSV/JSON. **Pas urgent** : "A étudier pour le moment je peux me
  contenter de l'existant" - Romain garde son contournement actuel en attendant.

- [ ] **Simulation "à blanc" d'une vraie séance (test end-to-end) - EN COURS (06/09/2026)**
  (retour Romain 06/09/2026, à propos du timer de repos - "je ne sais pas s'il faut
  vraiment le mettre en todo") : dérouler tout le process comme une vraie séance (créer
  le jour, logger des séries, lancer le timer de repos, éditer/réordonner/supprimer,
  etc.) sans la faire réellement (pour ne pas perturber l'entraînement de Romain), dans
  le but de repérer les points de friction éventuels avant qu'ils ne se manifestent en
  conditions réelles. Initialement gardé comme piste à évaluer, Romain a annoncé le
  06/09/2026 vouloir s'y mettre. Résultat/retours attendus à la prochaine session.

- [ ] **Retirer le backend de compte en ligne `verifit_rs`** : décision prise par Romain
  ("feature abandonnée officiellement"), à faire. Périmètre réel : au moins 14 fichiers
  touchent `verifit_rs`/`verifitrs`/`WorkoutSetsApi`/le mode offline
  (`MainActivity.initActivity()`, `AddExerciseActivity.saveComment()`, `DataStorage`,
  `SettingsActivity`, `ExerciseAdapter`, `BackupService`, la classe `SharedPreferences`
  maison, plus les 3 Activity dédiées `LoginActivity`/`ChangePasswordActivity`/
  `ForgotPasswordActivity` et le package `verifitrs`). Pas de SDK Android disponible côté
  Claude pour compiler et vérifier une suppression à la volée — à faire par étapes
  prudentes (forcer le mode offline en permanence \+ masquer l'UI de login d'abord,
  suppression effective du code mort ensuite), testées une à une.

- [ ] **Réorganisation/suppression multiple sur l'onglet Sessions (ex-Diary)** :
  `DiaryExerciseAdapter` n'a pas encore reçu la mécanique appliquée à `DayActivity` et à
  l'onglet Workout. Explicitement déprioritisé par Romain pour l'instant, mais c'est le
  dernier des 3 écrans "liste d'exercices d'un jour" à ne pas avoir la feature.

## Prochain chantier majeur

- [x] **Intégrer le générateur de séances (`workout_engine.py`)** — livré côté Coaching
  05/09/2026 : `build_session_import_json` génère le fichier au format "Import Session"
  déjà supporté par l'app (aucune modif Android nécessaire), échauffement et holds
  inclus, commentaire de chaque série = l'analyse PR déjà calculée pour le `.md`. Voir
  `SYNC_FITNOTES_APP.md` côté dépôt Coaching pour le détail. En test à l'usage réel par
  Romain.

## Pas urgent

- [ ] Upgrade AGP (actuellement 7.4.2) — délibérément reporté, pas bloquant.

* * *

*Ce fichier est aussi tenu à jour dans le projet Claude "FitNotes\_Fork" ; les deux
copies sont synchronisées à chaque session.*
