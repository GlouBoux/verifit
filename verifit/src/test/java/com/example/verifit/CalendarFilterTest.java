package com.example.verifit;

import static com.example.verifit.TestData.*;
import static org.junit.Assert.*;

import com.example.verifit.model.CalendarFilter;
import com.example.verifit.model.CalendarFilter.Comparison;
import com.example.verifit.model.WorkoutDay;

import java.util.Arrays;
import java.util.HashSet;

import org.junit.Test;

// Filtre du calendrier (model/CalendarFilter) : categories (OU / ET), exercice, seuils.
public class CalendarFilterTest
{
    private final WorkoutDay legsChest = day(
            set("2026-09-01", "Squat", "Legs", 5, 100),
            set("2026-09-01", "Bench", "Chest", 8, 80));
    private final WorkoutDay biceps = day(set("2026-09-02", "Curl", "Biceps", 10, 20));

    private static CalendarFilter categories(boolean matchAll, String... names)
    {
        CalendarFilter f = new CalendarFilter();
        f.setCategories(new HashSet<String>(Arrays.asList(names)));
        f.setMatchAll(matchAll);
        return f;
    }

    @Test
    public void sansFiltre_toutJourAvecSeries()
    {
        CalendarFilter f = new CalendarFilter();
        assertFalse(f.isActive());
        assertTrue(f.matches(legsChest));
        assertFalse(f.matches(new WorkoutDay()));
    }

    @Test
    public void categories_unDesDeux()
    {
        CalendarFilter f = categories(false, "Legs", "Biceps");
        assertTrue(f.isActive());
        assertTrue(f.matches(legsChest));
        assertTrue(f.matches(biceps));
        assertFalse(categories(false, "Back").matches(legsChest));
    }

    @Test
    public void categories_toutes()
    {
        assertTrue(categories(true, "Legs", "Chest").matches(legsChest));
        assertFalse(categories(true, "Legs", "Biceps").matches(legsChest));
        assertFalse(categories(true, "Legs", "Biceps").matches(biceps));
    }

    @Test
    public void exercice_seuilsSurLaMemeSerie()
    {
        CalendarFilter f = new CalendarFilter();
        f.setExerciseName("Squat");
        f.setWeightComparison(Comparison.AT_LEAST);
        f.setWeightThreshold(100.0);
        assertTrue(f.matches(legsChest));

        f.setWeightComparison(Comparison.MORE_THAN);
        assertFalse(f.matches(legsChest));

        f.setWeightComparison(Comparison.AT_LEAST);
        f.setRepsComparison(Comparison.EXACTLY);
        f.setRepsThreshold(8.0);
        assertFalse("les 8 reps sont au Bench, pas au Squat", f.matches(legsChest));
    }

    @Test
    public void categorieEtExercice_lesDeuxDoiventCorrespondre()
    {
        CalendarFilter f = categories(false, "Chest");
        f.setExerciseName("Squat");
        assertTrue(f.matches(legsChest));
        f.setCategories(new HashSet<String>(Arrays.asList("Biceps")));
        assertFalse(f.matches(legsChest));
    }

    @Test
    public void comparaisons()
    {
        assertTrue(Comparison.AT_MOST.matches(5, 5));
        assertFalse(Comparison.LESS_THAN.matches(5, 5));
        assertTrue(Comparison.EXACTLY.matches(5, 5));
    }
}
