package com.example.verifit.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

// Declencheur de l'export Coaching automatique (lot D, etape D6) : toggleSession() ne
// renvoie true que lorsqu'il ARRETE une seance en cours.
public class WorkoutDayStopTest
{
    @Test
    public void demarrage_nExportePas()
    {
        WorkoutDay day = new WorkoutDay();

        assertFalse(day.toggleSession(1000L));
        assertEquals(Long.valueOf(1000L), day.getSessionStartTimestamp());
        assertNull(day.getSessionEndTimestamp());
        assertTrue(day.isSessionTimerRunning());
    }

    @Test
    public void arret_exporte()
    {
        WorkoutDay day = new WorkoutDay();
        day.toggleSession(1000L);

        assertTrue(day.toggleSession(5000L));
        assertEquals(Long.valueOf(5000L), day.getSessionEndTimestamp());
        assertFalse(day.isSessionTimerRunning());
    }

    @Test
    public void reprise_nExportePas_puisNouvelArretExporte()
    {
        WorkoutDay day = new WorkoutDay();
        day.toggleSession(1000L);
        day.toggleSession(5000L);

        assertFalse(day.toggleSession(6000L));
        assertNull(day.getSessionEndTimestamp());
        assertTrue(day.isSessionTimerRunning());

        assertTrue(day.toggleSession(9000L));
        assertEquals(Long.valueOf(9000L), day.getSessionEndTimestamp());
    }

    @Test
    public void annulation_remetLeChronoAZero_etLeCycleRecommenceSansExport()
    {
        WorkoutDay day = new WorkoutDay();
        day.toggleSession(1000L);
        day.toggleSession(5000L);

        day.cancelSession();

        assertNull(day.getSessionStartTimestamp());
        assertNull(day.getSessionEndTimestamp());
        // Redemarrage apres annulation : c'est un demarrage, pas un arret.
        assertFalse(day.toggleSession(7000L));
    }
}
