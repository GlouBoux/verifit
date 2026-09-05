# Workflow de collaboration — FitNotes_Fork

Comment on avance sur ce projet, session après session.

## Boucle standard

1. **Cadrage** : Romain décrit un besoin ou un bug. Claude pose des questions si besoin
   (comportement exact voulu, priorité, périmètre) jusqu'à un consensus clair avant de
   coder — évite le travail à refaire sur une mauvaise interprétation.
2. **Implémentation** : Claude modifie le code directement sur la machine de Romain (via
   le pont avec son appareil, dossier `FitNotes_Fork` connecté), et livre en plus un
   patch git (`000X-....patch`) pour archive/traçabilité — mais le patch n'a jamais besoin
   d'être appliqué manuellement, le fichier est déjà à jour sur disque.
3. **Revue** : Romain relit le diff dans VSCode (`git status` / `git diff`) s'il veut
   voir précisément ce qui a changé avant de tester.
4. **Test réel** : build + installation via Android Studio (émulateur ou téléphone
   branché en USB avec débogage activé — le téléphone est préférable pour les bugs liés
   au cycle de vie de l'app, moins fidèlement reproduits par l'émulateur). Bouton Run.
5. **Validation** : si ça fonctionne comme prévu, Romain committe sur sa branche
   (`git add -A && git commit`) pour clore le changement dans son historique. Si ça ne
   fonctionne pas, retour à l'étape 1 avec le symptôme observé.
6. **Suivi** : à chaque session, Claude met à jour `docs/fitnotes-fork-plan.md` (le
   pourquoi et le détail de chaque chantier) et `docs/fitnotes-fork-todo.md` (l'état
   coché/pas coché), à la fois dans le projet Claude et dans ce dépôt.

## Message de commit fourni par Claude

Romain trouve la rédaction des messages de commit pénible (se souvenir de ce qui a
changé, trouver quoi écrire). Du coup, **à chaque fois qu'un changement est prêt à être
validé (étape 5), Claude fournit directement le message à utiliser tel quel** — pas
besoin de le rédiger soi-même ni de s'en souvenir après coup.

Format utilisé (une ligne de résumé + corps optionnel si plusieurs changements sont
groupés) :

```
<résumé court, à l'impératif : "Ajoute...", "Corrige...", "Renomme...">

<optionnel : quelques lignes expliquant le "pourquoi" si ce n'est pas évident, ou la
liste des changements si plusieurs sujets sont regroupés dans un même commit>
```

Exemple concret (patch #6) :

```
Ajoute un commentaire par série (appui long)

Le commentaire existant ("Exercise Comments") s'appliquait à toutes les séries de
l'exercice du jour d'un coup. Ici chaque série peut avoir son propre commentaire,
consultable/éditable par un appui long sur la série. Permettra de récupérer ces
commentaires fidèlement lors d'une future migration de données FitNotes.
```

Romain n'a qu'à copier-coller ce message dans `git commit -m "..."` (ou dans la zone de
message de VSCode) une fois le test validé sur le téléphone.

## Partager un build log

Si un build échoue et que tu veux me le faire lire sans copier-coller, double-clique
`scripts/build_and_log.bat` (ou lance-le depuis un terminal) : il build avec
`assembleDebug --info` (la même tâche que le bouton Run d'Android Studio, contrairement
à `./gradlew build` qui compile aussi l'APK de tests instrumentés — voir plus bas) et
écrit `build_log.txt` à la racine du dépôt, en capturant à la fois stdout et stderr
(c'est important : le résumé final "What went wrong" de Gradle part souvent sur stderr,
et un simple `> build_log.txt` sans `2>&1` le perd). Envoie-moi ensuite ce fichier
directement, comme un fichier joint normal.

`build_log.txt` est dans le `.gitignore` (c'est un artefact de build propre à ta machine,
pas du code) — s'il apparaît quand même dans `git status`, c'est qu'il a été ajouté avant
qu'on l'ignore ; un `git rm --cached build_log.txt` une fois suffit à le déstracker.

Note au 05/09/2026 : `./gradlew build` (la tâche générique, pas `assembleDebug`) échoue
systématiquement sur `:verifit:processDebugAndroidTestManifest` à cause d'un bug
préexistant et sans rapport avec nos changements — la vieille version d'espresso-core
(3.2.0) utilisée pour les tests instrumentés ne déclare pas `android:exported`, requis
depuis l'API 31. Ça ne bloque ni le bouton Run d'Android Studio ni `assembleDebug` (qui
ne construisent pas l'APK de test), donc ce n'est pas un problème pour tester l'app au
quotidien — seulement pour `./gradlew build` ou `connectedAndroidTest` tels quels.

## Répartition claire des responsabilités

- **Claude** : comprendre le besoin, lire/écrire le code, expliquer les causes de bug,
  livrer patch + application directe, tenir la documentation à jour.
- **Romain** : valider les choix de comportement (UX, priorités), builder et tester sur
  un vrai appareil (seul point que Claude ne peut pas faire — pas de SDK Android côté
  Claude), committer/pousser vers son propre repo GitHub, décider des PR vers l'amont.

## Ce que Claude ne fait jamais sans qu'on en discute d'abord

- Push vers un dépôt GitHub distant (Romain pousse lui-même).
- Changements larges et difficiles à vérifier sans build (ex. retrait de tout le backend
  `verifit_rs`) faits en un seul coup — préférence pour des étapes plus petites et
  testables individuellement.

---
*Ce fichier est aussi tenu à jour dans le projet Claude "FitNotes_Fork".*
