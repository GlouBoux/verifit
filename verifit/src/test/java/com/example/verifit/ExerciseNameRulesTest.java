package com.example.verifit;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// Retour Romain 14/10/2026 : renommage/duplication d'un exercice, voir ExerciseNameRules.
public class ExerciseNameRulesTest
{
    private static final List<String> KNOWN = Arrays.asList(
            "Flat Barbell Bench Press", "Pelican Curl", "Plate Loaded Iso Leg Ext P");

    @Test
    public void normalize_trimsAndCollapsesSpaces()
    {
        assertEquals("Pelican Curl", ExerciseNameRules.normalize("  Pelican   Curl \t"));
        assertEquals("", ExerciseNameRules.normalize(null));
        assertEquals("", ExerciseNameRules.normalize("   "));
    }

    @Test
    public void rename_toFreeName_isOkAndTrimmed()
    {
        ExerciseNameRules.Check c = ExerciseNameRules.check(" Pelican Curl Pause ", "Pelican Curl", KNOWN);
        assertTrue(c.isOk());
        assertEquals("Pelican Curl Pause", c.name);
    }

    @Test
    public void rename_toExistingName_isRefused()
    {
        ExerciseNameRules.Check c = ExerciseNameRules.check("Flat Barbell Bench Press", "Pelican Curl", KNOWN);
        assertEquals(ExerciseNameRules.Result.ALREADY_EXISTS, c.result);
        assertEquals("Flat Barbell Bench Press", c.name);
    }

    @Test
    public void rename_toExistingNameWithOtherCaseOrSpaces_isRefused()
    {
        ExerciseNameRules.Check c = ExerciseNameRules.check(" flat barbell  bench press", "Pelican Curl", KNOWN);
        assertEquals(ExerciseNameRules.Result.ALREADY_EXISTS, c.result);
    }

    @Test
    public void rename_changingOnlyItsOwnCase_isAllowed()
    {
        ExerciseNameRules.Check c = ExerciseNameRules.check("pelican curl", "Pelican Curl", KNOWN);
        assertTrue(c.isOk());
        assertEquals("pelican curl", c.name);
    }

    @Test
    public void rename_unchanged_keepsExactOldName()
    {
        // Nom historique avec double espace : un simple Save ne doit pas le renommer.
        List<String> known = Arrays.asList("Leg  Press", "Pelican Curl");
        ExerciseNameRules.Check c = ExerciseNameRules.check("Leg Press ", "Leg  Press", known);
        assertTrue(c.isOk());
        assertEquals("Leg  Press", c.name);
    }

    @Test
    public void emptyName_isRefused()
    {
        assertEquals(ExerciseNameRules.Result.EMPTY, ExerciseNameRules.check("   ", "Pelican Curl", KNOWN).result);
        assertEquals(ExerciseNameRules.Result.EMPTY, ExerciseNameRules.check(null, null, KNOWN).result);
    }

    @Test
    public void duplicate_toSourceName_isRefused()
    {
        // Duplication = pas de nom courant : le nom de la source compte comme un doublon.
        ExerciseNameRules.Check c = ExerciseNameRules.check("Pelican Curl", null, KNOWN);
        assertEquals(ExerciseNameRules.Result.ALREADY_EXISTS, c.result);
        assertFalse(c.isOk());
    }

    @Test
    public void duplicate_toFreeName_isOk()
    {
        ExerciseNameRules.Check c = ExerciseNameRules.check("Pelican Curl (copy)", null, KNOWN);
        assertTrue(c.isOk());
        assertEquals("Pelican Curl (copy)", c.name);
    }
}
