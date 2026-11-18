package com.example.verifit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generer une seance dans l'app (lot M4, 18/11/2026) : tout ce qui se calcule sans Android ni
 * Python, teste en JUnit. L'appel au moteur est dans PythonEngine.generate, l'ecran dans
 * ui/GenerateSessionFlow, l'apercu reste celui de l'import (ImportSessionActivity).
 *
 * - withAssistance : le fichier donnees_moteur.json de l'app a charge_ref_kg a null et
 *   assist_par_cote a false (decision 5 du lot M3 : l'Exercise de l'app est la seule source).
 *   Avant le calcul, on y remet les valeurs de l'app, sinon les commentaires d'assistance
 *   (RULES.md 6.10) disparaissent du plan.
 * - defaultChoice : cycle et type proposes, d'apres la derniere seance du carnet dont le
 *   commentaire est un libelle de plan (« Cycle B - Volume ») : meme cycle, autre type.
 * - fileName / withPendingPlan : le plan importe est garde dans plans_en_attente.json (M4.3,
 *   equivalent de a_comparer/ du PC), avec le meme nom qu'un plan du PC ; un plan plus ancien
 *   du meme type ET du meme cycle (meme prefixe, regle de pr_tracking.py) est retire.
 * - parseResult : lecture du texte rendu par moteur_app.generer_seance.
 * - fingerprint : empreinte courte du plan, la meme que celle affichee par generate_workout.py.
 */
public final class EngineSession
{
    public static final String FORCE = "Force";
    public static final String VOLUME = "Volume";
    public static final int FINGERPRINT_LENGTH = 12;

    // Nom des plans du PC et de l'app : Seance_<Type>_Cycle<X>_<AAAA-MM-JJ_HH-MM-SS>.json
    private static final Pattern PLAN_NAME =
            Pattern.compile("^Seance_(Force|Volume)_Cycle([A-Za-z0-9]+)_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}\\.json$");
    // Libelle du plan, commentaire du jour apres import (format_session_label du moteur).
    private static final Pattern SESSION_LABEL = Pattern.compile("^Cycle ([A-Za-z0-9]+) - (Force|Volume)$");

    // null garde dans le texte (charge_ref_kg: null est obligatoire dans le contrat), pas
    // d'echappement HTML (noms d'exercices avec apostrophe).
    private static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
    private static final Gson GSON_PRETTY = new GsonBuilder().serializeNulls().disableHtmlEscaping()
            .setPrettyPrinting().create();

    private EngineSession() {}

    // ---------------------------------------------------------------- assistance

    public static final class Assist
    {
        public final Double refKg;
        public final boolean perSide;

        public Assist(Double refKg, boolean perSide)
        {
            this.refKg = refKg;
            this.perSide = perSide;
        }
    }

    // Texte des donnees moteur avec l'assistance de l'app remise sur chaque exercice qui en a
    // une (refKg non null). Les autres exercices et tout le reste du texte sont gardes tels quels
    // (nombres reecrits a l'identique par Gson). Rend le texte d'origine si rien ne change.
    public static String withAssistance(String dataJson, Map<String, Assist> byName)
    {
        JsonObject data = JsonParser.parseString(dataJson).getAsJsonObject();
        boolean changed = false;
        for (JsonElement element : data.getAsJsonArray("exercices"))
        {
            JsonObject exercise = element.getAsJsonObject();
            Assist assist = byName.get(exercise.get("nom").getAsString());
            if (assist == null || assist.refKg == null)
            {
                continue;
            }
            exercise.add("charge_ref_kg", new JsonPrimitive(assist.refKg));
            exercise.add("assist_par_cote", new JsonPrimitive(assist.perSide));
            changed = true;
        }
        return changed ? GSON.toJson(data) : dataJson;
    }

    // Cycles des donnees moteur, dans l'ordre du fichier (« A », « B »).
    public static List<String> cycles(String dataJson)
    {
        JsonObject data = JsonParser.parseString(dataJson).getAsJsonObject();
        return new ArrayList<String>(data.getAsJsonObject("cycles").keySet());
    }

    // ---------------------------------------------------------------- choix par defaut

    public static final class Choice
    {
        public final String cycle;
        public final String type;
        // Libelle de la derniere seance reconnue (« Cycle B - Volume du 2026-11-16 »), null sinon.
        public final String basedOn;

        Choice(String cycle, String type, String basedOn)
        {
            this.cycle = cycle;
            this.type = type;
            this.basedOn = basedOn;
        }
    }

