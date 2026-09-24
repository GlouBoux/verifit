package com.example.verifit;

import java.util.List;

// Un jour de seance, pour l'export JSON dedie au pipeline Coaching - voir
// CoachingExportSet pour le detail du contrat et pourquoi ce format est separe du
// modele interne WorkoutDay/WorkoutSet.
public class CoachingExportDay
{
    private final String date;
    private final List<CoachingExportSet> sets;

    public CoachingExportDay(String date, List<CoachingExportSet> sets)
    {
        this.date = date;
        this.sets = sets;
    }
}
