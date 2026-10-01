package com.example.verifit;

import static com.example.verifit.TestData.*;
import static org.junit.Assert.*;

import com.example.verifit.adapters.ExerciseSelection;
import com.example.verifit.model.WorkoutDay;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

// Selection multiple et repli des cartes d'exercices (adapters/ExerciseSelection),
// partages par l'onglet Workout et l'ecran du jour.
public class ExerciseSelectionTest
{
    private final WorkoutDay day = day(
            set("2026-10-01", "Squat", "Legs", 5, 100),
            set("2026-10-01", "Curl", "Biceps", 10, 20),
            set("2026-10-01", "Dips", "Chest", 8, 10));

    @Test
    public void selection_dansLOrdreDAffichage()
    {
        ExerciseSelection s = new ExerciseSelection();
        s.enter();
        s.toggle("Dips");
        s.toggle("Squat");
        assertEquals(Arrays.asList("Squat", "Dips"), s.selectedInOrder(day.getExercises()));
        s.toggle("Dips");
        assertEquals(Collections.singletonList("Squat"), s.selectedInOrder(day.getExercises()));
        assertEquals(1, s.count());
    }

    @Test
    public void toutSelectionner_puisToutDeselectionner()
    {
        ExerciseSelection s = new ExerciseSelection();
        s.enter();
        s.toggle("Curl");
        s.selectAllOrNone(day.getExercises());
        assertEquals(3, s.count());
        s.selectAllOrNone(day.getExercises());
        assertEquals(0, s.count());
    }

    @Test
    public void sortieDuMode_videLaSelection()
    {
        ExerciseSelection s = new ExerciseSelection();
        s.enter();
        s.toggle("Curl");
        s.exit();
        assertFalse(s.isActive());
        assertFalse(s.isSelected("Curl"));
        s.enter();
        assertEquals(0, s.count());
    }

    @Test
    public void repli_parDragResteApresLeGeste()
    {
        ExerciseSelection s = new ExerciseSelection();
        assertFalse(s.isCollapsed("Squat"));
        s.collapseAll(day.getExercises());
        assertTrue(s.isCollapsed("Squat"));
        assertTrue(s.isCollapsed("Dips"));
        s.toggleCollapsed("Squat");
        assertFalse("rouvert a la main", s.isCollapsed("Squat"));
    }

    @Test
    public void modeSelection_replieTout()
    {
        ExerciseSelection s = new ExerciseSelection();
        s.enter();
        assertTrue(s.isCollapsed("Curl"));
        s.exit();
        assertFalse(s.isCollapsed("Curl"));
    }
}
