package com.example.verifit.model;

import java.util.HashSet;
import java.util.Set;

// "Calendar : Category Filter + Exercise Filter" (Vague 3 du plan de migration, item 11,
// feature FitNotes) - critere de filtrage applique par CalendarPickerDialog uniquement
// en mode "Browse" (calendrier ouvert depuis l'icone calendrier de l'accueil, voir
// CalendarPickerDialog.enableBrowsingFeatures()), jamais en mode "Pick a day" (Copy/Move
// Workout) - un filtre de recherche n'a pas de sens quand on choisit juste un jour source
// a copier/deplacer.
//
// Vit en memoire pour la duree de la session app (instance statique partagee dans
// CalendarPickerDialog, pas persistee sur disque) : repart a zero (aucun filtre actif) a
// chaque redemarrage de l'app. Choix delibere pour rester simple - a revoir si Romain
// veut que son filtre survive au redemarrage de l'app.
public class CalendarFilter
{
    // Comparateurs pour le filtre Exercise (poids/reps) - "More Than" de FitNotes plus
    // quelques variantes utiles (At Least/At Most/Less Than/Exactly), voir
    // fitnotes-features-calendar.md section "Exercise Filter".
    public enum Comparison
    {
        AT_LEAST("At Least"),
        MORE_THAN("More Than"),
        AT_MOST("At Most"),
        LESS_THAN("Less Than"),
        EXACTLY("Exactly");

        public final String label;

        Comparison(String label)
        {
            this.label = label;
        }

        public boolean matches(double actual, double threshold)
        {
            switch (this)
            {
                case AT_LEAST:
                    return actual >= threshold;
                case MORE_THAN:
                    return actual > threshold;
                case AT_MOST:
                    return actual <= threshold;
                case LESS_THAN:
                    return actual < threshold;
                case EXACTLY:
                    return actual == threshold;
                default:
                    return false;
            }
        }

        @Override
        public String toString()
        {
            return label;
        }
    }

    // Category filter - vide = inactif.
    private Set<String> categories = new HashSet<>();
    private boolean matchAll = false; // false = Match Any (OR), true = Match All (AND)

    // Exercise filter - exerciseName null/vide = inactif. Les seuils sont optionnels
    // meme quand un exercice est choisi (juste "cet exercice a ete fait ce jour-la",
    // sans condition de performance).
    private String exerciseName;
    private Comparison weightComparison;
    private Double weightThreshold;
    private Comparison repsComparison;
    private Double repsThreshold;

    public Set<String> getCategories()
    {
        return categories;
    }

    public void setCategories(Set<String> categories)
    {
        this.categories = categories != null ? categories : new HashSet<>();
    }

    public boolean isMatchAll()
    {
        return matchAll;
    }

    public void setMatchAll(boolean matchAll)
    {
        this.matchAll = matchAll;
    }

    public String getExerciseName()
    {
        return exerciseName;
    }

    public void setExerciseName(String exerciseName)
    {
        this.exerciseName = exerciseName;
    }

    public Comparison getWeightComparison()
    {
        return weightComparison;
    }

    public void setWeightComparison(Comparison weightComparison)
    {
        this.weightComparison = weightComparison;
    }

    public Double getWeightThreshold()
    {
        return weightThreshold;
    }

    public void setWeightThreshold(Double weightThreshold)
    {
        this.weightThreshold = weightThreshold;
    }

    public Comparison getRepsComparison()
    {
        return repsComparison;
    }

    public void setRepsComparison(Comparison repsComparison)
    {
        this.repsComparison = repsComparison;
    }

    public Double getRepsThreshold()
    {
        return repsThreshold;
    }

    public void setRepsThreshold(Double repsThreshold)
    {
        this.repsThreshold = repsThreshold;
    }

    public boolean isActive()
    {
        return !categories.isEmpty() || (exerciseName != null && !exerciseName.isEmpty());
    }

    public void clear()
    {
        categories = new HashSet<>();
        matchAll = false;
        exerciseName = null;
        weightComparison = null;
        weightThreshold = null;
        repsComparison = null;
        repsThreshold = null;
    }

    // Vrai si ce jour correspond au filtre actif. Un jour sans aucune serie ne
    // correspond jamais (rien a filtrer). Categorie ET exercice doivent tous les deux
    // etre satisfaits quand les deux sont actifs en meme temps.
    public boolean matches(WorkoutDay day)
    {
        if (day == null || day.getSets() == null || day.getSets().isEmpty())
        {
            return false;
        }

        if (!categories.isEmpty() && !matchesCategories(day))
        {
            return false;
        }

        if (exerciseName != null && !exerciseName.isEmpty() && !matchesExercise(day))
        {
            return false;
        }

        return true;
    }

    private boolean matchesCategories(WorkoutDay day)
    {
        Set<String> dayCategories = new HashSet<>();
        for (WorkoutSet set : day.getSets())
        {
            if (set.getCategory() != null)
            {
                dayCategories.add(set.getCategory());
            }
        }

        if (matchAll)
        {
            return dayCategories.containsAll(categories);
        }

        for (String category : categories)
        {
            if (dayCategories.contains(category))
            {
                return true;
            }
        }
        return false;
    }

    // Une SEULE serie doit satisfaire a la fois le seuil de poids ET de reps (si les
    // deux sont renseignes) - reprend l'exemple de fitnotes-features-calendar.md :
    // "Flat Barbell Bench Press a au moins 100kg pour 5+ reps" decrit une serie
    // precise, pas un poids max et un nombre de reps max pris sur deux series
    // differentes du meme jour.
    private boolean matchesExercise(WorkoutDay day)
    {
        for (WorkoutSet set : day.getSets())
        {
            if (!exerciseName.equals(set.getExerciseName()))
            {
                continue;
            }

            if (weightThreshold != null && weightComparison != null)
            {
                if (set.getWeight() == null || !weightComparison.matches(set.getWeight(), weightThreshold))
                {
                    continue;
                }
            }

            if (repsThreshold != null && repsComparison != null)
            {
                if (set.getReps() == null || !repsComparison.matches(set.getReps(), repsThreshold))
                {
                    continue;
                }
            }

            return true;
        }
        return false;
    }
}
