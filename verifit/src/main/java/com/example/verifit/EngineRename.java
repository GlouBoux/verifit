package com.example.verifit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renommage et suppression d'un exercice dans les donnees du moteur (lot M3.5, 17/11/2026),
 * appeles par l'app quand l'exercice est renomme (Edit) ou supprime : un seul nom par
 * exercice, celui de l'app (decision 7 du lot M3, meme perimetre que rename_exercise.py du
 * depot Coaching).
 *
 * Renommer touche : l'exercice et les listes de cycles (donnees moteur), le champ d'exercice
 * de chaque journal (historique des series, rejets Next PR, remplacements) et le nom des
 * exercices des plans en attente. Les seances annulees restent telles quelles (historique).
 * Dans les journaux et les plans, seul le jeton "champ": "nom" change (remplacement exact,
 * meme ecriture que json.dumps de Python, controle par relecture), le reste de la ligne est
 * garde octet pour octet. Les donnees moteur et le fichier des plans sont reecrits en entier
 * (Python les relit, leur mise en page n'a pas d'importance).
 *
 * Supprimer retire l'exercice et sa place dans les cycles ; journaux et plans gardent leurs
 * lignes (historique, comme sur le PC).
 *
 * Classe pure (testee en JUnit). Leve IllegalArgumentException sans rien modifier.
 */
public final class EngineRename
{
    // Journal -> champs portant un nom d'exercice (journaux absents : recopies tels quels).
    static final Map<String, List<String>> JOURNAL_FIELDS;
    static
    {
        LinkedHashMap<String, List<String>> m = new LinkedHashMap<String, List<String>>();
        m.put(EngineContent.HISTORY, Collections.singletonList("exercice"));
        m.put(EngineContent.PR_HISTORY, Collections.singletonList("exercice")); // lot M5b.2
        m.put(EngineContent.NEXT_PR, Collections.singletonList("exercise"));
        m.put(EngineContent.SUBSTITUTIONS, Arrays.asList("original_exercise", "substitute_exercise"));
        JOURNAL_FIELDS = Collections.unmodifiableMap(m);
    }

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping().create();

    private EngineRename() {}

    // Vrai si l'exercice figure dans les donnees moteur.
    public static boolean hasExercise(EngineContent content, String name)
    {
        return findExercise(content.data(), name) != null;
    }

    // Contenu apres renommage, ou le meme objet si le nom n'apparait nulle part.
    public static EngineContent rename(EngineContent content, String oldName, String newName)
    {
        if (oldName == null || newName == null || oldName.equals(newName))
        {
            return content;
        }
        JsonObject data = content.data();
        if (findExercise(data, newName) != null)
        {
            throw new IllegalArgumentException("« " + newName + " » existe déjà dans les données du moteur");
        }
        boolean changed = false;
        LinkedHashMap<String, String> files = new LinkedHashMap<String, String>(content.texts());

        JsonObject ex = findExercise(data, oldName);
        if (ex != null)
        {
            ex.addProperty("nom", newName);
            ex.addProperty("nom_app", newName);
            for (Map.Entry<String, JsonElement> cycle : data.getAsJsonObject("cycles").entrySet())
            {
                JsonArray names = cycle.getValue().getAsJsonArray();
                JsonArray renamed = new JsonArray();
                for (JsonElement n : names)
                {
                    renamed.add(oldName.equals(n.getAsString()) ? newName : n.getAsString());
                }
                cycle.setValue(renamed);
            }
            files.put(EngineContent.DATA, PRETTY.toJson(data) + "\n");
            changed = true;
        }

        for (Map.Entry<String, List<String>> journal : JOURNAL_FIELDS.entrySet())
        {
            String before = files.get(journal.getKey());
            String after = renameInJournal(before, journal.getValue(), oldName, newName, journal.getKey());
            if (!after.equals(before))
            {
                files.put(journal.getKey(), after);
                changed = true;
            }
        }

        String plans = files.get(EngineContent.PLANS);
        String renamedPlans = renameInPlans(plans, oldName, newName);
        if (renamedPlans != null)
        {
            files.put(EngineContent.PLANS, renamedPlans);
            changed = true;
        }
        return changed ? EngineContent.of(files) : content;
    }

    // Contenu apres suppression de l'exercice, ou le meme objet s'il n'y est pas.
    public static EngineContent delete(EngineContent content, String name)
    {
        JsonObject data = content.data();
        JsonObject ex = findExercise(data, name);
        if (ex == null)
        {
            return content;
        }
        JsonArray exercises = data.getAsJsonArray("exercices");
        JsonArray kept = new JsonArray();
        for (JsonElement e : exercises)
        {
            if (e != ex)
            {
                kept.add(e);
            }
        }
        data.add("exercices", kept);
        for (Map.Entry<String, JsonElement> cycle : data.getAsJsonObject("cycles").entrySet())
        {
            JsonArray names = new JsonArray();
            for (JsonElement n : cycle.getValue().getAsJsonArray())
            {
                if (!name.equals(n.getAsString()))
                {
                    names.add(n);
                }
            }
            cycle.setValue(names);
        }
        LinkedHashMap<String, String> files = new LinkedHashMap<String, String>(content.texts());
        files.put(EngineContent.DATA, PRETTY.toJson(data) + "\n");
        return EngineContent.of(files);
    }

