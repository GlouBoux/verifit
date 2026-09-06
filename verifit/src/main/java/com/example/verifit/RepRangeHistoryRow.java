package com.example.verifit;

import java.util.ArrayList;

// Une ligne de la liste principale de l'ecran "Historique des PR par nombre de reps"
// (retour Romain 06/09/2026) : un nombre de reps et son record ACTUEL (le dernier
// evenement de DataStorage.calculateRepRangeHistory() pour cette case - grise dans
// l'affichage s'il est deduit, cf. RepRangePREvent.isDeduced()). L'historique complet
// (record actuel + precedents) ne s'affiche qu'au clic, dans une popup - voir
// RepRangeHistoryAdapter.
public class RepRangeHistoryRow
{
    private final int reps;
    private final ArrayList<RepRangePREvent> allEvents;

    public RepRangeHistoryRow(int reps, ArrayList<RepRangePREvent> allEvents)
    {
        this.reps = reps;
        this.allEvents = allEvents;
    }

    public int getReps()
    {
        return reps;
    }

    public ArrayList<RepRangePREvent> getAllEvents()
    {
        return allEvents;
    }

    // Le dernier evenement chronologique = le record actuel pour cette case.
    public RepRangePREvent getCurrentEvent()
    {
        return allEvents.get(allEvents.size() - 1);
    }
}
