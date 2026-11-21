package com.example.verifit;

import static org.junit.Assert.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;

// Lot M5b.3 : historique des PR (historique_pr.jsonl), annulation et trace de l'import. La
// fixture moteur/annulation_synthetique.json est la sortie REELLE de moteur_app.annuler_pr sur
// le cas « Pelican RM12 » du depot Coaching (test_fin_de_seance_app.TestAnnulationPr).
public class EnginePrHistoryTest
{
    static String resource(String name) throws IOException
    {
        InputStream in = EnginePrHistoryTest.class.getClassLoader().getResourceAsStream(name);
        assertNotNull("ressource absente : " + name, in);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0)
        {
            out.write(buf, 0, n);
        }
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static EnginePrHistory.Cancellation cancellation() throws IOException
    {
        return EnginePrHistory.parseCancellation(resource("moteur/annulation_synthetique.json"));
    }

    private static final String B1 = "2026-11-20T19:30:00";
    private static final String A1 = "2026-11-21T09:00:00";
    private static final String I1 = "2026-11-22T08:00:00";

    private static String trace()
    {
        return "{\"timestamp\": \"" + B1 + "\", \"bilan\": \"" + B1 + "\", \"origine\": \"bilan\", \"exercice\": \"Squat\", \"rm\": 8, \"ancien_kg\": 85.0, \"nouveau_kg\": 90.0, \"plans\": [], \"justification\": {\"date\": \"2026-11-20\", \"poids\": 90.0, \"reps\": 8, \"cascade\": false}}\n"
                + "{\"timestamp\": \"" + B1 + "\", \"bilan\": \"" + B1 + "\", \"origine\": \"bilan\", \"exercice\": \"Squat\", \"rm\": 7, \"ancien_kg\": null, \"nouveau_kg\": 90.0, \"plans\": [], \"justification\": null}\n"
                + "{\"timestamp\": \"" + B1 + "\", \"bilan\": \"" + B1 + "\", \"origine\": \"bilan\", \"exercice\": \"Row\", \"rm\": 5, \"ancien_kg\": 50.0, \"nouveau_kg\": 52.5, \"plans\": [], \"justification\": null}\n"
                + "{\"timestamp\": \"" + B1 + "\", \"bilan\": \"" + B1 + "\", \"origine\": \"plans_sortis\", \"plans_sortis\": []}\n"
                + "{\"timestamp\": \"" + A1 + "\", \"bilan\": \"" + A1 + "\", \"origine\": \"annulation\", \"annule\": \"" + B1 + "\", \"exercice\": \"Squat\", \"rm\": 8, \"ancien_kg\": 90.0, \"nouveau_kg\": 85.0}\n"
                + "{\"timestamp\": \"" + I1 + "\", \"bilan\": \"" + I1 + "\", \"origine\": \"import\", \"exercice\": \"Row\", \"rm\": 5, \"ancien_kg\": 52.5, \"nouveau_kg\": 51.0, \"source\": \"FitEngine_moteur.json\"}\n";
    }

    @Test
    public void historique_groupesDuPlusRecentAuPlusAncien()
    {
        List<EnginePrHistory.Group> groups = EnginePrHistory.groups(trace());
        assertEquals(4, groups.size());
        assertEquals("import", groups.get(0).origin);
        assertEquals("annulation", groups.get(1).origin);
        assertEquals(B1, groups.get(1).cancels);
        EnginePrHistory.Group row = groups.get(2);
        EnginePrHistory.Group squat = groups.get(3);
        assertEquals("Row", row.exercise);
        assertTrue(row.cancellable());
        assertEquals("Squat", squat.exercise);
        assertEquals(2, squat.changes.size());
        assertFalse("deja annule", squat.cancellable());
        assertEquals(A1, squat.cancelledAt());
        assertFalse(groups.get(0).cancellable());
        assertFalse(groups.get(1).cancellable());
        assertTrue(EnginePrHistory.groups("").isEmpty());
    }

