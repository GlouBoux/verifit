package com.example.verifit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// Lot M4 : generer une seance dans l'app (EngineSession).
public class EngineSessionTest
{
    private static JsonObject exercise(String dataJson, String name)
    {
        for (com.google.gson.JsonElement e : JsonParser.parseString(dataJson).getAsJsonObject().getAsJsonArray("exercices"))
        {
            if (e.getAsJsonObject().get("nom").getAsString().equals(name))
            {
                return e.getAsJsonObject();
            }
        }
        throw new AssertionError("exercice absent : " + name);
    }

    // ---------------------------------------------------------------- assistance

    @Test
    public void assistance_remiseSurLesSeulsExercicesAssistes()
    {
        Map<String, EngineSession.Assist> assist = new HashMap<String, EngineSession.Assist>();
        assist.put("Assisted Pelican", new EngineSession.Assist(70.0, true));
        assist.put("Exercice absent des donnees", new EngineSession.Assist(80.0, false));
        assist.put("Jeff Curl 4x25", new EngineSession.Assist(null, true)); // pas d'assistance

        String out = EngineSession.withAssistance(EngineTestData.DATA, assist);

        JsonObject pelican = exercise(out, "Assisted Pelican");
        assertEquals(70.0, pelican.get("charge_ref_kg").getAsDouble(), 0.0);
        assertTrue(pelican.get("assist_par_cote").getAsBoolean());
        JsonObject jeff = exercise(out, "Jeff Curl 4x25");
        assertTrue(jeff.get("charge_ref_kg").isJsonNull());
        assertFalse(jeff.get("assist_par_cote").getAsBoolean());
        // null garde dans le texte (champ obligatoire du contrat) et lignes de PR intactes.
        assertTrue(out.contains("\"charge_ref_kg\":null"));
        assertEquals("[[10,40.0,null]]", jeff.get("pr").toString());
        assertEquals("[[3,52.5],[8,45.0]]", pelican.get("pr").toString());
        // Toujours un fichier de donnees moteur valide (3 exercices).
        assertEquals(3, EngineContent.validate(EngineContent.DATA, out));
        // Seuls ces deux champs changent.
        JsonObject before = JsonParser.parseString(EngineTestData.DATA).getAsJsonObject();
        JsonObject after = JsonParser.parseString(out).getAsJsonObject();
        JsonObject pelicanBefore = exercise(EngineTestData.DATA, "Assisted Pelican");
        pelicanBefore.add("charge_ref_kg", pelican.get("charge_ref_kg"));
        pelicanBefore.add("assist_par_cote", pelican.get("assist_par_cote"));
        before.getAsJsonArray("exercices").set(1, pelicanBefore);
        assertEquals(before, after);
    }

    @Test
    public void assistance_aucune_texteInchange()
    {
        String out = EngineSession.withAssistance(EngineTestData.DATA, Collections.<String, EngineSession.Assist>emptyMap());
        assertSame(EngineTestData.DATA, out);
    }

    @Test
    public void cycles_dansLOrdreDuFichier()
    {
        assertEquals(Arrays.asList("A", "B"), EngineSession.cycles(EngineTestData.DATA));
    }

    // ---------------------------------------------------------------- choix par defaut

    @Test
    public void choixParDefaut_memeCycleAutreType()
    {
        Map<String, String> days = new HashMap<String, String>();
        days.put("2026-11-16", "Cycle B - Volume");
        days.put("2026-11-19", "Cycle B - Force");
        days.put("2026-11-20", "jambes lourdes");
        days.put("2026-11-21", "Cycle C - Volume"); // cycle inconnu des donnees
        days.put("2026-11-22", "");
        EngineSession.Choice c = EngineSession.defaultChoice(days, Arrays.asList("A", "B"));
        assertEquals("B", c.cycle);
        assertEquals("Volume", c.type);
        assertEquals("Cycle B - Force du 2026-11-19", c.basedOn);

        days.put("2026-11-25", " Cycle A - Volume ");
        c = EngineSession.defaultChoice(days, Arrays.asList("A", "B"));
        assertEquals("A", c.cycle);
        assertEquals("Force", c.type);
    }

    @Test
    public void choixParDefaut_rienDeReconnu()
    {
        EngineSession.Choice c = EngineSession.defaultChoice(new HashMap<String, String>(), Arrays.asList("A", "B"));
        assertEquals("A", c.cycle);
        assertEquals("Force", c.type);
        assertNull(c.basedOn);
    }

    // ---------------------------------------------------------------- plans en attente

