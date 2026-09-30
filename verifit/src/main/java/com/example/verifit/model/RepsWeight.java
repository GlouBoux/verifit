package com.example.verifit.model;

/**
 * Couple (reps, poids) d'un record de volume par serie (DataStorage.setVolumePRs).
 *
 * Remplace android.util.Pair (lot C, etape C.1, 29/09/2026) : dans les tests JUnit
 * locaux (gradlew test), android.jar n'est qu'un bouchon et un Pair construit y garde
 * first/second a null, ce qui faisait planter calculatePersonalRecords() hors
 * telephone. Cette classe du projet se comporte pareil partout.
 */
public class RepsWeight
{
    private final Double reps;
    private final Double weight;

    public RepsWeight(Double reps, Double weight)
    {
        this.reps = reps;
        this.weight = weight;
    }

    public Double getReps()
    {
        return reps;
    }

    public Double getWeight()
    {
        return weight;
    }
}
