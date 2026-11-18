package com.example.verifit;

import static org.junit.Assert.*;

import com.example.verifit.model.Exercise;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

// Lot M3.4 : textes de l'ecran « Donnees du moteur » (lecture seule).
public class EngineDataViewTest
{
    @Test
    public void ordre_cyclesPuisHorsCycle()
    {
        List<Exercise> known = new ArrayList<Exercise>(Arrays.asList(
                new Exercise("Shoulder Press TG Mid Siège 5", "Shoulders"), new Exercise("Assisted Pelican", "Biceps")));
        known.get(1).setAssistRefKg(70.0);
        known.get(1).setAssistPerSide(true);
        List<EngineDataView.Row> rows = EngineDataView.rows(EngineTestData.content(), known);
        assertEquals(3, rows.size());
        assertEquals("Cycle A", rows.get(0).section);
        assertEquals("Shoulder Press TG Mid Siège 5", rows.get(0).name);
        assertEquals("Assisted Pelican", rows.get(1).name);
        assertEquals("Cycle B", rows.get(2).section);
        assertTrue(rows.get(2).subtitle.contains("INCONNU DE L'APP"));
        assertTrue(rows.get(1).detail.contains("Assistance (réglage de l'app) : 70 kg par côté"));
    }

    @Test
    public void tableau_valeursTellesQuEcrites()
    {
        List<EngineDataView.Row> rows = EngineDataView.rows(EngineTestData.content(), new ArrayList<Exercise>());
        String detail = rows.get(0).detail;
        assertTrue(detail, detail.contains("   1      34.0\n"));
        assertTrue(detail, detail.contains("   5      29.0\n"));
        assertTrue(detail, detail.contains("Cycle A, position 1"));
        assertTrue(rows.get(2).detail, rows.get(2).detail.contains("e1RM saisi"));
        assertTrue(rows.get(2).detail, rows.get(2).detail.contains("  10      40.0            -\n"));
    }

    @Test
    public void entete_selonLEtat() throws Exception
    {
        EngineTestData.MemoryFiles disk = new EngineTestData.MemoryFiles();
        EngineStorage storage = new EngineStorage(disk);
        assertTrue(EngineDataView.header(storage.load()).startsWith("Aucune donnée du moteur"));
        storage.replaceAll(EngineTestData.content(), "2026-11-17T21:00:00", "FitEngine_moteur_x.json", "2026-11-17T20:55:00");
        String h = EngineDataView.header(storage.load());
        assertTrue(h, h.startsWith("3 exercices, 6 lignes de PR, 2 lignes d'historique, 1 plan(s) en attente."));
        assertTrue(h, h.contains("Importé le 2026-11-17 21:00:00 (FitEngine_moteur_x.json), export du PC du 2026-11-17 20:55:00."));
        disk.files.remove(EngineContent.DATA);
        assertTrue(EngineDataView.header(storage.load()).startsWith("Données du moteur illisibles"));
    }
}
