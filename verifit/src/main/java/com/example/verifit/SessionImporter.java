package com.example.verifit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;

import com.example.verifit.model.ImportedExercise;
import com.example.verifit.model.ImportedSession;
import com.example.verifit.model.WorkoutDay;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.util.HashSet;

// Reads a JSON file describing one workout session (see docs/session-import-format.md)
// and merges it into a DataStorage instance. Used by DayActivity's "Import Session" menu
// action so an external tool - e.g. a workout-generator script - can hand a planned or
// logged day straight to the app instead of it being typed in by hand.
//
// This is intentionally separate from DataStorage.readFile()/parseCsvSets(): that path is a
// full backup restore (CSV, wipes existing data first). This one is additive and speaks
// JSON, tailored to "drop in a single day from another program".
public class SessionImporter {

    // Outcome of a single importFromUri() call, meant to be shown to the user as a
    // Toast/Snackbar (see DayActivity).
    public static class Result {
        // Point 1.5 de la revue du 28/09/2026 : le jour contient deja des series importees
        // pour ces exercices. Rien n'a ete importe ; l'appelant peut relancer avec
        // allowDuplicate = true apres confirmation (voir importWithDuplicateCheck()).
        public boolean alreadyImported;
        public int existingImportedSets;
        public String targetDate;
        // Import idempotent (15/10/2026) : ce PLAN (meme contenu, voir ImportedPlans) a deja
        // ete importe. Renseigne avec alreadyImported = true ; null sinon.
        public ImportedPlans.Entry previousPlanImport;
        // Accuse fitengine_plan_imports.json non ecrit (l'import, lui, a reussi) : message
        // a montrer a l'utilisateur, ou null.
        public String ackWarning;
        public boolean success;
        public String errorMessage = "";
        public DataStorage.ImportSummary summary;
    }

    // fallbackDate is used when the JSON file doesn't specify its own "date" field -
    // typically the date of the DayActivity screen the import was triggered from.
    public static Result importFromUri(Uri uri, Context context, DataStorage dataStorage, String fallbackDate) {
        return importFromUri(uri, context, dataStorage, fallbackDate, false);
    }

    public interface OnImportDone {
        void onImportDone(Result result);
    }

    // Point d'entree des ecrans (MainActivity, DayActivity) : importe la seance, sauf si
    // le jour contient deja des series importees pour ces exercices (retour de la revue
    // d'architecture du 28/09/2026, point 1.5 : un double import doublait toutes les
    // series). Dans ce cas, confirmation explicite avant d'importer quand meme ; sur
    // "Annuler", onDone n'est pas appele et rien ne change. Toute exception imprevue
    // devient un Result en echec plutot qu'un crash (fichier externe choisi par
    // l'utilisateur).
    public static void importWithDuplicateCheck(final Activity activity, final Uri uri, final DataStorage dataStorage,
                                                final String fallbackDate, final OnImportDone onDone) {
        runWithDuplicateCheck(activity, allowDuplicate -> safeImport(uri, activity, dataStorage, fallbackDate, allowDuplicate), onDone);
    }

    // Meme logique, a partir du TEXTE JSON deja lu (lot D, D8 : ImportSessionActivity,
    // qui a lu le fichier recu d'une autre appli, ou le JSON partage comme texte).
    public static void importJsonWithDuplicateCheck(final Activity activity, final String json, final DataStorage dataStorage,
                                                    final String fallbackDate, final OnImportDone onDone) {
        importJsonWithDuplicateCheck(activity, json, dataStorage, fallbackDate, "", onDone);
    }

    // sourceName : nom du fichier lu (garde dans le registre des plans importes et dans
    // l'accuse pour le PC), vide si inconnu.
    public static void importJsonWithDuplicateCheck(final Activity activity, final String json, final DataStorage dataStorage,
                                                    final String fallbackDate, final String sourceName, final OnImportDone onDone) {
        runWithDuplicateCheck(activity, allowDuplicate -> safeImportJson(json, activity, dataStorage, fallbackDate, sourceName, allowDuplicate), onDone);
    }