    @Test
    public void nomDuPlan_commeLePc()
    {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.NOVEMBER, 18, 7, 5, 9);
        assertEquals("Seance_Volume_CycleB_2026-11-18_07-05-09.json",
                EngineSession.fileName("Volume", "B", cal.getTime()));
    }

    @Test
    public void planEnAttente_remplaceLeMemeTypeEtLeMemeCycleSeulement()
    {
        String plans = EngineTestData.plans(
                "Seance_Force_CycleA_2026-11-01_10-00-00.json",
                "Seance_Volume_CycleA_2026-11-05_10-00-00.json",
                "Seance_Force_CycleB_2026-11-10_10-00-00.json",
                "Seance_Force_CycleA_2026-11-12_10-00-00.json");
        String plan = "{\r\n  \"comment\": \"Cycle A - Force\",\r\n  \"exercises\": []\r\n}";
        EngineSession.PendingUpdate up = EngineSession.withPendingPlan(plans,
                "Seance_Force_CycleA_2026-11-18_12-00-00.json", plan);

        assertEquals(Arrays.asList("Seance_Force_CycleA_2026-11-01_10-00-00.json",
                "Seance_Force_CycleA_2026-11-12_10-00-00.json"), up.replaced);
        assertEquals(3, EngineContent.validate(EngineContent.PLANS, up.text));
        JsonArray list = JsonParser.parseString(up.text).getAsJsonObject().getAsJsonArray("plans");
        assertEquals("Seance_Volume_CycleA_2026-11-05_10-00-00.json", list.get(0).getAsJsonObject().get("fichier").getAsString());
        assertEquals("Seance_Force_CycleB_2026-11-10_10-00-00.json", list.get(1).getAsJsonObject().get("fichier").getAsString());
        JsonObject added = list.get(2).getAsJsonObject();
        assertEquals("Seance_Force_CycleA_2026-11-18_12-00-00.json", added.get("fichier").getAsString());
        assertEquals("{\n  \"comment\": \"Cycle A - Force\",\n  \"exercises\": []\n}", added.get("contenu").getAsString());
        // Contenu des plans gardes inchange.
        assertEquals(EngineTestData.PLAN_CONTENT, list.get(0).getAsJsonObject().get("contenu").getAsString());
        assertTrue(up.text.endsWith("}\n"));
    }

    @Test
    public void planEnAttente_listeVide()
    {
        EngineSession.PendingUpdate up = EngineSession.withPendingPlan("{\"version\": 1, \"plans\": []}\n",
                "Seance_Volume_CycleB_2026-11-18_12-00-00.json", EngineTestData.PLAN_CONTENT);
        assertTrue(up.replaced.isEmpty());
        assertEquals(1, EngineContent.validate(EngineContent.PLANS, up.text));
    }

    @Test
    public void planEnAttente_nomInconnuRefuse()
    {
        try
        {
            EngineSession.withPendingPlan(EngineTestData.plans(), "plan.json", "{}");
            fail();
        }
        catch (IllegalArgumentException expected)
        {
            assertTrue(expected.getMessage().contains("plan.json"));
        }
    }

    // ---------------------------------------------------------------- resultat du moteur

    @Test
    public void resultat_luEtNotesDApercu()
    {
        String raw = "{\"plan\": \"{\\n  \\\"comment\\\": \\\"Cycle A - Force\\\"\\n}\", "
                + "\"exercices_ignores\": [\"Nordic Curl\", \"RDL\"], "
                + "\"avertissements\": [\"Pull Down TG : e1RM introuvable\"], \"calcul_ms\": 412.7}";
        EngineSession.Result r = EngineSession.parseResult(raw);
        assertEquals("{\n  \"comment\": \"Cycle A - Force\"\n}", r.plan);
        assertEquals(Arrays.asList("Nordic Curl", "RDL"), r.ignored);
        assertEquals(412.7, r.computeMs, 0.0);
        String notes = EngineSession.previewNotes(r);
        assertTrue(notes.contains("Pull Down TG : e1RM introuvable"));
        assertTrue(notes.contains("Nordic Curl, RDL"));

        EngineSession.Result clean = EngineSession.parseResult(
                "{\"plan\": \"{}\", \"exercices_ignores\": [], \"avertissements\": [], \"calcul_ms\": 1}");
        assertEquals("", EngineSession.previewNotes(clean));
    }

    // ---------------------------------------------------------------- empreinte

    @Test
    public void empreinte_memeValeurQueLePc()
    {
        // Meme texte et meme valeur que test_moteur_app.TestEmpreintePlan (plan_fingerprint).
        String plan = "{\n  \"comment\": \"Cycle A - Force\"\n}";
        assertEquals("9565e42d1838", EngineSession.fingerprint(plan));
        assertEquals("9565e42d1838", EngineSession.fingerprint(plan.replace("\n", "\r\n") + "\r\n"));
    }
}
