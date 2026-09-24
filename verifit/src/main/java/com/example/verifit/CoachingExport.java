package com.example.verifit;

import java.util.ArrayList;
import java.util.List;

// Racine de l'export JSON dedie au pipeline Coaching (retour Romain 24/09/2026, voir
// claude/verifit-migration-plan.md story 2.2, Groupe 2). schemaVersion permet a un
// futur lecteur Python de detecter un changement de format explicitement plutot que de
// deviner a partir des champs presents/absents - lecon tiree des decalages de colonnes
// deja rencontres sur l'export CSV historique (voir DataStorage.writeFile()). A
// incrementer si la structure de ce contrat change un jour de facon non retro-
// compatible (ajouter un champ optionnel ne le justifie pas).
public class CoachingExport
{
    private final int schemaVersion = 1;
    private final String exportedAt;
    private final List<CoachingExportDay> days = new ArrayList<CoachingExportDay>();

    public CoachingExport(String exportedAt)
    {
        this.exportedAt = exportedAt;
    }

    public void addDay(CoachingExportDay day)
    {
        days.add(day);
    }
}