    // Reimport explicitement demande ("Reimporter quand meme" de l'apercu, qui a deja
    // montre l'avertissement) : aucune des deux verifications de doublon, pas de seconde
    // alerte. L'import est marque "forced" dans le registre et l'accuse.
    public static void importJsonForced(final Context context, final String json, final DataStorage dataStorage,
                                        final String fallbackDate, final String sourceName, final OnImportDone onDone) {
        onDone.onImportDone(safeImportJson(json, context, dataStorage, fallbackDate, sourceName, true));
    }

    private interface ImportAttempt {
        Result run(boolean allowDuplicate);
    }

    private static void runWithDuplicateCheck(final Activity activity, final ImportAttempt attempt, final OnImportDone onDone) {
        Result first = attempt.run(false);
        if (!first.alreadyImported) {
            onDone.onImportDone(first);
            return;
        }

        // Deux raisons possibles : le meme plan (empreinte du contenu) a deja ete importe,
        // ou le jour contient deja des series importees pour ces exercices (point 1.5).
        String message;
        if (first.previousPlanImport != null) {
            message = ImportedPlans.describePreviousImport(first.previousPlanImport)
                    + "\n\nLe réimporter ajoutera ses séries une seconde fois.";
        } else {
            message = "Le " + first.targetDate + " contient déjà " + first.existingImportedSets
                    + " série(s) importée(s) pour les exercices de ce fichier.\n\n"
                    + "Importer quand même ajoutera ces séries une seconde fois.";
        }

        new AlertDialog.Builder(activity)
                .setTitle(first.previousPlanImport != null ? "Plan déjà importé" : "Séance déjà importée ?")
                .setMessage(message)
                .setPositiveButton("Importer quand même", (dialog, which) ->
                        onDone.onImportDone(attempt.run(true)))
                .setNegativeButton("Annuler", null)
                .show();
    }

    private static Result safeImport(Uri uri, Context context, DataStorage dataStorage, String fallbackDate, boolean allowDuplicate) {
        try {
            return importFromUri(uri, context, dataStorage, fallbackDate, allowDuplicate);
        } catch (Exception e) {
            Result result = new Result();
            result.success = false;
            result.errorMessage = e.toString();
            return result;
        }
    }

    private static Result safeImportJson(String json, Context context, DataStorage dataStorage, String fallbackDate, String sourceName, boolean allowDuplicate) {
        try {
            return importFromJson(json, context, dataStorage, fallbackDate, sourceName, allowDuplicate);
        } catch (Exception e) {
            Result result = new Result();
            result.success = false;
            result.errorMessage = e.toString();
            return result;
        }
    }

    public static Result importFromUri(Uri uri, Context context, DataStorage dataStorage, String fallbackDate, boolean allowDuplicate) {
        String json;
        try {
            json = TextFiles.readAll(uri, context, TextFiles.MAX_SESSION_CHARS);
        } catch (IOException e) {
            Result result = new Result();
            result.success = false;
            result.errorMessage = "Could not read file: " + e.getMessage();
            return result;
        }
        return importFromJson(json, context, dataStorage, fallbackDate, TextFiles.displayName(uri, context), allowDuplicate);
    }

    // Import a partir du texte JSON (lot D, D8) : tout ce que faisait importFromUri()
    // apres la lecture du fichier. Une date invalide (ni "AAAA-MM-JJ", ni une vraie
    // date) est refusee : elle servirait de cle de jour dans les donnees.
    public static Result importFromJson(String json, Context context, DataStorage dataStorage, String fallbackDate, boolean allowDuplicate) {
        return importFromJson(json, context, dataStorage, fallbackDate, "", allowDuplicate);
    }

