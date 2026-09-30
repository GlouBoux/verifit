package com.example.verifit;

import static com.example.verifit.TestData.*;
import static org.junit.Assert.*;

import com.example.verifit.model.WorkoutSet;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

// Backup CSV : ecriture (DataStorage.buildCsvBackup) et relecture validee avant tout
// remplacement (CSVFile + DataStorage.parseCsvSets).
public class CsvBackupTest
{
    private static final String HEADER = "Date,Exercise,Category,Weight (kg),Reps,Comment,Is Completed,Plan Comment";

    private static List readCsv(String text)
    {
        // Meme encodage (celui par defaut) a l'ecriture (writeFile) et a la lecture (CSVFile).
        return new CSVFile(new ByteArrayInputStream(text.getBytes())).read();
    }

    private static List rows(String[]... rows)
    {
        List list = new ArrayList();
        list.add(HEADER.split(","));
        list.addAll(Arrays.asList(rows));
        return list;
    }

    @Test
    public void allerRetour_tousLesChamps()
    {
        WorkoutSet done = set("2026-09-26", "Jeff Curl 4x25", "Legs", 3, 34.5);
        done.setCompleted(true);
        done.setComment("dur, tres dur\nligne 2");
        done.setPlanComment("S1 Ancrage, filet 2 reps");
        WorkoutSet todo = set("2026-09-26", "Pull Up", "Back", 8, 0);
        DataStorage ds = storage(new String[][] {}, day(done, todo));

        String csv = ds.buildCsvBackup();
        assertTrue(csv.startsWith(HEADER + "\n"));

        ArrayList<WorkoutSet> parsed = DataStorage.parseCsvSets(readCsv(csv));
        assertEquals(2, parsed.size());
        WorkoutSet p = parsed.get(0);
        assertEquals("2026-09-26", p.getDate());
        assertEquals("Jeff Curl 4x25", p.getExerciseName());
        assertEquals("Legs", p.getCategory());
        assertEquals(3.0, p.getReps(), 0.0);
        assertEquals(34.5, p.getWeight(), 0.0);
        assertTrue(p.isCompleted());
        // Virgules et retours a la ligne remplaces pour ne pas decaler les colonnes.
        assertEquals("dur; tres dur / ligne 2", p.getComment());
        assertEquals("S1 Ancrage; filet 2 reps", p.getPlanComment());
        assertFalse(parsed.get(1).isCompleted());
    }

    @Test
    public void colonnes_poidsEn4eRepsEn5e()
    {
        ArrayList<WorkoutSet> parsed = DataStorage.parseCsvSets(rows(new String[] {"2026-01-01", "Squat", "Legs", "100.0", "5.0"}));
        assertEquals(100.0, parsed.get(0).getWeight(), 0.0);
        assertEquals(5.0, parsed.get(0).getReps(), 0.0);
    }

    @Test
    public void ancienCsv5Colonnes_valeursParDefaut()
    {
        WorkoutSet p = DataStorage.parseCsvSets(rows(new String[] {"2026-01-01", "Squat", "Legs", "100.0", "5.0"})).get(0);
        assertEquals("", p.getComment());
        assertFalse(p.isCompleted());
        assertEquals("", p.getPlanComment());
    }

    @Test
    public void ligneVide_ignoree()
    {
        ArrayList<WorkoutSet> parsed = DataStorage.parseCsvSets(rows(
                new String[] {""},
                new String[] {"2026-01-01", "Squat", "Legs", "100.0", "5.0"}));
        assertEquals(1, parsed.size());
    }

    @Test
    public void ligneTronquee_refuseeAvecSonNumero()
    {
        try
        {
            DataStorage.parseCsvSets(rows(
                    new String[] {"2026-01-01", "Squat", "Legs", "100.0", "5.0"},
                    new String[] {"2026-01-02", "Squat", "Legs"}));
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage(), e.getMessage().startsWith("ligne 3 "));
        }
    }

    @Test
    public void poidsNonNumerique_refuseAvecSonNumero()
    {
        try
        {
            DataStorage.parseCsvSets(rows(new String[] {"2026-01-01", "Squat", "Legs", "abc", "5.0"}));
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage(), e.getMessage().startsWith("ligne 2 "));
        }
    }

    @Test
    public void enTeteSeul_aucuneSerie()
    {
        assertTrue(DataStorage.parseCsvSets(rows()).isEmpty());
    }

    // BUG CONNU (repere pendant C.1, 29/09/2026) : un nom d'exercice avec une virgule
    // (cas reel : "Pendulum Squat, Secu Low, 50°, Pieds Centraux") decale les colonnes,
    // et le backup CSV ecrit par l'app ne peut plus etre reimporte.
    @Ignore("Bug connu : nom d'exercice avec virgule (voir verifit-lot-C-handoff.md)")
    @Test
    public void nomAvecVirgule_allerRetour()
    {
        DataStorage ds = storage(new String[][] {}, day(set("2026-09-26", "Pendulum Squat, Secu Low", "Legs", 4, 17)));
        ArrayList<WorkoutSet> parsed = DataStorage.parseCsvSets(readCsv(ds.buildCsvBackup()));
        assertEquals("Pendulum Squat, Secu Low", parsed.get(0).getExerciseName());
    }
}
