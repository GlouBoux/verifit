package com.example.verifit;

import static org.junit.Assert.*;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;

// Lot M5b.2 : ce que le « Valider » du bilan ecrit, la trace des PR (historique_pr.jsonl) dans
// le stockage, l'import, le renommage et l'ordre d'ecriture.
public class EngineEndOfSessionApplyTest
{
    private static EngineEndOfSession.Result fixtureResult() throws IOException
    {
        return EngineEndOfSession.parse(EngineEndOfSessionTest.fixture());
    }

    private static String join(List<String> lines)
    {
        StringBuilder s = new StringBuilder();
        for (String l : lines)
        {
            s.append(l);
        }
        return s.toString();
    }

    @Test
    public void valider_ajouteJournalEtTraceRemplaceLesDonnees() throws IOException
    {
        EngineContent before = EngineTestData.content();
        EngineEndOfSession.Result r = fixtureResult();
        EngineContent after = EngineEndOfSession.apply(before, r);

        assertEquals(r.dataAfter, after.text(EngineContent.DATA));
        assertEquals(before.text(EngineContent.HISTORY) + join(r.journal), after.text(EngineContent.HISTORY));
        assertEquals(join(r.prHistory), after.text(EngineContent.PR_HISTORY));
        assertEquals(8, after.count(EngineContent.PR_HISTORY));
        assertEquals(before.count(EngineContent.HISTORY) + 3, after.count(EngineContent.HISTORY));
        for (String name : new String[] { EngineContent.NEXT_PR, EngineContent.SUBSTITUTIONS, EngineContent.CANCELLED, EngineContent.PLANS })
        {
            assertEquals(name, before.text(name), after.text(name));
        }
        // Avant le calcul et apres : le contenu de depart n'est pas modifie.
        assertEquals("", before.text(EngineContent.PR_HISTORY));
    }

    @Test
    public void valider_traceAjouteeALaSuite() throws IOException
    {
        EngineEndOfSession.Result r = fixtureResult();
        EngineContent once = EngineEndOfSession.apply(EngineTestData.content(), r);
        EngineContent twice = EngineEndOfSession.apply(once, r);
        assertEquals(16, twice.count(EngineContent.PR_HISTORY));
        assertTrue(twice.text(EngineContent.PR_HISTORY).startsWith(once.text(EngineContent.PR_HISTORY)));
    }

    @Test
    public void plansRetires_seulementCeuxDemandes()
    {
        String plans = EngineTestData.plans("Seance_Force_CycleA_2026-11-16_13-28-46.json", "Seance_Volume_CycleA_2026-11-18_10-00-00.json");
        String after = EngineEndOfSession.withoutPlans(plans, Collections.singletonList("Seance_Force_CycleA_2026-11-16_13-28-46.json"));
        assertEquals(1, EngineContent.validate(EngineContent.PLANS, after));
        assertTrue(after.contains("Seance_Volume_CycleA_2026-11-18_10-00-00.json"));
        assertFalse(after.contains("Seance_Force_CycleA_2026-11-16_13-28-46.json"));
        try
        {
            EngineEndOfSession.withoutPlans(plans, Collections.singletonList("absent.json"));
            fail("plan absent accepte");
        }
        catch (IllegalArgumentException expected)
        {
            assertTrue(expected.getMessage().contains("absent.json"));
        }
    }

    // ---------------------------------------------------------------- stockage

    @Test
    public void trace_ecriteEnPremier_donneesApresLeJournal() throws IOException
    {
        final List<String> order = new ArrayList<String>();
        final EngineTestData.MemoryFiles memory = new EngineTestData.MemoryFiles();
        EngineStorage storage = new EngineStorage(new EngineStorage.Files()
        {
            @Override
            public String read(String name) throws IOException
            {
                return memory.read(name);
            }

            @Override
            public void write(String name, String text) throws IOException
            {
                order.add(name);
                memory.write(name, text);
            }
        });
        storage.replaceAll(EngineTestData.content(), "2026-11-21T10:00:00", "test", null);
        assertEquals(EngineContent.PR_HISTORY, order.get(0));
        assertTrue(order.indexOf(EngineContent.HISTORY) < order.indexOf(EngineContent.DATA));
        assertTrue(order.indexOf(EngineContent.DATA) < order.indexOf(EngineContent.PLANS));
        assertEquals(EngineState.FILE, order.get(order.size() - 1));
        assertEquals(EngineContent.FILES.size() + 1, order.size());
    }

