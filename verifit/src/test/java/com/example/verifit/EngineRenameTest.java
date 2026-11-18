package com.example.verifit;

import static org.junit.Assert.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Map;

import org.junit.Test;

// Lot M3.5 : renommer ou supprimer un exercice dans l'app suit dans les donnees du moteur.
public class EngineRenameTest
{
    private static final String OLD = "Assisted Pelican";
    private static final String NEW = "Assisted Pelican Curl";

    private static EngineContent withNextPrAndSubstitution()
    {
        Map<String, String> files = EngineTestData.files();
        files.put(EngineContent.NEXT_PR, "{\"timestamp\": \"2026-11-01T10:00:00\", \"exercise\": \"Assisted Pelican\", \"excluded_rms\": [3]}\n");
        files.put(EngineContent.SUBSTITUTIONS, "{\"original_exercise\": \"Jeff Curl 4x25\", \"substitute_exercise\": \"Assisted Pelican\", \"portee\": \"ponctuel\"}\n");
        files.put(EngineContent.CANCELLED, "{\"plan\": \"x\", \"exercises\": [{\"name\": \"Assisted Pelican\", \"sets\": []}]}\n");
        return EngineContent.of(files);
    }

    @Test
    public void renommer_partout_saufSeancesAnnulees()
    {
        EngineContent before = withNextPrAndSubstitution();
        EngineContent after = EngineRename.rename(before, OLD, NEW);

        assertFalse(EngineRename.hasExercise(after, OLD));
        assertTrue(EngineRename.hasExercise(after, NEW));
        JsonObject data = after.data();
        JsonArray cycleA = data.getAsJsonObject("cycles").getAsJsonArray("A");
        assertEquals(NEW, cycleA.get(1).getAsString());
        assertEquals(3, after.count(EngineContent.DATA));
        assertEquals(before.prRows(), after.prRows());

        // Seul le jeton change, le reste de la ligne est garde a l'octet pres.
        assertEquals(EngineTestData.HISTORY_LINE_1.replace("\"exercice\": \"Assisted Pelican\"", "\"exercice\": \"" + NEW + "\"")
                + "\n" + EngineTestData.HISTORY_LINE_2 + "\n", after.text(EngineContent.HISTORY));
        assertTrue(after.text(EngineContent.NEXT_PR).contains("\"exercise\": \"" + NEW + "\""));
        assertTrue(after.text(EngineContent.SUBSTITUTIONS).contains("\"substitute_exercise\": \"" + NEW + "\""));
        assertTrue(after.text(EngineContent.SUBSTITUTIONS).contains("\"original_exercise\": \"Jeff Curl 4x25\""));
        assertEquals(before.text(EngineContent.CANCELLED), after.text(EngineContent.CANCELLED));

        String plan = com.google.gson.JsonParser.parseString(after.text(EngineContent.PLANS)).getAsJsonObject()
                .getAsJsonArray("plans").get(0).getAsJsonObject().get("contenu").getAsString();
        assertEquals(EngineTestData.PLAN_CONTENT.replace("\"name\": \"Assisted Pelican\"", "\"name\": \"" + NEW + "\""), plan);
    }

    @Test
    public void renommer_versUnNomExistant_refuse()
    {
        try
        {
            EngineRename.rename(EngineTestData.content(), OLD, "Jeff Curl 4x25");
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().contains("existe déjà"));
        }
    }

    @Test
    public void renommer_exerciceAbsent_rienNeChange()
    {
        EngineContent c = EngineTestData.content();
        assertSame(c, EngineRename.rename(c, "Bench Press", "Bench Press 2"));
        assertSame(c, EngineRename.rename(c, OLD, OLD));
    }

    @Test
    public void renommer_exerciceSeulementDansLeJournal()
    {
        Map<String, String> files = EngineTestData.files();
        files.put(EngineContent.HISTORY, "{\"exercice\": \"Ancien Exo\", \"rm\": 3}\n");
        EngineContent after = EngineRename.rename(EngineContent.of(files), "Ancien Exo", "Nouvel Exo");
        assertEquals("{\"exercice\": \"Nouvel Exo\", \"rm\": 3}\n", after.text(EngineContent.HISTORY));
    }

    @Test
    public void renommer_nomPrefixeNonTouche()
    {
        // « Assisted Pelican » ne doit jamais toucher « Assisted Pelican P » (guillemet fermant).
        Map<String, String> files = EngineTestData.files();
        files.put(EngineContent.HISTORY, "{\"exercice\": \"Assisted Pelican P\"}\n{\"exercice\": \"Assisted Pelican\"}\n");
        EngineContent after = EngineRename.rename(EngineContent.of(files), OLD, NEW);
        assertEquals("{\"exercice\": \"Assisted Pelican P\"}\n{\"exercice\": \"" + NEW + "\"}\n", after.text(EngineContent.HISTORY));
    }

    @Test
    public void ecritureFaconPython()
    {
        assertEquals("\"a\\\"b\\\\c\\nd\\u0001é\"", EngineRename.pythonString("a\"b\\c\nd\u0001é"));
        assertEquals("\"exercice\": \"Siège 5\"", EngineRename.token("exercice", "Siège 5"));
    }

    @Test
    public void supprimer_retireExerciceEtCycles_gardeJournaux()
    {
        EngineContent before = EngineTestData.content();
        assertEquals("2 ligne(s) de PR, sa place dans le cycle A", EngineRename.describeDeletion(before, OLD));
        EngineContent after = EngineRename.delete(before, OLD);
        assertFalse(EngineRename.hasExercise(after, OLD));
        assertEquals(2, after.count(EngineContent.DATA));
        assertEquals(4, after.prRows());
        assertEquals(1, after.data().getAsJsonObject("cycles").getAsJsonArray("A").size());
        assertEquals(before.text(EngineContent.HISTORY), after.text(EngineContent.HISTORY));
        assertEquals(before.text(EngineContent.PLANS), after.text(EngineContent.PLANS));
        assertNull(EngineRename.describeDeletion(before, "Bench Press"));
        assertSame(before, EngineRename.delete(before, "Bench Press"));
    }

    @Test
    public void renommerPuisRecharger_stockageOk() throws Exception
    {
        EngineTestData.MemoryFiles disk = new EngineTestData.MemoryFiles();
        EngineStorage storage = new EngineStorage(disk);
        storage.replaceAll(EngineTestData.content(), "t", "x.json", null);
        EngineStorage.Loaded l = storage.load();
        storage.rewrite(EngineRename.rename(l.content, OLD, NEW), l.state);
        EngineStorage.Loaded after = storage.load();
        assertEquals(EngineStorage.Status.OK, after.status);
        assertTrue(EngineRename.hasExercise(after.content, NEW));
        assertEquals("x.json", after.state.source);
    }
}
