package com.example.verifit;

import com.example.verifit.model.WorkoutSet;

import java.util.HashMap;
import java.util.List;

// Detection des "echecs" d'une serie de travail, pour le Share workout (retour Romain
// 01/10/2026) : comme le tag [PR] quand une serie bat un record, une serie de travail qui
// ne bat AUCUN record recoit "Echec d'un N RM", ou N est le plus petit nombre de reps
// pour lequel CE poids aurait ete un PR.
//
// Regle (exemples fournis par Romain) :
// - Records effectifs par nombre de reps r = meilleur poids avec AU MOINS r reps
//   (meme convention que DataStorage.calculateRepRangeHistory(), "transitivite").
// - Une serie de k reps a poids W est un PR si W > record(k) (ou aucun record a k) :
//   pas d'echec dans ce cas.
// - Sinon, N = le plus petit r > k tel que W > record(r) (ou aucun record a r).
//   "27 kg x 7, records 27.5@7, 26.0@8, 25.0@9" -> 8 ; "28 kg x 6, records 30@6,
//   27.5@7, 26.0@8, 25.0@9" -> 7 (28 > 27.5).
// - Les records sont ceux d'AVANT la serie (ordre chronologique), comme le badge PR.
// - Jamais pour un echauffement (commentaire "Echauffement", cf. workout_engine.py).
// - Aucun libelle si aucun r de 1 a (reps max connues + 1) ne convient : impossible
//   (le cas r = max + 1 est toujours battable), donc en pratique toujours un libelle
//   pour une serie hors PR qui n'est pas un echauffement.
//
// Classe volontairement sans dependance Android (testable en JUnit/javac pur).
public class FailedRmCalculator
{
    // Meme format que DataStorage.repRangePRKey() ("date#reps#weight") : la recherche se
    // fait avec DataStorage.repRangePRKey() cote WorkoutReportGenerator.
    static String key(String date, int reps, Double weight)
    {
        return date + "#" + reps + "#" + weight;
    }

    // "Echauffement" / "Échauffement" (insensible a la casse et aux accents), dans le
    // plan du script ou dans la note, en debut de texte.
    public static boolean isWarmup(WorkoutSet set)
    {
        return startsWithEchauffement(set.getPlanComment()) || startsWithEchauffement(set.getComment());
    }

    private static boolean startsWithEchauffement(String text)
    {
        if (text == null)
        {
            return false;
        }
        String t = text.trim().toLowerCase().replace('é', 'e').replace('è', 'e');
        return t.startsWith("echauffement");
    }

    // sets : TOUTES les series d'UN exercice, tri chronologique (jours tries par date,
    // ordre de saisie dans le jour), exactement comme DataStorage.calculateRepRangeHistory().
    // Retourne {cle "date#reps#weight" -> N} pour les series en echec.
    public static HashMap<String, Integer> compute(List<WorkoutSet> sets)
    {
        HashMap<String, Integer> failed = new HashMap<String, Integer>();
        HashMap<Integer, Double> best = new HashMap<Integer, Double>();
        int maxKnownReps = 0;

        for (WorkoutSet set : sets)
        {
            if (set.getReps() == null || set.getWeight() == null || set.getDate() == null || set.getReps() == 0.0)
            {
                continue;
            }
            int k = (int) Math.round(set.getReps());
            double w = set.getWeight();

            if (!isWarmup(set))
            {
                Double bestAtK = best.get(k);
                boolean isPr = bestAtK == null || w > bestAtK;
                if (!isPr)
                {
                    for (int r = k + 1; r <= maxKnownReps + 1; r++)
                    {
                        Double bestAtR = best.get(r);
                        if (bestAtR == null || w > bestAtR)
                        {
                            failed.put(key(set.getDate(), k, set.getWeight()), r);
                            break;
                        }
                    }
                }
            }

            // Mise a jour des records (echauffements inclus, comme l'historique des PR).
            for (int r = 1; r <= k; r++)
            {
                Double previous = best.get(r);
                if (previous == null || w > previous)
                {
                    best.put(r, w);
                }
            }
            if (k > maxKnownReps)
            {
                maxKnownReps = k;
            }
        }
        return failed;
    }
}
