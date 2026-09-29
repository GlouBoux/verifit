package com.example.verifit;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import com.example.verifit.model.ImportedSession;
import com.example.verifit.model.WorkoutDay;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

// Reads a JSON file describing one workout session (see docs/session-import-format.md)
// and merges it into a DataStorage instance. Used by DayActivity's "Import Session" menu
// action so an external tool - e.g. a workout-generator script - can hand a planned or
// logged day straight to the app instead of it being typed in by hand.
//
// This is intentionally separate from DataStorage.readFile()/csvToSets(): that path is a
// full backup restore (CSV, wipes existing data first). This one is additive and speaks
// JSON, tailored to "drop in a single day from another program".
public class SessionImporter {

    // Outcome of a single importFromUri() call, meant to be shown to the user as a
    // Toast/Snackbar (see DayActivity).
    public static class Result {
        public boolean success;
        public String errorMessage = "";
        public DataStorage.ImportSummary summary;
    }

    // fallbackDate is used when the JSON file doesn't specify its own "date" field -
    // typically the date of the DayActivity screen the import was triggered from.
    public static Result importFromUri(Uri uri, Context context, DataStorage dataStorage, String fallbackDate) {
        Result result = new Result();

        String json;
        try {
            json = readAll(uri, context);
        } catch (IOException e) {
            result.success = false;
            result.errorMessage = "Could not read file: " + e.getMessage();
            return result;
        }

        ImportedSession session;
        try {
            Gson gson = new Gson();
            session = gson.fromJson(json, ImportedSession.class);
        } catch (JsonSyntaxException e) {
            result.success = false;
            result.errorMessage = "Invalid JSON file: " + e.getMessage();
            return result;
        } catch (IllegalStateException | NumberFormatException e) {
            // Gson throws these directly (not wrapped in JsonSyntaxException) when a
            // field's JSON type doesn't match the Java type it's bound to - e.g. a
            // quoted "8" instead of a numeric 8 for weight/reps, or an object where a
            // list was expected. Catching only JsonSyntaxException misses these and lets
            // them crash the app, so treat them the same way as a syntax error.
            result.success = false;
            result.errorMessage = "Invalid JSON file (unexpected field type): " + e.getMessage();
            return result;
        }

        if (session == null || session.getExercises().isEmpty()) {
            result.success = false;
            result.errorMessage = "No exercises found in file";
            return result;
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
        // chrono ne demarrait donc en pratique jamais tout seul. Meme reglage "Auto
        // Start" que AddExerciseActivity.isSessionAutoStartEnabled() (meme fichier de
        // preferences, meme cle - dupliquee ici plutot que de faire dependre les donnees
        // de l'UI, meme convention que les dialogues deja dupliques entre adapters dans
        // ce projet).
        startOrResumeSessionTimerAfterImport(context, dataStorage, summary.date);

        // Persist straight away, same as every other mutation in DataStorage.
        dataStorage.saveKnownExerciseData(context);
        dataStorage.saveWorkoutData(context);

        result.success = true;
        result.summary = summary;
        return result;
    }

    // Demarre (ou reprend, si le jour avait ete Stop manuellement avant ce nouvel
    // import) le chrono de seance du jour importe - meme logique que
    // AddExerciseActivity.startOrResumeSessionTimer(), reprise ici car
    // mergeImportedSession() ne passe jamais par cette Activity. La sauvegarde reste
    // faite par l'appelant juste apres (dataStorage.saveWorkoutData()), pas besoin de
    // la refaire ici.
    private static void startOrResumeSessionTimerAfterImport(Context context, DataStorage dataStorage, String date) {
        int dayPosition = dataStorage.getDayPosition(date);
        if (dayPosition < 0) {
            return;
        }

        WorkoutDay day = dataStorage.getWorkoutDays().get(dayPosition);

        if (day.getSessionStartTimestamp() == null) {
            SharedPreferences sharedPreferences = context.getSharedPreferences("shared preferences", Context.MODE_PRIVATE);
            boolean autoStartEnabled = sharedPreferences.getBoolean("session_auto_start", true);
            if (!autoStartEnabled) {
                return;
            }
            day.setSessionStartTimestamp(System.currentTimeMillis());
        } else if (day.getSessionEndTimestamp() != null) {
            day.setSessionEndTimestamp(null);
        }
    }

    // Aussi utilisee par BackupManager.readFromUri() (restauration d'un backup complet).
    static String readAll(Uri uri, Context context) throws IOException {
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        if (inputStream == null) {
            throw new IOException("Unable to open selected file");
        }

        StringBuilder builder = new StringBuilder();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        } finally {
            if (reader != null) {
                reader.close();
            } else {
                inputStream.close();
            }
        }
        return builder.toString();
    }
}
