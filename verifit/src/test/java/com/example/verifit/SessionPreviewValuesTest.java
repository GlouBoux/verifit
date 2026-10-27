package com.example.verifit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.verifit.model.ImportedSession;
import com.google.gson.Gson;

import org.junit.Test;

// Lot S2 de la revue securite du 27/10/2026 (point E1) : un plan contenant NaN, l'infini
// ou une valeur absurde est refuse a l'apercu (et a l'import, meme regle). Lecture par
// Gson, comme l'app : Gson accepte NaN et 1e400 (devenu Infinity) sans erreur.
public class SessionPreviewValuesTest
{
    private static ImportedSession parse(String json)
    {
        return new Gson().fromJson(json, ImportedSession.class);
    }

    private static String plan(String sets)
    {
        return "{\"date\":\"2026-10-28\",\"exercises\":[{\"name\":\"Squat\",\"sets\":[" + sets + "]}]}";
    }

    @Test
    public void plan_normal_importable()
    {
        SessionPreview preview = SessionPreview.of(parse(plan("{\"weight\":100,\"reps\":5},{\"weight\":95,\"reps\":6}")), "2026-10-27", null);
        assertNull(preview.problem());
        assertTrue(preview.isImportable());
        assertEquals(2, preview.getValidSetCount());
    }

    @Test
    public void poids_nan_bloque_avec_la_serie_en_cause()
    {
        SessionPreview preview = SessionPreview.of(parse(plan("{\"weight\":100,\"reps\":5},{\"weight\":NaN,\"reps\":6}")), "2026-10-27", null);
        assertFalse(preview.isImportable());
        assertEquals("Valeur invalide pour « Squat », série 2 : poids non numérique (NaN). Le plan est à corriger sur le PC.",
                preview.problem());
    }

    @Test
    public void nombre_geant_lu_comme_infini_bloque()
    {
        SessionPreview preview = SessionPreview.of(parse(plan("{\"weight\":1e400,\"reps\":5}")), "2026-10-27", null);
        assertFalse(preview.isImportable());
    }

    @Test
    public void reps_negatives_bloquent()
    {
        SessionPreview preview = SessionPreview.of(parse(plan("{\"weight\":100,\"reps\":-3}")), "2026-10-27", null);
        assertFalse(preview.isImportable());
    }

    @Test
    public void serie_incomplete_reste_ignoree_sans_bloquer()
    {
        SessionPreview preview = SessionPreview.of(parse(plan("{\"weight\":100,\"reps\":5},{\"reps\":6}")), "2026-10-27", null);
        assertTrue(preview.isImportable());
        assertEquals(1, preview.getSkippedSetCount());
    }

    @Test
    public void elements_null_ne_font_pas_planter_l_apercu()
    {
        String json = "{\"date\":\"2026-10-28\",\"exercises\":[null,{\"name\":\"Squat\",\"sets\":[null,{\"weight\":100,\"reps\":5}]}]}";
        SessionPreview preview = SessionPreview.of(parse(json), "2026-10-27", null);
        assertTrue(preview.isImportable());
        assertEquals(1, preview.getValidSetCount());
        assertEquals(1, preview.getSkippedSetCount());
        assertEquals(1, preview.getUnnamedExerciseCount());
        assertNull(SessionPreview.valuesProblem(parse(json)));
    }
}
