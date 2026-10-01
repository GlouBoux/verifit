package com.example.verifit.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SetRestTest
{
    private static WorkoutSet set(Long timestamp)
    {
        WorkoutSet s = new WorkoutSet("2026-10-01", "Squat", "Legs", 5.0, 100.0);
        s.setTimestamp(timestamp);
        return s;
    }

    private static final long T0 = 1_000_000_000_000L;

    @Test
    public void restSeconds_ecartNormal()
    {
        assertEquals(Long.valueOf(135), SetRest.restSeconds(set(T0), set(T0 + 135_000L)));
    }

    @Test
    public void restSeconds_ignoreLesMillisecondesResiduelles()
    {
        assertEquals(Long.valueOf(90), SetRest.restSeconds(set(T0), set(T0 + 90_999L)));
    }

    @Test
    public void restSeconds_inconnuSiUnHorodatageManque()
    {
        assertNull(SetRest.restSeconds(set(null), set(T0)));
        assertNull(SetRest.restSeconds(set(T0), set(null)));
        assertNull(SetRest.restSeconds(set(null), set(null)));
    }

    @Test
    public void restSeconds_inconnuSiSerieNulle()
    {
        assertNull(SetRest.restSeconds(null, set(T0)));
        assertNull(SetRest.restSeconds(set(T0), null));
    }

    @Test
    public void restSeconds_inconnuSiOrdreIncoherent()
    {
        assertNull(SetRest.restSeconds(set(T0), set(T0)));
        assertNull(SetRest.restSeconds(set(T0 + 60_000L), set(T0)));
    }

    @Test
    public void restSeconds_limiteDePauseLongue()
    {
        assertEquals(Long.valueOf(1800), SetRest.restSeconds(set(T0), set(T0 + 1800_000L)));
        assertNull(SetRest.restSeconds(set(T0), set(T0 + 1801_000L)));
    }

    @Test
    public void exercice_premiereSerieSansRepos()
    {
        List<WorkoutSet> sets = Arrays.asList(set(T0), set(T0 + 120_000L), set(T0 + 300_000L));
        List<Long> rest = SetRest.restSecondsForExercise(sets);

        assertEquals(3, rest.size());
        assertNull(rest.get(0));
        assertEquals(Long.valueOf(120), rest.get(1));
        assertEquals(Long.valueOf(180), rest.get(2));
    }

    @Test
    public void exercice_serieSansHorodatageNeCasseQueSesVoisines()
    {
        // La serie du milieu vient d'un import (pas d'horodatage) : elle n'a pas de
        // repos, et la suivante non plus (pas de point de depart connu).
        List<WorkoutSet> sets = Arrays.asList(set(T0), set(null), set(T0 + 300_000L), set(T0 + 400_000L));
        List<Long> rest = SetRest.restSecondsForExercise(sets);

        assertNull(rest.get(0));
        assertNull(rest.get(1));
        assertNull(rest.get(2));
        assertEquals(Long.valueOf(100), rest.get(3));
    }

    @Test
    public void exercice_listeVideOuNulle()
    {
        assertEquals(0, SetRest.restSecondsForExercise(new ArrayList<WorkoutSet>()).size());
        assertEquals(0, SetRest.restSecondsForExercise(null).size());
    }

    @Test
    public void exercice_uneSeuleSerie()
    {
        List<Long> rest = SetRest.restSecondsForExercise(Arrays.asList(set(T0)));
        assertEquals(1, rest.size());
        assertNull(rest.get(0));
    }

    @Test
    public void format_minutesEtSecondes()
    {
        assertEquals("0:05", SetRest.formatRest(5));
        assertEquals("1:00", SetRest.formatRest(60));
        assertEquals("2:15", SetRest.formatRest(135));
        assertEquals("30:00", SetRest.formatRest(1800));
    }

    @Test
    public void labelAt_serieCourante()
    {
        List<WorkoutSet> sets = Arrays.asList(set(T0), set(T0 + 135_000L), set(null));

        assertEquals("", SetRest.labelAt(sets, 0));
        assertEquals("repos 2:15", SetRest.labelAt(sets, 1));
        assertEquals("", SetRest.labelAt(sets, 2));
    }

    @Test
    public void labelAt_positionsInvalidesOuListeNulle()
    {
        List<WorkoutSet> sets = Arrays.asList(set(T0), set(T0 + 60_000L));

        assertEquals("", SetRest.labelAt(sets, -1));
        assertEquals("", SetRest.labelAt(sets, 2));
        assertEquals("", SetRest.labelAt(null, 1));
    }

    @Test
    public void label_reposOuVide()
    {
        assertEquals("repos 2:15", SetRest.label(135L));
        assertEquals("", SetRest.label(null));
    }
}
