package com.example.verifit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Historique des PR de l'app et annulation (lot M5b.3, 21/11/2026, voir
 * claude/fitengine-moteur-lot-M5.md). Classe pure (testee en JUnit), sans Android.
 *
 * historique_pr.jsonl (EngineContent.PR_HISTORY) a trois sortes de lignes de modification,
 * une par RM : « bilan » (ecrite au « Valider » du bilan de fin de seance), « annulation »
 * (champ « annule » : identifiant du bilan annule) et « import » (valeurs changees par un
 * import depuis le PC). Une ligne « plans_sortis » garde les plans qu'un bilan a fait sortir
 * de l'attente, pour pouvoir les y remettre si ses PR sont annules. Toutes les lignes sont au
 * format json.dumps de Python ({"cle": valeur, ...}) : le renommage d'un exercice
 * (EngineRename) retrouve ainsi le jeton "exercice": "...".
 *
 * L'annulation elle-meme (valeurs remises avec le plancher de la cascade, journal corrige,
 * plans remis) est calculee par Python (moteur_app.annuler_pr) ; ici : lecture de son
 * resultat, texte de confirmation et contenu a ecrire.
 */
public final class EnginePrHistory
{
    public static final int CANCEL_VERSION = 1;

    private EnginePrHistory() {}

    // ---------------------------------------------------------------- lecture de la trace

    public static final class Change
    {
        public final int rm;
        public final Double before;  // null : case vide
        public final Double after;   // null : case videe (annulation)
        public final String justification; // « 15 × 10 le 2026-01-03 », ou null

        Change(int rm, Double before, Double after, String justification)
        {
            this.rm = rm;
            this.before = before;
            this.after = after;
            this.justification = justification;
        }
    }

    public static final class Group
    {
        public final String id;        // bilan, annulation ou import : horodatage
        public final String origin;    // "bilan", "annulation", "import"
        public final String exercise;
        public final String cancels;   // annulation : identifiant du bilan annule
        public final String source;    // import : fichier du PC
        public final List<Change> changes = new ArrayList<Change>();
        String cancelledAt;            // bilan annule : horodatage de l'annulation

        Group(String id, String origin, String exercise, String cancels, String source)
        {
            this.id = id;
            this.origin = origin;
            this.exercise = exercise;
            this.cancels = cancels;
            this.source = source;
        }

        public String cancelledAt()
        {
            return cancelledAt;
        }

        // Seuls les PR d'un bilan, pas encore annules, peuvent etre annules.
        public boolean cancellable()
        {
            return "bilan".equals(origin) && cancelledAt == null;
        }
    }

    // Groupes (origine, identifiant, exercice), du plus recent au plus ancien. Une ligne
    // illisible leve IllegalArgumentException (le fichier a deja ete valide au chargement).
    public static List<Group> groups(String trace)
    {
        LinkedHashMap<String, Group> byKey = new LinkedHashMap<String, Group>();
        List<JsonObject> cancels = new ArrayList<JsonObject>();
        for (String line : EngineContent.journalLines(trace))
        {
            JsonObject ev = EngineJson.parseStrictObject(line.trim());
            String origin = EngineJson.stringOrNull(ev, "origine");
            if (!"bilan".equals(origin) && !"annulation".equals(origin) && !"import".equals(origin))
            {
                continue; // plans_sortis, ou sorte de ligne future
            }
            String id = EngineJson.stringOrNull(ev, "bilan");
            String exercise = EngineJson.stringOrNull(ev, "exercice");
            String key = origin + "|" + id + "|" + exercise;
            Group g = byKey.get(key);
            if (g == null)
            {
                g = new Group(id, origin, exercise, EngineJson.stringOrNull(ev, "annule"), EngineJson.stringOrNull(ev, "source"));
                byKey.put(key, g);
            }
            g.changes.add(new Change(ev.get("rm").getAsInt(), number(ev.get("ancien_kg")), number(ev.get("nouveau_kg")),
                    justification(ev.get("justification"))));
            if ("annulation".equals(origin))
            {
                cancels.add(ev);
            }
        }
        for (JsonObject ev : cancels)
        {
            Group target = byKey.get("bilan|" + EngineJson.stringOrNull(ev, "annule") + "|" + EngineJson.stringOrNull(ev, "exercice"));
            if (target != null && target.cancelledAt == null)
            {
                target.cancelledAt = EngineJson.stringOrNull(ev, "bilan");
            }
        }
        List<Group> list = new ArrayList<Group>(byKey.values());
        Collections.reverse(list);
        return list;
    }