    // Import idempotent (15/10/2026) : sourceName = nom du fichier (vide si inconnu),
    // garde dans le registre des plans importes. allowDuplicate = true saute les DEUX
    // verifications (meme plan deja importe, jour deja rempli).
    public static Result importFromJson(String json, Context context, DataStorage dataStorage, String fallbackDate,
                                        String sourceName, boolean allowDuplicate) {
        Result result = new Result();

        // Lot S1 (revue securite du 27/10/2026) : donnees de l'app non chargees, l'import
        // ne pourrait pas etre enregistre.
        if (dataStorage.hasLoadProblem()) {
            result.success = false;
            result.errorMessage = "les données de l'app sont illisibles : ouvre " + AppNames.APP_NAME + " pour restaurer un backup";
            return result;
        }

        ImportedSession session;
        try {
            session = parseSession(json);
        } catch (InvalidSessionException e) {
            result.success = false;
            result.errorMessage = e.getMessage();
            return result;
        }

        if (session == null || session.getExercises().isEmpty()) {
            result.success = false;
            result.errorMessage = "No exercises found in file";
            return result;
        }

        String targetDate = session.getDate().isEmpty() ? fallbackDate : session.getDate();
        String dateProblem = SessionPreview.dateProblem(targetDate);
        if (dateProblem != null) {
            result.success = false;
            result.errorMessage = dateProblem;
            return result;
        }

        // Lot S2 (revue securite du 27/10/2026) : une valeur NaN ou infinie entrait dans
        // les donnees et faisait echouer toutes les sauvegardes suivantes. Meme refus qu'a
        // l'apercu (SessionPreview).
        String valuesProblem = SessionPreview.valuesProblem(session);
        if (valuesProblem != null) {
            result.success = false;
            result.errorMessage = valuesProblem;
            return result;
        }

        // Import idempotent (15/10/2026) : ce plan (meme contenu, quel que soit le nom du
        // fichier) a-t-il deja ete importe ? Verifie AVANT le controle par jour ci-dessous :
        // il reconnait aussi un plan importe sur un autre jour, ou dont les series ont ete
        // supprimees depuis.
        String planHash = ImportedPlans.hash(json);
        if (!allowDuplicate) {
            ImportedPlans.Entry previous = ImportedPlansStore.load(context).find(planHash);
            if (previous != null) {
                result.success = false;
                result.alreadyImported = true;
                result.previousPlanImport = previous;
                result.targetDate = targetDate;
                result.errorMessage = "Plan already imported on " + previous.importedAt;
                return result;
            }
        }

        // Point 1.5 : seance deja importee ce jour-la ? Detection AVANT toute
        // modification (et avant la copie automatique, inutile si on n'importe pas).
        if (!allowDuplicate) {
            HashSet<String> exerciseNames = new HashSet<String>();
            for (ImportedExercise exercise : session.getExercises()) {
                if (exercise != null) {
                    exerciseNames.add(exercise.getName());
                }
            }
            int existing = dataStorage.countImportedSets(targetDate, exerciseNames);
            if (existing > 0) {
                result.success = false;
                result.alreadyImported = true;
                result.existingImportedSets = existing;
                result.targetDate = targetDate;
                result.errorMessage = "Session already imported on " + targetDate;
                return result;
            }
        }

        // Copie automatique avant d'ajouter des series (point 1.4, voir BackupManager).
        BackupManager.snapshot(context, dataStorage, "avant_import_seance");

        DataStorage.ImportSummary summary = dataStorage.mergeImportedSession(session, fallbackDate);

        if (summary.setsImported == 0) {
            result.success = false;
            result.errorMessage = "Nothing to import (0 valid sets found)";
            result.summary = summary;
            return result;
        }

        // BUG corrige (retour Romain 28/09/2026 : "le timer de la séance ne se lance
        // pas automatiquement quand je loggue ma première série") : mergeImportedSession()
        // ci-dessus peuple les series du jour directement (WorkoutDay.addSet()), sans
        // jamais passer par AddExerciseActivity.addSetNewWorkoutDay()/
        // addSetExistingWorkoutDay() - les deux SEULS endroits qui demarraient jusqu'ici
        // ce chrono (voir leur javadoc, startOrResumeSessionTimer()). Or Romain travaille
        // toujours depuis un Import Session (jamais en tapant chaque serie a la main), le
        // chrono ne demarrait donc en pratique jamais tout seul. Meme regle et meme
        // reglage "Auto Start" que l'ecran de saisie (WorkoutDay.startOrResumeSession(),
        // SessionTimerTicker.isAutoStartEnabled()).
        startOrResumeSessionTimerAfterImport(context, dataStorage, summary.date);

        // Persist straight away, same as every other mutation in DataStorage.
        dataStorage.saveKnownExerciseData(context);
        dataStorage.saveWorkoutData(context);

        // Registre des plans importes + accuse pour le PC (Documents/FitEngine), apres la
        // sauvegarde : seul un import reellement enregistre est memorise.
        result.ackWarning = ImportedPlansStore.recordImport(context, new ImportedPlans.Entry(
                planHash, sourceName, summary.date, session.getComment().trim(),
                ImportedPlansStore.now(), summary.setsImported, allowDuplicate));

        result.success = true;
        result.summary = summary;
        return result;
    }

