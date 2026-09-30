package com.example.verifit;

import com.example.verifit.model.Exercise;
import com.example.verifit.model.WorkoutDay;
import com.example.verifit.model.WorkoutSet;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;

// Petites fabriques partagees par les tests JUnit (lot C, etape C.2). Les donnees sont
// inspirees de vraies seances de Romain, reduites a quelques exercices.
final class TestData
{
    private TestData() {}

    static WorkoutSet set(String date, String exercise, String category, double reps, double weight)
    {
        return new WorkoutSet(date, exercise, category, reps, weight);
    }

    static WorkoutDay day(WorkoutSet... sets)
    {
        WorkoutDay day = new WorkoutDay();
        for (WorkoutSet s : sets)
        {
            day.addSet(s);
        }
        return day;
    }

    // DataStorage sans Context : les champs sont package-private, remplis directement.
    static DataStorage storage(String[][] knownExercises, WorkoutDay... days)
    {
        DataStorage ds = new DataStorage();
        for (String[] e : knownExercises)
        {
            Exercise exercise = new Exercise(e[0], e[1]);
            exercise.setFavorite(false);
            exercise.setNotes("");
            ds.knownExercises.add(exercise);
        }
        for (WorkoutDay d : days)
        {
            ds.workoutDays.add(d);
        }
        return ds;
    }

    // Meme cycle que la sauvegarde de l'app : Gson aller-retour puis reconstruction de la
    // liste derivee Exercises (DataStorage.loadWorkoutData()).
    static ArrayList<WorkoutDay> reload(ArrayList<WorkoutDay> days)
    {
        Gson gson = new Gson();
        ArrayList<WorkoutDay> reloaded = gson.fromJson(gson.toJson(days), new TypeToken<ArrayList<WorkoutDay>>(){}.getType());
        for (WorkoutDay d : reloaded)
        {
            d.rebuildDerivedData();
        }
        return reloaded;
    }
}
