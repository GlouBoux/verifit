package com.example.verifit;

import android.content.Context;
import android.net.Uri;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;

// Reads a JSON file mapping exact exercise names to notes text
// ({"Assisted Baby Cross Hold": "20-20, DM : ...", ...}) and merges it into a
// DataStorage instance's known exercises. Used by ExercisesActivity's "Import Notes"
// menu action - retour Romain 28/09/2026, qui voulait recuperer en masse les notes
// deja tapees dans l'app FitNotes d'origine (colonne exercise.notes d'un vieux backup
// .fitnotes, voir Coaching/fitnotes_notes_to_verifit.py) plutot que de tout retaper a
// la main pour chacun des ~200 exercices concernes.
//
// Meme famille que SessionImporter (fichier JSON externe, additif, jamais de creation
// d'exercice a la volee), mais plus simple : pas de notion de jour/seance, juste une
// mise a jour du catalogue d'exercices - voir DataStorage.mergeExerciseNotes().
public class ExerciseNotesImporter {

    public static class Result {
        public boolean success;
        public String errorMessage = "";
        public DataStorage.NotesImportSummary summary;
    }

    public static Result importFromUri(Uri uri, Context context, DataStorage dataStorage) {
        Result result = new Result();

        String json;
        try {
            json = readAll(uri, context);
        } catch (IOException e) {
            result.success = false;
            result.errorMessage = "Could not read file: " + e.getMessage();
            return result;
        }

        Map<String, String> notesByExerciseName;
        try {
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            notesByExerciseName = gson.fromJson(json, type);
        } catch (JsonSyntaxException e) {
            result.success = false;
            result.errorMessage = "Invalid JSON file: " + e.getMessage();
            return result;
        } catch (IllegalStateException | NumberFormatException e) {
            // Same defensive catch as SessionImporter: a value that isn't a plain JSON
            // string (e.g. a nested object) throws one of these directly from Gson
            // rather than JsonSyntaxException.
            result.success = false;
            result.errorMessage = "Invalid JSON file (expected {\"exercise name\": \"notes\"} pairs): " + e.getMessage();
            return result;
        }

        if (notesByExerciseName == null || notesByExerciseName.isEmpty()) {
            result.success = false;
            result.errorMessage = "No entries found in file";
            return result;
        }

        DataStorage.NotesImportSummary summary = dataStorage.mergeExerciseNotes(notesByExerciseName);

        if (summary.applied == 0) {
            result.success = false;
            result.errorMessage = "Nothing imported (0 matching exercise with empty notes found)";
            result.summary = summary;
            return result;
        }

        // Persist straight away, same as every other mutation in DataStorage.
        dataStorage.saveKnownExerciseData(context);

        result.success = true;
        result.summary = summary;
        return result;
    }

    private static String readAll(Uri uri, Context context) throws IOException {
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
