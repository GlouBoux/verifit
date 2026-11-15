package com.example.verifit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

/**
 * Lot M0.3 : mise en forme, noms et rotation des journaux de plantage (CrashReport).
 * Fuseau fixe (UTC) pour que les dates soient les memes sur tous les postes.
 */
public class CrashReportTest
{
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    // 2026-11-14 12:06:51.123 UTC
    private static final long T = 1794658011123L;

    private static String sampleName(int day)
    {
        return CrashReport.FILE_PREFIX + "2026-11-" + (day < 10 ? "0" : "") + day + "_10-00-00-000_crash" + CrashReport.FILE_SUFFIX;
    }

    @Test
    public void fileNameContientDateEtOrigine()
    {
        assertEquals("fitengine_crash_2026-11-14_12-06-51-123_crash.txt", CrashReport.fileName(T, UTC, "crash"));
    }

    @Test
    public void fileNameNettoieLOrigine()
    {
        assertEquals("fitengine_crash_2026-11-14_12-06-51-123_python_erreur_a_b.txt",
                CrashReport.fileName(T, UTC, "python erreur/a.b"));
        String name = CrashReport.fileName(T, UTC, "../../etc/passwd");
        assertFalse(name.contains("/"));
        assertFalse(name.contains(".."));
    }

    @Test
    public void fileNameOrigineVideOuNulle()
    {
        assertTrue(CrashReport.fileName(T, UTC, null).endsWith("_inconnu.txt"));
        assertTrue(CrashReport.fileName(T, UTC, "").endsWith("_inconnu.txt"));
    }

    @Test
    public void fileNameLimiteLaLongueurDeLOrigine()
    {
        String name = CrashReport.fileName(T, UTC, "abcdefghijklmnopqrstuvwxyz0123456789");
        assertTrue(name.endsWith("_abcdefghijklmnopqrstuvwx.txt"));
    }

    @Test
    public void lOrdreAlphabetiqueEstChronologique()
    {
        String older = CrashReport.fileName(T, UTC, "python");
        String newerSameSecond = CrashReport.fileName(T + 1, UTC, "crash");
        String nextYear = CrashReport.fileName(T + 400L * 24 * 3600 * 1000, UTC, "a");
        assertTrue(older.compareTo(newerSameSecond) < 0);
        assertTrue(newerSameSecond.compareTo(nextYear) < 0);
    }

    @Test
    public void formatContientTousLesChamps()
    {
        Throwable cause = new IllegalStateException("cause racine");
        Throwable t = new RuntimeException("boum", cause);
        String text = CrashReport.format(T, UTC, "crash", "main", "1.0.14", 1790688527453L,
                "Google Pixel 8", "14", 34, t);

        assertTrue(text.contains("journal de plantage"));
        assertTrue(text.contains("Date : 2026-11-14 12:06:51.123 +0000"));
        assertTrue(text.contains("Origine : crash"));
        assertTrue(text.contains("Fil d'exécution : main"));
        assertTrue(text.contains("Version : 1.0.14 (build du "));
        assertTrue(text.contains("BUILD_TIMESTAMP 1790688527453"));
        assertTrue(text.contains("Appareil : Google Pixel 8"));
        assertTrue(text.contains("Android : 14 (API 34)"));
        assertTrue(text.contains("java.lang.RuntimeException: boum"));
    }

    @Test
    public void formatInclutLesCauses()
    {
        Throwable t = new RuntimeException("externe", new IllegalStateException("cause racine"));
        String text = CrashReport.format(T, UTC, "crash", "main", "v", 0L, "m", "r", 1, t);
        assertTrue(text.contains("Caused by: java.lang.IllegalStateException: cause racine"));
    }

    @Test
    public void formatSansException()
    {
        String text = CrashReport.format(T, UTC, null, null, "v", 0L, "m", "r", 1, null);
        assertTrue(text.contains("Origine : inconnu"));
        assertTrue(text.contains("Fil d'exécution : inconnu"));
        assertTrue(text.contains("(aucune exception fournie)"));
    }

    @Test
    public void formatTronqueUneTraceEnorme()
    {
        Throwable t = new RuntimeException(new String(new char[CrashReport.MAX_TRACE_CHARS + 5000]).replace('\0', 'x'));
        String trace = CrashReport.trace(t);
        assertTrue(trace.length() < CrashReport.MAX_TRACE_CHARS + 200);
        assertTrue(trace.contains("trace tronquée"));
    }

    @Test
    public void rotationGardeLesPlusRecents()
    {
        List<String> names = new ArrayList<String>();
        for (int day = 1; day <= 25; day++)
        {
            names.add(sampleName(day));
        }
        Collections.shuffle(names, new java.util.Random(7));

        List<String> toDelete = CrashReport.namesToDelete(names, CrashReport.KEEP);

        assertEquals(5, toDelete.size());
        for (int day = 1; day <= 5; day++)
        {
            assertTrue("jour " + day + " doit etre supprime", toDelete.contains(sampleName(day)));
        }
        assertFalse(toDelete.contains(sampleName(6)));
        assertFalse(toDelete.contains(sampleName(25)));
    }

    @Test
    public void rotationNeSupprimeRienSousLaLimite()
    {
        List<String> names = Arrays.asList(sampleName(1), sampleName(2), sampleName(3));
        assertTrue(CrashReport.namesToDelete(names, CrashReport.KEEP).isEmpty());
        // Exactement a la limite : rien non plus.
        List<String> exact = new ArrayList<String>();
        for (int day = 1; day <= CrashReport.KEEP; day++)
        {
            exact.add(sampleName(day));
        }
        assertTrue(CrashReport.namesToDelete(exact, CrashReport.KEEP).isEmpty());
    }

    @Test
    public void rotationIgnoreLesFichiersQuiNeSontPasDesJournaux()
    {
        List<String> names = new ArrayList<String>();
        names.add("notes.txt");
        names.add(sampleName(1) + ".tmp");
        names.add("fitengine_backup_complet_2020-01-01.json");
        names.add(sampleName(2));
        names.add(sampleName(3));

        List<String> toDelete = CrashReport.namesToDelete(names, 1);

        assertEquals(Arrays.asList(sampleName(2)), toDelete);
    }

    @Test
    public void rotationAvecLimiteNulleOuNegative()
    {
        List<String> names = Arrays.asList(sampleName(1), sampleName(2));
        assertEquals(2, CrashReport.namesToDelete(names, 0).size());
        assertEquals(2, CrashReport.namesToDelete(names, -3).size());
    }

    @Test
    public void rotationListeVide()
    {
        assertTrue(CrashReport.namesToDelete(new ArrayList<String>(), CrashReport.KEEP).isEmpty());
    }
}
