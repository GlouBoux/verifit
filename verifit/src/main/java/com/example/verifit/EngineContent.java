package com.example.verifit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Contenu complet des donnees du moteur stockees dans l'app (lot M3.1, 17/11/2026, voir
 * claude/fitengine-moteur-lot-M3.md, decisions de conception) : le texte exact de chaque
 * fichier, valide a la construction.
 *
 * Fichiers (dossier prive moteur/ de l'app) :
 * - donnees_moteur.json : contrat « donnees moteur » version 1 du depot Coaching
 *   (moteur/engine_data.py), avec le nom de l'app dans nom et nom_app ;
 * - un fichier .jsonl par journal, texte identique a celui du PC (une ligne JSON par
 *   evenement, chaque ligne terminee par un saut de ligne) ;
 * - plans_en_attente.json : {"version": 1, "plans": [{"fichier": ..., "contenu": texte du plan}]}.
 * Le fichier etat.json (EngineState) n'en fait pas partie : il est recalcule.
 *
 * Les textes sont gardes tels quels (jamais reserialises par Gson) : Python les relit, et
 * les comparaisons avec le PC se font octet par octet.
 *
 * Classe pure (testee en JUnit).
 */
public final class EngineContent
{
    public static final String DATA = "donnees_moteur.json";
    public static final String PLANS = "plans_en_attente.json";
    public static final String HISTORY = "historique_series.jsonl";
    public static final String NEXT_PR = "next_pr_rejections.jsonl";
    public static final String SUBSTITUTIONS = "exercise_substitutions.jsonl";
    public static final String CANCELLED = "seances_annulees.jsonl";
    // Lot M5b.2 (21/11/2026) : trace de chaque modification d'un PR faite dans l'app (bilan de
    // fin de seance, annulation). Ne vient jamais du PC : l'import le garde tel quel.
    public static final String PR_HISTORY = "historique_pr.jsonl";

    // Ordre d'affichage (et du bloc moteur des backups).
    public static final List<String> FILES = Collections.unmodifiableList(Arrays.asList(
            DATA, HISTORY, PR_HISTORY, NEXT_PR, SUBSTITUTIONS, CANCELLED, PLANS));
    public static final List<String> JOURNALS = Collections.unmodifiableList(Arrays.asList(
            HISTORY, PR_HISTORY, NEXT_PR, SUBSTITUTIONS, CANCELLED));
    // Fichiers apportes par l'export du PC (import M3) : tous sauf la trace des PR de l'app.
    public static final List<String> IMPORTED_FILES = Collections.unmodifiableList(Arrays.asList(
            DATA, HISTORY, NEXT_PR, SUBSTITUTIONS, CANCELLED, PLANS));
    // Ordre d'ecriture (EngineStorage) : la trace des PR d'abord, puis le journal, les donnees
    // et les plans. Un arret au milieu d'un « Valider » laisse au pire une trace sans le PR
    // qu'elle annonce, jamais un PR ecrit sans trace (la copie automatique d'avant permet de
    // revenir en arriere).
    public static final List<String> WRITE_ORDER = Collections.unmodifiableList(Arrays.asList(
            PR_HISTORY, HISTORY, NEXT_PR, SUBSTITUTIONS, CANCELLED, DATA, PLANS));

    public static final int DATA_VERSION = 1;
    public static final int PLANS_VERSION = 1;

    // Champs obligatoires d'un exercice (EXERCISE_FIELDS de engine_data.py).
    static final List<String> EXERCISE_FIELDS = Collections.unmodifiableList(Arrays.asList(
            "nom", "nom_app", "onglet", "onglet_excel", "is_hold", "e1rm_fenetre_reps",
            "charge_min_kg", "groupe_remplacement", "charge_ref_kg", "assist_par_cote",
            "barre_base_kg", "pr"));

    private final Map<String, String> texts;
    private final Map<String, Integer> counts;
    private final int prRows;

    private EngineContent(Map<String, String> texts, Map<String, Integer> counts, int prRows)
    {
        this.texts = Collections.unmodifiableMap(texts);
        this.counts = Collections.unmodifiableMap(counts);
        this.prRows = prRows;
    }

    // Valide chaque fichier. Un journal absent vaut un journal vide ; les donnees et les
    // plans sont obligatoires. Leve IllegalArgumentException (« fichier : probleme »).
    public static EngineContent of(Map<String, String> files)
    {
        LinkedHashMap<String, String> texts = new LinkedHashMap<String, String>();
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (String name : FILES)
        {
            String text = files.get(name);
            if (text == null && JOURNALS.contains(name))
            {
                text = "";
            }
            if (text == null)
            {
                throw new IllegalArgumentException(name + " : absent");
            }
            texts.put(name, text);
            counts.put(name, validate(name, text));
        }
        return new EngineContent(texts, counts, countPrRows(EngineJson.parseStrictObject(texts.get(DATA))));
    }

    // Nombre d'elements du fichier (exercices, lignes, plans) ; leve IllegalArgumentException
    // si le texte n'est pas valide. Message prefixe par le nom du fichier.
    public static int validate(String name, String text)
    {
        try
        {
            if (DATA.equals(name))
            {
                return validateData(EngineJson.parseStrictObject(text));
            }
            if (PLANS.equals(name))
            {
                return validatePlans(EngineJson.parseStrictObject(text));
            }
            if (JOURNALS.contains(name))
            {
                return validateJournal(text);
            }
        }
        catch (IllegalArgumentException e)
        {
            throw new IllegalArgumentException(name + " : " + e.getMessage());
        }
        throw new IllegalArgumentException(name + " : fichier inconnu");
    }

    public String text(String name)
    {
        return texts.get(name);
    }

    public Map<String, String> texts()
    {
        return texts;
    }

    // Exercices (donnees), lignes (journal) ou plans.
    public int count(String name)
    {
        Integer n = counts.get(name);
        return n == null ? 0 : n;
    }

    public Map<String, Integer> counts()
    {
        return counts;
    }

    public int prRows()
    {
        return prRows;
    }

    // Lot M5b (21/11/2026) : vrai si chaque exercice porte rm_max (donnees importees depuis un
    // PC a jour). Sans lui, le bilan de fin de seance est refuse (« refais l'import »).
    public boolean hasRmMax()
    {
        for (JsonElement ex : data().getAsJsonArray("exercices"))
        {
            if (!ex.getAsJsonObject().has("rm_max"))
            {
                return false;
            }
        }
        return true;
    }

    // Copie analysee des donnees moteur (a ne pas modifier pour ecrire : texte source).
    public JsonObject data()
    {
        return EngineJson.parseStrictObject(texts.get(DATA));
    }

    public boolean sameTexts(EngineContent other)
    {
        return other != null && texts.equals(other.texts);
    }

    // ---------------------------------------------------------------- donnees moteur

    static int validateData(JsonObject data)
    {
        JsonElement version = data.get("version");
        if (!EngineJson.isInteger(version) || version.getAsInt() != DATA_VERSION)
        {
            throw new IllegalArgumentException("version " + version + " non prise en charge (attendue : " + DATA_VERSION + ")");
        }
        JsonElement exercices = data.get("exercices");
        if (exercices == null || !exercices.isJsonArray())
        {
            throw new IllegalArgumentException("« exercices » doit être une liste");
        }
        Set<String> names = new HashSet<String>();
        for (JsonElement element : exercices.getAsJsonArray())
        {
            if (element == null || !element.isJsonObject())
            {
                throw new IllegalArgumentException("exercice illisible");
            }
            JsonObject ex = element.getAsJsonObject();
            String name = EngineJson.stringOrNull(ex, "nom");
            if (name == null || name.trim().isEmpty())
            {
                throw new IllegalArgumentException("exercice sans nom");
            }
            for (String field : EXERCISE_FIELDS)
            {
                if (!ex.has(field))
                {
                    throw new IllegalArgumentException("exercice « " + name + " » sans " + field);
                }
            }
            if (!names.add(name))
            {
                throw new IllegalArgumentException("exercice « " + name + " » en double");
            }
            checkExercise(name, ex);
        }
        JsonElement cycles = data.get("cycles");
        if (cycles == null || !cycles.isJsonObject())
        {
            throw new IllegalArgumentException("« cycles » doit être un objet");
        }
        for (Map.Entry<String, JsonElement> cycle : cycles.getAsJsonObject().entrySet())
        {
            if (!cycle.getValue().isJsonArray())
            {
                throw new IllegalArgumentException("cycle " + cycle.getKey() + " : une liste est attendue");
            }
            for (JsonElement n : cycle.getValue().getAsJsonArray())
            {
                if (!EngineJson.isString(n) || !names.contains(n.getAsString()))
                {
                    throw new IllegalArgumentException("cycle " + cycle.getKey() + " : exercice inconnu " + n);
                }
            }
        }
        JsonElement plates = data.get("disques_par_cote");
        if (plates == null || !plates.isJsonArray())
        {
            throw new IllegalArgumentException("« disques_par_cote » doit être une liste");
        }
        for (JsonElement p : plates.getAsJsonArray())
        {
            if (!EngineJson.isNumber(p) || p.getAsDouble() <= 0)
            {
                throw new IllegalArgumentException("disque invalide : " + p);
            }
        }
        return exercices.getAsJsonArray().size();
    }

    private static void checkExercise(String name, JsonObject ex)
    {
        String where = "exercice « " + name + " » : ";
        for (String field : new String[] { "nom_app", "onglet", "onglet_excel", "groupe_remplacement" })
        {
            if (!EngineJson.isNull(ex.get(field)) && !EngineJson.isString(ex.get(field)))
            {
                throw new IllegalArgumentException(where + field + " doit être un texte ou null");
            }
        }
        for (String field : new String[] { "is_hold", "assist_par_cote" })
        {
            if (!EngineJson.isBoolean(ex.get(field)))
            {
                throw new IllegalArgumentException(where + field + " doit valoir true ou false");
            }
        }
        if (!EngineJson.isNull(ex.get("e1rm_fenetre_reps"))
                && (!EngineJson.isInteger(ex.get("e1rm_fenetre_reps")) || ex.get("e1rm_fenetre_reps").getAsInt() < 1))
        {
            throw new IllegalArgumentException(where + "e1rm_fenetre_reps doit être un entier positif ou null");
        }
        for (String field : new String[] { "charge_min_kg", "charge_ref_kg", "barre_base_kg" })
        {
            if (!EngineJson.isNull(ex.get(field)) && !EngineJson.isNumber(ex.get(field)))
            {
                throw new IllegalArgumentException(where + field + " doit être un nombre ou null");
            }
        }
        // Lot M5b (21/11/2026) : champ facultatif de la version 1, dernier RM du tableau de PR
        // (borne de la fin de seance, voir engine_data.py) ; null si pas de tableau.
        if (ex.has("rm_max") && !EngineJson.isNull(ex.get("rm_max"))
                && (!EngineJson.isInteger(ex.get("rm_max")) || ex.get("rm_max").getAsDouble() < 0))
        {
            throw new IllegalArgumentException(where + "rm_max doit être un entier positif ou null");
        }
        JsonElement pr = ex.get("pr");
        if (!pr.isJsonArray())
        {
            throw new IllegalArgumentException(where + "pr doit être une liste");
        }
        for (JsonElement rowElement : pr.getAsJsonArray())
        {
            if (!rowElement.isJsonArray())
            {
                throw new IllegalArgumentException(where + "ligne de PR invalide " + rowElement);
            }
            JsonArray row = rowElement.getAsJsonArray();
            if (row.size() != 2 && row.size() != 3)
            {
                throw new IllegalArgumentException(where + "ligne de PR invalide " + row);
            }
            if (!EngineJson.isInteger(row.get(0)) || row.get(0).getAsDouble() < 1)
            {
                throw new IllegalArgumentException(where + "RM invalide dans " + row);
            }
            if (!EngineJson.isNumber(row.get(1)) || row.get(1).getAsDouble() <= 0)
            {
                throw new IllegalArgumentException(where + "poids invalide dans " + row);
            }
            if (row.size() == 3 && !EngineJson.isNull(row.get(2)) && !EngineJson.isNumber(row.get(2)))
            {
                throw new IllegalArgumentException(where + "e1RM invalide dans " + row);
            }
        }
    }

    static int countPrRows(JsonObject data)
    {
        int n = 0;
        for (JsonElement ex : data.getAsJsonArray("exercices"))
        {
            n += ex.getAsJsonObject().getAsJsonArray("pr").size();
        }
        return n;
    }

    // ---------------------------------------------------------------- journaux

    // Lignes JSON, chacune un objet, chacune terminee par "\n" (un fichier coupe en cours
    // d'ecriture finit sans saut de ligne : refuse). Texte vide = journal vide.
    static int validateJournal(String text)
    {
        if (text.isEmpty())
        {
            return 0;
        }
        if (!text.endsWith("\n"))
        {
            throw new IllegalArgumentException("dernière ligne incomplète");
        }
        String[] lines = text.substring(0, text.length() - 1).split("\n", -1);
        for (int i = 0; i < lines.length; i++)
        {
            String line = lines[i];
            if (line.endsWith("\r"))
            {
                line = line.substring(0, line.length() - 1);
            }
            try
            {
                EngineJson.parseStrictObject(line);
            }
            catch (IllegalArgumentException e)
            {
                throw new IllegalArgumentException("ligne " + (i + 1) + " : " + e.getMessage());
            }
        }
        return lines.length;
    }

    // Lignes du journal, sans saut de ligne final (liste vide pour un journal vide).
    public static List<String> journalLines(String text)
    {
        if (text == null || text.isEmpty())
        {
            return Collections.emptyList();
        }
        String body = text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
        return Arrays.asList(body.split("\n", -1));
    }

    // ---------------------------------------------------------------- plans en attente

    static int validatePlans(JsonObject plans)
    {
        JsonElement version = plans.get("version");
        if (!EngineJson.isInteger(version) || version.getAsInt() != PLANS_VERSION)
        {
            throw new IllegalArgumentException("version " + version + " non prise en charge (attendue : " + PLANS_VERSION + ")");
        }
        JsonElement list = plans.get("plans");
        if (list == null || !list.isJsonArray())
        {
            throw new IllegalArgumentException("« plans » doit être une liste");
        }
        Set<String> fileNames = new HashSet<String>();
        for (JsonElement element : list.getAsJsonArray())
        {
            if (element == null || !element.isJsonObject())
            {
                throw new IllegalArgumentException("plan illisible");
            }
            JsonObject plan = element.getAsJsonObject();
            String fileName = EngineJson.stringOrNull(plan, "fichier");
            String content = EngineJson.stringOrNull(plan, "contenu");
            if (fileName == null || fileName.trim().isEmpty() || content == null)
            {
                throw new IllegalArgumentException("plan sans fichier ou sans contenu");
            }
            if (!fileNames.add(fileName))
            {
                throw new IllegalArgumentException("plan « " + fileName + " » en double");
            }
            try
            {
                validateSession(EngineJson.parseStrictObject(content));
            }
            catch (IllegalArgumentException e)
            {
                throw new IllegalArgumentException("plan « " + fileName + " » : " + e.getMessage());
            }
        }
        return list.getAsJsonArray().size();
    }

    // Forme minimale d'un plan « Import Session » : une liste d'exercices nommes.
    private static void validateSession(JsonObject session)
    {
        JsonElement exercises = session.get("exercises");
        if (exercises == null || !exercises.isJsonArray())
        {
            throw new IllegalArgumentException("« exercises » doit être une liste");
        }
        for (JsonElement e : exercises.getAsJsonArray())
        {
            if (e == null || !e.isJsonObject() || !EngineJson.isString(e.getAsJsonObject().get("name")))
            {
                throw new IllegalArgumentException("exercice sans nom");
            }
        }
    }
}
