package com.example.verifit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ecran « Suivi des plans » (lot M5b.4, 21/11/2026) : l'equivalent de l'onglet Suivi_PR du PC,
 * par plan en attente, prevu contre realise. Lecture du texte de moteur_app.suivi et texte de
 * l'ecran. Classe pure (testee en JUnit), sans Android.
 *
 * Decisions de Romain (21/11/2026) : comparaison aux PR ENREGISTRES de l'app (un objectif tenu
 * dans le carnet mais pas encore valide par le bilan est signale comme tel, avec un renvoi au
 * bilan) ; detail par serie comme l'onglet Suivi_PR (methode, prevu, PR avant le plan, PR
 * actuel, ecart en kg et en %, statut), groupe par exercice. L'ecran n'ecrit rien.
 */
public final class EnginePlanTracking
{
    public static final int RESULT_VERSION = 1;

    private static final Pattern SERIE_PREFIX = Pattern.compile("^S\\d+\\s+");

    private EnginePlanTracking() {}

    public static final class Plan
    {
        public final String file;
        public final String label;
        public final List<JsonObject> rows;
        public final List<String> warnings;
        public final boolean complete;

        Plan(String file, String label, List<JsonObject> rows, List<String> warnings, boolean complete)
        {
            this.file = file;
            this.label = label;
            this.rows = rows;
            this.warnings = warnings;
            this.complete = complete;
        }
    }

    // PR tenus dans le carnet mais pas encore ecrits par un bilan valide.
    public static final class Pending
    {
        public final String exercise;
        public final List<Integer> rms;

        Pending(String exercise, List<Integer> rms)
        {
            this.exercise = exercise;
            this.rms = rms;
        }
    }

    public static final class Result
    {
        public final String status;      // "ok", "no_plan" ou "error"
        public final String error;
        public final List<Plan> plans;
        public final List<String> superseded;
        public final List<Pending> pending;
        public final double computeMs;

        Result(String status, String error, List<Plan> plans, List<String> superseded, List<Pending> pending,
               double computeMs)
        {
            this.status = status;
            this.error = error;
            this.plans = plans;
            this.superseded = superseded;
            this.pending = pending;
            this.computeMs = computeMs;
        }

        public int pendingCount()
        {
            int n = 0;
            for (Pending p : pending)
            {
                n += p.rms.size();
            }
            return n;
        }
    }

    // Texte de moteur_app.suivi. IllegalArgumentException si le texte n'a pas la forme attendue.
    public static Result parse(String raw)
    {
        JsonObject o = EngineJson.parseStrictObject(raw);
        JsonElement version = o.get("version");
        if (!EngineJson.isInteger(version) || version.getAsInt() != RESULT_VERSION)
        {
            throw new IllegalArgumentException("suivi du moteur : version " + version + " non prise en charge");
        }
        try
        {
            List<Plan> plans = new ArrayList<Plan>();
            for (JsonElement e : array(o, "plans"))
            {
                JsonObject p = e.getAsJsonObject();
                List<JsonObject> rows = new ArrayList<JsonObject>();
                for (JsonElement r : array(p, "lignes"))
                {
                    JsonObject row = r.getAsJsonObject();
                    // Champs dont l'ecran a besoin : absents = texte illisible, jamais une ligne inventee.
                    for (String key : new String[] {"exercice", "serie", "rm", "charge_prevue", "statut", "statut_apres_bilan"})
                    {
                        if (EngineJson.isNull(row.get(key)))
                        {
                            throw new IllegalArgumentException("suivi du moteur : champ " + key + " absent");
                        }
                    }
                    rows.add(row);
                }
                plans.add(new Plan(p.get("fichier").getAsString(), p.get("label").getAsString(),
                        Collections.unmodifiableList(rows), strings(array(p, "avertissements")),
                        p.get("complet").getAsBoolean()));
            }
            List<Pending> pending = new ArrayList<Pending>();
            for (JsonElement e : array(o, "pr_non_valides"))
            {
                JsonObject p = e.getAsJsonObject();
                List<Integer> rms = new ArrayList<Integer>();
                for (JsonElement rm : array(p, "rms"))
                {
                    rms.add(rm.getAsInt());
                }
                pending.add(new Pending(p.get("exercice").getAsString(), Collections.unmodifiableList(rms)));
            }
            JsonObject summary = o.getAsJsonObject("resume");
            String status = EngineJson.stringOrNull(summary, "status");
            if (status == null)
            {
                throw new IllegalArgumentException("suivi du moteur : résumé sans statut");
            }
            return new Result(status, EngineJson.stringOrNull(summary, "error"), Collections.unmodifiableList(plans),
                    strings(array(o, "remplaces")), Collections.unmodifiableList(pending), o.get("calcul_ms").getAsDouble());
        }
        catch (NullPointerException | IllegalStateException | ClassCastException | UnsupportedOperationException e)
        {
            throw new IllegalArgumentException("suivi du moteur illisible (" + e + ")");
        }
    }

