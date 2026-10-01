package com.example.verifit;

import com.example.verifit.model.SetRest;
import com.example.verifit.model.WorkoutSet;

import java.util.ArrayList;
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

    // Construit le jour a partir des series du modele, en ajoutant le repos reel de
    // chaque serie (SetRest.restSecondsForDay, lot D etape D4).
    public static CoachingExportDay fromWorkoutSets(String date, List<WorkoutSet> daySets)
    {
        List<Long> rests = SetRest.restSecondsForDay(daySets);
        List<CoachingExportSet> exportSets = new ArrayList<CoachingExportSet>();
        for (int i = 0; i < daySets.size(); i++)
        {
            exportSets.add(new CoachingExportSet(daySets.get(i), rests.get(i)));
        }
        return new CoachingExportDay(date, exportSets);
    }
}
