package com.example.verifit;

import static org.junit.Assert.*;

import com.example.verifit.model.Exercise;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Assume;
import org.junit.Test;

// Lot M3.3 : fichier d'import des donnees du moteur ecrit par le PC (export_moteur_app.py).
public class EngineImportTest
{
    // Meme structure que celle ecrite par Python.
    static JsonObject bundle()
    {
        JsonObject b = new JsonObject();
        b.addProperty("format", "fitengine-moteur-import");
        b.addProperty("version", 1);
        b.addProperty("exporte_le", "2026-11-17T21:00:00");
        b.addProperty("source", "Tableaux de PR final.xlsx");
        JsonObject files = new JsonObject();
        for (Map.Entry<String, String> e : EngineTestData.files().entrySet())
        {
            files.addProperty(e.getKey(), e.getValue());
        }
        b.add("fichiers", files);
        JsonArray assistance = new JsonArray();
        JsonObject pelican = new JsonObject();
        pelican.addProperty("nom", "Assisted Pelican");
        pelican.addProperty("charge_ref_kg", 70.0);
        pelican.addProperty("assist_par_cote", true);
        assistance.add(pelican);
        b.add("assistance", assistance);
        JsonArray exclus = new JsonArray();
        JsonObject nordic = new JsonObject();
        nordic.addProperty("nom", "Nordic Curl");
        nordic.addProperty("raison", "pas de nom dans l'app");
        exclus.add(nordic);
        b.add("exclus", exclus);
        b.add("avertissements", new JsonArray());
        JsonObject totals = new JsonObject();
        totals.addProperty(EngineContent.DATA, 3);
        totals.addProperty("lignes_pr", 6);
        totals.addProperty(EngineContent.HISTORY, 2);
        totals.addProperty(EngineContent.NEXT_PR, 0);
        totals.addProperty(EngineContent.SUBSTITUTIONS, 0);
        totals.addProperty(EngineContent.CANCELLED, 1);
        totals.addProperty(EngineContent.PLANS, 1);
        b.add("totaux", totals);
        return b;
    }

    static String text(JsonObject b)
    {
        return new GsonBuilder().disableHtmlEscaping().create().toJson(b);
    }