    // Texte de l'ecran.
    public static String text(Result r)
    {
        StringBuilder s = new StringBuilder();
        if (!r.pending.isEmpty())
        {
            s.append("⚠ ").append(r.pendingCount()).append(" PR tenus dans le carnet pas encore validés (");
            for (int i = 0; i < r.pending.size(); i++)
            {
                Pending p = r.pending.get(i);
                s.append(i > 0 ? ", " : "").append(p.exercise).append(" : ").append(p.rms.size()).append(" RM");
            }
            s.append("). Le suivi compare aux PR enregistrés : lance le bilan (Réglages > Moteur > "
                    + "Bilan de fin de séance) pour les enregistrer.\n\n");
        }
        if ("no_plan".equals(r.status))
        {
            s.append("Aucun plan en attente.\n");
        }
        else if ("error".equals(r.status))
        {
            s.append("⚠ Suivi impossible : ").append(r.error).append('\n');
        }
        for (Plan plan : r.plans)
        {
            plan(s, plan);
        }
        if (!r.superseded.isEmpty())
        {
            s.append("Remplacé(s) par un plan plus récent du même type, non comparé(s) :\n");
            for (String name : r.superseded)
            {
                String label = name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
                s.append("  ").append(EngineEndOfSessionView.planTitle(label)).append('\n');
            }
        }
        return s.toString().trim();
    }

    private static void plan(StringBuilder s, Plan plan)
    {
        s.append(EngineEndOfSessionView.planTitle(plan.label).toUpperCase(Locale.FRANCE)).append('\n');
        int done = 0;
        int filet = 0;
        for (JsonObject row : plan.rows)
        {
            if ("battu".equals(str(row, "statut")))
            {
                done++;
                filet += bool(row, "battu_par_filet") ? 1 : 0;
            }
        }
        s.append("Objectifs atteints : ").append(done).append('/').append(plan.rows.size());
        if (filet > 0)
        {
            s.append(" (dont ").append(filet).append(" par le filet)");
        }
        s.append(plan.complete ? ", plan entièrement réalisé" : "").append('\n');
        String exercise = null;
        for (JsonObject row : plan.rows)
        {
            String name = str(row, "exercice");
            if (!name.equals(exercise))
            {
                s.append('\n').append(name).append('\n');
                exercise = name;
            }
            s.append(rowText(row));
        }
        for (String w : plan.warnings)
        {
            s.append("⚠ ").append(w).append('\n');
        }
        s.append('\n');
    }

