package com.example.verifit;

import static com.example.verifit.TestData.*;
import static org.junit.Assert.*;

import com.example.verifit.model.ImportedSession;
import com.example.verifit.model.WorkoutDay;
import com.example.verifit.model.WorkoutSet;

import java.util.Arrays;

import org.junit.Test;

// Import d'une seance generee par workout_engine.py (SessionImporter.parseSession +
// DataStorage.mergeImportedSession / countImportedSets).
public class SessionImportTest
{
    // Extrait reduit de la seance Force B du 26/09/2026 (meme format que le fichier reel).
    private static final String SESSION_JSON = "{"
            + "\"date\": \"2026-09-26\", \"comment\": \"Force B\", \"exercises\": ["
            + "  {\"name\": \"Jeff Curl 4x25\", \"bodyPart\": \"Arms\", \"sets\": ["
            + "     {\"weight\": 24.0, \"reps\": 3, \"comment\": \"Echauffement\"},"
            + "     {\"weight\": 34.5, \"reps\": 3, \"comment\": \"S1 Ancrage\"}]},"
            + "  {\"name\": \"Nouvel exo\", \"bodyPart\": \"Back\", \"sets\": ["
            + "     {\"weight\": 10, \"reps\": 5},"
            + "     {\"weight\": null, \"reps\": 5}]},"
            + "  {\"name\": \"  \", \"sets\": [{\"weight\": 1, \"reps\": 1}]}"
            + "]}";

    private static DataStorage emptyStorage()
    {
        return storage(new String[][] { {"Jeff Curl 4x25", "Legs"} });
    }

    private static ImportedSession parse(String json) throws Exception
    {
        return SessionImporter.parseSession(json);
    }

    @Test
    public void nouveauJour_resumeDeLImport() throws Exception
    {
        DataStorage ds = emptyStorage();
        DataStorage.ImportSummary summary = ds.mergeImportedSession(parse(SESSION_JSON), "2000-01-01");

        assertEquals("2026-09-26", summary.date);
        assertEquals(3, summary.setsImported);
        assertEquals(1, summary.setsSkipped);
        assertEquals(1, summary.exercisesCreated);
        assertEquals(1, ds.workoutDays.size());
        assertEquals(3, ds.workoutDays.get(0).getSets().size());
    }

    @Test
    public void exercices_inconnuCreeConnuGardeSaCategorie() throws Exception
    {
        DataStorage ds = emptyStorage();
        ds.mergeImportedSession(parse(SESSION_JSON), "2000-01-01");

        assertTrue(ds.doesExerciseExist("Nouvel exo"));
        assertEquals("Back", ds.getExerciseCategory("Nouvel exo"));
        assertEquals("Legs", ds.getExerciseCategory("Jeff Curl 4x25"));
        assertEquals("Legs", ds.workoutDays.get(0).getSets().get(0).getCategory());
    }

    @Test
    public void valeursPrevuesFigees_commentaireDansLePlan() throws Exception
    {
        DataStorage ds = emptyStorage();
        ds.mergeImportedSession(parse(SESSION_JSON), "2000-01-01");
        WorkoutSet s = ds.workoutDays.get(0).getSets().get(0);

        assertEquals(3.0, s.getReps(), 0.0);
        assertEquals(24.0, s.getWeight(), 0.0);
        assertEquals(3.0, s.getPlannedReps(), 0.0);
        assertEquals(24.0, s.getPlannedWeight(), 0.0);
        assertEquals("Echauffement", s.getPlanComment());
        assertFalse("le texte du script ne va pas dans la note perso", s.hasNote());
        assertFalse(s.isCompleted());
    }

    @Test
    public void jourExistant_seriesAjouteesALaSuite() throws Exception
    {
        WorkoutDay existing = day(set("2026-09-26", "Jeff Curl 4x25", "Legs", 8, 30));
        DataStorage ds = storage(new String[][] { {"Jeff Curl 4x25", "Legs"} }, existing);
        ds.mergeImportedSession(parse(SESSION_JSON), "2000-01-01");

        assertEquals(1, ds.workoutDays.size());
        assertSame(existing, ds.workoutDays.get(0));
        assertEquals(4, existing.getSets().size());
        assertEquals(30.0, existing.getSets().get(0).getWeight(), 0.0);
    }

    @Test
    public void sansDate_jourAfficheUtilise() throws Exception
    {
        DataStorage ds = emptyStorage();
        DataStorage.ImportSummary summary = ds.mergeImportedSession(
                parse("{\"exercises\": [{\"name\": \"Jeff Curl 4x25\", \"sets\": [{\"weight\": 20, \"reps\": 5}]}]}"),
                "2026-09-30");
        assertEquals("2026-09-30", summary.date);
        assertEquals("2026-09-30", ds.workoutDays.get(0).getDate());
    }

    @Test
    public void aucuneSerieValide_aucunJourCree() throws Exception
    {
        DataStorage ds = emptyStorage();
        DataStorage.ImportSummary summary = ds.mergeImportedSession(
                parse("{\"date\": \"2026-09-26\", \"exercises\": [{\"name\": \"Jeff Curl 4x25\", \"sets\": [{\"reps\": 5}]}]}"),
                "2000-01-01");
        assertEquals(0, summary.setsImported);
        assertTrue(ds.workoutDays.isEmpty());
    }

    @Test
    public void doubleImport_detecteParLesSeriesPrevues() throws Exception
    {
        WorkoutDay existing = day(set("2026-09-26", "Pull Up", "Back", 8, 0));
        DataStorage ds = storage(new String[][] { {"Jeff Curl 4x25", "Legs"}, {"Pull Up", "Back"} }, existing);

        assertEquals(0, ds.countImportedSets("2026-09-26", Arrays.asList("Jeff Curl 4x25")));
        ds.mergeImportedSession(parse(SESSION_JSON), "2000-01-01");
        assertEquals(2, ds.countImportedSets("2026-09-26", Arrays.asList("Jeff Curl 4x25")));
        assertEquals("une saisie manuelle n'a pas de prevu", 0, ds.countImportedSets("2026-09-26", Arrays.asList("Pull Up")));
        assertEquals(0, ds.countImportedSets("2026-09-27", Arrays.asList("Jeff Curl 4x25")));
    }

    @Test
    public void fichiersInvalides_messagesAffiches() throws Exception
    {
        try
        {
            parse("{ pas du json");
            fail();
        }
        catch (SessionImporter.InvalidSessionException e)
        {
            assertTrue(e.getMessage().startsWith("Invalid JSON file: "));
        }
        try
        {
            parse("{\"exercises\": 3}");
            fail();
        }
        catch (SessionImporter.InvalidSessionException e)
        {
            assertTrue(e.getMessage().startsWith("Invalid JSON file: "));
        }
        try
        {
            parse("{\"exercises\": [{\"name\": \"x\", \"sets\": [{\"weight\": \"abc\", \"reps\": 3}]}]}");
            fail();
        }
        catch (SessionImporter.InvalidSessionException e)
        {
            assertTrue(e.getMessage().startsWith("Invalid JSON file (unexpected field type): "));
        }
        assertNull("fichier vide", parse(""));
        // Un nombre entre guillemets est accepte (Gson le convertit).
        ImportedSession quoted = parse("{\"exercises\": [{\"name\": \"x\", \"sets\": [{\"weight\": \"8\", \"reps\": 3}]}]}");
        assertEquals(8.0, quoted.getExercises().get(0).getSets().get(0).getWeight(), 0.0);
    }
}
