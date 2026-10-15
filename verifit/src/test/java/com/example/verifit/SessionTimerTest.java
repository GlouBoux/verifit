package com.example.verifit;

import static com.example.verifit.TestData.*;
import static org.junit.Assert.*;

import com.example.verifit.model.WorkoutDay;

import org.junit.Test;

// Chrono de seance (WorkoutDay.startOrResumeSession / toggleSession / cancelSession),
// partage par l'ecran de saisie, l'ecran du jour et l'Import Session. Depuis le
// 15/10/2026 : startOrResumeSessionIfToday (import) et startSessionOnFirstLog (premiere
// serie validee d'une seance importee).
public class SessionTimerTest
{
    private static WorkoutDay day()
    {
        return TestData.day(set("2026-10-01", "Squat", "Legs", 5, 100));
    }

    @Test
    public void premiereSerie_demarreSiAutoStartActif()
    {
        WorkoutDay d = day();
        assertTrue(d.startOrResumeSession(true, 1000L));
        assertEquals(Long.valueOf(1000L), d.getSessionStartTimestamp());
        assertTrue(d.isSessionTimerRunning());

        assertFalse("deja en cours : rien a changer", d.startOrResumeSession(true, 2000L));
        assertEquals(Long.valueOf(1000L), d.getSessionStartTimestamp());
    }

    @Test
    public void premiereSerie_neDemarrePasSiAutoStartInactif()
    {
        WorkoutDay d = day();
        assertFalse(d.startOrResumeSession(false, 1000L));
        assertNull(d.getSessionStartTimestamp());
    }

    @Test
    public void serieApresUnStop_reprendToujours()
    {
        WorkoutDay d = day();
        d.setSessionStartTimestamp(1000L);
        d.setSessionEndTimestamp(5000L);
        assertTrue("reprise meme avec Auto Start inactif", d.startOrResumeSession(false, 9000L));
        assertNull(d.getSessionEndTimestamp());
        assertEquals(Long.valueOf(1000L), d.getSessionStartTimestamp());
    }

    // Correctif du 15/10/2026 : seance importee la veille (Auto Start actif).

    @Test
    public void importDuJour_demarreLeChrono()
    {
        WorkoutDay d = day(); // seance du 2026-10-01
        assertTrue(d.startOrResumeSessionIfToday(true, 1000L, "2026-10-01"));
        assertEquals(Long.valueOf(1000L), d.getSessionStartTimestamp());
    }

    @Test
    public void importLaVeille_neDemarrePasLeChrono()
    {
        WorkoutDay d = day();
        assertFalse(d.startOrResumeSessionIfToday(true, 1000L, "2026-09-30"));
        assertNull(d.getSessionStartTimestamp());
        assertFalse(d.startOrResumeSessionIfToday(true, 1000L, null));
        assertNull(d.getSessionStartTimestamp());
    }

    @Test
    public void importLaVeille_neReprendPasUnChronoArrete()
    {
        WorkoutDay d = day();
        d.setSessionStartTimestamp(1000L);
        d.setSessionEndTimestamp(5000L);
        assertFalse(d.startOrResumeSessionIfToday(true, 9000L, "2026-09-30"));
        assertEquals(Long.valueOf(5000L), d.getSessionEndTimestamp());
    }

    @Test
    public void premiereSerieValidee_demarreLeChronoDuJour()
    {
        WorkoutDay d = day();
        assertTrue(d.startSessionOnFirstLog(true, 1000L, "2026-10-01"));
        assertEquals(Long.valueOf(1000L), d.getSessionStartTimestamp());
        assertFalse("deja demarre : rien a changer", d.startSessionOnFirstLog(true, 2000L, "2026-10-01"));
        assertEquals(Long.valueOf(1000L), d.getSessionStartTimestamp());
    }

    @Test
    public void premiereSerieValidee_respecteAutoStart()
    {
        WorkoutDay d = day();
        assertFalse(d.startSessionOnFirstLog(false, 1000L, "2026-10-01"));
        assertNull(d.getSessionStartTimestamp());
    }

    @Test
    public void correctionDUneAncienneSeance_neCreePasDeChrono()
    {
        WorkoutDay d = day();
        assertFalse(d.startSessionOnFirstLog(true, 1000L, "2026-10-15"));
        assertNull(d.getSessionStartTimestamp());
    }

    @Test
    public void correctionApresUnStop_neReprendPas()
    {
        // Regle du re-export automatique (D9) : corriger une seance arretee la laisse
        // arretee.
        WorkoutDay d = day();
        d.setSessionStartTimestamp(1000L);
        d.setSessionEndTimestamp(5000L);
        assertFalse(d.startSessionOnFirstLog(true, 9000L, "2026-10-01"));
        assertEquals(Long.valueOf(5000L), d.getSessionEndTimestamp());
        assertFalse(d.isSessionTimerRunning());
    }

    @Test
    public void cleDeJour_formatAAAAMMJJ()
    {
        String key = WorkoutDay.dateKey(System.currentTimeMillis());
        assertTrue(key, key.matches("\\d{4}-\\d{2}-\\d{2}"));
        assertEquals(new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date(0L)),
                WorkoutDay.dateKey(0L));
    }

    @Test
    public void boutonStartStopResume()
    {
        WorkoutDay d = day();
        d.toggleSession(1000L);
        assertTrue("Start", d.isSessionTimerRunning());
        d.toggleSession(5000L);
        assertEquals("Stop", Long.valueOf(5000L), d.getSessionEndTimestamp());
        d.toggleSession(9000L);
        assertTrue("Resume", d.isSessionTimerRunning());
        assertEquals(Long.valueOf(1000L), d.getSessionStartTimestamp());
    }

    @Test
    public void annulation_effaceLeChronoPasLesSeries()
    {
        WorkoutDay d = day();
        d.toggleSession(1000L);
        d.toggleSession(5000L);
        d.cancelSession();
        assertNull(d.getSessionStartTimestamp());
        assertNull(d.getSessionEndTimestamp());
        assertEquals(1, d.getSets().size());
    }
}
