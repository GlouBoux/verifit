package com.example.verifit;

import static com.example.verifit.TestData.*;
import static org.junit.Assert.*;

import com.example.verifit.model.WorkoutSet;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashSet;
import java.util.Arrays;

import org.junit.Test;

// Export JSON Coaching (DataStorage.buildCoachingExportJson) : contrat lu par
// verifit_export_reader.py / sync_verifit_to_excel.py cote Coaching. Ne rien casser.
public class CoachingExportTest
{
    private static JsonObject export(DataStorage ds)
    {
        return JsonParser.parseString(ds.buildCoachingExportJson("2026-09-30T10:00:00")).getAsJsonObject();
    }

    private static DataStorage sample()
    {
        WorkoutSet done = set("2026-09-26", "Jeff Curl 4x25", "Legs", 3, 34.5);
        done.setCompleted(true);
        done.setComment("dur");
        done.setPlanComment("S1 Ancrage");
        done.setPlannedReps(3.0);
        done.setPlannedWeight(35.0);
        WorkoutSet manual = set("2026-09-26", "Pull Up", "Back", 8, 0);
        return storage(new String[][] {}, day(done, manual), day(set("2026-09-28", "Pull Up", "Back", 6, 5)));
    }

    @Test
    public void enTete()
    {
        JsonObject root = export(sample());
        assertEquals(new HashSet<String>(Arrays.asList("schemaVersion", "exportedAt", "days")), root.keySet());
        assertEquals(1, root.get("schemaVersion").getAsInt());
        assertEquals("2026-09-30T10:00:00", root.get("exportedAt").getAsString());
        assertEquals(2, root.getAsJsonArray("days").size());
    }

    @Test
    public void serieComplete_tousLesChampsDuContrat()
    {
        JsonObject day = export(sample()).getAsJsonArray("days").get(0).getAsJsonObject();
        assertEquals("2026-09-26", day.get("date").getAsString());
        JsonArray sets = day.getAsJsonArray("sets");
        assertEquals(2, sets.size());

        JsonObject s = sets.get(0).getAsJsonObject();
        assertEquals(new HashSet<String>(Arrays.asList("date", "exercise", "category", "weight", "reps",
                "isCompleted", "comment", "planComment", "plannedReps", "plannedWeight")), s.keySet());
        assertEquals("2026-09-26", s.get("date").getAsString());
        assertEquals("Jeff Curl 4x25", s.get("exercise").getAsString());
        assertEquals("Legs", s.get("category").getAsString());
        assertEquals(34.5, s.get("weight").getAsDouble(), 0.0);
        assertEquals(3.0, s.get("reps").getAsDouble(), 0.0);
        assertTrue(s.get("isCompleted").getAsBoolean());
        assertEquals("dur", s.get("comment").getAsString());
        assertEquals("S1 Ancrage", s.get("planComment").getAsString());
        assertEquals(3.0, s.get("plannedReps").getAsDouble(), 0.0);
        assertEquals(35.0, s.get("plannedWeight").getAsDouble(), 0.0);
    }

    @Test
    public void serieManuelle_sansNoteNiPrevu()
    {
        JsonObject s = export(sample()).getAsJsonArray("days").get(0).getAsJsonObject()
                .getAsJsonArray("sets").get(1).getAsJsonObject();
        assertFalse(s.get("isCompleted").getAsBoolean());
        assertFalse(s.has("comment"));
        assertFalse(s.has("planComment"));
        assertFalse(s.has("plannedReps"));
        assertEquals(8.0, s.get("reps").getAsDouble(), 0.0);
    }
}
