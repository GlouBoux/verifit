package com.example.verifit;

import static com.example.verifit.TestData.*;
import static org.junit.Assert.*;

import com.example.verifit.model.WorkoutDay;
import com.example.verifit.model.WorkoutExercise;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

// Records globaux par exercice (DataStorage.calculatePersonalRecords), affiches par
// l'ecran Personal Records et les badges du resume du jour.
public class PersonalRecordsTest
{
    private DataStorage ds;
    private WorkoutDay d1, d2, d3;

    @Before
    public void setUp()
    {
        d1 = day(set("2026-09-01", "Squat", "Legs", 5, 100), set("2026-09-01", "Squat", "Legs", 1, 110));
        d2 = day(set("2026-09-08", "Squat", "Legs", 3, 105), set("2026-09-08", "Squat", "Legs", 10, 80));
        d3 = day(set("2026-09-15", "Squat", "Legs", 5, 60));
        ds = storage(new String[][] { {"Squat", "Legs"}, {"Bench", "Chest"} }, d1, d2, d3);
        ds.calculatePersonalRecords();
    }

    private static WorkoutExercise ex(WorkoutDay day)
    {
        return day.getExercises().get(0);
    }

    @Test
    public void volume()
    {
        // 09-01 : 500 + 110 = 610 ; 09-08 : 315 + 800 = 1115 ; 09-15 : 300.
        assertEquals(1115.0, ds.getVolumePRs().get("Squat"), 0.001);
        assertTrue(ex(d1).isVolumePR());
        assertTrue(ex(d2).isVolumePR());
        assertFalse(ex(d3).isVolumePR());
    }

    @Test
    public void unRMReel_seulementLesSeriesAUneRep()
    {
        assertEquals(110.0, ds.getActualOneRepMaxPRs().get("Squat"), 0.001);
        assertTrue(ex(d1).isActualOneRepMaxPR());
        assertFalse(ex(d2).isActualOneRepMaxPR());
    }

    @Test
    public void unRMEstime_formuleEpley()
    {
        // 100 x (1 + 5/30) = 116,67 bat 110 x (1 + 1/30) et 105 x (1 + 3/30) = 115,5.
        assertEquals(100 * (1 + 5 / 30.0), ds.getEstimatedOneRMPRs().get("Squat"), 0.001);
        assertTrue(ex(d1).isEstimatedOneRepMaxPR());
        assertFalse(ex(d2).isEstimatedOneRepMaxPR());
    }

    @Test
    public void repsMaxEtPoidsMax_avecLaSerieCorrespondante()
    {
        assertEquals(10.0, ds.getMaxRepsPRs().get("Squat"), 0.0);
        assertEquals(80.0, ds.getMaxRepsSetPRs().get("Squat").getWeight(), 0.0);
        assertEquals(110.0, ds.getMaxWeightPRs().get("Squat"), 0.0);
        assertEquals(1.0, ds.getMaxWeightSetPRs().get("Squat").getReps(), 0.0);
        assertTrue(ex(d2).isMaxRepsPR());
        assertFalse(ex(d3).isMaxWeightPR());
    }

    @Test
    public void plusDurQueLaDerniereFois()
    {
        assertTrue(ex(d1).isHTLT());
        assertTrue(ex(d2).isHTLT());
        assertFalse(ex(d3).isHTLT());
    }

    @Test
    public void exerciceJamaisFait_recordsAZero()
    {
        assertEquals(0.0, ds.getVolumePRs().get("Bench"), 0.0);
        assertEquals(0.0, ds.getSetVolumePRs().get("Bench").getReps(), 0.0);
        assertNull(ds.getMaxWeightSetPRs().get("Bench"));
    }

    // BUG CONNU (repere pendant C.1, 29/09/2026), a corriger apres decision de Romain :
    // le "Max set volume" combine les reps max et le poids max du jour, qui ne viennent
    // pas de la meme serie. Ici la meilleure serie est 60 kg x 12 (720), mais l'app
    // retient 12 reps x 100 kg (1 200), une serie qui n'a jamais existe.
    @Ignore("Bug connu : Max set volume melange deux series (voir verifit-lot-C-handoff.md)")
    @Test
    public void maxSetVolume_estUneVraieSerie()
    {
        DataStorage s = storage(new String[][] { {"Squat", "Legs"} },
                day(set("2026-09-01", "Squat", "Legs", 5, 100), set("2026-09-01", "Squat", "Legs", 12, 60)));
        s.calculatePersonalRecords();
        assertEquals(12.0, s.getSetVolumePRs().get("Squat").getReps(), 0.0);
        assertEquals(60.0, s.getSetVolumePRs().get("Squat").getWeight(), 0.0);
    }
}
