package com.example.verifit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.verifit.model.ImportedSession;
import com.google.gson.Gson;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Lot D, etape D7 : apercu d'une seance JSON avant import (SessionPreview).
 */
public class SessionPreviewTest
{
    private static ImportedSession session(String json)
    {
        return new Gson().fromJson(json, ImportedSession.class);
    }

    private static final String FULL = "{\"date\":\"2026-10-02\",\"exercises\":["
            + "{\"name\":\"Squat\",\"sets\":[{\"weight\":100,\"reps\":5},{\"weight\":102.5,\"reps\":4.6}]},"
            + "{\"name\":\"Rowing\",\"sets\":[{\"weight\":60,\"reps\":8}]}]}";

    @Test
    public void countsExercisesAndSets()
    {
        SessionPreview p = SessionPreview.of(session(FULL), "2026-01-01", null);
        assertEquals("2026-10-02", p.getDate());
        assertTrue(p.isDateFromFile());
        assertTrue(p.isDateValid());
        assertEquals(2, p.getExerciseCount());
        assertEquals(3, p.getValidSetCount());
        assertEquals(0, p.getSkippedSetCount());
        assertTrue(p.isImportable());
        assertNull(p.problem());
    }

    @Test
    public void setsAreListedWithRoundedReps()
    {
        SessionPreview p = SessionPreview.of(session(FULL), "2026-01-01", null);
        assertEquals("100.0 x 5, 102.5 x 5", p.getExercises().get(0).detail);
        assertEquals("60.0 x 8", p.getExercises().get(1).detail);
    }

    @Test
    public void textShowsTotalsAndEachExercise()
    {
        String text = SessionPreview.of(session(FULL), "2026-01-01", null).toText();
        assertTrue(text, text.startsWith("Date : 2026-10-02\n2 exercices, 3 séries"));
        assertTrue(text, text.contains("Squat\n  2 séries : 100.0 x 5, 102.5 x 5"));
        assertTrue(text, text.contains("Rowing\n  1 série : 60.0 x 8"));
        assertFalse(text, text.contains("Attention"));
    }

    @Test
    public void fallbackDateIsUsedWhenTheFileHasNone()
    {
        SessionPreview p = SessionPreview.of(
                session("{\"exercises\":[{\"name\":\"Squat\",\"sets\":[{\"weight\":100,\"reps\":5}]}]}"), "2026-10-03", null);
        assertEquals("2026-10-03", p.getDate());
        assertFalse(p.isDateFromFile());
        assertTrue(p.isImportable());
        assertTrue(p.toText().contains("Date : 2026-10-03 (date du jour, absente du fichier)"));
    }

    @Test
    public void invalidDatesBlockTheImport()
    {
        String[] bad = { "2026-13-01", "2026-02-30", "2026-1-5", "pas une date", "02/10/2026", "2026-10-02T10:00" };
        for (String date : bad)
        {
            SessionPreview p = SessionPreview.of(session("{\"date\":\"" + date + "\",\"exercises\":["
                    + "{\"name\":\"Squat\",\"sets\":[{\"weight\":100,\"reps\":5}]}]}"), "2026-10-03", null);
            assertFalse(date, p.isDateValid());
            assertFalse(date, p.isImportable());
            assertTrue(date, p.problem().startsWith("Date invalide"));
        }
    }

    @Test
    public void validDatesAreAccepted()
    {
        assertTrue(SessionPreview.isValidIsoDate("2026-10-02"));
        assertTrue(SessionPreview.isValidIsoDate("2028-02-29"));
        assertFalse(SessionPreview.isValidIsoDate("2026-02-29"));
        assertFalse(SessionPreview.isValidIsoDate(null));
        assertFalse(SessionPreview.isValidIsoDate(""));
    }

    @Test
    public void incompleteSetsAreSkippedAndReported()
    {
        SessionPreview p = SessionPreview.of(session("{\"date\":\"2026-10-02\",\"exercises\":[{\"name\":\"Squat\",\"sets\":["
                + "{\"weight\":100,\"reps\":5},{\"weight\":100},{\"reps\":5}]}]}"), null, null);
        assertEquals(1, p.getValidSetCount());
        assertEquals(2, p.getSkippedSetCount());
        assertEquals(2, p.getExercises().get(0).skippedSets);
        assertTrue(p.isImportable());
        assertTrue(p.toText().contains("Attention : 2 séries incomplètes (poids ou reps manquant) seront ignorées."));
    }

