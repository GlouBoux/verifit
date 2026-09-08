package com.example.verifit.model;

import android.graphics.Color;

import java.util.LinkedHashMap;
import java.util.Map;

// "Category Colours" (Vague 1 du plan de migration `docs/fitnotes-migration-plan.md`,
// feature FitNotes) : une couleur par categorie. Palette fixe pour les 7 categories
// par defaut de R.array.Categories/strings.xml - il n'existe PAS d'ecran de gestion des
// categories cote Verifit aujourd'hui (juste ce tableau compile en dur), donc pas de
// personnalisation utilisateur possible pour l'instant : a revisiter si Romain veut un
// vrai ecran "Category List" (Add/Edit/Delete/Reorder + Colours, voir
// fitnotes-features-exercises.md section 10) plus tard - voir la note correspondante
// dans le plan de migration.
//
// Utilise pour l'instant par le point de couleur a cote de la categorie dans la liste
// d'exercices (ExerciseAdapter) - prepare aussi le terrain pour les points multicolores
// du Calendrier (Vague 3, calendar_day_dot_shape.xml qui n'a aujourd'hui qu'une seule
// couleur fixe).
public class CategoryColours
{
    private static final Map<String, Integer> COLOURS = new LinkedHashMap<>();

    static
    {
        COLOURS.put("Chest", Color.parseColor("#E53935"));
        COLOURS.put("Back", Color.parseColor("#1E88E5"));
        COLOURS.put("Shoulders", Color.parseColor("#8E24AA"));
        COLOURS.put("Biceps", Color.parseColor("#FB8C00"));
        COLOURS.put("Triceps", Color.parseColor("#00897B"));
        COLOURS.put("Legs", Color.parseColor("#43A047"));
        COLOURS.put("Abs", Color.parseColor("#FDD835"));
    }

    // Categorie inconnue (custom, ou future categorie ajoutee a l'array sans mise a
    // jour de cette palette) - gris neutre plutot que planter/afficher du noir.
    private static final int DEFAULT_COLOUR = Color.parseColor("#9E9E9E");

    private CategoryColours()
    {
    }

    public static int getColour(String categoryName)
    {
        Integer colour = COLOURS.get(categoryName);
        return colour != null ? colour : DEFAULT_COLOUR;
    }
}
