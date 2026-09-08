package com.example.verifit.model;

import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;

// Palette de couleurs pour les groupes de superset (barre coloree dans la liste
// du jour - DayActivity/DayExerciseAdapter, Vague 2 du plan de migration, retour
// Romain 07/09/2026). Deliberement distincte de CategoryColours (categories
// musculaires, ecran Exercises) : les deux ne sont jamais affichees sur le meme
// ecran, mais des palettes separees evitent toute ambiguite si ca change un jour.
public class SupersetColours {

    private static final int[] PALETTE = new int[] {
            Color.argb(255, 216, 27, 96),   // Rose/Rouge
            Color.argb(255, 30, 136, 229),  // Bleu
            Color.argb(255, 67, 160, 71),   // Vert
            Color.argb(255, 251, 140, 0),   // Orange
            Color.argb(255, 142, 36, 170),  // Violet
            Color.argb(255, 0, 172, 193),   // Turquoise
            Color.argb(255, 253, 216, 53),  // Jaune
            Color.argb(255, 109, 76, 65),   // Marron
    };

    public static int[] getPalette()
    {
        return PALETTE;
    }

    // Premiere couleur de la palette pas deja utilisee par un groupe existant ce
    // jour-la (proposee par defaut a la creation d'un nouveau groupe) - retombe sur
    // la palette en boucle (modulo) si tous les groupes existants les ont deja
    // toutes prises.
    public static int getNextAvailableColor(List<SupersetGroup> existingGroups)
    {
        List<Integer> used = new ArrayList<Integer>();
        if (existingGroups != null)
        {
            for (SupersetGroup group : existingGroups)
            {
                used.add(group.getColor());
            }
        }

        for (int color : PALETTE)
        {
            if (!used.contains(color))
            {
                return color;
            }
        }

        return PALETTE[existingGroups == null ? 0 : existingGroups.size() % PALETTE.length];
    }
}
