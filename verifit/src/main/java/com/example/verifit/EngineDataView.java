package com.example.verifit;

import com.example.verifit.model.Exercise;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Textes de l'ecran « Donnees du moteur » (lot M3.4, 17/11/2026), en lecture seule : par
 * exercice, ses reglages et son tableau RM -> poids, pour verifier l'import contre l'Excel.
 * Ordre : cycles (A, B...) dans l'ordre de la configuration, puis les exercices hors cycle.
 *
 * Classe pure (testee en JUnit) ; l'ecran est ui/EngineDataActivity.
 */
public final class EngineDataView
{
    public static final class Row
    {
        public final String section;   // « Cycle A », « Hors cycle »
        public final String name;
        public final String subtitle;  // « 14 lignes de PR, répétitions »
        public final String detail;    // texte complet (police a chasse fixe)

        Row(String section, String name, String subtitle, String detail)
        {
            this.section = section;
            this.name = name;
            this.subtitle = subtitle;
            this.detail = detail;
        }
    }

    private EngineDataView() {}

    public static List<Row> rows(EngineContent content, List<Exercise> known)
    {
        JsonObject data = content.data();
        Map<String, Exercise> knownByName = new HashMap<String, Exercise>();
        for (Exercise e : known)
        {
            if (e != null && e.getName() != null)
            {
                knownByName.put(e.getName(), e);
            }
        }
        LinkedHashMap<String, JsonObject> byName = new LinkedHashMap<String, JsonObject>();
        for (JsonElement e : data.getAsJsonArray("exercices"))
        {
            byName.put(e.getAsJsonObject().get("nom").getAsString(), e.getAsJsonObject());
        }

        ArrayList<Row> rows = new ArrayList<Row>();
        java.util.HashSet<String> placed = new java.util.HashSet<String>();
        ArrayList<String> cycleKeys = new ArrayList<String>(data.getAsJsonObject("cycles").keySet());
        java.util.Collections.sort(cycleKeys);
        for (String key : cycleKeys)
        {
            JsonArray names = data.getAsJsonObject("cycles").getAsJsonArray(key);
            for (int i = 0; i < names.size(); i++)
            {
                String name = names.get(i).getAsString();
                placed.add(name);
                String where = "Cycle " + key + ", position " + (i + 1);
                rows.add(row("Cycle " + key, byName.get(name), knownByName.get(name), where));
            }
        }
        for (Map.Entry<String, JsonObject> e : byName.entrySet())
        {
            if (!placed.contains(e.getKey()))
            {
                rows.add(row("Hors cycle", e.getValue(), knownByName.get(e.getKey()), "Hors cycle"));
            }
        }
        return rows;
    }

    private static Row row(String section, JsonObject ex, Exercise app, String where)
    {
        String name = ex.get("nom").getAsString();
        boolean hold = ex.get("is_hold").getAsBoolean();
        JsonArray pr = ex.getAsJsonArray("pr");
        StringBuilder d = new StringBuilder();
        d.append(name).append('\n');
        d.append(where).append('\n');
        if (app == null)
        {
            d.append("ATTENTION : exercice inconnu de l'app\n");
        }
        d.append("Type : ").append(hold ? "maintien (RM en secondes)" : "répétitions").append('\n');
        d.append("Fenêtre e1RM : ").append(text(ex.get("e1rm_fenetre_reps"), " reps")).append('\n');
        d.append("Charge mini : ").append(text(ex.get("charge_min_kg"), " kg")).append('\n');
        d.append("Barre de base : ").append(text(ex.get("barre_base_kg"), " kg")).append('\n');
        d.append("Groupe de remplacement : ").append(text(ex.get("groupe_remplacement"), "")).append('\n');
        d.append("Assistance (réglage de l'app) : ")
                .append(app == null ? "-" : EngineImport.describe(app.getAssistRefKg(), app.isAssistPerSide())).append('\n');
        d.append("Onglet Excel d'origine : ").append(text(ex.get("onglet"), "")).append("\n\n");
        boolean hasE1rm = false;
        for (JsonElement r : pr)
        {
            hasE1rm |= r.getAsJsonArray().size() == 3;
        }
        if (pr.size() == 0)
        {
            d.append("Aucune ligne de PR.\n");
        }
        else
        {
            d.append(hold ? " Sec" : "  RM").append("     Poids").append(hasE1rm ? "   e1RM saisi" : "").append('\n');
            for (JsonElement r : pr)
            {
                JsonArray row = r.getAsJsonArray();
                d.append(String.format(Locale.US, "%4d %9s", row.get(0).getAsInt(), number(row.get(1))));
                if (row.size() == 3)
                {
                    d.append(String.format(Locale.US, " %12s", row.get(2).isJsonNull() ? "-" : number(row.get(2))));
                }
                d.append('\n');
            }
        }
        String subtitle = pr.size() + " ligne(s) de PR, " + (hold ? "maintien" : "répétitions")
                + (app == null ? ", INCONNU DE L'APP" : "");
        return new Row(section, name, subtitle, d.toString());
    }

    // Nombre tel qu'ecrit dans le fichier (34.0 reste 34.0, comme dans l'Excel lu par Python).
    private static String number(JsonElement e)
    {
        return e.getAsJsonPrimitive().getAsString();
    }

    private static String text(JsonElement e, String unit)
    {
        if (EngineJson.isNull(e))
        {
            return "-";
        }
        return e.getAsJsonPrimitive().getAsString() + unit;
    }

    // Ligne d'en-tete de l'ecran : etat du stockage et origine du dernier import.
    public static String header(EngineStorage.Loaded loaded)
    {
        if (loaded.status == EngineStorage.Status.EMPTY)
        {
            return "Aucune donnée du moteur. Sur le PC : 8_Export_moteur_app.bat, puis ici : Réglages > Importer les données du moteur.";
        }
        if (loaded.status == EngineStorage.Status.BLOCKED)
        {
            return "Données du moteur illisibles (rien n'a été effacé) :\n" + loaded.problem
                    + "Réimporte-les depuis le PC, ou restaure un backup complet qui les contient.";
        }
        EngineState s = loaded.state;
        EngineContent c = loaded.content;
        return c.count(EngineContent.DATA) + " exercices, " + c.prRows() + " lignes de PR, "
                + c.count(EngineContent.HISTORY) + " lignes d'historique, " + c.count(EngineContent.PLANS) + " plan(s) en attente.\n"
                + "Importé le " + (s.importedAt == null ? "?" : s.importedAt.replace('T', ' '))
                + (s.source == null ? "" : " (" + s.source + ")")
                + (s.exportedAt == null ? "" : ", export du PC du " + s.exportedAt.replace('T', ' ')) + ".";
    }
}
