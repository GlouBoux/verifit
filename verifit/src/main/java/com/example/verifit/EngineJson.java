package com.example.verifit;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

/**
 * Lecture STRICTE des fichiers JSON du moteur (lot M3.1, 17/11/2026).
 *
 * Les fichiers du moteur sont ecrits par Python (json.dumps) : du JSON strict, sans NaN ni
 * infini. JsonParser.parseString de Gson est permissif (il lit NaN, des textes sans
 * guillemets, des virgules en trop) : on lit donc avec un JsonReader strict, puis on verifie
 * que chaque nombre est fini (1e400 se lit, mais vaut l'infini). Meme raison que
 * SetValueRules (lot S2) : une valeur non finie bloquerait toute ecriture suivante.
 *
 * Classe pure (testee en JUnit). Toute erreur devient une IllegalArgumentException au
 * message lisible.
 */
public final class EngineJson
{
    private static final TypeAdapter<JsonElement> ELEMENT = new Gson().getAdapter(JsonElement.class);

    private EngineJson() {}

    // Un document JSON strict, entierement consomme, sans nombre non fini.
    public static JsonElement parseStrict(String text)
    {
        if (text == null)
        {
            throw new IllegalArgumentException("texte absent");
        }
        JsonElement element;
        try
        {
            JsonReader reader = new JsonReader(new StringReader(text));
            reader.setLenient(false);
            element = ELEMENT.read(reader);
            boolean ended;
            try
            {
                ended = reader.peek() == JsonToken.END_DOCUMENT;
            }
            catch (IOException e)
            {
                ended = false;
            }
            if (!ended)
            {
                throw new IllegalArgumentException("texte en trop après le JSON");
            }
        }
        catch (IOException | JsonParseException | IllegalStateException | NumberFormatException e)
        {
            throw new IllegalArgumentException("JSON invalide (" + e.getMessage() + ")");
        }
        if (element == null || element.isJsonNull())
        {
            throw new IllegalArgumentException("JSON vide");
        }
        String bad = firstNonFinite(element, "");
        if (bad != null)
        {
            throw new IllegalArgumentException("valeur non numérique (NaN ou infini) : " + bad);
        }
        return element;
    }

    public static JsonObject parseStrictObject(String text)
    {
        JsonElement element = parseStrict(text);
        if (!element.isJsonObject())
        {
            throw new IllegalArgumentException("un objet JSON est attendu");
        }
        return element.getAsJsonObject();
    }

    // Chemin du premier nombre non fini, ou null.
    static String firstNonFinite(JsonElement element, String path)
    {
        if (element == null || element.isJsonNull())
        {
            return null;
        }
        if (element.isJsonPrimitive())
        {
            JsonPrimitive p = element.getAsJsonPrimitive();
            if (p.isNumber())
            {
                double v = p.getAsDouble();
                if (Double.isNaN(v) || Double.isInfinite(v))
                {
                    return path.isEmpty() ? "(racine)" : path;
                }
            }
            return null;
        }
        if (element.isJsonArray())
        {
            JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < array.size(); i++)
            {
                String bad = firstNonFinite(array.get(i), path + "[" + i + "]");
                if (bad != null)
                {
                    return bad;
                }
            }
            return null;
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet())
        {
            String bad = firstNonFinite(entry.getValue(), path.isEmpty() ? entry.getKey() : path + "." + entry.getKey());
            if (bad != null)
            {
                return bad;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- acces types

    public static boolean isString(JsonElement e)
    {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString();
    }

    public static boolean isNumber(JsonElement e)
    {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber();
    }

    public static boolean isBoolean(JsonElement e)
    {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean();
    }

    public static boolean isNull(JsonElement e)
    {
        return e == null || e.isJsonNull();
    }

    // Entier exact (3 ou 3.0, pas 3.5).
    public static boolean isInteger(JsonElement e)
    {
        if (!isNumber(e))
        {
            return false;
        }
        double v = e.getAsDouble();
        return v == Math.rint(v) && Math.abs(v) < 1e15;
    }

    // Texte d'un champ chaine, ou null s'il est absent, null ou d'un autre type.
    public static String stringOrNull(JsonObject o, String key)
    {
        JsonElement e = o.get(key);
        return isString(e) ? e.getAsString() : null;
    }
}
