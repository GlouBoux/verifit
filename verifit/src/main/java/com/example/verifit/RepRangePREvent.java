package com.example.verifit;

import com.example.verifit.model.WorkoutSet;

// Un evenement de l'historique des PR par nombre de reps (retour Romain 06/09/2026 :
// "Les PRs sont egalement deduis (20 kgs pour 8 reps est egalement un PR pour 7 reps
// s'il n'y a pas de valeur. transitivite)"). Represente soit un record REEL (une
// serie loggee exactement a ce nombre de reps), soit un record DEDUIT (une serie avec
// PLUS de reps qui, par transitivite, prouve que ce poids est aussi atteignable a un
// nombre de reps inferieur - si on a reussi R repetitions, on a forcement pu en faire
// moins). sourceReps garde le nombre de reps REEL de la serie qui a produit ce
// record (peut differer de la case du tableau ou cet evenement apparait, ex : un
// evenement "deduit" pour la case 5 reps peut provenir d'une serie de 6 reps) -
// affiche a l'utilisateur pour qu'il comprenne d'ou vient le record. Construit par
// DataStorage.calculateRepRangeHistory().
public class RepRangePREvent
{
    private final Double weight;
    private final String date;
    private final int sourceReps;
    private final boolean deduced;
    private final WorkoutSet sourceSet;

    public RepRangePREvent(Double weight, String date, int sourceReps, boolean deduced, WorkoutSet sourceSet)
    {
        this.weight = weight;
        this.date = date;
        this.sourceReps = sourceReps;
        this.deduced = deduced;
        this.sourceSet = sourceSet;
    }

    public Double getWeight()
    {
        return weight;
    }

    public String getDate()
    {
        return date;
    }

    public int getSourceReps()
    {
        return sourceReps;
    }

    public boolean isDeduced()
    {
        return deduced;
    }

    public WorkoutSet getSourceSet()
    {
        return sourceSet;
    }
}
