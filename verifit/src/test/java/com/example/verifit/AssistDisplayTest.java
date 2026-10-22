package com.example.verifit;

import com.example.verifit.model.Exercise;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

// Affichage assistance / lest (20/10/2026). Les valeurs viennent de vraies seances de
// Romain (export du 16/10/2026) et des exemples de RULES.md §6.10 cote Coaching.
public class AssistDisplayTest
{
    private static final AssistDisplay.Config PER_SIDE = new AssistDisplay.Config(70.0, true);
    private static final AssistDisplay.Config GLOBAL = new AssistDisplay.Config(70.0, false);

    @Test
    public void assistanceParCote_seancesReelles()
    {
        // Pelican 12/10 : 50 kg x 5 = assistance 20 kg, 10 kg par cote.
        assertEquals("assistance 20 kg (10 kg/côté)", AssistDisplay.longLabel(PER_SIDE, 50.0));
        assertEquals("assist. 10/côté", AssistDisplay.shortLabel(PER_SIDE, 50.0));
        // Pelican 12/10 : 62.5 kg = assistance 7.5 kg, 3.75 par cote.
        assertEquals("assistance 7.5 kg (3.75 kg/côté)", AssistDisplay.longLabel(PER_SIDE, 62.5));
        assertEquals("assist. 3.75/côté", AssistDisplay.shortLabel(PER_SIDE, 62.5));
        // HSPU FP 12/10 : 48.5 kg = assistance 21.5 kg, 10.75 par cote.
        assertEquals("assistance 21.5 kg (10.75 kg/côté)", AssistDisplay.longLabel(PER_SIDE, 48.5));
    }

    @Test
    public void lest_auDessusDeLaReference()
    {
        // Pelican 02/10 : 72.5 kg x 2 = lest +2.5 kg.
        assertEquals("lest +2.5 kg", AssistDisplay.longLabel(PER_SIDE, 72.5));
        assertEquals("lest +2.5", AssistDisplay.shortLabel(PER_SIDE, 72.5));
        // Exemple de RULES.md : micro-lest de 0.5 kg.
        assertEquals("lest +0.5 kg", AssistDisplay.longLabel(PER_SIDE, 70.5));
    }

    @Test
    public void poidsDeCorpsSeul()
    {
        assertEquals("poids de corps seul", AssistDisplay.longLabel(PER_SIDE, 70.0));
        assertEquals("PDC", AssistDisplay.shortLabel(PER_SIDE, 70.0));
        // Bruit de virgule flottante : toujours le poids de corps.
        assertEquals("PDC", AssistDisplay.shortLabel(PER_SIDE, 69.99999999999));
    }

    @Test
    public void assistanceSansParCote()
    {
        assertEquals("assistance 12.5 kg", AssistDisplay.longLabel(GLOBAL, 57.5));
        assertEquals("assist. 12.5", AssistDisplay.shortLabel(GLOBAL, 57.5));
    }

    @Test
    public void exempleRulesMicroLoading()
    {
        // RULES.md §6.10 : "assistance 12.5 kg (6.25 kg/cote)".
        assertEquals("assistance 12.5 kg (6.25 kg/côté)", AssistDisplay.longLabel(PER_SIDE, 57.5));
    }

    @Test
    public void autreReference()
    {
        AssistDisplay.Config ref75 = new AssistDisplay.Config(75.0, true);
        assertEquals("assistance 25 kg (12.5 kg/côté)", AssistDisplay.longLabel(ref75, 50.0));
        assertEquals("lest +5 kg", AssistDisplay.longLabel(ref75, 80.0));
    }

    @Test
    public void rienAAfficher_sansReglageOuSansPoids()
    {
        assertEquals("", AssistDisplay.longLabel(null, 50.0));
        assertEquals("", AssistDisplay.shortLabel(null, 50.0));
        assertEquals("", AssistDisplay.longLabel(PER_SIDE, null));
        assertEquals("", AssistDisplay.shortLabel(PER_SIDE, Double.NaN));
    }