    // commentsByDate : commentaire de chaque jour du carnet (cle AAAA-MM-JJ). On prend le jour
    // le plus recent dont le commentaire est un libelle de plan connu ; on propose le meme
    // cycle (un cycle dure plusieurs semaines) et l'autre type (Force et Volume alternent).
    // Rien de reconnu : premier cycle, Force.
    public static Choice defaultChoice(Map<String, String> commentsByDate, List<String> cycles)
    {
        String first = cycles.isEmpty() ? "A" : cycles.get(0);
        String bestDate = null;
        Matcher best = null;
        for (Map.Entry<String, String> day : commentsByDate.entrySet())
        {
            String comment = day.getValue() == null ? "" : day.getValue().trim();
            Matcher m = SESSION_LABEL.matcher(comment);
            if (!m.matches() || !cycles.contains(m.group(1)))
            {
                continue;
            }
            if (bestDate == null || day.getKey().compareTo(bestDate) > 0)
            {
                bestDate = day.getKey();
                best = m;
            }
        }
        if (best == null)
        {
            return new Choice(first, FORCE, null);
        }
        String other = FORCE.equals(best.group(2)) ? VOLUME : FORCE;
        return new Choice(best.group(1), other, best.group(0) + " du " + bestDate);
    }

    // ---------------------------------------------------------------- plans en attente

    public static String fileName(String type, String cycle, Date now)
    {
        return "Seance_" + type + "_Cycle" + cycle + "_"
                + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(now) + ".json";
    }

    public static final class PendingUpdate
    {
        public final String text;
        public final List<String> replaced;

        PendingUpdate(String text, List<String> replaced)
        {
            this.text = text;
            this.replaced = replaced;
        }
    }

    // Ajoute le plan a plans_en_attente.json et retire les plans du meme type et du meme cycle
    // (prefixe Seance_<Type>_Cycle<X>_), comme pr_tracking.py qui ne compare que le plus recent
    // de chaque type. Un plan deja present sous le meme nom est remplace.
    public static PendingUpdate withPendingPlan(String plansText, String fileName, String planText)
    {
        Matcher m = PLAN_NAME.matcher(fileName);
        if (!m.matches())
        {
            throw new IllegalArgumentException("nom de plan non reconnu : " + fileName);
        }
        String prefix = "Seance_" + m.group(1) + "_Cycle" + m.group(2) + "_";
        JsonObject plans = JsonParser.parseString(plansText).getAsJsonObject();
        JsonArray kept = new JsonArray();
        List<String> replaced = new ArrayList<String>();
        for (JsonElement element : plans.getAsJsonArray("plans"))
        {
            String name = element.getAsJsonObject().get("fichier").getAsString();
            if (name.startsWith(prefix) || name.equals(fileName))
            {
                replaced.add(name);
            }
            else
            {
                kept.add(element);
            }
        }
        JsonObject plan = new JsonObject();
        plan.addProperty("fichier", fileName);
        plan.addProperty("contenu", planText.replace("\r\n", "\n"));
        kept.add(plan);
        plans.add("plans", kept);
        return new PendingUpdate(GSON_PRETTY.toJson(plans) + "\n", Collections.unmodifiableList(replaced));
    }

    // ---------------------------------------------------------------- resultat du moteur

    public static final class Result
    {
        public final String plan;
        public final List<String> ignored;
        public final List<String> warnings;
        public final double computeMs;

        Result(String plan, List<String> ignored, List<String> warnings, double computeMs)
        {
            this.plan = plan;
            this.ignored = ignored;
            this.warnings = warnings;
            this.computeMs = computeMs;
        }
    }

    // Texte de moteur_app.generer_seance : {"plan", "exercices_ignores", "avertissements", "calcul_ms"}.
    public static Result parseResult(String raw)
    {
        JsonObject o = JsonParser.parseString(raw).getAsJsonObject();
        return new Result(o.get("plan").getAsString(), strings(o.getAsJsonArray("exercices_ignores")),
                strings(o.getAsJsonArray("avertissements")), o.get("calcul_ms").getAsDouble());
    }

    private static List<String> strings(JsonArray array)
    {
        List<String> list = new ArrayList<String>();
        if (array != null)
        {
            for (JsonElement e : array)
            {
                list.add(e.getAsString());
            }
        }
        return Collections.unmodifiableList(list);
    }

    // Lignes ajoutees en tete de l'apercu pour un plan genere dans l'app : avertissements du
    // moteur (exercice sans serie, e1RM introuvable) et exercices sans nom d'app.
    public static String previewNotes(Result result)
    {
        StringBuilder text = new StringBuilder();
        for (String w : result.warnings)
        {
            text.append("⚠ Exercice sans série (non inclus) : ").append(w).append('\n');
        }
        if (!result.ignored.isEmpty())
        {
            text.append("⚠ Sans nom d'app (non inclus) : ");
            for (int i = 0; i < result.ignored.size(); i++)
            {
                text.append(i == 0 ? "" : ", ").append(result.ignored.get(i));
            }
            text.append('\n');
        }
        return text.toString();
    }

    // ---------------------------------------------------------------- empreinte

    // Debut de ImportedPlans.hash (SHA-256 du texte canonique) : meme valeur que
    // plan_fingerprint de generate_workout.py sur le PC.
    public static String fingerprint(String planText)
    {
        return ImportedPlans.hash(planText).substring(0, FINGERPRINT_LENGTH);
    }
}
