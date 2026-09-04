package com.example.verifit.model;

import java.util.ArrayList;

// Root object of the JSON file accepted by "Import Session" (see DayActivity /
// SessionImporter). Meant to be easy to emit from an external workout-generation
// script: one file == one day's worth of exercises and sets.
//
// Full schema documented in docs/session-import-format.md.
public class ImportedSession {

    private String date; // optional, "yyyy-MM-dd"; falls back to the day currently open in the app
    private String comment; // optional, currently informational only (not persisted)
    private ArrayList<ImportedExercise> exercises;

    public ImportedSession() {
    }

    public String getDate() {
        return date == null ? "" : date.trim();
    }

    public String getComment() {
        return comment == null ? "" : comment;
    }

    public ArrayList<ImportedExercise> getExercises() {
        return exercises == null ? new ArrayList<ImportedExercise>() : exercises;
    }
}