    private static Double number(JsonElement e)
    {
        return e == null || e.isJsonNull() ? null : e.getAsDouble();
    }

    private static String justification(JsonElement e)
    {
        if (e == null || !e.isJsonObject())
        {
            return null;
        }
        JsonObject j = e.getAsJsonObject();
        return EngineEndOfSessionView.kg(j.get("poids").getAsDouble()).replace(" kg", "") + " × "
                + j.get("reps").getAsInt() + " le " + j.get("date").getAsString();
    }

    // « 2026-11-21T15:08:11 » -> « 21/11/2026 15:08 ».
    static String dateTime(String ts)
    {
        if (ts == null || ts.length() < 16)
        {
            return ts == null ? "?" : ts;
        }
        return ts.substring(8, 10) + "/" + ts.substring(5, 7) + "/" + ts.substring(0, 4) + " " + ts.substring(11, 16);
    }

    public static String text(Group g)
    {
        StringBuilder s = new StringBuilder(g.exercise).append('\n');
        if ("bilan".equals(g.origin))
        {
            s.append("Bilan du ").append(dateTime(g.id));
            if (g.cancelledAt != null)
            {
                s.append(" (annulé le ").append(dateTime(g.cancelledAt)).append(')');
            }
        }
        else if ("annulation".equals(g.origin))
        {
            s.append("Annulation du ").append(dateTime(g.id)).append(" (bilan du ").append(dateTime(g.cancels)).append(')');
        }
        else
        {
            s.append("Import du PC du ").append(dateTime(g.id));
            if (g.source != null)
            {
                s.append(" (").append(g.source).append(')');
            }
        }
        for (Change c : g.changes)
        {
            s.append("\n  RM ").append(c.rm).append(" : ").append(kgOrEmpty(c.before)).append(" → ").append(kgOrEmpty(c.after));
            if (c.justification != null)
            {
                s.append(" (").append(c.justification).append(')');
            }
        }
        return s.toString();
    }

    static String kgOrEmpty(Double value)
    {
        return value == null ? "vide" : EngineEndOfSessionView.kg(value);
    }

    // ---------------------------------------------------------------- annulation

    public static final class Cancellation
    {
        public final String exercise;
        public final String bilanId;
        public final List<Change> changes;   // before : valeur actuelle, after : valeur remise
        public final String dataAfter;
        public final List<String> prHistory;
        public final String journal;
        public final int correctedLines;
        public final List<String[]> restoredPlans; // {fichier, contenu}
        public final List<String> warnings;

        Cancellation(String exercise, String bilanId, List<Change> changes, String dataAfter, List<String> prHistory,
                     String journal, int correctedLines, List<String[]> restoredPlans, List<String> warnings)
        {
            this.exercise = exercise;
            this.bilanId = bilanId;
            this.changes = changes;
            this.dataAfter = dataAfter;
            this.prHistory = prHistory;
            this.journal = journal;
            this.correctedLines = correctedLines;
            this.restoredPlans = restoredPlans;
            this.warnings = warnings;
        }
    }