    @Test
    public void formatKg_commePython()
    {
        // _fmt_kg_display : f"{v:.2f}".rstrip("0").rstrip(".")
        assertEquals("10", AssistDisplay.formatKg(10.0));
        assertEquals("2.5", AssistDisplay.formatKg(2.5));
        assertEquals("6.25", AssistDisplay.formatKg(6.25));
        assertEquals("0", AssistDisplay.formatKg(0.0));
        // 0.125 est exact en binaire : Python arrondit au pair ("0.12").
        assertEquals("0.12", AssistDisplay.formatKg(0.125));
        assertEquals("0.38", AssistDisplay.formatKg(0.375));
    }

    @Test
    public void joinWithRest()
    {
        assertEquals("assist. 10/côté · repos 2:15", AssistDisplay.joinWithRest("assist. 10/côté", "repos 2:15"));
        assertEquals("assist. 10/côté", AssistDisplay.joinWithRest("assist. 10/côté", ""));
        assertEquals("repos 2:15", AssistDisplay.joinWithRest("", "repos 2:15"));
        assertEquals("repos 2:15", AssistDisplay.joinWithRest(null, "repos 2:15"));
        assertEquals("", AssistDisplay.joinWithRest(null, null));
    }

    @Test
    public void parseRefKg()
    {
        assertEquals(70.0, AssistDisplay.parseRefKg("70"), 0.0);
        assertEquals(72.5, AssistDisplay.parseRefKg(" 72,5 "), 0.0);
        assertNull(AssistDisplay.parseRefKg(""));
        assertNull(AssistDisplay.parseRefKg(null));
        assertNull(AssistDisplay.parseRefKg("abc"));
        assertNull(AssistDisplay.parseRefKg("0"));
        assertNull(AssistDisplay.parseRefKg("-5"));
    }

    @Test
    public void reglageLuSurLExercice()
    {
        Exercise pelican = new Exercise("Assisted Pelican", "Arms");
        Exercise squat = new Exercise("Hack Squat TG", "Legs");
        pelican.setAssistRefKg(70.0);
        pelican.setAssistPerSide(true);
        List<Exercise> known = Arrays.asList(squat, pelican);

        AssistDisplay.Config config = AssistDisplay.forExercise(known, "Assisted Pelican");
        assertNotNull(config);
        assertEquals(70.0, config.refKg, 0.0);
        assertTrue(config.perSide);

        // Exercice non coche, inconnu ou nom null : aucun affichage.
        assertNull(AssistDisplay.forExercise(known, "Hack Squat TG"));
        assertNull(AssistDisplay.forExercise(known, "Inconnu"));
        assertNull(AssistDisplay.forExercise(known, null));
        assertNull(AssistDisplay.forExercise(null, "Assisted Pelican"));
    }

    @Test
    public void ancienneSauvegarde_aucunExerciceAssiste()
    {
        // known_exercises tel qu'ecrit avant ce changement (pas de champ assist*).
        String oldJson = "[{\"Name\":\"Assisted Pelican\",\"BodyPart\":\"Arms\",\"favorite\":false,\"notes\":\"\"}]";
        List<Exercise> known = new Gson().fromJson(oldJson, new TypeToken<ArrayList<Exercise>>(){}.getType());

        assertNull(known.get(0).getAssistRefKg());
        assertFalse(known.get(0).isAssistPerSide());
        assertNull(AssistDisplay.forExercise(known, "Assisted Pelican"));
    }

    @Test
    public void allerRetourGson_conserveLeReglage()
    {
        Exercise pelican = new Exercise("Assisted Pelican", "Arms");
        pelican.setAssistRefKg(70.0);
        pelican.setAssistPerSide(true);
        ArrayList<Exercise> list = new ArrayList<>(Arrays.asList(pelican));

        Gson gson = new Gson();
        List<Exercise> back = gson.fromJson(gson.toJson(list), new TypeToken<ArrayList<Exercise>>(){}.getType());

        assertEquals(70.0, back.get(0).getAssistRefKg(), 0.0);
        assertTrue(back.get(0).isAssistPerSide());
    }

    @Test
    public void affichageNeModifiePasLaCharge()
    {
        // Garantie centrale de l'option A : formater ne touche jamais la valeur.
        Double weight = 51.0;
        AssistDisplay.longLabel(PER_SIDE, weight);
        AssistDisplay.shortLabel(PER_SIDE, weight);
        assertEquals(51.0, weight, 0.0);
    }
}
