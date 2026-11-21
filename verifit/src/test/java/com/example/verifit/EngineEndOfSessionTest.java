package com.example.verifit;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.TimeZone;

import org.junit.Test;

// Lot M5b.1 : lecture du bilan rendu par moteur_app.fin_de_seance. La fixture
// moteur/bilan_synthetique.json est la sortie REELLE de Python sur un cas synthetique du depot
// Coaching (scripts/test_fin_de_seance_app.py, synthetic_case) ; un test Python verifie
// qu'elle reste a jour (contrat entre les deux cotes).
public class EngineEndOfSessionTest
{
    static String fixture() throws IOException
    {
        InputStream in = EngineEndOfSessionTest.class.getClassLoader().getResourceAsStream("moteur/bilan_synthetique.json");
        assertNotNull("fixture absente", in);
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

    @Test
    public void lecture_prJustificationsEtCascade() throws IOException
    {
        EngineEndOfSession.Result r = EngineEndOfSession.parse(fixture());
        assertEquals(1, r.prs.size());
        EngineEndOfSession.ExercisePr squat = r.prs.get(0);
        assertEquals("Squat", squat.exercise);
        assertEquals(8, squat.changes.size());
        assertEquals(8, r.prCount());

        EngineEndOfSession.Change rm15 = squat.changes.get(0);
        assertEquals(15, rm15.rm);
        assertNull(rm15.before);
        assertEquals(60.0, rm15.after, 0);
        assertFalse(rm15.justification.cascade);

        EngineEndOfSession.Change rm9 = squat.changes.get(5);
        assertEquals(9, rm9.rm);
        assertNull("case remplie par la cascade depuis le tableau", rm9.justification);

        EngineEndOfSession.Change rm8 = squat.changes.get(6);
        assertEquals(85.0, rm8.before, 0);
        assertEquals(90.0, rm8.after, 0);
        assertEquals("2026-11-20", rm8.justification.date);
        assertEquals(8, rm8.justification.reps);
        assertTrue(squat.changes.get(7).justification.cascade);
        assertEquals(1, r.noSet.size());
        assertEquals("Assisted Pelican", r.noSet.get(0));
    }

    @Test
    public void lecture_suiviJournalEtPlans() throws IOException
    {
        EngineEndOfSession.Result r = EngineEndOfSession.parse(fixture());
        assertFalse(r.journalOnly);
        assertNotNull(r.dataAfter);
        assertTrue(r.dataAfter.contains("[9, 78.0]"));
        EngineContent.validate(EngineContent.DATA, r.dataAfter);

        assertEquals(1, r.plans.size());
        EngineEndOfSession.PlanReport plan = r.plans.get(0);
        assertEquals("Seance_Force_CycleA_2026-11-19_10-00-00.json", plan.file);
        assertEquals(2, plan.rows.size());
        assertEquals("battu", plan.rows.get(0).get("statut").getAsString());
        assertEquals(7, plan.rows.get(1).get("tentative_reps").getAsInt());
        assertEquals(1, plan.unplanned.size());
        assertFalse(plan.complete);

        assertEquals(3, r.journal.size());
        assertTrue(r.journal.get(2).contains("\"hors_plan\": true"));
        assertEquals(3, EngineContent.validateJournal(r.journal.get(0) + r.journal.get(1) + r.journal.get(2)));
        assertEquals(0, r.alreadyWritten);
        assertTrue(r.plansToRemove.isEmpty());
        assertEquals("ok", r.summaryStatus());
        assertEquals(1, r.summary.get("n_echecs").getAsInt());
        assertTrue(r.hasChanges());
        assertEquals(12.5, r.computeMs, 0);
    }

    @Test
    public void rienANouveau()
    {
        String raw = "{\"version\": 1, \"pr\": [], \"rapport\": {\"sans_changement\": [], \"sans_serie\": [], \"sans_tableau\": []},"
                + " \"donnees_apres\": null, \"journal_only\": true, \"plans\": [], \"remplaces\": [], \"plans_a_retirer\": [],"
                + " \"journal\": [], \"lignes_deja_ecrites\": 4, \"historique_pr\": [], \"resume\": {\"status\": \"no_plan\"},"
                + " \"calcul_ms\": 3.0}";
        EngineEndOfSession.Result r = EngineEndOfSession.parse(raw);
        assertFalse(r.hasChanges());
        assertNull(r.dataAfter);
        assertEquals(4, r.alreadyWritten);
        assertEquals("no_plan", r.summaryStatus());
    }

    @Test
    public void versionOuFormeInconnues_refusees() throws IOException
    {
        String ok = fixture();
        for (String bad : new String[] {
                ok.replaceFirst("\"version\": 1", "\"version\": 2"),
                ok.replaceFirst("\"plans_a_retirer\": \\[\\]", "\"plans_a_retirer\": null"),
                ok.replaceFirst("\"journal_only\": false,", ""),
                ok.replaceFirst("\\\\n\",\n  \"\\{", "\",\n  \"{"),
                ok.replaceFirst("\"historique_pr\": \\[\n  \"[^\n]*\n", "\"historique_pr\": [\n"),
                ok.replaceFirst("\"donnees_apres\": \"[^\n]*\",\n", "\"donnees_apres\": null,\n"),
                "[]" })
        {
            try
            {
                EngineEndOfSession.parse(bad);
                fail("accepte a tort");
            }
            catch (IllegalArgumentException expected)
            {
                // attendu
            }
        }
    }

    @Test
    public void horodatage_formatDuPc()
    {
        TimeZone saved = TimeZone.getDefault();
        try
        {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            assertEquals("1970-01-01T00:00:01", EngineEndOfSession.timestamp(new Date(1000L)));
        }
        finally
        {
            TimeZone.setDefault(saved);
        }
    }
}
