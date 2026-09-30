package com.example.verifit;

import static com.example.verifit.TestData.*;
import static org.junit.Assert.*;

import com.example.verifit.model.WorkoutDay;
import com.example.verifit.model.WorkoutSet;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.TimeZone;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

// Texte du "Share workout" (format Discord repris de FitNotes).
public class WorkoutReportGeneratorTest
{
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private TimeZone previous;

    @Before
    public void fuseauParis()
    {
        previous = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(PARIS));
    }

    @After
    public void restaurer()
    {
        TimeZone.setDefault(previous);
    }

    private static long at(int hour, int minute)
    {
        return ZonedDateTime.of(2026, 9, 4, hour, minute, 0, 0, PARIS).toInstant().toEpochMilli();
    }

    private static WorkoutDay sampleDay()
    {
        WorkoutSet fail = set("2026-09-04", "Assisted HSPU FP", "Shoulders", 2, 50);
        fail.setPlanComment("S1");
        fail.setComment("Echec d'un 7");
        WorkoutSet curl = set("2026-09-04", "Curl", "Biceps", 8, 20);
        curl.setComment("a\nb");
        WorkoutDay d = day(
                set("2026-09-04", "Assisted HSPU FP", "Shoulders", 4, 40),
                set("2026-09-04", "Assisted HSPU FP", "Shoulders", 3, 38),
                fail,
                curl);
        d.setComment("Bonne séance");
        d.setSessionStartTimestamp(at(17, 43));
        d.setSessionEndTimestamp(at(22, 15));
        return d;
    }

    private static final String EXPECTED_BODY =
            "** Assisted HSPU FP **\n"
            + "- 40.0 kgs x 4 reps [PR]\n"
            + "- 38.0 kgs x 3 reps\n"
            + "- 50.0 kgs x 2 reps [PR. S1 — Note : Echec d'un 7]\n"
            + "** Curl **\n"
            + "- 20.0 kgs x 8 reps [PR. a / b]";

    @Test
    public void rapportComplet()
    {
        WorkoutDay d = sampleDay();
        DataStorage ds = storage(new String[][] {}, d);
        String expected = "Verifit Workout - Vendredi 4 septembre 2026\n"
                + "Bonne séance\n"
                + "Time: 17:43 – 22:15 (4h 32m)\n"
                + EXPECTED_BODY;
        assertEquals(expected, WorkoutReportGenerator.generateReport("Verifit", d, ds, 0L));
    }

    @Test
    public void chronoEnCours_finAMaintenant()
    {
        WorkoutDay d = sampleDay();
        d.setSessionEndTimestamp(null);
        String report = WorkoutReportGenerator.generateReport("Verifit", d, storage(new String[][] {}, d), at(18, 48));
        assertTrue(report, report.contains("\nTime: 17:43 – 18:48 (1h 5m)\n"));
    }

    @Test
    public void sansChronoNiCommentaire_pasDeLigneTime()
    {
        WorkoutDay d = sampleDay();
        d.setSessionStartTimestamp(null);
        d.setComment("");
        String report = WorkoutReportGenerator.generateReport("Verifit", d, storage(new String[][] {}, d), 0L);
        assertEquals("Verifit Workout - Vendredi 4 septembre 2026\n" + EXPECTED_BODY, report);
    }

    @Test
    public void tagPR_toujoursPresentApresRechargement()
    {
        DataStorage ds = storage(new String[][] {}, sampleDay());
        ds.workoutDays = reload(ds.workoutDays);
        String report = WorkoutReportGenerator.generateReport("Verifit", ds.workoutDays.get(0), ds, 0L);
        assertTrue(report, report.endsWith(EXPECTED_BODY));
    }

    @Test
    public void dateEnTete()
    {
        assertEquals("Vendredi 4 septembre 2026", WorkoutReportGenerator.formatDateHeader("2026-09-04"));
        assertEquals("pas-une-date", WorkoutReportGenerator.formatDateHeader("pas-une-date"));
    }
}
