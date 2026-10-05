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

    private static WorkoutSet setOf(String exercise, Long timestamp)
    {
        WorkoutSet s = new WorkoutSet("2026-10-01", exercise, "Cat", 5.0, 100.0);
        s.setTimestamp(timestamp);
        return s;
    }

    @Test
    public void jour_exercicesAlternesChacunAvecSonPrecedent()
    {
        // Superset A/B : A1 B1 A2 B2 - le repos de A2 se compte depuis A1, pas depuis B1.
        List<WorkoutSet> day = Arrays.asList(
                setOf("A", T0), setOf("B", T0 + 30_000L),
                setOf("A", T0 + 150_000L), setOf("B", T0 + 200_000L));
        List<Long> rest = SetRest.restSecondsForDay(day);

        assertEquals(4, rest.size());
        assertNull(rest.get(0));
        assertNull(rest.get(1));
        assertEquals(Long.valueOf(150), rest.get(2));
        assertEquals(Long.valueOf(170), rest.get(3));
    }

    @Test
    public void jour_exerciceSansNomEtListeVideOuNulle()
    {
        List<WorkoutSet> day = Arrays.asList(setOf(null, T0), setOf(null, T0 + 60_000L));
        List<Long> rest = SetRest.restSecondsForDay(day);
        assertNull(rest.get(0));
        assertEquals(Long.valueOf(60), rest.get(1));

        assertEquals(0, SetRest.restSecondsForDay(new ArrayList<WorkoutSet>()).size());
        assertEquals(0, SetRest.restSecondsForDay(null).size());
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

    // Serie issue d'un Import Session : aucun horodatage au depart.
    private static WorkoutSet imported()
    {
        return new WorkoutSet("2026-10-05", "Row TG", "Back", 3.0, 40.0);
    }

    @Test
    public void validation_serieImporteeSansHorodatageEnRecoitUn()
    {
        WorkoutSet s = imported();
        assertEquals(false, s.hasTimestamp());

        assertEquals(true, s.stampValidationIfMissing(T0));
        assertEquals(true, s.hasTimestamp());
        assertEquals(Long.valueOf(T0), s.getTimestamp());
    }

    @Test
    public void validation_premierGesteGagneJamaisDEcrasement()
    {
        WorkoutSet s = imported();
        s.stampValidationIfMissing(T0);

        // Un second geste (Update, nouvelle coche) plus tard ne change rien.
        assertEquals(false, s.stampValidationIfMissing(T0 + 600_000L));
        assertEquals(Long.valueOf(T0), s.getTimestamp());

        // Meme chose pour une serie saisie via Save (horodatage deja pose a la creation).
        WorkoutSet saved = set(T0 + 5_000L);
        assertEquals(false, saved.stampValidationIfMissing(T0 + 900_000L));
        assertEquals(Long.valueOf(T0 + 5_000L), saved.getTimestamp());
    }

    @Test
    public void validation_seriesImporteesCocheesAfficheLeRepos()
    {
        // Cas du retour UAT : plan importe, series cochees a 90 s puis 150 s d'ecart.
        WorkoutSet s1 = imported();
        WorkoutSet s2 = imported();
        WorkoutSet s3 = imported();
        List<WorkoutSet> sets = Arrays.asList(s1, s2, s3);

        // Avant toute validation : aucun repos, aucun libelle.
        assertEquals("", SetRest.labelAt(sets, 1));

        s1.stampValidationIfMissing(T0);
        s2.stampValidationIfMissing(T0 + 90_000L);
        s3.stampValidationIfMissing(T0 + 240_000L);

        assertEquals("", SetRest.labelAt(sets, 0));
        assertEquals("repos 1:30", SetRest.labelAt(sets, 1));
        assertEquals("repos 2:30", SetRest.labelAt(sets, 2));
    }

    @Test
    public void validation_serieNonCocheeCasseSeulementSesVoisines()
    {
        // La 2e n'est jamais validee : pas de repos pour elle ni pour la 3e.
        WorkoutSet s1 = imported();
        WorkoutSet s2 = imported();
        WorkoutSet s3 = imported();
        s1.stampValidationIfMissing(T0);
        s3.stampValidationIfMissing(T0 + 240_000L);
        List<WorkoutSet> sets = Arrays.asList(s1, s2, s3);

        assertEquals("", SetRest.labelAt(sets, 1));
        assertEquals("", SetRest.labelAt(sets, 2));
    }

    @Test
    public void label_reposOuVide()
    {
        assertEquals("repos 2:15", SetRest.label(135L));
        assertEquals("", SetRest.label(null));
    }
}
