package com.example.verifit;

import static com.example.verifit.TestData.*;
import static org.junit.Assert.*;

import com.example.verifit.model.WorkoutSet;

import java.util.Arrays;
import java.util.HashMap;

import org.junit.Test;

// "Échec d'un N RM" du Share workout (FailedRmCalculator, retour Romain 01/10/2026) :
// serie de travail qui ne bat aucun record, N = plus petit nombre de reps pour lequel
// ce poids aurait ete un record (records d'AVANT la serie, avec transitivite).
public class FailedRmCalculatorTest
{
    private static WorkoutSet s(String date, double reps, double weight)
    {
        return set(date, "Curl", "Biceps", reps, weight);
    }

    @Test
    public void exempleDeRomain_27kgX7()
    {
        // Records 27.5@7, 26.0@8, 25.0@9 -> 27 kg x 7 aurait ete un record a 8 reps.
        HashMap<String, Integer> failed = FailedRmCalculator.compute(Arrays.asList(
                s("2026-09-01", 7, 27.5), s("2026-09-02", 8, 26.0), s("2026-09-03", 9, 25.0),
                s("2026-10-01", 7, 27.0)));
        assertEquals(Integer.valueOf(8), failed.get("2026-10-01#7#27.0"));
    }

    @Test
    public void exempleDeRomain_28kgX6()
    {
        // Records 30@6, 27.5@7, 26.0@8, 25.0@9 -> 28 kg bat 27.5 a 7 reps.
        HashMap<String, Integer> failed = FailedRmCalculator.compute(Arrays.asList(
                s("2026-09-01", 6, 30.0), s("2026-09-02", 7, 27.5), s("2026-09-03", 8, 26.0),
                s("2026-09-04", 9, 25.0), s("2026-10-01", 6, 28.0)));
        assertEquals(Integer.valueOf(7), failed.get("2026-10-01#6#28.0"));
    }

    @Test
    public void unRecord_nEstPasUnEchec()
    {
        HashMap<String, Integer> failed = FailedRmCalculator.compute(Arrays.asList(
                s("2026-09-01", 5, 20.0), s("2026-10-01", 5, 22.0)));
        assertTrue(failed.isEmpty());
    }

    @Test
    public void egalite_estUnEchec()
    {
        // 20 kg x 5 refait : pas un record, aurait ete un record a 6 reps.
        HashMap<String, Integer> failed = FailedRmCalculator.compute(Arrays.asList(
                s("2026-09-01", 5, 20.0), s("2026-10-01", 5, 20.0)));
        assertEquals(Integer.valueOf(6), failed.get("2026-10-01#5#20.0"));
    }

    @Test
    public void echauffement_jamaisEnEchec_maisCompteDansLesRecords()
    {
        WorkoutSet warmup = s("2026-10-01", 5, 10.0);
        warmup.setPlanComment("Échauffement");
        WorkoutSet warmupNote = s("2026-10-01", 3, 8.0);
        warmupNote.setComment("echauffement leger");
        HashMap<String, Integer> failed = FailedRmCalculator.compute(Arrays.asList(
                s("2026-09-01", 8, 20.0), warmup, warmupNote));
        assertTrue(failed.isEmpty());
        assertTrue(FailedRmCalculator.isWarmup(warmup));
        assertTrue(FailedRmCalculator.isWarmup(warmupNote));
        assertFalse(FailedRmCalculator.isWarmup(s("2026-10-01", 5, 10.0)));
    }

    @Test
    public void serieAZeroRep_ignoree()
    {
        HashMap<String, Integer> failed = FailedRmCalculator.compute(Arrays.asList(
                s("2026-09-01", 5, 20.0), s("2026-10-01", 0, 10.0)));
        assertTrue(failed.isEmpty());
    }
}
