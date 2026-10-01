package com.example.verifit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.verifit.model.WorkoutSet;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.Arrays;

// Champ optionnel "restSeconds" de l'export Coaching (lot D, etape D4) : present pour une
// serie dont le repos est connu, absent sinon, sans toucher au reste du contrat.
public class CoachingExportRestTest
{
    private static final long T0 = 1_000_000_000_000L;

    private static WorkoutSet set(String exercise, Long timestamp)
    {
        WorkoutSet s = new WorkoutSet("2026-10-01", exercise, "Legs", 5.0, 100.0);
        s.setTimestamp(timestamp);
        return s;
    }

    private static JsonObject exportOf(WorkoutSet... sets)
    {
        CoachingExport export = new CoachingExport("2026-10-01T12:00:00");
        export.addDay(CoachingExportDay.fromWorkoutSets("2026-10-01", Arrays.asList(sets)));
        return JsonParser.parseString(new Gson().toJson(export)).getAsJsonObject();
    }

    private static JsonArray setsOf(JsonObject export)
    {
        return export.getAsJsonArray("days").get(0).getAsJsonObject().getAsJsonArray("sets");
    }

    @Test
    public void restSecondsPresentSeulementQuandConnu()
    {
        JsonArray sets = setsOf(exportOf(
                set("Squat", T0), set("Squat", T0 + 135_000L), set("Squat", null)));

        assertFalse(sets.get(0).getAsJsonObject().has("restSeconds"));
        assertEquals(135, sets.get(1).getAsJsonObject().get("restSeconds").getAsInt());
        assertFalse(sets.get(2).getAsJsonObject().has("restSeconds"));
    }

    @Test
    public void exercicesAlternesCalculesParExercice()
    {
        JsonArray sets = setsOf(exportOf(
                set("A", T0), set("B", T0 + 30_000L), set("A", T0 + 150_000L)));

        assertFalse(sets.get(1).getAsJsonObject().has("restSeconds"));
        assertEquals(150, sets.get(2).getAsJsonObject().get("restSeconds").getAsInt());
    }

    @Test
    public void contratInchange_schemaVersionEtChampsExistants()
    {
        JsonObject export = exportOf(set("Squat", T0), set("Squat", T0 + 90_000L));
        assertEquals(1, export.get("schemaVersion").getAsInt());

        JsonObject s = setsOf(export).get(1).getAsJsonObject();
        for (String key : new String[]{"date", "exercise", "category", "weight", "reps", "isCompleted"})
        {
            assertTrue("cle manquante : " + key, s.has(key));
        }
        assertEquals("Squat", s.get("exercise").getAsString());
        assertEquals(5.0, s.get("reps").getAsDouble(), 0.0);
    }

    @Test
    public void ancienConstructeurSansRepos()
    {
        JsonObject s = JsonParser.parseString(
                new Gson().toJson(new CoachingExportSet(set("Squat", T0)))).getAsJsonObject();

        assertFalse(s.has("restSeconds"));
        assertEquals("Squat", s.get("exercise").getAsString());
    }

    @Test
    public void jourSansSerie()
    {
        CoachingExport export = new CoachingExport("x");
        export.addDay(CoachingExportDay.fromWorkoutSets("2026-10-01", new java.util.ArrayList<WorkoutSet>()));
        JsonObject json = JsonParser.parseString(new Gson().toJson(export)).getAsJsonObject();
        assertEquals(0, setsOf(json).size());
    }
}