    @Test
    public void historique_texte()
    {
        List<EnginePrHistory.Group> groups = EnginePrHistory.groups(trace());
        assertEquals("Squat\nBilan du 20/11/2026 19:30 (annulé le 21/11/2026 09:00)"
                + "\n  RM 8 : 85 kg → 90 kg (90 × 8 le 2026-11-20)\n  RM 7 : vide → 90 kg", EnginePrHistory.text(groups.get(3)));
        assertEquals("Squat\nAnnulation du 21/11/2026 09:00 (bilan du 20/11/2026 19:30)\n  RM 8 : 90 kg → 85 kg",
                EnginePrHistory.text(groups.get(1)));
        assertEquals("Row\nImport du PC du 22/11/2026 08:00 (FitEngine_moteur.json)\n  RM 5 : 52,5 kg → 51 kg",
                EnginePrHistory.text(groups.get(0)));
    }

    @Test
    public void annulation_lectureDeLaSortieDuMoteur() throws IOException
    {
        EnginePrHistory.Cancellation c = cancellation();
        assertEquals("Assisted Pelican", c.exercise);
        assertEquals("2026-09-30T16:30:39", c.bilanId);
        assertEquals(1, c.changes.size());
        assertEquals(12, c.changes.get(0).rm);
        assertEquals(53.5, c.changes.get(0).before, 0);
        assertEquals(49.0, c.changes.get(0).after, 0);
        assertEquals(1, c.correctedLines);
        assertEquals(1, c.restoredPlans.size());
        assertEquals("Seance_Volume_CycleA_2026-09-28_21-43-32.json", c.restoredPlans.get(0)[0]);
        assertEquals(1, c.warnings.size());
        assertTrue(c.journal.contains("\"correction\": \"2026-10-14 : PR annulé dans l'app (RM 12, bilan du 2026-09-30T16:30:39)\""));
        String text = EnginePrHistory.cancellationText(c);
        assertTrue(text, text.contains("  RM 12 : 53,5 kg → 49 kg\n"));
        assertTrue(text, text.contains("Volume, cycle A (plan du 28/09)"));
        assertTrue(text, text.contains("⚠ Le carnet contient toujours"));
    }

    @Test
    public void annulation_contenuEcrit() throws IOException
    {
        EnginePrHistory.Cancellation c = cancellation();
        Map<String, String> files = EngineTestData.files();
        files.put(EngineContent.HISTORY, EngineTestData.HISTORY_LINE_1 + "\n"); // une ligne, comme le journal de la fixture
        files.put(EngineContent.PR_HISTORY, trace());
        EngineContent before = EngineContent.of(files);
        EngineContent after = EnginePrHistory.apply(before, c);
        assertEquals(c.dataAfter, after.text(EngineContent.DATA));
        assertEquals(c.journal, after.text(EngineContent.HISTORY));
        assertEquals(trace() + c.prHistory.get(0), after.text(EngineContent.PR_HISTORY));
        assertEquals(2, after.count(EngineContent.PLANS));
        // Un plan deja en attente n'est pas double.
        EngineContent again = EnginePrHistory.apply(after, c);
        assertEquals(2, again.count(EngineContent.PLANS));
        // Journal corrige qui n'a pas le meme nombre de lignes : refuse, rien n'est ecrit.
        Map<String, String> two = EngineTestData.files();
        two.put(EngineContent.PR_HISTORY, trace());
        try
        {
            EnginePrHistory.apply(EngineContent.of(two), c);
            fail("journal de longueur differente accepte");
        }
        catch (IllegalArgumentException expected)
        {
            assertTrue(expected.getMessage().contains("nombre de lignes"));
        }
    }

    @Test
    public void annulation_formeInconnueRefusee() throws IOException
    {
        String ok = resource("moteur/annulation_synthetique.json");
        for (String bad : new String[] { ok.replaceFirst("\"version\": 1", "\"version\": 2"),
                ok.replaceFirst("\"origine\\\\\": \\\\\"annulation", "\"origine\\\\\": \\\\\"bilan"),
                ok.replaceFirst("\"lignes_corrigees\": 1,", ""), "{}" })
        {
            try
            {
                EnginePrHistory.parseCancellation(bad);
                fail("accepte a tort");
            }
            catch (IllegalArgumentException expected)
            {
                // attendu
            }
        }
    }

