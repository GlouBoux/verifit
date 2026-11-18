package com.example.verifit;

import com.example.verifit.model.Exercise;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fichier d'import des donnees du moteur, ecrit sur le PC par scripts/export_moteur_app.py du
 * depot Coaching (lot M3.3, 17/11/2026) : lecture, controle, et apercu avant import.
 *
 * Format (version 1) :
 * {"format": "fitengine-moteur-import", "version": 1, "exporte_le": ..., "source": ...,
 *  "fichiers": {nom de fichier: texte exact} (EngineContent.FILES),
 *  "assistance": [{"nom", "charge_ref_kg", "assist_par_cote"}],
 *  "exclus": [{"nom", "raison"}], "avertissements": [texte], "totaux": {fichier ou "lignes_pr": n}}
 *
 * L'import REMPLACE toutes les donnees du moteur de l'app (regle de transition : l'Excel reste
 * la source jusqu'a la bascule, l'import est rejouable). L'assistance n'est pas dans les
 * donnees du moteur : elle remplit seulement le reglage de l'exercice de l'app quand il est
 * vide (decision 5 du lot M3 : l'app est la seule source de ce reglage).
 *
 * Classe pure (testee en JUnit).
 */
public final class EngineImport
{
    public static final String FORMAT = "fitengine-moteur-import";
    public static final int VERSION = 1;
    public static final String TOTAL_PR_ROWS = "lignes_pr";
    // Nom des fichiers ecrits par le PC dans Documents/FitEngine_plans (ignores par PendingPlans).
    public static final String FILE_PREFIX = "FitEngine_moteur_";

    public static final class Assistance
    {
        public final String name;
        public final Double refKg;
        public final boolean perSide;

        Assistance(String name, Double refKg, boolean perSide)
        {
            this.name = name;
            this.refKg = refKg;
            this.perSide = perSide;
        }
    }

    public final String exportedAt;
    public final String source;
    public final EngineContent content;
    public final List<Assistance> assistance;
    public final List<String> excluded;     // « Nordic Curl (raison) »
    public final List<String> warnings;     // avertissements du PC
    public final Map<String, Integer> totals;

    private EngineImport(String exportedAt, String source, EngineContent content, List<Assistance> assistance,
                         List<String> excluded, List<String> warnings, Map<String, Integer> totals)
    {
        this.exportedAt = exportedAt;
        this.source = source;
        this.content = content;
        this.assistance = Collections.unmodifiableList(assistance);
        this.excluded = Collections.unmodifiableList(excluded);
        this.warnings = Collections.unmodifiableList(warnings);
        this.totals = Collections.unmodifiableMap(totals);
    }

    // Leve IllegalArgumentException avec un message lisible ; ne modifie rien.
    public static EngineImport parse(String text)
    {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("\uFEFF"))
        {
            trimmed = trimmed.substring(1);
        }
        JsonObject root;
        try
        {
            root = EngineJson.parseStrictObject(trimmed);
        }
        catch (IllegalArgumentException e)
        {
            throw new IllegalArgumentException("ce fichier n'est pas un export des données du moteur (" + e.getMessage() + ")");
        }
        if (!FORMAT.equals(EngineJson.stringOrNull(root, "format")))
        {
            throw new IllegalArgumentException("ce fichier n'est pas un export des données du moteur (le bon fichier s'appelle "
                    + FILE_PREFIX + "<date>.json, écrit par 8_Export_moteur_app.bat)");
        }
        JsonElement version = root.get("version");
        if (!EngineJson.isInteger(version) || version.getAsInt() != VERSION)
        {
            throw new IllegalArgumentException("export de version " + version + " non pris en charge par cette version de l'app");
        }

        JsonElement filesElement = root.get("fichiers");
        if (filesElement == null || !filesElement.isJsonObject())
        {
            throw new IllegalArgumentException("export incomplet (fichiers absents)");
        }
        LinkedHashMap<String, String> files = new LinkedHashMap<String, String>();
        for (Map.Entry<String, JsonElement> e : filesElement.getAsJsonObject().entrySet())
        {
            if (!EngineContent.FILES.contains(e.getKey()))
            {
                throw new IllegalArgumentException("export : fichier inattendu « " + e.getKey() + " »");
            }
            if (!EngineJson.isString(e.getValue()))
            {
                throw new IllegalArgumentException("export : contenu illisible pour " + e.getKey());
            }
            files.put(e.getKey(), e.getValue().getAsString());
        }
        for (String name : EngineContent.FILES)
        {
            if (!files.containsKey(name))
            {
                throw new IllegalArgumentException("export incomplet (" + name + " absent)");
            }
        }
        EngineContent content = EngineContent.of(files);
        checkAppNames(content.data());

        LinkedHashMap<String, Integer> totals = readTotals(root);
        for (String name : EngineContent.FILES)
        {
            checkTotal(totals, name, content.count(name));
        }
        checkTotal(totals, TOTAL_PR_ROWS, content.prRows());

        return new EngineImport(EngineJson.stringOrNull(root, "exporte_le"), EngineJson.stringOrNull(root, "source"),
                content, readAssistance(root), readExcluded(root), readStrings(root, "avertissements"), totals);
    }

    // Dans l'app, un exercice n'a qu'un nom : celui de l'app, dans nom ET nom_app (decision 3).
    private static void checkAppNames(JsonObject data)
    {
        for (JsonElement e : data.getAsJsonArray("exercices"))
        {
            JsonObject ex = e.getAsJsonObject();
            String name = EngineJson.stringOrNull(ex, "nom");
            if (!name.equals(EngineJson.stringOrNull(ex, "nom_app")))
            {
                throw new IllegalArgumentException("export : « " + name + " » n'est pas converti au nom de l'app");
            }
        }
    }

    private static LinkedHashMap<String, Integer> readTotals(JsonObject root)
    {
        JsonElement t = root.get("totaux");
        if (t == null || !t.isJsonObject())
        {
            throw new IllegalArgumentException("export incomplet (totaux absents)");
        }
        LinkedHashMap<String, Integer> totals = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, JsonElement> e : t.getAsJsonObject().entrySet())
        {
            if (!EngineJson.isInteger(e.getValue()))
            {
                throw new IllegalArgumentException("export : total illisible pour " + e.getKey());
            }
            totals.put(e.getKey(), e.getValue().getAsInt());
        }
        return totals;
    }

    // Le fichier recu doit contenir exactement ce que le PC a compte (fichier tronque ou modifie).
    private static void checkTotal(Map<String, Integer> totals, String name, int actual)
    {
        Integer expected = totals.get(name);
        if (expected == null || expected != actual)
        {
            throw new IllegalArgumentException("export incohérent : " + name + " compte " + actual
                    + " élément(s), le PC en annonçait " + expected);
        }
    }

    private static List<Assistance> readAssistance(JsonObject root)
    {
        ArrayList<Assistance> list = new ArrayList<Assistance>();
        JsonElement a = root.get("assistance");
        if (a == null || a.isJsonNull())
        {
            return list;
        }
        if (!a.isJsonArray())
        {
            throw new IllegalArgumentException("export : assistance illisible");
        }
        for (JsonElement e : a.getAsJsonArray())
        {
            if (!e.isJsonObject())
            {
                throw new IllegalArgumentException("export : assistance illisible");
            }
            JsonObject o = e.getAsJsonObject();
            String name = EngineJson.stringOrNull(o, "nom");
            JsonElement ref = o.get("charge_ref_kg");
            JsonElement perSide = o.get("assist_par_cote");
            if (name == null || (!EngineJson.isNull(ref) && (!EngineJson.isNumber(ref) || ref.getAsDouble() <= 0))
                    || !EngineJson.isBoolean(perSide))
            {
                throw new IllegalArgumentException("export : assistance illisible pour " + name);
            }
            list.add(new Assistance(name, EngineJson.isNull(ref) ? null : ref.getAsDouble(), perSide.getAsBoolean()));
        }
        return list;
    }

    private static List<String> readExcluded(JsonObject root)
    {
        ArrayList<String> list = new ArrayList<String>();
        JsonElement x = root.get("exclus");
        if (x != null && x.isJsonArray())
        {
            for (JsonElement e : x.getAsJsonArray())
            {
                if (e.isJsonObject())
                {
                    String name = EngineJson.stringOrNull(e.getAsJsonObject(), "nom");
                    String reason = EngineJson.stringOrNull(e.getAsJsonObject(), "raison");
                    list.add(name + (reason == null ? "" : " (" + reason + ")"));
                }
            }
        }
        return list;
    }

    private static List<String> readStrings(JsonObject root, String key)
    {
        ArrayList<String> list = new ArrayList<String>();
        JsonElement x = root.get(key);
        if (x != null && x.isJsonArray())
        {
            for (JsonElement e : x.getAsJsonArray())
            {
                if (EngineJson.isString(e))
                {
                    list.add(e.getAsString());
                }
            }
        }
        return list;
    }

    // Controle apres import (lot M3.3) : les donnees RELUES sur le disque doivent avoir
    // exactement les totaux annonces par le PC. Renvoie null si tout correspond, sinon le
    // detail des ecarts.
    public String verify(EngineContent reloaded)
    {
        StringBuilder s = new StringBuilder();
        for (String name : EngineContent.FILES)
        {
            Integer expected = totals.get(name);
            if (expected == null || expected != reloaded.count(name))
            {
                s.append(name).append(" : ").append(reloaded.count(name)).append(" au lieu de ").append(expected).append('\n');
            }
        }
        Integer rows = totals.get(TOTAL_PR_ROWS);
        if (rows == null || rows != reloaded.prRows())
        {
            s.append("lignes de PR : ").append(reloaded.prRows()).append(" au lieu de ").append(rows).append('\n');
        }
        if (!content.sameTexts(reloaded))
        {
            s.append("contenu relu différent du fichier importé\n");
        }
        return s.length() == 0 ? null : s.toString();
    }

    // ---------------------------------------------------------------- apercu

    public static final class Preview
    {
        public final List<String> unknownInCycle = new ArrayList<String>();   // bloquant
        public final List<String> unknownOther = new ArrayList<String>();
        public final List<Assistance> toFill = new ArrayList<Assistance>();   // reglage vide dans l'app
        public final List<String> assistDifferences = new ArrayList<String>(); // l'app garde le sien

        public boolean blocking()
        {
            return !unknownInCycle.isEmpty();
        }
    }

    // Compare l'export aux exercices de l'app (noms exacts, comme partout dans l'app).
    public Preview preview(List<Exercise> known)
    {
        Map<String, Exercise> byName = new HashMap<String, Exercise>();
        for (Exercise e : known)
        {
            if (e != null && e.getName() != null)
            {
                byName.put(e.getName(), e);
            }
        }
        JsonObject data = content.data();
        Set<String> inCycle = new HashSet<String>();
        for (Map.Entry<String, JsonElement> cycle : data.getAsJsonObject("cycles").entrySet())
        {
            for (JsonElement n : cycle.getValue().getAsJsonArray())
            {
                inCycle.add(n.getAsString());
            }
        }
        Preview p = new Preview();
        JsonArray exercises = data.getAsJsonArray("exercices");
        for (JsonElement e : exercises)
        {
            String name = e.getAsJsonObject().get("nom").getAsString();
            if (!byName.containsKey(name))
            {
                (inCycle.contains(name) ? p.unknownInCycle : p.unknownOther).add(name);
            }
        }
        for (Assistance a : assistance)
        {
            Exercise e = byName.get(a.name);
            if (e == null)
            {
                continue;
            }
            if (e.getAssistRefKg() == null)
            {
                if (a.refKg != null)
                {
                    p.toFill.add(a);
                }
            }
            else if (!e.getAssistRefKg().equals(a.refKg) || e.isAssistPerSide() != a.perSide)
            {
                p.assistDifferences.add(a.name + " : app " + describe(e.getAssistRefKg(), e.isAssistPerSide())
                        + ", PC " + describe(a.refKg, a.perSide) + " (l'app garde le sien)");
            }
        }
        return p;
    }

    static String describe(Double refKg, boolean perSide)
    {
        if (refKg == null)
        {
            return "aucune";
        }
        String kg = refKg == Math.rint(refKg) ? String.valueOf(refKg.longValue()) : String.valueOf(refKg);
        return kg + " kg" + (perSide ? " par côté" : "");
    }

    // Texte du dialogue de confirmation.
    public String summary(Preview p)
    {
        StringBuilder s = new StringBuilder();
        s.append("Export du PC du ").append(exportedAt == null ? "?" : exportedAt.replace('T', ' '))
                .append(source == null ? "" : " (" + source + ")").append(" :\n");
        s.append("- ").append(content.count(EngineContent.DATA)).append(" exercices, ")
                .append(content.prRows()).append(" lignes de PR\n");
        s.append("- historique des séries : ").append(content.count(EngineContent.HISTORY)).append(" lignes\n");
        s.append("- rejets Next PR : ").append(content.count(EngineContent.NEXT_PR))
                .append(", remplacements : ").append(content.count(EngineContent.SUBSTITUTIONS))
                .append(", séances annulées : ").append(content.count(EngineContent.CANCELLED)).append('\n');
        s.append("- plans en attente : ").append(content.count(EngineContent.PLANS)).append('\n');
        if (!excluded.isEmpty())
        {
            s.append("\nLaissés de côté par le PC : ").append(join(excluded)).append('\n');
        }
        if (!p.unknownInCycle.isEmpty())
        {
            s.append("\nIMPORT IMPOSSIBLE : exercice(s) d'un cycle inconnu(s) de l'app : ")
                    .append(join(p.unknownInCycle)).append(". Corrige le nom dans le CSV de correspondance (PC) ou crée l'exercice dans l'app, puis refais l'export.\n");
        }
        if (!p.unknownOther.isEmpty())
        {
            s.append("\nHors cycle, inconnu(s) de l'app (importés quand même) : ").append(join(p.unknownOther)).append('\n');
        }
        if (!p.toFill.isEmpty())
        {
            s.append("\nAssistance à remplir dans l'app : ");
            ArrayList<String> names = new ArrayList<String>();
            for (Assistance a : p.toFill)
            {
                names.add(a.name + " (" + describe(a.refKg, a.perSide) + ")");
            }
            s.append(join(names)).append('\n');
        }
        if (!p.assistDifferences.isEmpty())
        {
            s.append("\nAssistance différente : ").append(join(p.assistDifferences)).append('\n');
        }
        if (!warnings.isEmpty())
        {
            s.append("\nAvertissements du PC :\n");
            for (String w : warnings)
            {
                s.append("- ").append(w).append('\n');
            }
        }
        if (!p.blocking())
        {
            s.append("\nToutes les données du moteur de l'app seront remplacées. Une copie automatique est faite avant.");
        }
        return s.toString();
    }

    private static String join(List<String> items)
    {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < items.size(); i++)
        {
            if (i > 0)
            {
                s.append(", ");
            }
            s.append(items.get(i));
        }
        return s.toString();
    }
}