    @Test
    public void ancienneInstallation_sansTrace_chargeeOk() throws IOException
    {
        // Donnees ecrites avant le lot M5b : pas de historique_pr.jsonl, etat sans son compteur.
        EngineTestData.MemoryFiles files = new EngineTestData.MemoryFiles();
        EngineStorage storage = new EngineStorage(files);
        storage.replaceAll(EngineTestData.content(), "2026-11-18T10:00:00", "test", null);
        files.files.remove(EngineContent.PR_HISTORY);
        EngineStorage.Loaded loaded = storage.load();
        assertEquals(EngineStorage.Status.OK, loaded.status);
        assertEquals("", loaded.content.text(EngineContent.PR_HISTORY));
    }

    @Test
    public void traceDisparueAlorsQuAttendue_bloque() throws IOException
    {
        EngineTestData.MemoryFiles files = new EngineTestData.MemoryFiles();
        EngineStorage storage = new EngineStorage(files);
        EngineContent withTrace = EngineEndOfSession.apply(EngineTestData.content(), fixtureResult());
        storage.replaceAll(withTrace, "2026-11-21T10:00:00", "test", null);
        files.files.remove(EngineContent.PR_HISTORY);
        EngineStorage.Loaded loaded = storage.load();
        assertEquals(EngineStorage.Status.BLOCKED, loaded.status);
        assertTrue(loaded.problem, loaded.problem.contains(EngineContent.PR_HISTORY));
    }

    // ---------------------------------------------------------------- import et renommage

    @Test
    public void import_gardeLaTraceDeLApp() throws IOException
    {
        EngineImport imp = EngineImport.parse(EngineImportTest.text(EngineImportTest.bundle()));
        String trace = join(fixtureResult().prHistory);
        EngineContent written = imp.contentKeeping(trace);
        assertEquals(trace, written.text(EngineContent.PR_HISTORY));
        for (String name : EngineContent.IMPORTED_FILES)
        {
            assertEquals(name, imp.content.text(name), written.text(name));
        }
        assertNull(imp.verify(written));
        assertEquals("", imp.contentKeeping(null).text(EngineContent.PR_HISTORY));
    }

    @Test
    public void import_refuseUneTraceVenantDuPc()
    {
        JsonObject b = EngineImportTest.bundle();
        b.getAsJsonObject("fichiers").addProperty(EngineContent.PR_HISTORY, "");
        try
        {
            EngineImport.parse(EngineImportTest.text(b));
            fail("trace du PC acceptee");
        }
        catch (IllegalArgumentException expected)
        {
            assertTrue(expected.getMessage().contains("inattendu"));
        }
    }

    @Test
    public void renommage_suitLaTrace() throws IOException
    {
        // La fixture parle de « Squat » : on la rattache a un exercice des donnees de test.
        String trace = join(fixtureResult().prHistory).replace("\"exercice\": \"Squat\"", "\"exercice\": \"Assisted Pelican\"");
        Map<String, String> files = EngineTestData.files();
        files.put(EngineContent.PR_HISTORY, trace);
        EngineContent renamed = EngineRename.rename(EngineContent.of(files), "Assisted Pelican", "Assisted Pelican P");
        String after = renamed.text(EngineContent.PR_HISTORY);
        assertEquals(trace.replace("\"exercice\": \"Assisted Pelican\"", "\"exercice\": \"Assisted Pelican P\""), after);
        assertFalse(after.contains("\"exercice\": \"Assisted Pelican\""));
    }

    @Test
    public void listesDeFichiers_coherentes()
    {
        assertEquals(EngineContent.FILES.size(), EngineContent.WRITE_ORDER.size());
        assertTrue(EngineContent.WRITE_ORDER.containsAll(EngineContent.FILES));
        List<String> imported = new ArrayList<String>(EngineContent.FILES);
        imported.remove(EngineContent.PR_HISTORY);
        assertEquals(imported, EngineContent.IMPORTED_FILES);
        assertTrue(EngineContent.JOURNALS.contains(EngineContent.PR_HISTORY));
        assertEquals(Arrays.asList(EngineContent.PR_HISTORY, EngineContent.HISTORY), EngineContent.WRITE_ORDER.subList(0, 2));
    }
}
