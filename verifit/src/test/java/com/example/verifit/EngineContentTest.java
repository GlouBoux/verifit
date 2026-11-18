package com.example.verifit;

import static org.junit.Assert.*;

import java.util.Map;

import org.junit.Test;

// Lot M3.1 : validation des fichiers du moteur (lecture stricte, comptes, refus lisibles).
public class EngineContentTest
{
    private static void assertRefused(String file, String text, String expectedFragment)
    {
        Map<String, String> files = EngineTestData.files();
        files.put(file, text);
        try
        {
            EngineContent.of(files);
            fail("accepte a tort : " + text);
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage(), e.getMessage().startsWith(file + " : "));
            assertTrue(e.getMessage(), e.getMessage().contains(expectedFragment));
        }
    }

    @Test
    public void contenuValide_comptes()
    {
        EngineContent c = EngineTestData.content();
        assertEquals(3, c.count(EngineContent.DATA));
        assertEquals(6, c.prRows());
        assertEquals(2, c.count(EngineContent.HISTORY));
        assertEquals(0, c.count(EngineContent.NEXT_PR));
        assertEquals(1, c.count(EngineContent.CANCELLED));
        assertEquals(1, c.count(EngineContent.PLANS));
        assertEquals(EngineTestData.HISTORY, c.text(EngineContent.HISTORY));
    }

    @Test
    public void journalAbsent_vautJournalVide()
    {
        Map<String, String> files = EngineTestData.files();
        files.remove(EngineContent.SUBSTITUTIONS);
        EngineContent c = EngineContent.of(files);
        assertEquals("", c.text(EngineContent.SUBSTITUTIONS));
        assertEquals(0, c.count(EngineContent.SUBSTITUTIONS));
    }

    @Test
    public void donneesAbsentes_refusees()
    {
        Map<String, String> files = EngineTestData.files();
        files.remove(EngineContent.DATA);
        try
        {
            EngineContent.of(files);
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().contains("absent"));
        }
    }

    @Test
    public void journalCRLF_accepte()
    {
        Map<String, String> files = EngineTestData.files();
        files.put(EngineContent.HISTORY, EngineTestData.HISTORY_LINE_1 + "\r\n" + EngineTestData.HISTORY_LINE_2 + "\r\n");
        assertEquals(2, EngineContent.of(files).count(EngineContent.HISTORY));
    }

    @Test
    public void journalCoupe_refuse()
    {
        String cut = EngineTestData.HISTORY.substring(0, EngineTestData.HISTORY.length() - 30);
        assertRefused(EngineContent.HISTORY, cut, "incomplète");
        assertRefused(EngineContent.HISTORY, EngineTestData.HISTORY_LINE_1 + "\n{\"rm\": \n", "ligne 2");
    }

    @Test
    public void journalLigneVide_refusee()
    {
        assertRefused(EngineContent.HISTORY, EngineTestData.HISTORY_LINE_1 + "\n\n", "ligne 2");
    }

    @Test
    public void journalNaN_refuse()
    {
        assertRefused(EngineContent.HISTORY, "{\"marge_pct\": NaN}\n", "ligne 1");
        assertRefused(EngineContent.HISTORY, "{\"marge_pct\": 1e400}\n", "non numérique");
    }

    @Test
    public void versionInconnue_refusee()
    {
        assertRefused(EngineContent.DATA, EngineTestData.DATA.replace("\"version\": 1", "\"version\": 2"), "version");
        assertRefused(EngineContent.PLANS, EngineTestData.plans("a.json").replace("\"version\": 1", "\"version\": 9"), "version");
    }

    @Test
    public void exerciceIncomplet_refuse()
    {
        assertRefused(EngineContent.DATA, EngineTestData.DATA.replaceFirst("   \"barre_base_kg\": null,\n", ""), "sans barre_base_kg");
    }

    @Test
    public void exerciceEnDouble_refuse()
    {
        assertRefused(EngineContent.DATA, EngineTestData.DATA.replace("\"Jeff Curl 4x25\"", "\"Assisted Pelican\""), "en double");
    }

    @Test
    public void cycleAvecExerciceInconnu_refuse()
    {
        assertRefused(EngineContent.DATA, EngineTestData.DATA.replace("   \"Jeff Curl 4x25\"\n  ]", "   \"Inconnu\"\n  ]"), "exercice inconnu");
    }

    @Test
    public void ligneDePrInvalide_refusee()
    {
        assertRefused(EngineContent.DATA, EngineTestData.DATA.replace("[1, 34.0]", "[0, 34.0]"), "RM invalide");
        assertRefused(EngineContent.DATA, EngineTestData.DATA.replace("[1, 34.0]", "[1.5, 34.0]"), "RM invalide");
        assertRefused(EngineContent.DATA, EngineTestData.DATA.replace("[1, 34.0]", "[1, -34.0]"), "poids invalide");
        assertRefused(EngineContent.DATA, EngineTestData.DATA.replace("[1, 34.0]", "[1, \"34\"]"), "poids invalide");
        assertRefused(EngineContent.DATA, EngineTestData.DATA.replace("[1, 34.0]", "[1]"), "ligne de PR invalide");
        assertRefused(EngineContent.DATA, EngineTestData.DATA.replace("[1, 34.0]", "[1, NaN]"), "JSON invalide");
    }

    @Test
    public void typesDesReglages_controles()
    {
        assertRefused(EngineContent.DATA, EngineTestData.DATA.replaceFirst("\"is_hold\": false", "\"is_hold\": \"Non\""), "is_hold");
        assertRefused(EngineContent.DATA, EngineTestData.DATA.replaceFirst("\"e1rm_fenetre_reps\": null", "\"e1rm_fenetre_reps\": 2.5"), "e1rm_fenetre_reps");
        assertRefused(EngineContent.DATA, EngineTestData.DATA.replaceFirst("\"charge_min_kg\": null", "\"charge_min_kg\": \"30\""), "charge_min_kg");
    }

    @Test
    public void texteEnTrop_refuse()
    {
        assertRefused(EngineContent.DATA, EngineTestData.DATA + "}", "texte en trop");
    }

    @Test
    public void plans_doublonEtContenuIllisible_refuses()
    {
        assertRefused(EngineContent.PLANS, EngineTestData.plans("a.json", "a.json"), "en double");
        assertRefused(EngineContent.PLANS, "{\"version\": 1, \"plans\": [{\"fichier\": \"a.json\", \"contenu\": \"{pas du json\"}]}", "plan « a.json »");
    }

    @Test
    public void lignesDuJournal()
    {
        assertEquals(2, EngineContent.journalLines(EngineTestData.HISTORY).size());
        assertEquals(EngineTestData.HISTORY_LINE_2, EngineContent.journalLines(EngineTestData.HISTORY).get(1));
        assertTrue(EngineContent.journalLines("").isEmpty());
    }
}