    // Texte de moteur_app.annuler_pr. IllegalArgumentException si la forme est inattendue.
    public static Cancellation parseCancellation(String raw)
    {
        JsonObject o = EngineJson.parseStrictObject(raw);
        JsonElement version = o.get("version");
        if (!EngineJson.isInteger(version) || version.getAsInt() != CANCEL_VERSION)
        {
            throw new IllegalArgumentException("annulation du moteur : version " + version + " non prise en charge");
        }
        try
        {
            List<Change> changes = new ArrayList<Change>();
            for (JsonElement e : o.getAsJsonArray("changements"))
            {
                JsonObject c = e.getAsJsonObject();
                changes.add(new Change(c.get("rm").getAsInt(), number(c.get("actuel")), number(c.get("remis")), null));
            }
            List<String> prHistory = new ArrayList<String>();
            for (JsonElement e : o.getAsJsonArray("historique_pr"))
            {
                String line = e.getAsString();
                JsonObject ev = EngineJson.parseStrictObject(line.trim());
                if (!line.endsWith("\n") || !"annulation".equals(EngineJson.stringOrNull(ev, "origine")))
                {
                    throw new IllegalArgumentException("ligne de trace inattendue");
                }
                prHistory.add(line);
            }
            if (changes.isEmpty() || prHistory.size() != changes.size())
            {
                throw new IllegalArgumentException("annulation du moteur : trace incomplète");
            }
            List<String[]> plans = new ArrayList<String[]>();
            for (JsonElement e : o.getAsJsonArray("plans_remis"))
            {
                JsonObject p = e.getAsJsonObject();
                plans.add(new String[] { p.get("fichier").getAsString(), p.get("contenu").getAsString() });
            }
            List<String> warnings = new ArrayList<String>();
            for (JsonElement e : o.getAsJsonArray("avertissements"))
            {
                warnings.add(e.getAsString());
            }
            return new Cancellation(o.get("exercice").getAsString(), o.get("bilan").getAsString(),
                    Collections.unmodifiableList(changes), o.get("donnees_apres").getAsString(),
                    Collections.unmodifiableList(prHistory), o.get("journal").getAsString(),
                    o.get("lignes_corrigees").getAsInt(), Collections.unmodifiableList(plans),
                    Collections.unmodifiableList(warnings));
        }
        catch (NullPointerException | IllegalStateException | ClassCastException | UnsupportedOperationException e)
        {
            throw new IllegalArgumentException("annulation du moteur illisible (" + e + ")");
        }
    }

    public static String cancellationText(Cancellation c)
    {
        StringBuilder s = new StringBuilder(c.exercise).append(", bilan du ").append(dateTime(c.bilanId)).append('\n');
        for (Change ch : c.changes)
        {
            s.append("  RM ").append(ch.rm).append(" : ").append(kgOrEmpty(ch.before)).append(" → ").append(kgOrEmpty(ch.after)).append('\n');
        }
        s.append('\n').append(c.correctedLines).append(" ligne(s) du journal des séries marquée(s) « correction ».\n");
        if (!c.restoredPlans.isEmpty())
        {
            s.append("Plan(s) remis en attente :");
            for (String[] p : c.restoredPlans)
            {
                String label = p[0].endsWith(".json") ? p[0].substring(0, p[0].length() - 5) : p[0];
                s.append("\n  ").append(EngineEndOfSessionView.planTitle(label));
            }
            s.append('\n');
        }
        for (String w : c.warnings)
        {
            s.append("\n⚠ ").append(w).append('\n');
        }
        s.append("\nRappel : corrige aussi la série dans le carnet, puis fais un bilan : il écrira la tentative réelle "
                + "(mémoire d'échec). Copie automatique avant écriture.");
        return s.toString();
    }

    // Contenu du moteur apres l'annulation : donnees, trace completee, journal corrige (meme
    // nombre de lignes, seules les lignes corrigees changent), plans remis en attente (ceux deja
    // presents ne sont pas doubles).
    public static EngineContent apply(EngineContent before, Cancellation c)
    {
        String journalBefore = before.text(EngineContent.HISTORY);
        if (EngineContent.journalLines(journalBefore).size() != EngineContent.journalLines(c.journal).size())
        {
            throw new IllegalArgumentException("journal corrigé : nombre de lignes différent, rien n'est écrit");
        }
        LinkedHashMap<String, String> files = new LinkedHashMap<String, String>(before.texts());
        files.put(EngineContent.DATA, c.dataAfter);
        files.put(EngineContent.PR_HISTORY, before.text(EngineContent.PR_HISTORY) + join(c.prHistory));
        files.put(EngineContent.HISTORY, c.journal);
        if (!c.restoredPlans.isEmpty())
        {
            files.put(EngineContent.PLANS, withPlans(before.text(EngineContent.PLANS), c.restoredPlans));
        }
        return EngineContent.of(files);
    }

