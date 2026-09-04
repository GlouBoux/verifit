package com.example.verifit;

import android.content.Context;
import android.net.Uri;

import com.example.verifit.model.ImportedSession;
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
        }

        if (session == null || session.getExercises().isEmpty()) {
            result.success = false;
            result.errorMessage = "No exercises found in file";
            return result;
        }

        DataStorage.ImportSummary summary = dataStorage.mergeImportedSession(session, fallbackDate);

        if (summary.setsImported == 0) {
            result.success = false;
            result.errorMessage = "Nothing to import (0 valid sets found)";
            result.summary = summary;
            return result;
        }

        // Persist straight away, same as every other mutation in DataStorage.
        dataStorage.saveKnownExerciseData(context);
        dataStorage.saveWorkoutData(context);

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
