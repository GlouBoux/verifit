package com.example.verifit.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SetRestTest
{
    // Serie cochee a l'instant donne (null = jamais cochee). Depuis le 16/10/2026, seule
    // une serie cochee compte pour le repos (voir SetRest).
    private static WorkoutSet set(Long timestamp)
    {
        WorkoutSet s = new WorkoutSet("2026-10-01", "Squat", "Legs", 5.0, 100.0);
        if (timestamp != null)
        {
            s.markCompleted(true, timestamp);
        }
        return s;
    }

    // Serie horodatee mais NON cochee : cas des series ajoutees via Save avant le
    // correctif du 16/10/2026 (Save posait un horodatage).
    private static WorkoutSet savedNotChecked(String exercise, long timestamp)
    {
        WorkoutSet s = new WorkoutSet("2026-10-16", exercise, "Legs", 3.0, 52.0);
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

    private static WorkoutSet setOf(String exercise, Long timestamp)
    {
        WorkoutSet s = new WorkoutSet("2026-10-01", exercise, "Cat", 5.0, 100.0);
        if (timestamp != null)
        {
            s.markCompleted(true, timestamp);
        }
        return s;
    }

    @Test
    public void jour_exercicesAlternesReposDepuisLaDerniereSerieCochee()
    {
        // Regle du 14/10/2026 : A1 B1 A2 B2 - le repos de A2 se compte depuis B1 (derniere
        // serie cochee, tous exercices), plus depuis A1. 1re serie de chaque exercice : null.
        List<WorkoutSet> day = Arrays.asList(
                setOf("A", T0), setOf("B", T0 + 30_000L),
                setOf("A", T0 + 150_000L), setOf("B", T0 + 200_000L));
        List<Long> rest = SetRest.restSecondsForDay(day);

        assertEquals(4, rest.size());
        assertNull(rest.get(0));
        assertNull(rest.get(1));
        assertEquals(Long.valueOf(120), rest.get(2));
        assertEquals(Long.valueOf(50), rest.get(3));
    }

    @Test
    public void jour_precedentChercheParHorodatagePasParOrdreDeListe()
    {
        // Le jour est range par exercice (toutes les A puis toutes les B), pas dans
        // l'ordre chronologique : le precedent de A2 est B1, liste apres lui.
        WorkoutSet a1 = setOf("A", T0 + 10_000L);
        WorkoutSet a2 = setOf("A", T0 + 200_000L);
        WorkoutSet b1 = setOf("B", T0);
        WorkoutSet b2 = setOf("B", T0 + 100_000L);
        List<WorkoutSet> day = Arrays.asList(a1, a2, b1, b2);
        List<Long> rest = SetRest.restSecondsForDay(day);

        assertNull(rest.get(0));
        assertEquals(Long.valueOf(100), rest.get(1));
        assertNull(rest.get(2));
        assertEquals(Long.valueOf(90), rest.get(3));
        assertSame(b2, SetRest.previousValidatedInDay(day, a2));
        assertSame(b1, SetRest.previousValidatedInDay(day, a1));
        assertNull(SetRest.previousValidatedInDay(day, b1));
    }

    @Test
    public void jour_serieNonCocheeIgnoree()
    {
        // B1 jamais cochee (pas d'horodatage) : le precedent de A2 reste A1.
        List<WorkoutSet> day = Arrays.asList(
                setOf("A", T0), setOf("B", null), setOf("A", T0 + 150_000L));
        assertEquals(Long.valueOf(150), SetRest.restSecondsForDay(day).get(2));
        assertNull(SetRest.restSecondsForDay(day).get(1));
    }

    @Test
    public void jour_pauseLongueDepuisLaDerniereSerie()
    {
        List<WorkoutSet> day = Arrays.asList(setOf("A", T0), setOf("A", T0 + 1801_000L));
        assertNull(SetRest.restSecondsForDay(day).get(1));
    }

    // Cas reel (seance du 12/10/2026, retour Romain 14/10/2026) : Assisted Pelican
    // alterne avec Assisted HSPU FP. L'ancienne regle affichait 11:34 / 12:17 / 12:22
    // sur les Pelican ; les vrais repos sont 6:25 / 6:21 / 12:22.
    private static long hms(int h, int m, int s)
    {
        return T0 + ((h * 60L + m) * 60L + s) * 1000L;
    }

    @Test
    public void jour_casReelPelicanAlterneAvecHspu()
    {
        WorkoutSet p1 = setOf("Assisted Pelican", hms(18, 15, 15));
        WorkoutSet p2 = setOf("Assisted Pelican", hms(18, 26, 49));
        WorkoutSet p3 = setOf("Assisted Pelican", hms(18, 39, 6));
        WorkoutSet p4 = setOf("Assisted Pelican", hms(18, 51, 29));
        WorkoutSet h1 = setOf("Assisted HSPU FP", hms(18, 12, 7));
        WorkoutSet h2 = setOf("Assisted HSPU FP", hms(18, 20, 24));
        WorkoutSet h3 = setOf("Assisted HSPU FP", hms(18, 32, 45));
        // Ordre du jour tel que l'app le stocke : par exercice.
        List<WorkoutSet> day = Arrays.asList(p1, p2, p3, p4, h1, h2, h3);
        List<WorkoutSet> pelican = Arrays.asList(p1, p2, p3, p4);
        List<WorkoutSet> hspu = Arrays.asList(h1, h2, h3);

        assertEquals("", SetRest.labelAt(pelican, 0, day));
        assertEquals("repos 6:25", SetRest.labelAt(pelican, 1, day));
        assertEquals("repos 6:21", SetRest.labelAt(pelican, 2, day));
        assertEquals("repos 12:23", SetRest.labelAt(pelican, 3, day));
        assertEquals("", SetRest.labelAt(hspu, 0, day));
        assertEquals("repos 5:09", SetRest.labelAt(hspu, 1, day));
        assertEquals("repos 5:56", SetRest.labelAt(hspu, 2, day));

        assertEquals(Arrays.asList(null, 385L, 381L, 743L, null, 309L, 356L),
                SetRest.restSecondsForDay(day));
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

        assertEquals("", SetRest.labelAt(sets, 0, sets));
        assertEquals("repos 2:15", SetRest.labelAt(sets, 1, sets));
        assertEquals("", SetRest.labelAt(sets, 2, sets));
    }

    @Test
    public void labelAt_positionsInvalidesOuListesNulles()
    {
        List<WorkoutSet> sets = Arrays.asList(set(T0), set(T0 + 60_000L));

        assertEquals("", SetRest.labelAt(sets, -1, sets));
        assertEquals("", SetRest.labelAt(sets, 2, sets));
        assertEquals("", SetRest.labelAt(null, 1, sets));
        assertEquals("", SetRest.labelAt(sets, 1, null));
    }

    @Test
    public void labelAt_premiereSerieDeLExerciceToujoursVide()
    {
        // Option A (Romain, 14/10/2026) : meme si une autre serie a ete cochee juste
        // avant, la 1re serie de l'exercice n'affiche pas de repos.
        WorkoutSet b1 = setOf("B", T0);
        WorkoutSet a1 = setOf("A", T0 + 60_000L);
        List<WorkoutSet> day = Arrays.asList(a1, b1);

        assertEquals("", SetRest.labelAt(Arrays.asList(a1), 0, day));
    }

    // Serie issue d'un Import Session : aucun horodatage au depart.
    private static WorkoutSet imported()
    {
        return new WorkoutSet("2026-10-05", "Row TG", "Back", 3.0, 40.0);
    }

    @Test
    public void validation_cocherHorodateLaSerie()
    {
        WorkoutSet s = imported();
        assertEquals(false, s.hasTimestamp());
        assertEquals(false, s.isCompleted());

        s.markCompleted(true, T0);
        assertEquals(true, s.isCompleted());
        assertEquals(Long.valueOf(T0), s.getTimestamp());
    }

    @Test
    public void validation_cocherRemplaceUnAncienHorodatageDeSave()
    {
        // Serie ajoutee via Save avant le correctif du 16/10/2026 (horodatee a la
        // creation, jamais cochee) : la cocher plus tard prend l'heure de la coche.
        WorkoutSet saved = savedNotChecked("Leg Extension", T0);
        saved.markCompleted(true, T0 + 900_000L);
        assertEquals(Long.valueOf(T0 + 900_000L), saved.getTimestamp());
    }

    @Test
    public void validation_decocherEffaceLHorodatage()
    {
        WorkoutSet s = imported();
        s.markCompleted(true, T0);
        s.markCompleted(false, T0 + 60_000L);

        assertEquals(false, s.isCompleted());
        assertEquals(false, s.hasTimestamp());

        // Recocher plus tard : c'est la nouvelle heure qui compte.
        s.markCompleted(true, T0 + 300_000L);
        assertEquals(Long.valueOf(T0 + 300_000L), s.getTimestamp());
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
        assertEquals("", SetRest.labelAt(sets, 1, sets));

        s1.markCompleted(true, T0);
        s2.markCompleted(true, T0 + 90_000L);
        s3.markCompleted(true, T0 + 240_000L);

        assertEquals("", SetRest.labelAt(sets, 0, sets));
        assertEquals("repos 1:30", SetRest.labelAt(sets, 1, sets));
        assertEquals("repos 2:30", SetRest.labelAt(sets, 2, sets));
    }

    @Test
    public void validation_serieNonCocheeIgnoreeParLaSuivante()
    {
        // La 2e n'est jamais validee : pas de repos pour elle ; la 3e se compte depuis
        // la derniere serie cochee, donc la 1re (regle du 14/10/2026).
        WorkoutSet s1 = imported();
        WorkoutSet s2 = imported();
        WorkoutSet s3 = imported();
        s1.markCompleted(true, T0);
        s3.markCompleted(true, T0 + 240_000L);
        List<WorkoutSet> sets = Arrays.asList(s1, s2, s3);

        assertEquals("", SetRest.labelAt(sets, 1, sets));
        assertEquals("repos 4:00", SetRest.labelAt(sets, 2, sets));
    }

    // Retour Romain 16/10/2026 (capture Leg Extension) : deux series ajoutees via Save
    // a 16 s d'ecart, sans cocher, seance pas commencee : affichait "repos 0:16".
    @Test
    public void casReel_seriesAjouteesSansCocherNOntPasDeRepos()
    {
        WorkoutSet s1 = savedNotChecked("Leg Extension", T0);
        WorkoutSet s2 = savedNotChecked("Leg Extension", T0 + 16_000L);
        List<WorkoutSet> sets = Arrays.asList(s1, s2);

        assertEquals("", SetRest.labelAt(sets, 1, sets));
        assertNull(SetRest.restSeconds(s1, s2));
        assertEquals(Arrays.asList((Long) null, null), SetRest.restSecondsForDay(sets));
    }

    @Test
    public void serieHorodateeNonCocheeNeSertPasDePrecedent()
    {
        // A1 cochee, B1 horodatee par un ancien Save mais non cochee, A2 cochee : le
        // repos de A2 se compte depuis A1, pas depuis B1.
        WorkoutSet a1 = setOf("A", T0);
        WorkoutSet b1 = savedNotChecked("B", T0 + 100_000L);
        WorkoutSet a2 = setOf("A", T0 + 150_000L);
        List<WorkoutSet> day = Arrays.asList(a1, b1, a2);

        assertSame(a1, SetRest.previousValidatedInDay(day, a2));
        assertEquals(Long.valueOf(150), SetRest.restSecondsForDay(day).get(2));
        assertNull(SetRest.previousValidatedInDay(day, b1));
    }

    @Test
    public void label_reposOuVide()
    {
        assertEquals("repos 2:15", SetRest.label(135L));
        assertEquals("", SetRest.label(null));
    }
}
