package com.example.verifit;

/**
 * Valeurs acceptables pour le poids et les reps d'une serie venue de l'exterieur (revue
 * securite du 27/10/2026, lot S2, point E1 de claude/fitengine-revue-securite-2026-10-27.md).
 *
 * Pourquoi : Gson lit sans erreur un poids "NaN" ou "1e400" (devenu Infinity), mais refuse
 * ensuite de l'ecrire. Une seule serie de ce genre importee faisait echouer TOUTES les
 * sauvegardes suivantes (plantage a chaque serie loguee, series perdues jusqu'au
 * redemarrage de l'app). Les valeurs non finies sont donc refusees AVANT d'entrer dans
 * les donnees : a l'apercu et a l'import d'une seance (SessionPreview, SessionImporter),
 * a l'import CSV (DataStorage.parseCsvSets) et a la restauration (BackupManager.parse).
 *
 * Les bornes ne s'appliquent qu'a l'import de seance (plans du moteur Coaching) : une
 * restauration de backup doit pouvoir reprendre n'importe quel historique, meme ancien
 * (poids negatifs FitNotes pour un exercice assiste par exemple), seul le non-fini y est
 * refuse.
 *
 * Logique pure, sans Android : testee en JUnit (SetValueRulesTest). Pas de
 * Double.isFinite() : API Android 24, l'app vise encore minSdk 16.
 */
public final class SetValueRules
{
    // Poids en kg, en valeur absolue (une assistance peut etre notee en negatif).
    public static final double MAX_ABS_WEIGHT_KG = 1000.0;
    // Reps, ou secondes pour un maintien (hold).
    public static final double MAX_REPS = 1000.0;

    private SetValueRules() {}

    public static boolean isFinite(Double value)
    {
        return value != null && !value.isNaN() && !value.isInfinite();
    }

    // Probleme d'une serie importee (seance), ou null si elle est acceptable. Une valeur
    // null n'est pas un probleme ici : la serie est alors "incomplete" et ignoree, comme
    // avant (voir SessionPreview).
    public static String importProblem(Double weight, Double reps)
    {
        if (weight != null)
        {
            if (!isFinite(weight))
            {
                return "poids non numérique (" + weight + ")";
            }
            if (Math.abs(weight) > MAX_ABS_WEIGHT_KG)
            {
                return "poids hors limites (" + weight + " kg)";
            }
        }
        if (reps != null)
        {
            if (!isFinite(reps))
            {
                return "reps non numériques (" + reps + ")";
            }
            if (reps < 0 || reps > MAX_REPS)
            {
                return "reps hors limites (" + reps + ")";
            }
        }
        return null;
    }

    // Probleme d'une serie restauree (backup, CSV) : seul le non-fini est refuse. null si
    // acceptable (null compris : la presence est verifiee par l'appelant).
    public static String restoreProblem(Double weight, Double reps)
    {
        if (weight != null && !isFinite(weight))
        {
            return "poids non numérique (" + weight + ")";
        }
        if (reps != null && !isFinite(reps))
        {
            return "reps non numériques (" + reps + ")";
        }
        return null;
    }
}
