package com.example.verifit;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

// Lot M3.1 : stockage des donnees du moteur. Jamais de depart silencieux a vide : un fichier
// absent, vide ou illisible alors que l'etat annonce du contenu bloque, sans rien ecrire.
public class EngineStorageTest
{
    private EngineTestData.MemoryFiles disk;
    private EngineStorage storage;

    @Before
    public void setUp()
    {
        disk = new EngineTestData.MemoryFiles();
        storage = new EngineStorage(disk);
    }

    private void imported() throws IOException
    {
        storage.replaceAll(EngineTestData.content(), "2026-11-17T21:00:00", "FitEngine_moteur_test.json", "2026-11-17T20:55:00");
    }

    @Test
    public void rienDuTout_vide()
    {
        EngineStorage.Loaded l = storage.load();
        assertEquals(EngineStorage.Status.EMPTY, l.status);
        assertNull(l.content);
        assertEquals(0, disk.writes);
    }

    @Test
    public void allerRetour_textesIdentiques() throws IOException
    {
        imported();
        EngineStorage.Loaded l = storage.load();
        assertEquals(EngineStorage.Status.OK, l.status);
        assertTrue(EngineTestData.content().sameTexts(l.content));
        assertEquals(EngineTestData.DATA, disk.files.get(EngineContent.DATA));
        assertEquals("", disk.files.get(EngineContent.NEXT_PR));
        assertEquals("FitEngine_moteur_test.json", l.state.source);
        assertEquals("2026-11-17T21:00:00", l.state.importedAt);
        assertEquals("2026-11-17T20:55:00", l.state.exportedAt);
        assertEquals(2, l.state.count(EngineContent.HISTORY));
        assertFalse(l.stateRewritten);
    }

    @Test
    public void etatEcritEnDernier() throws IOException
    {
        disk.failWrite.add(EngineContent.PLANS);
        try
        {
            imported();
            fail();
        }
        catch (IOException e)
        {
            assertNull(disk.files.get(EngineState.FILE));
        }
    }

    @Test
    public void fichierDeDonneesDisparu_bloque() throws IOException
    {
        imported();
        disk.files.remove(EngineContent.DATA);
        int writes = disk.writes;
        EngineStorage.Loaded l = storage.load();
        assertEquals(EngineStorage.Status.BLOCKED, l.status);
        assertTrue(l.problem, l.problem.contains(EngineContent.DATA + " : absent"));
        assertEquals(writes, disk.writes);
    }

    @Test
    public void journalVideAlorsQueLEtatEnAnnonce_bloque() throws IOException
    {
        imported();
        disk.files.put(EngineContent.HISTORY, "");
        EngineStorage.Loaded l = storage.load();
        assertEquals(EngineStorage.Status.BLOCKED, l.status);
        assertTrue(l.problem, l.problem.contains("2 élément(s) attendu(s)"));
    }

    @Test
    public void journalDisparuAlorsQueLEtatEnAnnonce_bloque() throws IOException
    {
        imported();
        disk.files.remove(EngineContent.HISTORY);
        EngineStorage.Loaded l = storage.load();
        assertEquals(EngineStorage.Status.BLOCKED, l.status);
        assertTrue(l.problem, l.problem.contains(EngineContent.HISTORY + " : vide, 2 élément(s) attendu(s)"));
    }

    @Test
    public void journalTronque_bloqueEtGardeLeTexteBrut() throws IOException
    {
        imported();
        String cut = EngineTestData.HISTORY.substring(0, 50);
        disk.files.put(EngineContent.HISTORY, cut);
        int writes = disk.writes;
        EngineStorage.Loaded l = storage.load();
        assertEquals(EngineStorage.Status.BLOCKED, l.status);
        assertEquals(cut, l.unreadable.get(EngineContent.HISTORY));
        assertTrue(l.problem, l.problem.contains("incomplète"));
        assertEquals(writes, disk.writes);
        assertEquals(cut, disk.files.get(EngineContent.HISTORY)); // rien d'efface
    }

    @Test
    public void versionInconnue_bloque() throws IOException
    {
        imported();
        disk.files.put(EngineContent.DATA, EngineTestData.DATA.replace("\"version\": 1", "\"version\": 2"));
        EngineStorage.Loaded l = storage.load();
        assertEquals(EngineStorage.Status.BLOCKED, l.status);
        assertTrue(l.problem, l.problem.contains("version 2 non prise en charge"));
    }

    @Test
    public void lectureImpossible_bloque() throws IOException
    {
        imported();
        disk.failRead.add(EngineContent.PLANS);
        EngineStorage.Loaded l = storage.load();
        assertEquals(EngineStorage.Status.BLOCKED, l.status);
        assertTrue(l.problem, l.problem.contains("lecture impossible"));
        assertFalse(l.problem, l.problem.contains(EngineContent.PLANS + " : absent"));
    }

    @Test
    public void etatDisparu_recale() throws IOException
    {
        imported();
        disk.files.remove(EngineState.FILE);
        EngineStorage.Loaded l = storage.load();
        assertEquals(EngineStorage.Status.OK, l.status);
        assertTrue(l.stateRewritten);
        assertEquals(2, EngineState.parse(disk.files.get(EngineState.FILE)).count(EngineContent.HISTORY));
    }

    @Test
    public void etatIllisible_recale() throws IOException
    {
        imported();
        disk.files.put(EngineState.FILE, "{\"version\": 1, \"compteurs\": ");
        EngineStorage.Loaded l = storage.load();
        assertEquals(EngineStorage.Status.OK, l.status);
        assertTrue(l.stateRewritten);
    }

    @Test
    public void etatEnRetard_recale() throws IOException
    {
        imported();
        disk.files.put(EngineContent.HISTORY, EngineTestData.HISTORY + EngineTestData.HISTORY_LINE_1 + "\n");
        EngineStorage.Loaded l = storage.load();
        assertEquals(EngineStorage.Status.OK, l.status);
        assertTrue(l.stateRewritten);
        assertEquals(3, l.state.count(EngineContent.HISTORY));
        assertEquals("FitEngine_moteur_test.json", l.state.source); // origine gardee
    }

    @Test
    public void etatSansContenu_bloque() throws IOException
    {
        imported();
        for (String name : EngineContent.FILES)
        {
            disk.files.remove(name);
        }
        assertEquals(EngineStorage.Status.BLOCKED, storage.load().status);
    }

    @Test
    public void reecriture_gardeLOrigine() throws IOException
    {
        imported();
        EngineState previous = storage.load().state;
        Map<String, String> files = new HashMap<String, String>(EngineTestData.files());
        files.put(EngineContent.HISTORY, EngineTestData.HISTORY_LINE_1 + "\n");
        storage.rewrite(EngineContent.of(files), previous);
        EngineStorage.Loaded l = storage.load();
        assertEquals(EngineStorage.Status.OK, l.status);
        assertEquals(1, l.state.count(EngineContent.HISTORY));
        assertEquals("FitEngine_moteur_test.json", l.state.source);
        assertFalse(l.stateRewritten);
    }

    @Test
    public void etat_allerRetour()
    {
        Map<String, Integer> counts = new HashMap<String, Integer>();
        counts.put(EngineContent.HISTORY, 765);
        EngineState s = new EngineState("2026-11-17T21:00:00", "x.json", null, counts);
        EngineState back = EngineState.parse(s.toJson());
        assertEquals(765, back.count(EngineContent.HISTORY));
        assertEquals(0, back.count(EngineContent.PLANS));
        assertNull(back.exportedAt);
        assertEquals("x.json", back.source);
    }
}
