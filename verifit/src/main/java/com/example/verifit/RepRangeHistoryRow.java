package com.example.verifit;

import com.example.verifit.model.WorkoutSet;

// Une ligne de l'ecran "Historique des PR par nombre de reps" (retour Romain
// 06/09/2026 : "Un PR c'est un record [...] pour ce rep range [...] pour ce poids.
// Fitnotes garde un historique de PR pour chaque exercice"). Soit un en-tete de
// section (un nombre de reps donne, ex "43 reps"), soit une entree d'historique (le
// record atteint a une date donnee pour ce nombre de reps - le plus recent etant
// marque comme "actuel"). Construite par RepRangeRecordsActivity a partir de
// DataStorage.calculateRepRangeHistory().
public class RepRangeHistoryRow
{
    private boolean header;
    private Double reps;
    private WorkoutSet set; // null si header
    private boolean current; // vrai si c'est le record actuel pour ce nombre de reps

    public static RepRangeHistoryRow newHeader(Double reps)
    {
        RepRangeHistoryRow row = new RepRangeHistoryRow();
        row.header = true;
        row.reps = reps;
        return row;
    }

    public static RepRangeHistoryRow newEntry(Double reps, WorkoutSet set, boolean current)
    {
        RepRangeHistoryRow row = new RepRangeHistoryRow();
        row.header = false;
        row.reps = reps;
        row.set = set;
        row.current = current;
        return row;
    }

    public boolean isHeader()
    {
        return header;
    }

    public Double getReps()
    {
        return reps;
    }

    public WorkoutSet getSet()
    {
        return set;
    }

    public boolean isCurrent()
    {
        return current;
    }
}