    // Trois lignes par serie :
    //   « ✓ S1 RM 8, ancrage »
    //   « prévu 120 kg · PR avant 117,5 · PR actuel 122,5 »
    //   « écart +2,5 kg (+2,1 %) » (ou l'etat de la serie quand il n'y a pas d'ecart a donner)
    static String rowText(JsonObject row)
    {
        String unit = "sec".equals(str(row, "unite")) ? " s" : "";
        String statut = str(row, "statut");
        boolean battu = "battu".equals(statut);
        Integer attempt = integerOrNull(row, "tentative_reps");
        boolean failed = !battu && attempt != null;
        String symbol = battu ? "✓" : failed ? "✗" : "·";

        StringBuilder s = new StringBuilder();
        s.append("  ").append(symbol).append(' ').append(str(row, "serie")).append(" RM ")
                .append(row.get("rm").getAsInt()).append(unit);
        String method = method(row);
        if (!method.isEmpty())
        {
            s.append(", ").append(method);
        }
        s.append('\n');
        s.append("    prévu ").append(EngineEndOfSessionView.kg(num(row, "charge_prevue")))
                .append(" · PR avant ").append(bare(row, "pr_avant_plan"))
                .append(" · PR actuel ").append(bare(row, "pr_actuel")).append('\n');

        List<String> parts = new ArrayList<String>();
        if (!EngineJson.isNull(row.get("ecart_kg")))
        {
            parts.add("écart " + signedKg(num(row, "ecart_kg"))
                    + (EngineJson.isNull(row.get("ecart_pct")) ? "" : " (" + signedPct(num(row, "ecart_pct")) + ")"));
        }
        if (battu && bool(row, "battu_par_filet"))
        {
            parts.add("rattrapé par le filet (tenu " + attempt + unit + ")");
        }
        else if (failed)
        {
            parts.add("tenu " + attempt + unit + " (échec)");
        }
        else if (!battu)
        {
            parts.add("battu".equals(str(row, "statut_apres_bilan"))
                    ? "atteint dans le carnet, PR pas encore validé par le bilan"
                    : "pas encore tenté");
        }
        s.append("    ").append(String.join(", ", parts)).append('\n');
        return s.toString();
    }

    // « S1 Ancrage » -> « ancrage », « Pivot 10→12 » -> « pivot 10→12 ».
    static String method(JsonObject row)
    {
        String m = SERIE_PREFIX.matcher(str(row, "methode").trim()).replaceFirst("");
        if (m.isEmpty())
        {
            return m;
        }
        Matcher upperWord = Pattern.compile("^[A-Z][a-zéèàù]").matcher(m);
        return upperWord.find() ? Character.toLowerCase(m.charAt(0)) + m.substring(1) : m;
    }

    // Poids sans unite (« 117,5 »), « vide » si la case n'a pas de PR.
    private static String bare(JsonObject row, String key)
    {
        return EngineJson.isNull(row.get(key)) ? "vide" : EngineEndOfSessionView.kg(num(row, key)).replace(" kg", "");
    }

    static String signedKg(double value)
    {
        if (Math.abs(value) < 1e-9)
        {
            return "0 kg";
        }
        return (value > 0 ? "+" : "-") + EngineEndOfSessionView.kg(Math.abs(value));
    }

    static String signedPct(double value)
    {
        String text = String.format(Locale.FRANCE, "%.1f", Math.abs(value));
        if ("0,0".equals(text))
        {
            return "0 %";
        }
        return (value > 0 ? "+" : "-") + text + " %";
    }

    private static JsonArray array(JsonObject o, String key)
    {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonArray())
        {
            throw new IllegalArgumentException("suivi du moteur : champ " + key + " absent");
        }
        return e.getAsJsonArray();
    }

    private static List<String> strings(JsonArray array)
    {
        List<String> list = new ArrayList<String>();
        for (JsonElement e : array)
        {
            list.add(e.getAsString());
        }
        return Collections.unmodifiableList(list);
    }

    private static String str(JsonObject o, String key)
    {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static double num(JsonObject o, String key)
    {
        return o.get(key).getAsDouble();
    }

    private static Integer integerOrNull(JsonObject o, String key)
    {
        JsonElement e = o.get(key);
        return EngineJson.isNull(e) ? null : e.getAsInt();
    }

    private static boolean bool(JsonObject o, String key)
    {
        JsonElement e = o.get(key);
        return e != null && !e.isJsonNull() && e.getAsBoolean();
    }
}
