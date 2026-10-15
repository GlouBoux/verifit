package com.example.verifit;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Import idempotent (15/10/2026) : empreinte d'un plan, registre des plans importes et
 * accuse lu par le PC (ImportedPlans, logique pure). Les empreintes attendues sont
 * calculees a part, en Python (hashlib.sha256), pas par le code teste.
 */
public class ImportedPlansTest
{
    // Petit plan au format de generate_workout.py (fins de ligne "\n").
    private static final String PLAN_LF =
            "{\n  \"comment\": \"Cycle A - Force\",\n  \"date\": \"2026-10-16\",\n  \"exercises\": []\n}";
    // hashlib.sha256(PLAN_LF.encode("utf-8")).hexdigest()
    private static final String PLAN_SHA256 = "9ab1e226928138cd91112ec1bfd7d6929ad40dbcdc8a93cc9158ac1e49ff1dab";

    private static ImportedPlans.Entry entry(String sha, String fileName, String importedAt, boolean forced)
    {
        return new ImportedPlans.Entry(sha, fileName, "2026-10-16", "Cycle A - Force", importedAt, 12, forced);
    }

    // ---------------------------------------------------------------- empreinte

    @Test
    public void hashMatchesStandardSha256()
    {
        // Vecteur de test officiel SHA-256 ("abc").
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ImportedPlans.hash("abc"));
    }

    @Test
    public void hashEncodesUtf8()
    {
        // hashlib.sha256("Séance".encode("utf-8")).hexdigest()
        assertEquals("c1574e19f27f0f9271f864377c5bddceb10a3b25bf0f3d4f7b5d4bf0bdce6185", ImportedPlans.hash("Séance"));
    }

    @Test
    public void hashOfPlanMatchesPythonValue()
    {
        assertEquals(PLAN_SHA256, ImportedPlans.hash(PLAN_LF));
    }

    @Test
    public void hashIgnoresLineEndingsTrailingNewlinesAndBom()
    {
        // Fichier Windows (CRLF), relu par TextFiles.readAll() (lignes + "\n" final),
        // ou partage comme texte avec un BOM : meme plan, meme empreinte.
        String crlf = PLAN_LF.replace("\n", "\r\n") + "\r\n";
        String readAllStyle = PLAN_LF + "\n";
        String withBom = "﻿" + PLAN_LF;
        assertEquals(PLAN_SHA256, ImportedPlans.hash(crlf));
        assertEquals(PLAN_SHA256, ImportedPlans.hash(readAllStyle));
        assertEquals(PLAN_SHA256, ImportedPlans.hash(withBom));
        assertEquals(PLAN_SHA256, ImportedPlans.hash(PLAN_LF.replace("\n", "\r")));
    }

    @Test
    public void hashChangesWhenContentChanges()
    {
        String otherDate = PLAN_LF.replace("2026-10-16", "2026-10-17");
        assertNotEquals(PLAN_SHA256, ImportedPlans.hash(otherDate));
        // Un espace en plus compte : seules les fins de ligne sont normalisees.
        assertNotEquals(PLAN_SHA256, ImportedPlans.hash(PLAN_LF.replace("\"date\":", "\"date\" :")));
    }

    @Test
    public void hashOfNullIsHashOfEmptyText()
    {
        // hashlib.sha256(b"").hexdigest()
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", ImportedPlans.hash(null));
    }

    // ---------------------------------------------------------------- registre

    @Test
    public void unknownPlanIsNotFound()
    {
        ImportedPlans plans = ImportedPlans.empty();
        assertNull(plans.find(PLAN_SHA256));
        assertNull(plans.find(null));
    }

    @Test
    public void importedPlanIsFoundWhateverTheFileName()
    {
        ImportedPlans plans = ImportedPlans.empty();
        plans.add(entry(PLAN_SHA256, "Seance_Force_CycleA_2026-10-15_19-39-29.json", "2026-10-15T19:45:00", false));

        // La recherche se fait sur l'empreinte seule : le meme contenu sous un autre nom
        // (plan regenere a l'identique) est reconnu.
        ImportedPlans.Entry found = plans.find(ImportedPlans.hash(PLAN_LF.replace("\n", "\r\n")));
        assertEquals("Seance_Force_CycleA_2026-10-15_19-39-29.json", found.fileName);
        assertNull(plans.find(ImportedPlans.hash(PLAN_LF.replace("Force", "Volume"))));
    }

    @Test
    public void forcedReimportBecomesTheLatestEntry()
    {
        ImportedPlans plans = ImportedPlans.empty();
        plans.add(entry(PLAN_SHA256, "a.json", "2026-10-15T19:45:00", false));
        plans.add(entry(PLAN_SHA256, "a.json", "2026-10-16T07:10:00", true));

        assertEquals(2, plans.size());
        assertEquals("2026-10-16T07:10:00", plans.find(PLAN_SHA256).importedAt);
        assertTrue(plans.find(PLAN_SHA256).forced);
    }

    @Test
    public void entryWithoutHashIsIgnored()
    {
        ImportedPlans plans = ImportedPlans.empty();
        plans.add(entry("", "a.json", "2026-10-15T19:45:00", false));
        plans.add(null);
        assertEquals(0, plans.size());
    }

    @Test
    public void registryKeepsOnlyTheMostRecentEntries()
    {
        ImportedPlans plans = ImportedPlans.empty();
        for (int i = 0; i < ImportedPlans.MAX_ENTRIES + 3; i++)
        {
            plans.add(entry("sha" + i, "plan" + i + ".json", "2026-10-15T19:45:00", false));
        }
        assertEquals(ImportedPlans.MAX_ENTRIES, plans.size());
        assertEquals("sha" + (ImportedPlans.MAX_ENTRIES + 2), plans.getEntries().get(0).sha256);
        assertNull(plans.find("sha0"));
        assertNull(plans.find("sha2"));
        assertEquals("plan3.json", plans.find("sha3").fileName);
    }

    // ---------------------------------------------------------------- JSON / accuse

    @Test
    public void jsonRoundTripKeepsEveryField()
    {
        ImportedPlans plans = ImportedPlans.empty();
        plans.add(entry(PLAN_SHA256, "Seance_Force_CycleA_2026-10-15_19-39-29.json", "2026-10-15T19:45:00", false));
        plans.add(new ImportedPlans.Entry("abc", "", "2026-10-17", "Cycle B - Volume", "2026-10-17T08:00:00", 9, true));

        ImportedPlans reread = ImportedPlans.fromJson(plans.toJson("2026-10-17T08:00:01"));

        assertEquals(2, reread.size());
        ImportedPlans.Entry latest = reread.getEntries().get(0);
        assertEquals("abc", latest.sha256);
        assertEquals("", latest.fileName);
        assertEquals("2026-10-17", latest.sessionDate);
        assertEquals("Cycle B - Volume", latest.comment);
        assertEquals("2026-10-17T08:00:00", latest.importedAt);
        assertEquals(9, latest.setsImported);
        assertTrue(latest.forced);
        ImportedPlans.Entry first = reread.find(PLAN_SHA256);
        assertEquals("Seance_Force_CycleA_2026-10-15_19-39-29.json", first.fileName);
        assertEquals(12, first.setsImported);
        assertFalse(first.forced);
    }

    @Test
    public void ackJsonHasKindAndVersionButNoDaysKey()
    {
        ImportedPlans plans = ImportedPlans.empty();
        plans.add(entry(PLAN_SHA256, "a.json", "2026-10-15T19:45:00", false));
        String json = plans.toJson("2026-10-15T19:45:01");

        assertTrue(json.contains("\"kind\": \"fitengine-plan-imports\""));
        assertTrue(json.contains("\"schemaVersion\": 1"));
        assertTrue(json.contains("\"updatedAt\": \"2026-10-15T19:45:01\""));
        assertTrue(json.contains("\"sha256\": \"" + PLAN_SHA256 + "\""));
        // Sans cle "days", sync_verifit_to_excel.py ne peut pas le prendre pour l'export
        // des seances (looks_like_verifit_export).
        assertFalse(json.contains("\"days\""));
    }

    @Test
    public void unreadableOrForeignJsonGivesAnEmptyRegistry()
    {
        assertEquals(0, ImportedPlans.fromJson(null).size());
        assertEquals(0, ImportedPlans.fromJson("").size());
        assertEquals(0, ImportedPlans.fromJson("{ pas du json").size());
        assertEquals(0, ImportedPlans.fromJson("[1, 2]").size());
        // Un export de seances (autre "kind") n'est pas un registre.
        assertEquals(0, ImportedPlans.fromJson("{\"schemaVersion\": 1, \"days\": []}").size());
    }

    @Test
    public void emptyFactoryReturnsDistinctRegistries()
    {
        ImportedPlans a = ImportedPlans.empty();
        a.add(entry("x", "x.json", "2026-10-15T19:45:00", false));
        assertEquals(0, ImportedPlans.empty().size());
        assertSame(a.find("x"), a.getEntries().get(0));
    }

    @Test
    public void fileAndFolderNamesSharedWithThePc()
    {
        // Noms lus ou ecrits cote PC (Syncthing, Coaching) : ne pas les changer sans
        // adapter SYNC_FITNOTES_APP.md et la configuration Syncthing.
        assertEquals("fitengine_plan_imports.json", ImportedPlansStore.ACK_FILE_NAME);
        assertEquals("FitEngine_plans", AppNames.PLANS_FOLDER);
        assertEquals("FitEngine", AppNames.EXPORT_FOLDER);
    }

    // ---------------------------------------------------------------- affichage

    @Test
    public void importedAtIsShownAsDayMonthYearAndTime()
    {
        assertEquals("15/10/2026 à 19:45", ImportedPlans.formatImportedAt("2026-10-15T19:45:12"));
        assertEquals("hier", ImportedPlans.formatImportedAt("hier"));
        assertEquals("", ImportedPlans.formatImportedAt(null));
    }

    @Test
    public void previousImportDescriptionNamesDateSessionAndFile()
    {
        String text = ImportedPlans.describePreviousImport(
                entry(PLAN_SHA256, "Seance_Force_CycleA_2026-10-15_19-39-29.json", "2026-10-15T19:45:00", false));
        assertEquals("Ce plan a déjà été importé le 15/10/2026 à 19:45 (séance du 2026-10-16).\n"
                + "Fichier : Seance_Force_CycleA_2026-10-15_19-39-29.json", text);

        String noFile = ImportedPlans.describePreviousImport(
                new ImportedPlans.Entry(PLAN_SHA256, "", "", "", "2026-10-15T19:45:00", 1, false));
        assertEquals("Ce plan a déjà été importé le 15/10/2026 à 19:45.", noFile);
    }
}
