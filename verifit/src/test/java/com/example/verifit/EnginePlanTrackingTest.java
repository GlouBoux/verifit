package com.example.verifit;

import static org.junit.Assert.*;

import com.google.gson.JsonObject;

import java.io.IOException;

import org.junit.Test;

// Lot M5b.4 : ecran « Suivi des plans ». La fixture moteur/suivi_synthetique.json est la sortie
// REELLE de moteur_app.suivi sur le cas synthetique du bilan (depot Coaching,
// test_fin_de_seance_app.TestSuiviSynthetique) : PR enregistres, S1 tenue dans le carnet mais
// pas encore validee, S2 ratee a 7 reps.
public class EnginePlanTrackingTest
{
    private static String fixture() throws IOException
    {
        return EnginePrHistoryTest.resource("moteur/suivi_synthetique.json");
    }

    @Test
    public void lecture_fixture() throws IOException
    {
        EnginePlanTracking.Result r = EnginePlanTracking.parse(fixture());
        assertEquals("ok", r.status);
        assertEquals(1, r.plans.size());
        assertEquals("Seance_Force_CycleA_2026-11-19_10-00-00", r.plans.get(0).label);
        assertEquals(2, r.plans.get(0).rows.size());
        assertFalse(r.plans.get(0).complete);
        assertEquals(1, r.pending.size());
        assertEquals("Squat", r.pending.get(0).exercise);
        assertEquals(8, r.pendingCount());
        assertTrue(r.superseded.isEmpty());
    }

    @Test
    public void texte_fixture() throws IOException
    {
        String text = EnginePlanTracking.text(EnginePlanTracking.parse(fixture()));
        assertTrue(text, text.startsWith("⚠ 8 PR tenus dans le carnet pas encore validés (Squat : 8 RM)."));
        assertTrue(text, text.contains("FORCE, CYCLE A (PLAN DU 19/11)\nObjectifs atteints : 0/2\n\nSquat\n"));
        assertTrue(text, text.contains("  · S1 RM 8, ancrage\n    prévu 90 kg · PR avant 85 · PR actuel 85\n"
                + "    écart -5 kg (-5,6 %), atteint dans le carnet, PR pas encore validé par le bilan\n"));
        assertTrue(text, text.contains("  ✗ S2 RM 10, rep max\n    prévu 80 kg · PR avant 78 · PR actuel 78\n"
                + "    écart -2 kg (-2,5 %), tenu 7 (échec)"));
    }

    private static JsonObject row(String statut, String apres)
    {
        JsonObject o = new JsonObject();
        o.addProperty("exercice", "Baby Cross Hold");
        o.addProperty("serie", "S2");
        o.addProperty("methode", "S2 Pivot 20→25");
        o.addProperty("unite", "sec");
        o.addProperty("rm", 25);
        o.addProperty("charge_prevue", 12.5);
        o.add("pr_avant_plan", com.google.gson.JsonNull.INSTANCE);
        o.addProperty("pr_actuel", 12.5);
        o.addProperty("ecart_kg", 0.0);
        o.addProperty("ecart_pct", 0.0);
        o.addProperty("statut", statut);
        o.addProperty("battu_par_filet", false);
        o.add("tentative_reps", com.google.gson.JsonNull.INSTANCE);
        o.addProperty("statut_apres_bilan", apres);
        return o;
    }

    @Test
    public void ligne_battue_filet_hold_et_case_vide()
    {
        JsonObject r = row("battu", "battu");
        assertEquals("  ✓ S2 RM 25 s, pivot 20→25\n    prévu 12,5 kg · PR avant vide · PR actuel 12,5\n    écart 0 kg (0 %)\n",
                EnginePlanTracking.rowText(r));
        r.addProperty("battu_par_filet", true);
        r.addProperty("tentative_reps", 23);
        assertTrue(EnginePlanTracking.rowText(r).endsWith("    écart 0 kg (0 %), rattrapé par le filet (tenu 23 s)\n"));

        JsonObject empty = row("aucune_donnee", "aucune_donnee");
        empty.add("pr_actuel", com.google.gson.JsonNull.INSTANCE);
        empty.add("ecart_kg", com.google.gson.JsonNull.INSTANCE);
        empty.add("ecart_pct", com.google.gson.JsonNull.INSTANCE);
        assertEquals("  · S2 RM 25 s, pivot 20→25\n    prévu 12,5 kg · PR avant vide · PR actuel vide\n    pas encore tenté\n",
                EnginePlanTracking.rowText(empty));
    }

    @Test
    public void ecartsEtMethodes()
    {
        assertEquals("+2,5 kg", EnginePlanTracking.signedKg(2.5));
        assertEquals("-5 kg", EnginePlanTracking.signedKg(-5.0));
        assertEquals("+2,1 %", EnginePlanTracking.signedPct(2.083));
        assertEquals("-4,5 %", EnginePlanTracking.signedPct(-4.545));
        JsonObject o = new JsonObject();
        o.addProperty("methode", "PR Direct");
        assertEquals("PR Direct", EnginePlanTracking.method(o));
        o.addProperty("methode", "S1 Ancrage");
        assertEquals("ancrage", EnginePlanTracking.method(o));
    }

    @Test
    public void sansPlan_erreur_planRemplace()
    {
        String base = "{\"version\": 1, \"plans\": [], \"remplaces\": %s, \"pr_non_valides\": [],"
                + " \"resume\": %s, \"calcul_ms\": 2.0}";
        assertEquals("Aucun plan en attente.", EnginePlanTracking.text(EnginePlanTracking.parse(
                String.format(base, "[]", "{\"status\": \"no_plan\"}"))));
        assertEquals("⚠ Suivi impossible : plan illisible", EnginePlanTracking.text(EnginePlanTracking.parse(
                String.format(base, "[]", "{\"status\": \"error\", \"error\": \"plan illisible\"}"))));
        String text = EnginePlanTracking.text(EnginePlanTracking.parse(
                String.format(base, "[\"Seance_Force_CycleA_2026-11-10_10-00-00.json\"]", "{\"status\": \"ok\"}")));
        assertTrue(text, text.endsWith("non comparé(s) :\n  Force, cycle A (plan du 10/11)"));
    }

    @Test
    public void texteIllisibleRefuse() throws IOException
    {
        String ok = fixture();
        try
        {
            EnginePlanTracking.parse(ok.replaceFirst("\"version\": 1", "\"version\": 2"));
            fail("version 2 acceptée");
        }
        catch (IllegalArgumentException expected)
        {
            assertTrue(expected.getMessage().contains("version"));
        }
        try
        {
            EnginePlanTracking.parse(ok.replaceFirst("\"statut_apres_bilan\": \"battu\"", "\"statut_apres_bilan\": null"));
            fail("ligne sans statut_apres_bilan acceptée");
        }
        catch (IllegalArgumentException expected)
        {
            assertTrue(expected.getMessage().contains("statut_apres_bilan"));
        }
        try
        {
            EnginePlanTracking.parse(ok.replaceFirst("\"pr_non_valides\"", "\"autre\""));
            fail("texte sans pr_non_valides accepté");
        }
        catch (IllegalArgumentException expected)
        {
            assertTrue(expected.getMessage().contains("pr_non_valides"));
        }
    }
}