    // Lecture du JSON de seance (contrat du fichier genere par workout_engine.py cote
    // Coaching), sans aucune dependance Android : separee de importFromUri() pour etre
    // testee en JUnit (lot C, etape C.1, 29/09/2026). Renvoie null pour un fichier vide.
    // Leve InvalidSessionException avec le message affiche a l'utilisateur, inchange.
    // Public depuis le lot D (D7) : ImportSessionActivity (package ui) l'appelle pour l'apercu.
    public static ImportedSession parseSession(String json) throws InvalidSessionException {
        try {
            Gson gson = new Gson();
            return gson.fromJson(json, ImportedSession.class);
        } catch (JsonSyntaxException e) {
            throw new InvalidSessionException("Invalid JSON file: " + e.getMessage());
        } catch (IllegalStateException | NumberFormatException e) {
            // Gson throws these directly (not wrapped in JsonSyntaxException) when a
            // field's JSON type doesn't match the Java type it's bound to - e.g. a
            // quoted "8" instead of a numeric 8 for weight/reps, or an object where a
            // list was expected. Catching only JsonSyntaxException misses these and lets
            // them crash the app, so treat them the same way as a syntax error.
            throw new InvalidSessionException("Invalid JSON file (unexpected field type): " + e.getMessage());
        }
    }

    public static class InvalidSessionException extends Exception {
        InvalidSessionException(String message) {
            super(message);
        }
    }

    // Demarre (ou reprend, si le jour avait ete Stop manuellement avant ce nouvel
    // import) le chrono de seance du jour importe - meme logique que
    // AddExerciseActivity.startOrResumeSessionTimer(), reprise ici car
    // mergeImportedSession() ne passe jamais par cette Activity. La sauvegarde reste
    // faite par l'appelant juste apres (dataStorage.saveWorkoutData()), pas besoin de
    // la refaire ici.
    //
    // Correctif du 15/10/2026 : seulement si le jour importe est aujourd'hui. Une seance
    // importee la veille ne demarre plus son chrono a l'heure de l'import ; il demarrera
    // a sa premiere serie validee (WorkoutDay.startSessionOnFirstLog()).
    private static void startOrResumeSessionTimerAfterImport(Context context, DataStorage dataStorage, String date) {
        int dayPosition = dataStorage.getDayPosition(date);
        if (dayPosition < 0) {
            return;
        }
        long now = System.currentTimeMillis();
        dataStorage.getWorkoutDays().get(dayPosition)
                .startOrResumeSessionIfToday(SessionTimerTicker.isAutoStartEnabled(context), now, WorkoutDay.dateKey(now));
    }
}
