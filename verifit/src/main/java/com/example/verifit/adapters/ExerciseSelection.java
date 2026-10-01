package com.example.verifit.adapters;

import com.example.verifit.model.WorkoutExercise;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Etat de selection multiple et de repli des cartes d'exercices, partage par
 * ViewPagerExerciseAdapter (onglet Workout) et DayExerciseAdapter (ecran d'un jour).
 *
 * Lot C, etape C.3 (01/10/2026) : ce bloc etait copie a l'identique dans les deux
 * adapters. Sans dependance Android (teste en JUnit) ; les adapters gardent le
 * rafraichissement de l'affichage (notify...) et le listener.
 *
 * Suivi par NOM d'exercice plutot que par position (retour Romain 05/09/2026) : la
 * poignee de reorganisation reste active pendant la selection, une selection par
 * position deviendrait fausse des qu'un glisser-deposer change l'ordre.
 *
 * Repli (retour Romain 05/09/2026, bis) : un drag replie toutes les cartes et elles
 * RESTENT repliees apres le geste, jusqu'a un tap manuel sur la carte.
 */
public final class ExerciseSelection
{
    private boolean active = false;
    private final Set<String> selected = new HashSet<>();
    private final Set<String> collapsed = new HashSet<>();

    public void enter()
    {
        active = true;
        selected.clear();
    }

    public void exit()
    {
        active = false;
        selected.clear();
    }

    public boolean isActive()
    {
        return active;
    }

    public boolean isSelected(String exerciseName)
    {
        return selected.contains(exerciseName);
    }

    public int count()
    {
        return selected.size();
    }

    public void toggle(String exerciseName)
    {
        if (!selected.remove(exerciseName))
        {
            selected.add(exerciseName);
        }
    }

    // Bouton "All" (retour Romain 08/09/2026) : selectionne tous les exercices affiches ;
    // un reclic alors que tout est deja selectionne deselectionne tout (bis, 08/09).
    public void selectAllOrNone(List<WorkoutExercise> shown)
    {
        if (!shown.isEmpty() && selected.size() == shown.size())
        {
            selected.clear();
        }
        else
        {
            for (WorkoutExercise exercise : shown)
            {
                selected.add(exercise.getExercise());
            }
        }
    }

    // Dans l'ordre d'AFFICHAGE (Vague 2, 07/09/2026), pas l'ordre du HashSet : sert
    // d'ordre d'enchainement quand on cree un superset (DayActions).
    public List<String> selectedInOrder(List<WorkoutExercise> shown)
    {
        List<String> ordered = new ArrayList<>();
        for (WorkoutExercise exercise : shown)
        {
            if (selected.contains(exercise.getExercise()))
            {
                ordered.add(exercise.getExercise());
            }
        }
        return ordered;
    }

    // Carte repliee a l'affichage : toujours en mode selection, sinon si repliee a la
    // main ou par un drag.
    public boolean isCollapsed(String exerciseName)
    {
        return active || collapsed.contains(exerciseName);
    }

    public void toggleCollapsed(String exerciseName)
    {
        if (!collapsed.remove(exerciseName))
        {
            collapsed.add(exerciseName);
        }
    }

    // Debut d'un drag : replie (et memorise) toutes les cartes affichees.
    public void collapseAll(List<WorkoutExercise> shown)
    {
        for (WorkoutExercise exercise : shown)
        {
            collapsed.add(exercise.getExercise());
        }
    }
}
