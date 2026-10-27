package com.example.verifit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

// Lot S2 de la revue securite du 27/10/2026 (point E1). Voir SetValueRules.
public class SetValueRulesTest
{
    @Test
    public void isFinite()
    {
        assertTrue(SetValueRules.isFinite(0.0));
        assertTrue(SetValueRules.isFinite(-12.5));
        assertFalse(SetValueRules.isFinite(null));
        assertFalse(SetValueRules.isFinite(Double.NaN));
        assertFalse(SetValueRules.isFinite(Double.POSITIVE_INFINITY));
        assertFalse(SetValueRules.isFinite(Double.NEGATIVE_INFINITY));
    }

    @Test
    public void import_accepte_les_valeurs_normales()
    {
        assertNull(SetValueRules.importProblem(100.0, 5.0));
        assertNull(SetValueRules.importProblem(0.0, 0.0));       // poids du corps, serie ratee
        assertNull(SetValueRules.importProblem(1000.0, 1000.0)); // bornes incluses
        assertNull(SetValueRules.importProblem(-20.0, 8.0));     // assistance notee en negatif
        assertNull(SetValueRules.importProblem(80.0, 45.0));     // maintien de 45 s
    }

    @Test
    public void import_laisse_les_valeurs_absentes_a_l_appelant()
    {
        // Serie incomplete : ignoree par l'apercu, pas une valeur invalide.
        assertNull(SetValueRules.importProblem(null, 5.0));
        assertNull(SetValueRules.importProblem(100.0, null));
    }

    @Test
    public void import_refuse_le_non_fini()
    {
        assertEquals("poids non numérique (NaN)", SetValueRules.importProblem(Double.NaN, 5.0));
        assertEquals("poids non numérique (Infinity)", SetValueRules.importProblem(Double.POSITIVE_INFINITY, 5.0));
        assertEquals("reps non numériques (NaN)", SetValueRules.importProblem(100.0, Double.NaN));
    }

    @Test
    public void import_refuse_les_valeurs_hors_limites()
    {
        assertNotNull(SetValueRules.importProblem(1000.5, 5.0));
        assertNotNull(SetValueRules.importProblem(-1000.5, 5.0));
        assertNotNull(SetValueRules.importProblem(100.0, -1.0));
        assertNotNull(SetValueRules.importProblem(100.0, 1000.5));
    }

    @Test
    public void restauration_ne_refuse_que_le_non_fini()
    {
        // Un vieil historique doit toujours pouvoir etre restaure, meme atypique.
        assertNull(SetValueRules.restoreProblem(5000.0, -3.0));
        assertNull(SetValueRules.restoreProblem(null, null));
        assertNotNull(SetValueRules.restoreProblem(Double.NaN, 5.0));
        assertNotNull(SetValueRules.restoreProblem(100.0, Double.NEGATIVE_INFINITY));
    }
}