    @Test
    public void singleIncompleteSetUsesTheSingularWording()
    {
        SessionPreview p = SessionPreview.of(session("{\"date\":\"2026-10-02\",\"exercises\":[{\"name\":\"Squat\",\"sets\":["
                + "{\"weight\":100,\"reps\":5},{\"weight\":100}]}]}"), null, null);
        assertTrue(p.toText().contains("Attention : 1 série incomplète (poids ou reps manquant) sera ignorée."));
    }

    @Test
    public void exercisesWithoutNameAreIgnored()
    {
        SessionPreview p = SessionPreview.of(session("{\"date\":\"2026-10-02\",\"exercises\":["
                + "{\"name\":\"  \",\"sets\":[{\"weight\":100,\"reps\":5}]},"
                + "{\"sets\":[{\"weight\":100,\"reps\":5}]},"
                + "{\"name\":\"Squat\",\"sets\":[{\"weight\":100,\"reps\":5}]}]}"), null, null);
        assertEquals(1, p.getExerciseCount());
        assertEquals(1, p.getValidSetCount());
        assertEquals(2, p.getUnnamedExerciseCount());
        assertTrue(p.toText().contains("Attention : 2 exercices sans nom seront ignorés."));
    }

    @Test
    public void newExercisesAreMarkedOnlyWhenKnownNamesAreGiven()
    {
        Set<String> known = new HashSet<String>(Arrays.asList("Squat"));
        SessionPreview withKnown = SessionPreview.of(session(FULL), "2026-01-01", known);
        assertFalse(withKnown.getExercises().get(0).isNew);
        assertTrue(withKnown.getExercises().get(1).isNew);
        assertEquals(1, withKnown.getNewExerciseCount());
        assertTrue(withKnown.toText().contains("Rowing (nouvel exercice)"));
        assertFalse(withKnown.toText().contains("Squat (nouvel exercice)"));

        SessionPreview withoutKnown = SessionPreview.of(session(FULL), "2026-01-01", null);
        assertEquals(0, withoutKnown.getNewExerciseCount());
    }

    @Test
    public void emptyOrMissingSessionHasNothingToImport()
    {
        SessionPreview nullSession = SessionPreview.of(null, "2026-10-03", null);
        assertEquals(0, nullSession.getExerciseCount());
        assertFalse(nullSession.isImportable());
        assertEquals("Aucun exercice dans ce fichier.", nullSession.problem());

        SessionPreview noExercises = SessionPreview.of(session("{\"date\":\"2026-10-02\"}"), null, null);
        assertEquals("Aucun exercice dans ce fichier.", noExercises.problem());
    }

    @Test
    public void aFileWithOnlyUnusableSetsIsNotImportable()
    {
        SessionPreview p = SessionPreview.of(session("{\"date\":\"2026-10-02\",\"exercises\":["
                + "{\"name\":\"Squat\",\"sets\":[{\"weight\":100}]}]}"), null, null);
        assertEquals(1, p.getExerciseCount());
        assertEquals(0, p.getValidSetCount());
        assertFalse(p.isImportable());
        assertTrue(p.problem().startsWith("Aucune série valide"));
        assertTrue(p.toText().contains("Squat\n  aucune série valide"));
    }

    @Test
    public void anExerciseWithoutValidSetIsListedButDoesNotBlockTheOthers()
    {
        SessionPreview p = SessionPreview.of(session("{\"date\":\"2026-10-02\",\"exercises\":["
                + "{\"name\":\"Squat\",\"sets\":[]},"
                + "{\"name\":\"Rowing\",\"sets\":[{\"weight\":60,\"reps\":8}]}]}"), null, null);
        assertEquals(2, p.getExerciseCount());
        assertEquals(1, p.getValidSetCount());
        assertTrue(p.isImportable());
    }

    @Test
    public void theCoachingExportIsNotMistakenForASession()
    {
        // Un export Coaching (cle "days", pas "exercises") ne doit jamais etre importable.
        SessionPreview p = SessionPreview.of(session("{\"schemaVersion\":1,\"exportedAt\":\"2026-10-01T10:00:00\",\"days\":[]}"),
                "2026-10-03", null);
        assertFalse(p.isImportable());
        assertNotNull(p.problem());
    }
}
