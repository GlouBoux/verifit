package com.example.verifit;

import static com.example.verifit.TestData.*;
import static org.junit.Assert.*;

import com.example.verifit.model.Exercise;
import com.example.verifit.model.Goal;
import com.example.verifit.model.SupersetGroup;
import com.example.verifit.model.WorkoutDay;
import com.example.verifit.model.WorkoutSet;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

// Backup JSON complet (BackupManager.toJson / parse) : tout ce que l'app sauvegarde
// doit revenir a l'identique, et un mauvais fichier doit etre refuse sans rien toucher.
public class FullBackupTest
{
    private DataStorage ds;

    @Before
    public void setUp()
    {
        WorkoutSet a = set("2026-09-26", "Jeff Curl 4x25", "Legs", 3, 34.5);
        a.setPlannedReps(3.0);
        a.setPlannedWeight(35.0);
        a.setPlanComment("S1 Ancrage");
        a.setComment("dur");
        a.setTimestamp(1790420000000L);
        a.setCompleted(true);
        WorkoutSet b = set("2026-09-26", "Pull Up", "Back", 8, 0);
        WorkoutSet c = set("2026-09-26", "Dips", "Chest", 10, 5);
        WorkoutDay d = day(a, b, c);
        d.moveExercise(2, 0); // Dips en premier
        d.addToSupersetGroup(new ArrayList<String>(Arrays.asList("Pull Up", "Dips")), "SS", 0xFF123456);
        d.setComment("Bonne seance");
        d.setSessionStartTimestamp(1790419000000L);
        d.setSessionEndTimestamp(1790431000000L);

        ds = storage(new String[][] { {"Jeff Curl 4x25", "Legs"}, {"Pull Up", "Back"}, {"Dips", "Chest"} }, d);
        ds.knownExercises.get(0).setFavorite(true);
        ds.knownExercises.get(0).setNotes("Siege 3");
        ds.goals.add(new Goal(1, "Jeff Curl 4x25", Goal.GoalType.MAX_WEIGHT, 40.0));
    }

    @Test
    public void allerRetour_jourComplet()
    {
        BackupManager.FullBackup fb = BackupManager.parse(BackupManager.toJson(ds));
        assertEquals(3, fb.countSets());
        WorkoutDay d = fb.getWorkoutDays().get(0);

        assertEquals("2026-09-26", d.getDate());
        assertEquals("Bonne seance", d.getComment());
        assertEquals(Long.valueOf(1790419000000L), d.getSessionStartTimestamp());
        assertEquals(Long.valueOf(1790431000000L), d.getSessionEndTimestamp());
        assertEquals(Arrays.asList("Dips", "Jeff Curl 4x25", "Pull Up"), d.getExerciseOrder());

        SupersetGroup g = d.getSupersetGroups().get(0);
        SupersetGroup original = ds.workoutDays.get(0).getSupersetGroups().get(0);
        assertEquals(original.getId(), g.getId());
        assertEquals("SS", g.getName());
        assertEquals(0xFF123456, g.getColor());
        assertEquals(Arrays.asList("Pull Up", "Dips"), g.getExerciseNames());
    }

    @Test
    public void allerRetour_serie()
    {
        WorkoutSet s = BackupManager.parse(BackupManager.toJson(ds)).getWorkoutDays().get(0).getSets().get(0);
        assertEquals("Jeff Curl 4x25", s.getExerciseName());
        assertEquals("Legs", s.getCategory());
        assertEquals(3.0, s.getReps(), 0.0);
        assertEquals(34.5, s.getWeight(), 0.0);
        assertEquals(3.0, s.getPlannedReps(), 0.0);
        assertEquals(35.0, s.getPlannedWeight(), 0.0);
        assertEquals("S1 Ancrage", s.getPlanComment());
        assertEquals("dur", s.getComment());
        assertEquals(Long.valueOf(1790420000000L), s.getTimestamp());
        assertTrue(s.isCompleted());
    }

    @Test
    public void allerRetour_exercicesEtObjectifs()
    {
        BackupManager.FullBackup fb = BackupManager.parse(BackupManager.toJson(ds));
        Exercise e = fb.getKnownExercises().get(0);
        assertEquals("Jeff Curl 4x25", e.getName());
        assertEquals("Legs", e.getBodyPart());
        assertTrue(e.getFavorite());
        assertEquals("Siege 3", e.getNotes());
        assertEquals(3, fb.getKnownExercises().size());

        Goal goal = fb.getGoals().get(0);
        assertEquals(1, goal.getId());
        assertEquals("Jeff Curl 4x25", goal.getExerciseName());
        assertEquals(Goal.GoalType.MAX_WEIGHT, goal.getType());
        assertEquals(40.0, goal.getTargetValue(), 0.0);
    }

    @Test
    public void fichierAvecBOM_accepte()
    {
        assertEquals(3, BackupManager.parse("﻿" + BackupManager.toJson(ds)).countSets());
    }

    private static void assertRefused(String json, String expectedFragment)
    {
        try
        {
            BackupManager.parse(json);
            fail("fichier accepte a tort");
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage(), e.getMessage().contains(expectedFragment));
        }
    }

    @Test
    public void refus_exportCoaching()
    {
        assertRefused(ds.buildCoachingExportJson("2026-09-30T10:00:00"), "pas un backup complet");
    }

    @Test
    public void refus_csv()
    {
        assertRefused(ds.buildCsvBackup(), "pas du JSON");
    }

    @Test
    public void refus_versionFuture()
    {
        String json = BackupManager.toJson(ds).replace("\"schemaVersion\":1", "\"schemaVersion\":2");
        assertRefused(json, "version plus recente");
    }

    @Test
    public void refus_serieIncomplete()
    {
        ds.workoutDays.get(0).getSets().get(1).setWeight(null);
        assertRefused(BackupManager.toJson(ds), "incompl");
    }
}
