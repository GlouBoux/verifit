package com.example.verifit.model;

// One logged (or planned) set inside an ImportedExercise, as produced by an external
// workout-generation script and consumed by SessionImporter.
// See docs/session-import-format.md for the full JSON schema.
public class ImportedSet {

    private Double weight;
    private Double reps;
    private String comment;

    public ImportedSet() {
    }

    public ImportedSet(Double weight, Double reps, String comment) {
        this.weight = weight;
        this.reps = reps;
        this.comment = comment;
    }

    public Double getWeight() {
        return weight;
    }

    public Double getReps() {
        return reps;
    }

    public String getComment() {
        return comment == null ? "" : comment;
    }
}
