package com.example.verifit;

import com.example.verifit.model.Exercise;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

// Affichage assistance / lest d'un exercice assiste (retour Romain 20/10/2026, option A).
//
// La charge ENREGISTREE reste la charge effective (poids de corps de reference moins
// l'assistance, ou plus le lest) : c'est l'echelle positive que supposent les PR, le
// moteur Coaching (Wathan, delta min en %) et l'Excel. Cette classe ne fait que
// TRADUIRE cette charge pour l'ecran, au moment de l'afficher : rien n'est ecrit, rien
// n'est converti dans les donnees, tout l'historique s'affiche donc aussi converti.
//
// Meme regle et meme texte que format_load_display() cote Coaching (workout_engine.py,
// RULES.md §6.10), pour que l'app et la feuille de route disent la meme chose :
//   charge > reference : "lest +2.5 kg"
//   charge < reference : "assistance 20 kg (10 kg/cote)"
//   charge = reference : "poids de corps seul"
// Classe pure (aucun appel Android), testee par AssistDisplayTest.
public final class AssistDisplay
{
    public static final double DEFAULT_REF_KG = 70.0;

    private static final double EPS = 1e-9;
    private static final String SEPARATOR = " · "; // " · "

    private AssistDisplay() {}

    public static final class Config
    {
        public final double refKg;
        public final boolean perSide;

        public Config(double refKg, boolean perSide)
        {
            this.refKg = refKg;
            this.perSide = perSide;
        }
    }

    // Reglage d'un exercice, ou null s'il n'est pas marque comme assiste.
    public static Config configOf(Exercise exercise)
    {
        if (exercise == null || exercise.getAssistRefKg() == null)
        {
            return null;
        }
        return new Config(exercise.getAssistRefKg(), exercise.isAssistPerSide());
    }

    // Reglage de l'exercice nomme dans le catalogue, ou null (inconnu ou non assiste).
    public static Config forExercise(List<Exercise> knownExercises, String exerciseName)
    {
        if (knownExercises == null || exerciseName == null)
        {
            return null;
        }
        for (Exercise exercise : knownExercises)
        {
            if (exercise != null && exerciseName.equals(exercise.getName()))
            {
                return configOf(exercise);
            }
        }
        return null;
    }

    // Texte complet (champ de saisie, tableau de PR). Chaine vide si rien a afficher.
    public static String longLabel(Config config, Double weight)
    {
        Double delta = delta(config, weight);
        if (delta == null)
        {
            return "";
        }
        if (Math.abs(delta) < EPS)
        {
            return "poids de corps seul";
        }
        if (delta > 0)
        {
            return "lest +" + formatKg(delta) + " kg";
        }
        double assist = -delta;
        String text = "assistance " + formatKg(assist) + " kg";
        if (config.perSide)
        {
            text += " (" + formatKg(assist / 2) + " kg/côté)";
        }
        return text;
    }

    // Texte court pour une ligne de serie : ce qu'on regle sur la machine.
    public static String shortLabel(Config config, Double weight)
    {
        Double delta = delta(config, weight);
        if (delta == null)
        {
            return "";
        }
        if (Math.abs(delta) < EPS)
        {
            return "PDC";
        }
        if (delta > 0)
        {
            return "lest +" + formatKg(delta);
        }
        double assist = -delta;
        return config.perSide
                ? "assist. " + formatKg(assist / 2) + "/côté"
                : "assist. " + formatKg(assist);
    }

    // Ligne grise sous le poids : "assist. 10/cote · repos 2:15" (chaque partie peut
    // etre vide).
    public static String joinWithRest(String assistLabel, String restLabel)
    {
        boolean hasAssist = assistLabel != null && !assistLabel.isEmpty();
        boolean hasRest = restLabel != null && !restLabel.isEmpty();
        if (hasAssist && hasRest) return assistLabel + SEPARATOR + restLabel;
        if (hasAssist) return assistLabel;
        if (hasRest) return restLabel;
        return "";
    }

    // Lecture du champ "poids de corps de reference" du dialogue Edit : null si vide,
    // illisible, nul ou negatif (l'appelant refuse alors l'enregistrement).
    public static Double parseRefKg(String text)
    {
        if (text == null)
        {
            return null;
        }
        String trimmed = text.trim().replace(',', '.');
        if (trimmed.isEmpty())
        {
            return null;
        }
        try
        {
            double value = Double.parseDouble(trimmed);
            return (value > 0 && !Double.isNaN(value) && !Double.isInfinite(value)) ? value : null;
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    // Comme _fmt_kg_display() cote Python : 2 decimales au plus, zeros inutiles retires
    // ("10", "2.5", "6.25"). Arrondi au pair sur la valeur binaire exacte, comme le
    // format "{:.2f}" de Python.
    public static String formatKg(double value)
    {
        String text = new BigDecimal(value).setScale(2, RoundingMode.HALF_EVEN).toPlainString();
        if (text.contains("."))
        {
            text = text.replaceAll("0+$", "");
            if (text.endsWith("."))
            {
                text = text.substring(0, text.length() - 1);
            }
        }
        return text.equals("-0") ? "0" : text;
    }

    // Ecart a la reference, arrondi comme cote Python (round(..., 4)), ou null.
    private static Double delta(Config config, Double weight)
    {
        if (config == null || weight == null || Double.isNaN(weight))
        {
            return null;
        }
        return new BigDecimal(weight - config.refKg).setScale(4, RoundingMode.HALF_EVEN).doubleValue();
    }
}
