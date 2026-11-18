package com.example.verifit;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

// Jeux de donnees des tests du moteur (lot M3) : textes au format reellement ecrit par
// Python (json.dumps, une ligne de PR par ligne de fichier).
final class EngineTestData
{
    private EngineTestData() {}

    static String exercise(String name, String prRows)
    {
        return "  {\n   \"nom\": \"" + name + "\",\n   \"nom_app\": \"" + name + "\",\n   \"onglet\": \"Onglet " + name + "\",\n"
                + "   \"onglet_excel\": \"Onglet " + name + "\",\n   \"is_hold\": false,\n   \"e1rm_fenetre_reps\": null,\n"
                + "   \"charge_min_kg\": null,\n   \"groupe_remplacement\": null,\n   \"charge_ref_kg\": null,\n"
                + "   \"assist_par_cote\": false,\n   \"barre_base_kg\": null,\n   \"pr\": [\n" + prRows + "\n   ]\n  }";
    }

    static final String DATA = "{\n \"version\": 1,\n \"exercices\": [\n"
            + exercise("Shoulder Press TG Mid Siège 5", "    [1, 34.0],\n    [2, 33.5],\n    [5, 29.0]") + ",\n"
            + exercise("Assisted Pelican", "    [3, 52.5],\n    [8, 45.0]") + ",\n"
            + exercise("Jeff Curl 4x25", "    [10, 40.0, null]") + "\n"
            + " ],\n \"cycles\": {\n  \"A\": [\n   \"Shoulder Press TG Mid Siège 5\",\n   \"Assisted Pelican\"\n  ],\n"
            + "  \"B\": [\n   \"Jeff Curl 4x25\"\n  ]\n },\n"
            + " \"disques_par_cote\": [20, 15, 10, 2.5, 1.25]\n}\n";

    static final String HISTORY_LINE_1 = "{\"timestamp\": \"2026-09-15T17:34:02\", \"plan\": \"Seance_Force_CycleA_2026-09-13_20-04-11\", \"exercice\": \"Assisted Pelican\", \"serie\": \"S1\", \"unite\": \"reps\", \"rm\": 6, \"charge_prevue_kg\": 79.0, \"statut\": \"battu\", \"nouveau_ce_sync\": true, \"methode\": \"S1 Ancrage\"}";
    static final String HISTORY_LINE_2 = "{\"timestamp\": \"2026-09-16T10:00:00\", \"plan\": \"Seance_Force_CycleA_2026-09-13_20-04-11\", \"exercice\": \"Shoulder Press TG Mid Siège 5\", \"serie\": \"S2\", \"unite\": \"reps\", \"rm\": 3, \"charge_prevue_kg\": 33.0, \"statut\": \"échec\", \"tentative_reps\": 2, \"filet\": null}";
    static final String HISTORY = HISTORY_LINE_1 + "\n" + HISTORY_LINE_2 + "\n";

    static final String CANCELLED = "{\"timestamp\": \"2026-09-26T15:13:40\", \"plan\": \"Seance_Force_CycleB_2026-09-24_21-10-58\", \"categorie\": \"autre\", \"note\": \"ordre inversé\", \"exercises\": []}\n";

    static final String PLAN_CONTENT = "{\n  \"comment\": \"Cycle A Force\",\n  \"date\": \"2026-11-19\",\n  \"exercises\": [\n    {\n      \"name\": \"Assisted Pelican\",\n      \"sets\": [\n        {\n          \"weight\": 52.5,\n          \"reps\": 3,\n          \"comment\": \"S1 Ancrage\"\n        }\n      ]\n    }\n  ]\n}";

    static String plans(String... fileNames)
    {
        StringBuilder s = new StringBuilder("{\"version\": 1, \"plans\": [");
        for (int i = 0; i < fileNames.length; i++)
        {
            if (i > 0) s.append(", ");
            s.append("{\"fichier\": \"").append(fileNames[i]).append("\", \"contenu\": ")
                    .append(new com.google.gson.Gson().toJson(PLAN_CONTENT)).append("}");
        }
        return s.append("]}\n").toString();
    }

    static Map<String, String> files()
    {
        LinkedHashMap<String, String> f = new LinkedHashMap<String, String>();
        f.put(EngineContent.DATA, DATA);
        f.put(EngineContent.HISTORY, HISTORY);
        f.put(EngineContent.NEXT_PR, "");
        f.put(EngineContent.SUBSTITUTIONS, "");
        f.put(EngineContent.CANCELLED, CANCELLED);
        f.put(EngineContent.PLANS, plans("Seance_Force_CycleA_2026-11-16_13-28-46.json"));
        return f;
    }

    static EngineContent content()
    {
        return EngineContent.of(files());
    }

    // Faux dossier moteur/ : un fichier = une entree. Lecture ou ecriture en echec a la demande.
    static final class MemoryFiles implements EngineStorage.Files
    {
        final Map<String, String> files = new HashMap<String, String>();
        final Set<String> failRead = new HashSet<String>();
        final Set<String> failWrite = new HashSet<String>();
        int writes = 0;

        @Override
        public String read(String name) throws IOException
        {
            if (failRead.contains(name))
            {
                throw new IOException("disque illisible");
            }
            return files.get(name);
        }

        @Override
        public void write(String name, String text) throws IOException
        {
            if (failWrite.contains(name))
            {
                throw new IOException("disque plein");
            }
            writes++;
            files.put(name, text);
        }
    }
}
