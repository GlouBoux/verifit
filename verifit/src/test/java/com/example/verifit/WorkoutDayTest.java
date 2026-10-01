package com.example.verifit;

import static com.example.verifit.TestData.*;
import static org.junit.Assert.*;

import com.example.verifit.model.WorkoutDay;
import com.example.verifit.model.WorkoutSet;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

// Jour d'entrainement (model/WorkoutDay) : liste derivee Exercises, ordre, supersets.
public class WorkoutDayTest
{
    private static WorkoutDay sample()
    {
        return day(
                set("2026-09-26", "Squat", "Legs", 5, 100),
                set("2026-09-26", "Curl", "Biceps", 10, 20),
                set("2026-09-26", "Squat", "Legs", 3, 110));
    }

    @Test
    public void exercices_regroupesDansLOrdreDApparition()
    {
        WorkoutDay d = sample();
        assertEquals("Squat", d.getExercises().get(0).getExercise());
        assertEquals(2, d.getExercises().get(0).getSets().size());
        assertEquals(1030.0, d.getDayVolume(), 0.0);
        assertEquals(18, d.getReps());
    }

    @Test
    public void sauvegarde_neContientPasLaListeDerivee()
    {
        String json = new Gson().toJson(sample());
        assertFalse(json.contains("\"Exercises\""));
        assertTrue(json.contains("\"Sets\""));
    }

    @Test
    public void rechargement_memesObjetsDansSetsEtExercises()
    {
        ArrayList<WorkoutDay> reloaded = reload(new ArrayList<WorkoutDay>(Collections.singletonList(sample())));
        WorkoutDay d = reloaded.get(0);

        // Une note ecrite depuis l'onglet Workout (Exercises[].Sets) doit se retrouver
        // dans Sets (source sauvegardee) : bug 1.1 de la revue du 28/09/2026.
        WorkoutSet viaExercises = d.getExercises().get(0).getSets().get(1);
        assertSame(d.getSets().get(2), viaExercises);
        viaExercises.setComment("note");
        assertEquals("note", d.getSets().get(2).getComment());
    }

    @Test
    public void rechargement_jourSansSerieGardeSaDate()
    {
        WorkoutDay empty = new WorkoutDay();
        empty.setDate("2026-09-26");
        WorkoutDay d = reload(new ArrayList<WorkoutDay>(Collections.singletonList(empty))).get(0);
        assertEquals("2026-09-26", d.getDate());
        assertTrue(d.getExercises().isEmpty());
    }

    @Test
    public void deplacerUnExercice()
    {
        WorkoutDay d = sample();
        d.moveExercise(1, 0);
        assertEquals(Arrays.asList("Curl", "Squat"), d.getExerciseOrder());
        assertEquals("Curl", d.getExercises().get(0).getExercise());
    }

    @Test
    public void supprimerLesSeriesDUnExercice_leRetireDeLOrdreEtDuSuperset()
    {
        WorkoutDay d = sample();
        d.addToSupersetGroup(new ArrayList<String>(Arrays.asList("Squat", "Curl")), "SS", 1);
        assertEquals(1, d.getSupersetGroups().size());

        d.removeSets(new ArrayList<WorkoutSet>(Collections.singletonList(d.getSets().get(1))));
        assertEquals(Collections.singletonList("Squat"), d.getExerciseOrder());
        assertTrue("un superset d'un seul exercice disparait", d.getSupersetGroups().isEmpty());
    }

    // Suppression d'exercices selectionnes (DayActions) et "Move a Workout".
    @Test
    public void retirerDesExercicesDUnJour()
    {
        WorkoutDay d = sample();
        DataStorage ds = storage(new String[][] {}, d);

        assertEquals(1, ds.removeExerciseSetsFromDay("2026-09-26", Arrays.asList("Curl")));
        assertEquals(Collections.singletonList("Squat"), d.getExerciseOrder());
        assertEquals(0, ds.removeExerciseSetsFromDay("2026-09-26", Arrays.asList("Curl")));
        assertEquals(0, ds.removeExerciseSetsFromDay("2026-01-01", Arrays.asList("Squat")));

        assertEquals(2, ds.removeExerciseSetsFromDay("2026-09-26", Arrays.asList("Squat")));
        assertTrue("jour sans serie retire", ds.workoutDays.isEmpty());
    }

    @Test
    public void insererUneSerie_positionBornee()
    {
        WorkoutDay d = sample();
        WorkoutSet extra = set("2026-09-26", "Curl", "Biceps", 12, 15);
        d.insertSetAt(99, extra);
        assertSame(extra, d.getSets().get(3));
        WorkoutSet first = set("2026-09-26", "Curl", "Biceps", 12, 10);
        d.insertSetAt(-5, first);
        assertSame(first, d.getSets().get(0));
    }
}
