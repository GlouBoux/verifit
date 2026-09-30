package com.example.verifit;

import static com.example.verifit.TestData.*;
import static org.junit.Assert.*;

import com.example.verifit.model.WorkoutDay;
import com.example.verifit.model.WorkoutSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.TreeMap;

import org.junit.Test;

// PR par nombre de reps (DataStorage.calculateRepRangeHistory / getRepRangePRKeys) :
// source unique du trophee et du tag [PR] du Share.
public class RepRangePRTest
{
    private static final String[][] KNOWN = { {"Squat", "Legs"} };

    private DataStorage history()
    {
        return storage(KNOWN,
                day(set("2026-09-01", "Squat", "Legs", 5, 100)),
                day(set("2026-09-08", "Squat", "Legs", 3, 105), set("2026-09-08", "Squat", "Legs", 8, 90)),
                day(set("2026-09-15", "Squat", "Legs", 5, 100), set("2026-09-15", "Squat", "Legs", 0, 200)));
    }

    @Test
    public void clesPR_seulementLesRecordsReels()
    {
        HashSet<String> expected = new HashSet<String>(Arrays.asList(
                "2026-09-01#5#100.0", "2026-09-08#3#105.0", "2026-09-08#8#90.0"));
        assertEquals(expected, history().getRepRangePRKeys("Squat"));
    }

    @Test
    public void transitivite_unRecordA5RepsVautPour3Reps()
    {
        TreeMap<Integer, ArrayList<RepRangePREvent>> h = history().calculateRepRangeHistory("Squat");

        // Case 3 : 100 kg deduit du 5 reps, puis 105 kg reel.
        assertEquals(2, h.get(3).size());
        assertEquals(100.0, h.get(3).get(0).getWeight(), 0.0);
        assertTrue(h.get(3).get(0).isDeduced());
        assertEquals(105.0, h.get(3).get(1).getWeight(), 0.0);
        assertFalse(h.get(3).get(1).isDeduced());

        // Case 4 : seul le 5 reps a 100 kg la couvre (le 3 reps a 105 kg non).
        assertEquals(1, h.get(4).size());
        assertEquals(100.0, h.get(4).get(0).getWeight(), 0.0);

        // Case 6 a 8 : ouvertes par le 8 reps a 90 kg.
        assertEquals(90.0, h.get(6).get(0).getWeight(), 0.0);
        assertFalse(h.get(8).get(0).isDeduced());
    }

    @Test
    public void egaliteEtSerieAZeroRep_neSontPasDesPR()
    {
        TreeMap<Integer, ArrayList<RepRangePREvent>> h = history().calculateRepRangeHistory("Squat");
        assertEquals("100 kg x5 refait le 15/09 : pas un nouveau record", 1, h.get(5).size());
        assertFalse(h.containsKey(0));
    }

    @Test
    public void unRecordA5RepsBatUn3RepsPlusLegerFaitApres()
    {
        DataStorage ds = storage(KNOWN,
                day(set("2026-09-01", "Squat", "Legs", 5, 100)),
                day(set("2026-09-08", "Squat", "Legs", 3, 95)));
        assertFalse(ds.getRepRangePRKeys("Squat").contains("2026-09-08#3#95.0"));
    }

    @Test
    public void ordreChronologique_quelQueSoitLOrdreDeLaListe()
    {
        DataStorage ds = history();
        HashSet<String> expected = ds.getRepRangePRKeys("Squat");
        java.util.Collections.reverse(ds.workoutDays);
        assertEquals(expected, ds.getRepRangePRKeys("Squat"));
    }

    @Test
    public void trophee_apresRechargement_lesSeriesAfficheesSontReconnues()
    {
        DataStorage ds = history();
        ds.workoutDays = reload(ds.workoutDays);

        // Le badge lit les series de Exercises[].Sets (WorkoutSetAdapter) : leur cle doit
        // etre dans les PR calcules depuis Sets.
        WorkoutDay firstDay = ds.workoutDays.get(0);
        WorkoutSet shown = firstDay.getExercises().get(0).getSets().get(0);
        HashSet<String> keys = ds.getRepRangePRKeys("Squat");
        assertTrue(keys.contains(DataStorage.repRangePRKey(shown.getDate(), (int) Math.round(shown.getReps()), shown.getWeight())));
    }

    @Test
    public void cle_format()
    {
        assertEquals("2026-09-01#5#100.0", DataStorage.repRangePRKey("2026-09-01", 5, 100.0));
    }
}
