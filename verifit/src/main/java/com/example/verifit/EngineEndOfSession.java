package com.example.verifit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Bilan de fin de seance (lot M5b, 21/11/2026, voir claude/fitengine-moteur-lot-M5.md) : tout
 * ce qui se fait sans Android ni Python, teste en JUnit. Le calcul est celui de la sync du PC
 * (moteur_app.fin_de_seance, depot Coaching), appele par PythonEngine.endOfSession.
 *
 * M5b.1 : lecture du texte rendu par le moteur (version 1). Les lignes de suivi par plan
 * restent des objets JSON (affichees au bilan et a l'ecran de suivi, jamais recalculees ici).
 *
 * Rien n'est ecrit au calcul : les PR, le journal et les plans ne changent qu'au « Valider »
 * du bilan (M5b.2), apres une copie automatique.
 */
public final class EngineEndOfSession
{
    public static final int RESULT_VERSION = 1;

    private EngineEndOfSession() {}

    // Horodatage des lignes de journal, meme format que le PC (isoformat a la seconde).
    public static String timestamp(Date now)
    {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(now);
    }

    // ---------------------------------------------------------------- resultat du moteur

    // Serie du carnet qui justifie un nouveau PR (null : case vide remplie par la cascade
    // depuis une valeur deja au tableau).
    public static final class Justification
    {
        public final String date;
        public final double weight;
        public final int reps;
        public final boolean cascade;

        Justification(String date, double weight, int reps, boolean cascade)
        {
            this.date = date;
            this.weight = weight;
            this.reps = reps;
            this.cascade = cascade;
        }
    }

    public static final class Change
    {
        public final int rm;
        public final Double before; // null : case vide
        public final double after;
        public final Justification justification;

        Change(int rm, Double before, double after, Justification justification)
        {
            this.rm = rm;
            this.before = before;
            this.after = after;
            this.justification = justification;
        }
    }

    public static final class ExercisePr
    {
        public final String exercise;
        public final List<Change> changes;

        ExercisePr(String exercise, List<Change> changes)
        {
            this.exercise = exercise;
            this.changes = changes;
        }
    }

    public static final class PlanReport
    {
        public final String file;
        public final String label;
        public final List<JsonObject> rows;
        public final List<String> warnings;
        public final List<JsonObject> unplanned; // series hors plan nouvelles (ecrites au Valider)
        public final List<String> unplannedWarnings;
        public final boolean complete;

        PlanReport(String file, String label, List<JsonObject> rows, List<String> warnings,
                   List<JsonObject> unplanned, List<String> unplannedWarnings, boolean complete)
        {
            this.file = file;
            this.label = label;
            this.rows = rows;
            this.warnings = warnings;
            this.unplanned = unplanned;
            this.unplannedWarnings = unplannedWarnings;
            this.complete = complete;
        }
    }

    public static final class Result
    {
        public final List<ExercisePr> prs;
        public final List<String> noSet;      // exercices sans serie dans le carnet
        public final String dataAfter;        // texte des donnees moteur avec les PR, null si aucun
        public final boolean journalOnly;
        public final List<PlanReport> plans;
        public final List<String> superseded;
        public final List<String> plansToRemove;
        public final List<String> journal;    // lignes a ajouter, saut de ligne compris
        public final int alreadyWritten;      // series prevues deja journalisees a l'identique
        public final List<String> prHistory;  // lignes de historique_pr.jsonl (une par RM modifie)
        public final JsonObject summary;      // resume du suivi (forme de update_pr_tracking)
        public final double computeMs;

        Result(List<ExercisePr> prs, List<String> noSet, String dataAfter, boolean journalOnly,
               List<PlanReport> plans, List<String> superseded, List<String> plansToRemove,
               List<String> journal, int alreadyWritten, List<String> prHistory, JsonObject summary,
               double computeMs)
        {
            this.prHistory = prHistory;
            this.prs = prs;
            this.noSet = noSet;
            this.dataAfter = dataAfter;
            this.journalOnly = journalOnly;
            this.plans = plans;
            this.superseded = superseded;
            this.plansToRemove = plansToRemove;
            this.journal = journal;
            this.alreadyWritten = alreadyWritten;
            this.summary = summary;
            this.computeMs = computeMs;
        }

        public int prCount()
        {
            int n = 0;
            for (ExercisePr p : prs)
            {
                n += p.changes.size();
            }
            return n;
        }

        // Quelque chose a ecrire au « Valider » : un PR, une ligne de journal ou un plan a
        // retirer. Sinon le bilan n'a rien de nouveau (deja valide, ou seance sans effet).
        public boolean hasChanges()
        {
            return !prs.isEmpty() || !journal.isEmpty() || !plansToRemove.isEmpty();
        }

        // "ok", "no_plan" ou "error" (suivi impossible : message dans summaryError()).
        public String summaryStatus()
        {
            return EngineJson.stringOrNull(summary, "status");
        }

        public String summaryError()
        {
            return EngineJson.stringOrNull(summary, "error");
        }
    }

    // Texte de moteur_app.fin_de_seance. Leve IllegalArgumentException si le texte n'a pas la
    // forme attendue (version, champs) : jamais un bilan partiel affiche comme complet.
    public static Result parse(String raw)
    {
        JsonObject o = EngineJson.parseStrictObject(raw);
        JsonElement version = o.get("version");
        if (!EngineJson.isInteger(version) || version.getAsInt() != RESULT_VERSION)
        {
            throw new IllegalArgumentException("bilan du moteur : version " + version + " non prise en charge");
        }
        try
        {
            List<ExercisePr> prs = new ArrayList<ExercisePr>();
            for (JsonElement e : array(o, "pr"))
            {
                JsonObject p = e.getAsJsonObject();
                List<Change> changes = new ArrayList<Change>();
                for (JsonElement c : array(p, "changements"))
                {
                    JsonObject ch = c.getAsJsonObject();
                    JsonElement before = ch.get("ancien");
                    changes.add(new Change(ch.get("rm").getAsInt(),
                            EngineJson.isNull(before) ? null : before.getAsDouble(),
                            ch.get("nouveau").getAsDouble(), justification(ch.get("justification"))));
                }
                prs.add(new ExercisePr(p.get("exercice").getAsString(), Collections.unmodifiableList(changes)));
            }
            List<PlanReport> plans = new ArrayList<PlanReport>();
            for (JsonElement e : array(o, "plans"))
            {
                JsonObject p = e.getAsJsonObject();
                plans.add(new PlanReport(p.get("fichier").getAsString(), p.get("label").getAsString(),
                        objects(array(p, "lignes")), strings(array(p, "avertissements")),
                        objects(array(p, "hors_plan")), strings(array(p, "hors_plan_avertissements")),
                        p.get("complet").getAsBoolean()));
            }
            JsonElement dataAfter = o.get("donnees_apres");
            List<String> journal = lines(array(o, "journal"));
            List<String> prHistory = lines(array(o, "historique_pr"));
            if (prHistory.size() != countChanges(prs))
            {
                throw new IllegalArgumentException("bilan du moteur : trace des PR incomplète");
            }
            if ((dataAfter == null || EngineJson.isNull(dataAfter)) != prs.isEmpty())
            {
                throw new IllegalArgumentException("bilan du moteur : données après bilan incohérentes avec les PR");
            }
            return new Result(Collections.unmodifiableList(prs),
                    strings(array(o.getAsJsonObject("rapport"), "sans_serie")),
                    EngineJson.isNull(dataAfter) ? null : dataAfter.getAsString(),
                    o.get("journal_only").getAsBoolean(), Collections.unmodifiableList(plans),
                    strings(array(o, "remplaces")), strings(array(o, "plans_a_retirer")), journal,
                    o.get("lignes_deja_ecrites").getAsInt(), prHistory, o.getAsJsonObject("resume"),
                    o.get("calcul_ms").getAsDouble());
        }
        catch (NullPointerException | IllegalStateException | ClassCastException | UnsupportedOperationException e)
        {
            throw new IllegalArgumentException("bilan du moteur illisible (" + e + ")");
        }
    }

    private static Justification justification(JsonElement e)
    {
        if (EngineJson.isNull(e))
        {
            return null;
        }
        JsonObject j = e.getAsJsonObject();
        return new Justification(j.get("date").getAsString(), j.get("poids").getAsDouble(),
                j.get("reps").getAsInt(), j.get("cascade").getAsBoolean());
    }

    private static int countChanges(List<ExercisePr> prs)
    {
        int n = 0;
        for (ExercisePr p : prs)
        {
            n += p.changes.size();
        }
        return n;
    }

    // Lignes de journal : chacune un objet JSON termine par un saut de ligne.
    private static List<String> lines(JsonArray array)
    {
        List<String> list = strings(array);
        for (String line : list)
        {
            if (!line.endsWith("\n") || line.indexOf('\n') != line.length() - 1)
            {
                throw new IllegalArgumentException("ligne de journal mal terminée");
            }
            EngineJson.parseStrictObject(line.trim());
        }
        return list;
    }

    // ---------------------------------------------------------------- « Valider » (M5b.2)

    private static final Gson GSON_PRETTY = new GsonBuilder().serializeNulls().disableHtmlEscaping()
            .setPrettyPrinting().create();

    // Contenu du moteur apres validation du bilan : donnees avec les PR (texte rendu par le
    // moteur), journal et trace des PR completes, plans remplaces ou termines retires. Rien
    // d'autre ne change. Leve IllegalArgumentException si le resultat n'est pas valide ou si un
    // plan a retirer n'est plus en attente (contenu modifie depuis le calcul) : rien n'est
    // ecrit dans ce cas.
    public static EngineContent apply(EngineContent before, Result r)
    {
        LinkedHashMap<String, String> files = new LinkedHashMap<String, String>(before.texts());
        if (r.dataAfter != null)
        {
            files.put(EngineContent.DATA, r.dataAfter);
        }
        files.put(EngineContent.HISTORY, before.text(EngineContent.HISTORY) + join(r.journal));
        files.put(EngineContent.PR_HISTORY, before.text(EngineContent.PR_HISTORY) + join(r.prHistory));
        if (!r.plansToRemove.isEmpty())
        {
            files.put(EngineContent.PLANS, withoutPlans(before.text(EngineContent.PLANS), r.plansToRemove));
        }
        return EngineContent.of(files);
    }

    static String withoutPlans(String plansText, List<String> names)
    {
        JsonObject plans = EngineJson.parseStrictObject(plansText);
        JsonArray kept = new JsonArray();
        Set<String> missing = new HashSet<String>(names);
        for (JsonElement element : plans.getAsJsonArray("plans"))
        {
            String name = element.getAsJsonObject().get("fichier").getAsString();
            if (!missing.remove(name))
            {
                kept.add(element);
            }
        }
        if (!missing.isEmpty())
        {
            throw new IllegalArgumentException("plan(s) plus en attente : " + missing);
        }
        plans.add("plans", kept);
        return GSON_PRETTY.toJson(plans) + "\n";
    }

    private static String join(List<String> lines)
    {
        StringBuilder s = new StringBuilder();
        for (String line : lines)
        {
            s.append(line);
        }
        return s.toString();
    }

    private static JsonArray array(JsonObject o, String key)
    {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonArray())
        {
            throw new IllegalArgumentException("bilan du moteur : « " + key + " » doit être une liste");
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

    private static List<JsonObject> objects(JsonArray array)
    {
        List<JsonObject> list = new ArrayList<JsonObject>();
        for (JsonElement e : array)
        {
            list.add(e.getAsJsonObject());
        }
        return Collections.unmodifiableList(list);
    }
}
