package com.example.verifit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.verifit.model.WorkoutDay;
import com.example.verifit.model.WorkoutSet;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

// Lot D, etape D9 (05/10/2026) : re-export du fichier Coaching apres correction d'une
// seance dont le chrono est arrete.
public class CoachingReexportTrackerTest
{
    private CoachingReexportTracker tracker;
    private WorkoutDay stopped;
    private WorkoutSet squat;
    private List<WorkoutDay> days;

    private static WorkoutDay dayWith(String date, double reps, double weight)
    {
        WorkoutDay day = new WorkoutDay();
        day.addSet(new WorkoutSet(date, "Squat", "Legs", reps, weight));
        day.addSet(new WorkoutSet(date, "Squat", "Legs", reps, weight));
        return day;
    }

    @Before
    public void setUp()
    {
        tracker = new CoachingReexportTracker();
        stopped = dayWith("2026-10-05", 5.0, 100.0);
        stopped.toggleSession(1000L); // demarre
        stopped.toggleSession(2000L); // arrete
        squat = stopped.getSets().get(0);
        days = new ArrayList<WorkoutDay>();
        days.add(stopped);
    }

    @Test
    public void jourArreteJamaisExporteDeclenche()
    {
        assertTrue(tracker.stoppedDayChanged(days));
    }

    @Test
    public void rienNeChangeApresExport()
    {
        tracker.remember(days);
        assertFalse(tracker.stoppedDayChanged(days));
    }

    @Test
    public void correctionDesRepsDeclenche()
    {
        tracker.remember(days);
        squat.setReps(4.0);
        assertTrue(tracker.stoppedDayChanged(days));
    }

    @Test
    public void correctionDuPoidsDeclenche()
    {
        tracker.remember(days);
        squat.setWeight(102.5);
        assertTrue(tracker.stoppedDayChanged(days));
    }

    @Test
    public void notesEtCaseFaiteDeclenchent()
    {
        tracker.remember(days);
        squat.setComment("difficile");
        assertTrue(tracker.stoppedDayChanged(days));

        tracker.remember(days);
        squat.setCompleted(true);
        assertTrue(tracker.stoppedDayChanged(days));
    }

    @Test
    public void serieAjouteeOuSupprimeeDeclenche()
    {
        tracker.remember(days);
        stopped.addSet(new WorkoutSet("2026-10-05", "Squat", "Legs", 5.0, 100.0));
        assertTrue(tracker.stoppedDayChanged(days));

        tracker.remember(days);
        List<WorkoutSet> toRemove = new ArrayList<WorkoutSet>();
        toRemove.add(stopped.getSets().get(2));
        stopped.removeSets(toRemove);
        assertTrue(tracker.stoppedDayChanged(days));
    }

    @Test
    public void unExportRemetLeSuiviAZero()
    {
        tracker.remember(days);
        squat.setReps(4.0);
        assertTrue(tracker.stoppedDayChanged(days));

        tracker.remember(days); // l'export vient d'etre ecrit
        assertFalse(tracker.stoppedDayChanged(days));
    }

    @Test
    public void retourAuxValeursExporteesNeDeclenchePas()
    {
        tracker.remember(days);
        squat.setReps(4.0);
        squat.setReps(5.0);
        assertFalse(tracker.stoppedDayChanged(days));
    }

    @Test
    public void seanceEnCoursNeDeclenchePas()
    {
        WorkoutDay running = dayWith("2026-10-06", 8.0, 60.0);
        running.toggleSession(1000L); // demarre, jamais arrete
        days.add(running);
        tracker.remember(days);

        running.getSets().get(0).setReps(10.0);
        assertFalse(tracker.stoppedDayChanged(days));
    }

    @Test
    public void jourSansChronoNeDeclenchePas()
    {
        WorkoutDay noTimer = dayWith("2026-10-07", 8.0, 60.0);
        days.add(noTimer);
        tracker.remember(days);

        noTimer.getSets().get(0).setWeight(65.0);
        assertFalse(tracker.stoppedDayChanged(days));
    }

    @Test
    public void seanceReprisePuisModifieeNeDeclenchePasAvantLeNouvelArret()
    {
        tracker.remember(days);
        stopped.toggleSession(3000L); // reprise : le chrono tourne a nouveau
        squat.setReps(4.0);
        assertFalse(tracker.stoppedDayChanged(days));

        stopped.toggleSession(4000L); // nouvel arret
        assertTrue(tracker.stoppedDayChanged(days));
    }

    @Test
    public void jourSupprimeNeDeclenchePasSeul()
    {
        tracker.remember(days);
        days.clear();
        assertFalse(tracker.stoppedDayChanged(days));
    }

    @Test
    public void unJourModifieParmiPlusieursDeclenche()
    {
        WorkoutDay older = dayWith("2026-10-01", 5.0, 90.0);
        older.toggleSession(1000L);
        older.toggleSession(2000L);
        days.add(older);
        tracker.remember(days);
        assertFalse(tracker.stoppedDayChanged(days));

        older.getSets().get(1).setWeight(92.5); // correction d'une ancienne seance
        assertTrue(tracker.stoppedDayChanged(days));
    }
}