    private static final Gson GSON_PRETTY = new GsonBuilder().serializeNulls().disableHtmlEscaping()
            .setPrettyPrinting().create();
    private static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();

    static String withPlans(String plansText, List<String[]> plans)
    {
        JsonObject doc = EngineJson.parseStrictObject(plansText);
        JsonArray list = doc.getAsJsonArray("plans");
        Set<String> present = new HashSet<String>();
        for (JsonElement e : list)
        {
            present.add(e.getAsJsonObject().get("fichier").getAsString());
        }
        for (String[] p : plans)
        {
            if (present.add(p[0]))
            {
                JsonObject plan = new JsonObject();
                plan.addProperty("fichier", p[0]);
                plan.addProperty("contenu", p[1]);
                list.add(plan);
            }
        }
        return GSON_PRETTY.toJson(doc) + "\n";
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

    // ---------------------------------------------------------------- import depuis le PC

    // Lignes « import » de la trace : une par RM dont la valeur change entre les donnees du
    // moteur avant et apres l'import (ajout, changement, case videe), exercice par exercice.
    // Meme format que les lignes ecrites par Python (json.dumps).
    public static List<String> importLines(JsonObject before, JsonObject after, String timestamp, String source)
    {
        Map<String, TreeMap<Integer, Double>> old = tables(before);
        Map<String, TreeMap<Integer, Double>> neu = tables(after);
        TreeSet<String> names = new TreeSet<String>(old.keySet());
        names.addAll(neu.keySet());
        List<String> lines = new ArrayList<String>();
        for (String name : names)
        {
            TreeMap<Integer, Double> a = old.containsKey(name) ? old.get(name) : new TreeMap<Integer, Double>();
            TreeMap<Integer, Double> b = neu.containsKey(name) ? neu.get(name) : new TreeMap<Integer, Double>();
            TreeSet<Integer> rms = new TreeSet<Integer>(a.keySet());
            rms.addAll(b.keySet());
            for (Integer rm : rms.descendingSet())
            {
                Double x = a.get(rm);
                Double y = b.get(rm);
                if (x == null ? y == null : (y != null && Math.abs(x - y) < 1e-9))
                {
                    continue;
                }
                lines.add("{\"timestamp\": " + str(timestamp) + ", \"bilan\": " + str(timestamp)
                        + ", \"origine\": \"import\", \"exercice\": " + str(name) + ", \"rm\": " + rm
                        + ", \"ancien_kg\": " + num(x) + ", \"nouveau_kg\": " + num(y)
                        + ", \"source\": " + (source == null ? "null" : str(source)) + "}\n");
            }
        }
        return lines;
    }

    private static Map<String, TreeMap<Integer, Double>> tables(JsonObject data)
    {
        Map<String, TreeMap<Integer, Double>> out = new LinkedHashMap<String, TreeMap<Integer, Double>>();
        if (data == null)
        {
            return out;
        }
        for (JsonElement e : data.getAsJsonArray("exercices"))
        {
            JsonObject ex = e.getAsJsonObject();
            TreeMap<Integer, Double> t = new TreeMap<Integer, Double>();
            for (JsonElement row : ex.getAsJsonArray("pr"))
            {
                JsonArray r = row.getAsJsonArray();
                t.put(r.get(0).getAsInt(), r.get(1).getAsDouble());
            }
            out.put(ex.get("nom").getAsString(), t);
        }
        return out;
    }

    // Texte JSON d'une chaine, comme json.dumps(ensure_ascii=False).
    private static String str(String value)
    {
        return GSON.toJson(value);
    }

    // Nombre comme json.dumps : 15.0, 52.5 ; null.
    private static String num(Double value)
    {
        return value == null ? "null" : Double.toString(value);
    }
}
