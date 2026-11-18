package com.example.verifit;

import static org.junit.Assert.*;

import com.google.gson.JsonObject;

import org.junit.Test;

// Lot M3.1 : lecture stricte (le JSON ecrit par Python, rien de plus).
public class EngineJsonTest
{
    private static void assertRefused(String text, String expectedFragment)
    {
        try
        {
            EngineJson.parseStrict(text);
            fail("accepte a tort : " + text);
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage(), e.getMessage().contains(expectedFragment));
        }
    }

    @Test
    public void jsonNormal_lu()
    {
        JsonObject o = EngineJson.parseStrictObject("{\"a\": [1, 2.5, null], \"b\": \"Siège\", \"c\": true}");
        assertEquals(2.5, o.getAsJsonArray("a").get(1).getAsDouble(), 0.0);
        assertEquals("Siège", o.get("b").getAsString());
    }

    @Test
    public void nonFinis_refuses()
    {
        assertRefused("{\"a\": NaN}", "JSON invalide");
        assertRefused("{\"a\": Infinity}", "JSON invalide");
        assertRefused("{\"a\": [1e400]}", "a[0]");
    }

    @Test
    public void jsonPermissif_refuse()
    {
        assertRefused("{a: 1}", "JSON invalide");
        assertRefused("{'a': 1}", "JSON invalide");
        assertRefused("{\"a\": 1,}", "JSON invalide");
        assertRefused("{\"a\": 1} {\"b\": 2}", "texte en trop");
        assertRefused("{\"a\": [1, 2", "JSON invalide");
        assertRefused("", "JSON invalide");
    }

    @Test
    public void entiers()
    {
        JsonObject o = EngineJson.parseStrictObject("{\"a\": 3, \"b\": 3.0, \"c\": 3.5, \"d\": \"3\"}");
        assertTrue(EngineJson.isInteger(o.get("a")));
        assertTrue(EngineJson.isInteger(o.get("b")));
        assertFalse(EngineJson.isInteger(o.get("c")));
        assertFalse(EngineJson.isInteger(o.get("d")));
    }
}