    // « 14 lignes de PR, cycle A » : ce que la suppression retire, ou null si rien.
    public static String describeDeletion(EngineContent content, String name)
    {
        JsonObject data = content.data();
        JsonObject ex = findExercise(data, name);
        if (ex == null)
        {
            return null;
        }
        StringBuilder s = new StringBuilder();
        s.append(ex.getAsJsonArray("pr").size()).append(" ligne(s) de PR");
        List<String> cycles = new ArrayList<String>();
        for (Map.Entry<String, JsonElement> cycle : data.getAsJsonObject("cycles").entrySet())
        {
            for (JsonElement n : cycle.getValue().getAsJsonArray())
            {
                if (name.equals(n.getAsString()))
                {
                    cycles.add("cycle " + cycle.getKey());
                }
            }
        }
        if (!cycles.isEmpty())
        {
            s.append(", sa place dans le ").append(cycles.get(0));
            for (int i = 1; i < cycles.size(); i++)
            {
                s.append(" et le ").append(cycles.get(i));
            }
        }
        return s.toString();
    }

    private static JsonObject findExercise(JsonObject data, String name)
    {
        for (JsonElement e : data.getAsJsonArray("exercices"))
        {
            if (name.equals(e.getAsJsonObject().get("nom").getAsString()))
            {
                return e.getAsJsonObject();
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- journaux

    static String renameInJournal(String text, List<String> fields, String oldName, String newName, String label)
    {
        if (text.isEmpty())
        {
            return text;
        }
        String[] lines = text.substring(0, text.length() - 1).split("\n", -1);
        StringBuilder out = new StringBuilder(text.length() + 64);
        boolean changed = false;
        for (int i = 0; i < lines.length; i++)
        {
            String line = lines[i];
            String eol = "\n";
            if (line.endsWith("\r"))
            {
                line = line.substring(0, line.length() - 1);
                eol = "\r\n";
            }
            JsonObject obj = EngineJson.parseStrictObject(line);
            JsonObject expected = obj.deepCopy();
            String newLine = line;
            for (String field : fields)
            {
                JsonElement v = obj.get(field);
                if (EngineJson.isString(v) && oldName.equals(v.getAsString()))
                {
                    String tokenOld = token(field, oldName);
                    if (count(newLine, tokenOld) != 1)
                    {
                        throw new IllegalArgumentException(label + " ligne " + (i + 1) + " : champ " + field + " introuvable tel quel, renommage refusé");
                    }
                    newLine = newLine.replace(tokenOld, token(field, newName));
                    expected.addProperty(field, newName);
                }
            }
            if (!newLine.equals(line))
            {
                if (!EngineJson.parseStrictObject(newLine).equals(expected))
                {
                    throw new IllegalArgumentException(label + " ligne " + (i + 1) + " : renommage incohérent, refusé");
                }
                changed = true;
            }
            out.append(newLine).append(eol);
        }
        return changed ? out.toString() : text;
    }

    // ---------------------------------------------------------------- plans en attente

    // Texte du fichier des plans apres renommage, ou null si aucun plan ne cite l'exercice.
    static String renameInPlans(String plansText, String oldName, String newName)
    {
        JsonObject plans = EngineJson.parseStrictObject(plansText);
        boolean changed = false;
        for (JsonElement e : plans.getAsJsonArray("plans"))
        {
            JsonObject plan = e.getAsJsonObject();
            String contenu = plan.get("contenu").getAsString();
            int expected = countNames(EngineJson.parseStrictObject(contenu), oldName);
            if (expected == 0)
            {
                continue;
            }
            String tokenOld = token("name", oldName);
            if (count(contenu, tokenOld) != expected)
            {
                throw new IllegalArgumentException("plan « " + plan.get("fichier").getAsString() + " » : nom introuvable tel quel, renommage refusé");
            }
            String renamed = contenu.replace(tokenOld, token("name", newName));
            JsonObject check = EngineJson.parseStrictObject(renamed);
            if (countNames(check, oldName) != 0 || countNames(check, newName) != expected + countNames(EngineJson.parseStrictObject(contenu), newName))
            {
                throw new IllegalArgumentException("plan « " + plan.get("fichier").getAsString() + " » : renommage incohérent, refusé");
            }
            plan.addProperty("contenu", renamed);
            changed = true;
        }
        return changed ? PRETTY.toJson(plans) + "\n" : null;
    }

    // Nombre de champs "name" valant `name`, a toute profondeur.
    private static int countNames(JsonElement e, String name)
    {
        int n = 0;
        if (e.isJsonObject())
        {
            for (Map.Entry<String, JsonElement> m : e.getAsJsonObject().entrySet())
            {
                if ("name".equals(m.getKey()) && EngineJson.isString(m.getValue()) && name.equals(m.getValue().getAsString()))
                {
                    n++;
                }
                n += countNames(m.getValue(), name);
            }
        }
        else if (e.isJsonArray())
        {
            for (JsonElement x : e.getAsJsonArray())
            {
                n += countNames(x, name);
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- ecriture facon Python

    // `"champ": "valeur"` tel que l'ecrit json.dumps(..., ensure_ascii=False) de Python.
    static String token(String field, String value)
    {
        return "\"" + field + "\": " + pythonString(value);
    }

    static String pythonString(String s)
    {
        StringBuilder b = new StringBuilder(s.length() + 2);
        b.append('"');
        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            switch (c)
            {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                case '\b': b.append("\\b"); break;
                case '\f': b.append("\\f"); break;
                default:
                    if (c < 0x20)
                    {
                        b.append(String.format("\\u%04x", (int) c));
                    }
                    else
                    {
                        b.append(c);
                    }
            }
        }
        return b.append('"').toString();
    }

    private static int count(String text, String token)
    {
        int n = 0;
        int from = 0;
        while ((from = text.indexOf(token, from)) >= 0)
        {
            n++;
            from += token.length();
        }
        return n;
    }
}
