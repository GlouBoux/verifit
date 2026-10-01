package com.example.verifit;

import static com.example.verifit.TestData.*;
import static org.junit.Assert.*;

import com.example.verifit.model.WorkoutDay;

import org.junit.Test;

// Chrono de seance (WorkoutDay.startOrResumeSession / toggleSession / cancelSession),
// partage par l'ecran de saisie, l'ecran du jour et l'Import Session.
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
