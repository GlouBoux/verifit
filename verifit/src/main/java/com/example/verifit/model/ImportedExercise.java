package com.example.verifit.model;

import java.util.ArrayList;

// One exercise entry inside an ImportedSession, as produced by an external
// workout-generation script and consumed by SessionImporter.
// See docs/session-import-format.md for the full JSON schema.
public class ImportedExercise {

    private String name;
    private String bodyPart;
    private String comment;
    private ArrayList<ImportedSet> sets;

    public ImportedExercise() {
    }

    public String getName() {
        return name == null ? "" : name.trim();
    }

    // May be null/empty; SessionImporter falls back to the exercise's already known
    // body part, or leaves it blank for a brand new exercise so the user can fill it
    // in later from the Edit dialog.
    public String getBodyPart() {
        return bodyPart == null ? "" : bodyPart.trim();
    }

    public String getComment() {
        return comment == null ? "" : comment;
    }

    public ArrayList<ImportedSet> getSets() {
        return sets == null ? new ArrayList<ImportedSet>() : sets;
    }
}
