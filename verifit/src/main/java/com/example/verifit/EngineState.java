package com.example.verifit;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fichier etat.json des donnees du moteur (lot M3.1, 17/11/2026) : compteurs attendus de
 * chaque fichier (exercices, lignes, plans), date et origine du dernier import.
 *
 * Meme role que le compteur de series du lot S1 (StorageCheck) : un fichier qui a disparu
 * ou se lit vide alors que le compteur annonce du contenu bloque les fonctions du moteur,
 * au lieu de repartir d'un vide silencieux. Ecrit APRES les fichiers de contenu.
 *
 * Classe pure (testee en JUnit).
 */
public final class EngineState
{
    public static final String FILE = "etat.json";
    public static final int VERSION = 1;

    public final String importedAt;   // "2026-11-17T21:04:00", heure du telephone
    public final String source;       // nom du fichier importe, ou origine (« restauration »)
    public final String exportedAt;   // date de l'export sur le PC, ou null
    private final Map<String, Integer> counts;

    public EngineState(String importedAt, String source, String exportedAt, Map<String, Integer> counts)
    {
        this.importedAt = importedAt;
        this.source = source;
        this.exportedAt = exportedAt;
        this.counts = Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(counts));
    }

    // Compteur attendu pour un fichier de contenu, 0 s'il n'est pas connu.
    public int count(String file)
    {
        Integer n = counts.get(file);
        return n == null ? 0 : n;
    }

    public Map<String, Integer> counts()
    {
        return counts;
    }

    public EngineState withCounts(Map<String, Integer> newCounts)
    {
        return new EngineState(importedAt, source, exportedAt, newCounts);
    }

    public String toJson()
    {
        JsonObject o = new JsonObject();
        o.addProperty("version", VERSION);
        o.addProperty("importe_le", importedAt);
        o.addProperty("source", source);
        o.addProperty("exporte_le", exportedAt);
        JsonObject c = new JsonObject();
        for (Map.Entry<String, Integer> e : counts.entrySet())
        {
            c.addProperty(e.getKey(), e.getValue());
        }
        o.add("compteurs", c);
        return new GsonBuilder().serializeNulls().setPrettyPrinting().disableHtmlEscaping().create().toJson(o) + "\n";
    }

    // Leve IllegalArgumentException si le texte n'est pas un etat lisible.
    public static EngineState parse(String text)
    {
        JsonObject o = EngineJson.parseStrictObject(text);
        JsonElement version = o.get("version");
        if (!EngineJson.isInteger(version) || version.getAsInt() != VERSION)
        {
            throw new IllegalArgumentException("etat.json : version " + version + " non prise en charge");
        }
        JsonElement c = o.get("compteurs");
        if (c == null || !c.isJsonObject())
        {
            throw new IllegalArgumentException("etat.json : compteurs absents");
        }
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, JsonElement> e : c.getAsJsonObject().entrySet())
        {
            if (!EngineJson.isInteger(e.getValue()) || e.getValue().getAsInt() < 0)
            {
                throw new IllegalArgumentException("etat.json : compteur invalide pour " + e.getKey());
            }
            counts.put(e.getKey(), e.getValue().getAsInt());
        }
        return new EngineState(EngineJson.stringOrNull(o, "importe_le"), EngineJson.stringOrNull(o, "source"),
                EngineJson.stringOrNull(o, "exporte_le"), counts);
    }
}
