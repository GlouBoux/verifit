package com.example.verifit;

import static com.example.verifit.TestData.*;
import static org.junit.Assert.*;

import com.example.verifit.model.WorkoutDay;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;

// Lot M3.2 : les donnees du moteur dans le backup complet (format 2). Un ancien backup
// (format 1, sans moteur) doit toujours se restaurer.
public class EngineBackupTest
{
    private DataStorage ds;

    @Before
    public void setUp()
    {
        WorkoutDay d = day(set("2026-11-16", "Assisted Pelican", "Biceps", 3, 52.5));
        ds = storage(new String[][] { {"Assisted Pelican", "Biceps"} }, d);
    }

    @Test
    public void allerRetour_moteurIdentiqueOctetParOctet()
    {
        EngineContent engine = EngineTestData.content();
        String json = BackupManager.toJson(ds, engine);
        assertTrue(json.contains("\"schemaVersion\":2"));
        BackupManager.FullBackup fb = BackupManager.parse(json);
        assertNotNull(fb.getEngineContent());
        assertTrue(engine.sameTexts(fb.getEngineContent()));
        assertEquals(EngineTestData.HISTORY, fb.getEngineContent().text(EngineContent.HISTORY));
        assertEquals(1, fb.countSets());
    }

    @Test
    public void backupSansMoteur_accepte()
    {
        BackupManager.FullBackup fb = BackupManager.parse(BackupManager.toJson(ds, null));
        assertNull(fb.getEngineContent());
        assertFalse(BackupManager.toJson(ds, null).contains("\"moteur\""));
    }

    // Copie conforme de la structure d'un backup ecrit par l'app avant le lot M3 (format 1).
    @Test
    public void ancienBackupFormat1_accepte()
    {
        String v1 = "{\"exportedAt\":\"2026-11-15T17:18:50\",\"format\":\"" + AppNames.BACKUP_FORMAT + "\","
                + "\"goals\":[],\"knownExercises\":[{\"BodyPart\":\"Biceps\",\"Name\":\"Assisted Pelican\","
                + "\"assistPerSide\":true,\"assistRefKg\":70.0,\"favorite\":false,\"notes\":\"\"}],\"schemaVersion\":1,"
                + "\"workoutDays\":[{\"Comment\":\"\",\"Date\":\"2026-11-16\",\"DayVolume\":157.5,\"ExerciseOrder\":[\"Assisted Pelican\"],"
                + "\"Reps\":3,\"Sets\":[{\"category\":\"Biceps\",\"comment\":\"\",\"date\":\"2026-11-16\",\"exercise_name\":\"Assisted Pelican\","
                + "\"id\":0,\"isCompleted\":true,\"reps\":3.0,\"weight\":52.5}],\"SupersetGroups\":[]}]}";
        BackupManager.FullBackup fb = BackupManager.parse(v1);
        assertEquals(1, fb.getSchemaVersion());
        assertNull(fb.getEngineContent());
        assertEquals(1, fb.countSets());
        assertEquals(70.0, fb.getKnownExercises().get(0).getAssistRefKg(), 0.0);
    }

    @Test
    public void moteurIllisible_backupRefuse()
    {
        Map<String, String> files = EngineTestData.files();
        files.put(EngineContent.HISTORY, EngineTestData.HISTORY.substring(0, 40));
        EngineContent valid = EngineTestData.content();
        String json = BackupManager.toJson(ds, valid).replace(
                new com.google.gson.Gson().toJson(EngineTestData.HISTORY),
                new com.google.gson.Gson().toJson(files.get(EngineContent.HISTORY)));
        try
        {
            BackupManager.parse(json);
            fail("backup accepte a tort");
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage(), e.getMessage().contains("données du moteur du backup illisibles"));
            assertTrue(e.getMessage(), e.getMessage().contains(EngineContent.HISTORY));
        }
    }

    @Test
    public void moteurVersionInconnue_backupRefuse()
    {
        String json = BackupManager.toJson(ds, EngineTestData.content()).replace("\\\"version\\\": 1,\\n \\\"exercices\\\"", "\\\"version\\\": 7,\\n \\\"exercices\\\"");
        assertTrue(json.contains("\\\"version\\\": 7"));
        try
        {
            BackupManager.parse(json);
            fail("backup accepte a tort");
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage(), e.getMessage().contains("version 7"));
        }
    }
}
