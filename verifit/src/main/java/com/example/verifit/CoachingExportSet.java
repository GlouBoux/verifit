package com.example.verifit;

import com.example.verifit.model.WorkoutSet;

// Une serie loguee, mise en forme pour l'export JSON dedie au pipeline Coaching
// (workout_engine.py / pr_tracking.py) - retour Romain 24/09/2026, voir
// claude/verifit-migration-plan.md, story 2.2 du Groupe 2 ("historiser les series
// prevues/tentees sans dependre du backup FitNotes"). Contrat delibérément separe du
// modele interne WorkoutSet : un futur renommage/refactor cote app ne casse jamais ce
// format tant que cette classe n'est pas touchee, et un lecteur Python qui ignore les
// cles qu'il ne connait pas encore reste compatible avec un futur ajout de champ ici -
// contrairement au CSV existant (voir le commentaire de DataStorage.writeFile() sur
// son decalage de colonnes a chaque ajout de champ, deja rencontre plusieurs fois).
//
// Serialise par Gson par reflexion sur les champs (pas besoin de getters pour ca) - les
// noms de champs Java ci-dessous SONT les cles JSON produites (schemaVersion,
// isCompleted, planComment, plannedReps, plannedWeight...), aucune annotation
// @SerializedName utilisee ici pour rester simple et verifiable sans compilateur
// Android disponible cote Claude (voir CLAUDE.md du depot Coaching, "limite connue").
public class CoachingExportSet
{
    private final String date;
    private final String exercise;
    private final String category;
    private final Double weight;
    private final Double reps;
    private final boolean isCompleted;
    // null quand la serie n'a pas de note perso (voir WorkoutSet.hasNote()) - jamais la
    // chaine vide ni la chaine "null", pour rester sans ambiguite cote lecteur Python
    // (`is None` plutot que devoir tester une chaine magique).
    private final String comment;
    // null quand la serie n'a pas de plan du script generateur (voir
    // WorkoutSet.hasPlanComment()). Meme texte que celui deja lu cote FitNotes par
    // pr_tracking._parse_set_comment() (methode - vs X kg - theorique Y kg - filet N
    // reps), reutilisable tel quel par un futur lecteur Python de ce JSON.
    private final String planComment;
    // "Prevu" au moment de l'import (voir WorkoutSet.plannedReps/plannedWeight) - null
    // si cette serie n'a jamais ete importee (saisie manuelle, import CSV d'historique).
    private final Double plannedReps;
    private final Double plannedWeight;

    public CoachingExportSet(WorkoutSet set)
    {
        this.date = set.getDate();
        this.exercise = set.getExerciseName();
        this.category = set.getCategory();
        this.weight = set.getWeight();
        this.reps = set.getReps();
        this.isCompleted = set.isCompleted();
        this.comment = set.hasNote() ? set.getComment() : null;
        this.planComment = set.hasPlanComment() ? set.getPlanComment() : null;
        this.plannedReps = set.getPlannedReps();
        this.plannedWeight = set.getPlannedWeight();
    }
}