    private static void assertRefused(JsonObject b, String fragment)
    {
        try
        {
            EngineImport.parse(text(b));
            fail("accepte a tort");
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage(), e.getMessage().contains(fragment));
        }
    }

    private static List<Exercise> known(String... names)
    {
        List<Exercise> list = new ArrayList<Exercise>();
        for (String n : names)
        {
            list.add(new Exercise(n, "Arms"));
        }
        return list;
    }

    @Test
    public void exportValide_lu()
    {
        EngineImport imp = EngineImport.parse(text(bundle()));
        assertEquals("2026-11-17T21:00:00", imp.exportedAt);
        assertEquals(3, imp.content.count(EngineContent.DATA));
        assertEquals(EngineTestData.HISTORY, imp.content.text(EngineContent.HISTORY));
        assertEquals(1, imp.assistance.size());
        assertEquals(70.0, imp.assistance.get(0).refKg, 0.0);
        assertEquals(Arrays.asList("Nordic Curl (pas de nom dans l'app)"), imp.excluded);
        assertNull(imp.verify(imp.content));
    }

    @Test
    public void autreFichier_refuse()
    {
        try
        {
            EngineImport.parse("Date,Exercise,Category\n");
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().contains("pas un export des données du moteur"));
        }
        JsonObject backup = new JsonObject();
        backup.addProperty("format", "fitengine-full-backup");
        assertRefused(backup, "FitEngine_moteur_");
    }

    @Test
    public void versionInconnue_refusee()
    {
        JsonObject b = bundle();
        b.addProperty("version", 2);
        assertRefused(b, "version 2");
    }

    @Test
    public void totalDifferent_refuse()
    {
        JsonObject b = bundle();
        b.getAsJsonObject("totaux").addProperty(EngineContent.HISTORY, 765);
        assertRefused(b, "le PC en annonçait 765");
        b = bundle();
        b.getAsJsonObject("totaux").addProperty("lignes_pr", 7);
        assertRefused(b, "lignes_pr");
    }

    @Test
    public void fichierManquantOuInattendu_refuse()
    {
        JsonObject b = bundle();
        b.getAsJsonObject("fichiers").remove(EngineContent.PLANS);
        assertRefused(b, "absent");
        b = bundle();
        b.getAsJsonObject("fichiers").addProperty("autre.json", "{}");
        assertRefused(b, "inattendu");
    }

    @Test
    public void journalTronque_refuse()
    {
        JsonObject b = bundle();
        b.getAsJsonObject("fichiers").addProperty(EngineContent.HISTORY, EngineTestData.HISTORY.substring(0, 60));
        assertRefused(b, EngineContent.HISTORY);
    }

    @Test
    public void nomsNonConvertis_refuses()
    {
        JsonObject b = bundle();
        String data = b.getAsJsonObject("fichiers").get(EngineContent.DATA).getAsString()
                .replace("\"nom_app\": \"Assisted Pelican\"", "\"nom_app\": \"Pelican Curl\"");
        b.getAsJsonObject("fichiers").addProperty(EngineContent.DATA, data);
        assertRefused(b, "n'est pas converti");
    }

    @Test
    public void apercu_inconnusEtAssistance()
    {
        EngineImport imp = EngineImport.parse(text(bundle()));
        // Shoulder Press (cycle A) inconnu : bloquant. Jeff Curl (cycle B) inconnu aussi.
        EngineImport.Preview p = imp.preview(known("Assisted Pelican"));
        assertTrue(p.blocking());
        assertEquals(Arrays.asList("Shoulder Press TG Mid Siège 5", "Jeff Curl 4x25"), p.unknownInCycle);
        assertTrue(imp.summary(p).contains("IMPORT IMPOSSIBLE"));
        assertEquals(1, p.toFill.size());

        List<Exercise> all = known("Assisted Pelican", "Shoulder Press TG Mid Siège 5", "Jeff Curl 4x25");
        p = imp.preview(all);
        assertFalse(p.blocking());
        assertEquals("Assisted Pelican", p.toFill.get(0).name);
        assertFalse(imp.summary(p).contains("IMPORT IMPOSSIBLE"));
        assertTrue(imp.summary(p).contains("Assisted Pelican (70 kg par côté)"));

        // Reglage deja rempli dans l'app : jamais ecrase, ecart signale.
        all.get(0).setAssistRefKg(75.0);
        all.get(0).setAssistPerSide(true);
        p = imp.preview(all);
        assertTrue(p.toFill.isEmpty());
        assertEquals(1, p.assistDifferences.size());
        assertTrue(p.assistDifferences.get(0).contains("app 75 kg par côté, PC 70 kg par côté"));

        all.get(0).setAssistRefKg(70.0);
        p = imp.preview(all);
        assertTrue(p.toFill.isEmpty());
        assertTrue(p.assistDifferences.isEmpty());
    }

    @Test
    public void verification_apresImport()
    {
        EngineImport imp = EngineImport.parse(text(bundle()));
        Map<String, String> files = EngineTestData.files();
        files.put(EngineContent.HISTORY, EngineTestData.HISTORY_LINE_1 + "\n");
        String gap = imp.verify(EngineContent.of(files));
        assertNotNull(gap);
        assertTrue(gap, gap.contains(EngineContent.HISTORY + " : 1 au lieu de 2"));
    }

    @Test
    public void planEtExportMoteur_planIgnoreSansEtreCompte()
    {
        List<PendingPlans.FileEntry<String>> entries = new ArrayList<PendingPlans.FileEntry<String>>();
        entries.add(new PendingPlans.FileEntry<String>("a", "Seance_Force_CycleA_2026-11-16_13-28-46.json", 1L, false));
        entries.add(new PendingPlans.FileEntry<String>("b", "FitEngine_moteur_2026-11-17_21-00-00.json", 2L, false));
        PendingPlans.Selection<String> s = PendingPlans.select(entries);
        assertEquals(1, s.latestPerType.size());
        assertEquals(0, s.ignoredFiles);
    }

    // Export reel du PC (donnees personnelles, hors git) : teste seulement si la variable
    // d'environnement ENGINE_IMPORT_REEL donne son chemin.
    @Test
    public void exportReelDuPc_luEtCoherent() throws Exception
    {
        String path = System.getenv("ENGINE_IMPORT_REEL");
        Assume.assumeTrue(path != null && new File(path).isFile());
        String text = new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
        long t0 = System.nanoTime();
        EngineImport imp = EngineImport.parse(text);
        long ms = (System.nanoTime() - t0) / 1000000L;
        assertNull(imp.verify(imp.content));
        EngineTestData.MemoryFiles disk = new EngineTestData.MemoryFiles();
        EngineStorage storage = new EngineStorage(disk);
        storage.replaceAll(imp.content, "t", "reel", imp.exportedAt);
        EngineStorage.Loaded loaded = storage.load();
        assertEquals(EngineStorage.Status.OK, loaded.status);
        assertNull(imp.verify(loaded.content));
        BackupManager.FullBackup fb = BackupManager.parse(BackupManager.toJson(
                TestData.storage(new String[][] { {"x", "y"} }, TestData.day(TestData.set("2026-11-16", "x", "y", 1, 1))),
                loaded.content));
        assertTrue(imp.content.sameTexts(fb.getEngineContent()));
        System.out.println("Export reel : " + imp.content.count(EngineContent.DATA) + " exercices, "
                + imp.content.prRows() + " lignes de PR, lecture " + ms + " ms, "
                + EngineDataView.rows(imp.content, new ArrayList<Exercise>()).size() + " lignes d'ecran");
    }
}