    @Test
    public void import_traceChaqueValeurChangee()
    {
        JsonObject before = EngineTestData.content().data();
        JsonObject after = EngineTestData.content().data();
        JsonArray pelican = after.getAsJsonArray("exercices").get(1).getAsJsonObject().getAsJsonArray("pr");
        pelican.get(0).getAsJsonArray().set(1, new com.google.gson.JsonPrimitive(53.0)); // RM3 52.5 -> 53.0
        pelican.remove(1);                                                               // RM8 45.0 -> vide
        JsonArray row = new JsonArray();
        row.add(10);
        row.add(40.0);
        pelican.add(row);                                                                // RM10 vide -> 40.0
        List<String> lines = EnginePrHistory.importLines(before, after, "2026-11-22T08:00:00", "FitEngine_moteur_x.json");
        assertEquals(3, lines.size());
        assertEquals("{\"timestamp\": \"2026-11-22T08:00:00\", \"bilan\": \"2026-11-22T08:00:00\", \"origine\": \"import\", "
                + "\"exercice\": \"Assisted Pelican\", \"rm\": 10, \"ancien_kg\": null, \"nouveau_kg\": 40.0, "
                + "\"source\": \"FitEngine_moteur_x.json\"}\n", lines.get(0));
        assertTrue(lines.get(1).contains("\"rm\": 8, \"ancien_kg\": 45.0, \"nouveau_kg\": null"));
        assertTrue(lines.get(2).contains("\"rm\": 3, \"ancien_kg\": 52.5, \"nouveau_kg\": 53.0"));
        assertEquals(3, EngineContent.validateJournal(lines.get(0) + lines.get(1) + lines.get(2)));
        assertTrue(EnginePrHistory.importLines(before, EngineTestData.content().data(), "t", null).isEmpty());
        // Renommage : la trace de l'import suit, comme celle des bilans.
        Map<String, String> files = EngineTestData.files();
        files.put(EngineContent.PR_HISTORY, lines.get(0));
        EngineContent renamed = EngineRename.rename(EngineContent.of(files), "Assisted Pelican", "Assisted Pelican P");
        assertTrue(renamed.text(EngineContent.PR_HISTORY).contains("\"exercice\": \"Assisted Pelican P\""));
    }

    // Fixture du bilan avec une ligne de trace de plus, d'origine donnee, en tete de historique_pr.
    private static String withExtraTraceLine(String origin) throws IOException
    {
        JsonObject o = EngineJson.parseStrictObject(EngineEndOfSessionTest.fixture());
        JsonArray trace = o.getAsJsonArray("historique_pr");
        JsonArray extended = new JsonArray();
        extended.add("{\"timestamp\": \"t\", \"bilan\": \"t\", \"origine\": \"" + origin + "\", \"plans_sortis\": []}\n");
        extended.addAll(trace);
        o.add("historique_pr", extended);
        return new com.google.gson.GsonBuilder().serializeNulls().create().toJson(o);
    }

    @Test
    public void bilan_ligneDesPlansSortisAcceptee() throws IOException
    {
        String withPlans = withExtraTraceLine("plans_sortis");
        assertEquals(9, EngineEndOfSession.parse(withPlans).prHistory.size());
        String unknown = withExtraTraceLine("autre");
        try
        {
            EngineEndOfSession.parse(unknown);
            fail("origine inconnue acceptee");
        }
        catch (IllegalArgumentException expected)
        {
            assertTrue(expected.getMessage().contains("origine inconnue"));
        }
    }

    @Test
    public void plansRemis_sansDoublon()
    {
        String plans = EngineTestData.plans("a.json");
        String once = EnginePrHistory.withPlans(plans, Collections.singletonList(new String[] { "b.json", EngineTestData.PLAN_CONTENT }));
        assertEquals(2, EngineContent.validate(EngineContent.PLANS, once));
        String twice = EnginePrHistory.withPlans(once, Collections.singletonList(new String[] { "a.json", EngineTestData.PLAN_CONTENT }));
        assertEquals(2, EngineContent.validate(EngineContent.PLANS, twice));
    }
}
