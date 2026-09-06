package com.example.verifit.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

public class WorkoutDay {

    // Attributes
    private ArrayList<WorkoutExercise> Exercises;
    private ArrayList<WorkoutSet> Sets;
    private Double DayVolume;
    private String Date;
    private int Reps;

    // Display order of this day's exercises (list of exercise names), retour Romain
    // 05/09/2026 : réordonnable à la main, comme FitNotes. Absent des sauvegardes
    // antérieures à cet ajout - Gson (voir DataStorage.saveWorkoutData/loadWorkoutData)
    // appelle ce constructeur par défaut à la désérialisation, donc un ancien fichier
    // sans ce champ retombe simplement sur la liste vide initialisée ici, jamais null ;
    // UpdateData() la garde quand même défensivement.
    private ArrayList<String> ExerciseOrder;


    // Default Constructor
    public WorkoutDay()
    {
        Sets = new ArrayList<WorkoutSet>();
        Exercises = new ArrayList<WorkoutExercise>();
        ExerciseOrder = new ArrayList<String>();
        DayVolume = 0.0;
        Date = "0000-00-00";
        Reps = 0;
    }

    // Add Set to Object
    public void addSet(WorkoutSet Set)
    {
        this.getSets().add(Set);
        UpdateData();
    }

    // Reinsertion a un index precis dans Sets (retour Romain 06/09/2026 : "je voudrai
    // que l'undo remette la serie la ou elle etait avant la suppression" - addSet()
    // ci-dessus l'ajoutait toujours en dernier position, ce qui la faisait apparaitre
    // tout en bas au lieu de sa place d'origine parmi les series de son exercice).
    // L'index est celui qu'occupait la serie dans Sets juste avant sa suppression ;
    // borne aux limites actuelles de la liste au cas ou d'autres series auraient ete
    // ajoutees/supprimees entre-temps (undo tardif).
    public void insertSetAt(int index, WorkoutSet Set)
    {
        int clampedIndex = Math.max(0, Math.min(index, Sets.size()));
        Sets.add(clampedIndex, Set);
        UpdateData();
    }

    // Suppression d'UNE serie. Retour Romain 06/09/2026 ("si je supprime la derniere
    // ligne du dernier exercice ca fait planter l'app") : cette methode reposait sur un
    // assert (Sets.size() > 1) qui n'est JAMAIS actif en production (les asserts Java
    // sont desactives par defaut sur Android) - supprimer l'unique serie restante du
    // jour videait donc Sets sans aucun garde-fou reel, ce que son propre message
    // d'assert disait pourtant vouloir empecher. Deleguee a removeSets() (voir
    // ci-dessous, deja sans assert) plutot que de dupliquer la logique - c'est
    // desormais la seule implementation de suppression dans cette classe.
    public void removeSet(WorkoutSet Set)
    {
        removeSets(java.util.Collections.singletonList(Set));
    }

    // Batch removal (retour Romain 05/09/2026 : suppression multiple) - une seule
    // UpdateData() pour tout le lot plutôt qu'un removeSet() par élément, à la fois plus
    // efficace et sans le risque ci-dessus quand la sélection (ou une suppression
    // unique via removeSet()) vide le jour jusqu'à sa dernière série - le jour lui-même
    // (dans DataStorage.getWorkoutDays()) doit alors être retiré séparément par
    // l'appelant, cf. AddExerciseActivity.deleteSetLogic()/deleteSetsLocally() et
    // DayActivity.deleteExerciseSetsLocally().
    public void removeSets(Collection<WorkoutSet> setsToRemove)
    {
        this.getSets().removeAll(setsToRemove);
        UpdateData();
    }

    public ArrayList<String> getExerciseOrder()
    {
        return ExerciseOrder;
    }

    public void setExerciseOrder(ArrayList<String> exerciseOrder)
    {
        ExerciseOrder = exerciseOrder;
    }

    // Réordonne un exercice dans l'affichage du jour (drag & drop, retour Romain
    // 05/09/2026, "comme FitNotes"). N'affecte que l'ordre - aucune série n'est
    // modifiée. Recalcule Exercises via UpdateData() pour rester la seule source de
    // vérité de la liste affichée (cf. commentaire sur UpdateData()).
    public void moveExercise(int fromIndex, int toIndex)
    {
        if (ExerciseOrder == null || fromIndex < 0 || fromIndex >= ExerciseOrder.size()
                || toIndex < 0 || toIndex >= ExerciseOrder.size() || fromIndex == toIndex)
        {
            return;
        }
        String moved = ExerciseOrder.remove(fromIndex);
        ExerciseOrder.add(toIndex, moved);
        UpdateData();
    }

    // Réordonne les séries d'UN exercice (drag & drop sur l'écran de log d'un exercice,
    // retour Romain 06/09/2026 : "un appui long sur la ligne lance l'utilitaire de
    // réordonnement de série"). Sets est une liste unique qui mélange les séries de TOUS
    // les exercices du jour dans l'ordre chronologique de création/import - pas de champ
    // d'ordre dédié comme ExerciseOrder ci-dessus. On retrouve donc les index de Sets
    // qui appartiennent à cet exercice (dans leur ordre actuel, qui correspond à
    // l'affichage - voir AddExerciseActivity.initrecyclerView()/updateTodaysExercises())
    // et on y réécrit newOrder à ces MÊMES positions : ça déplace bien ces séries entre
    // elles sans perturber leur entrelacement avec les séries des autres exercices.
    public void reorderSetsForExercise(String exerciseName, ArrayList<WorkoutSet> newOrder)
    {
        ArrayList<Integer> indices = new ArrayList<Integer>();
        for (int i = 0; i < Sets.size(); i++)
        {
            if (Sets.get(i).getExerciseName().equals(exerciseName))
            {
                indices.add(i);
            }
        }

        // Incohérence inattendue (liste modifiée entre-temps) - on abandonne plutôt que
        // de risquer d'écrire au mauvais endroit dans Sets.
        if (indices.size() != newOrder.size())
        {
            return;
        }

        for (int i = 0; i < indices.size(); i++)
        {
            Sets.set(indices.get(i), newOrder.get(i));
        }
    }

    // Update Data Structure Data
    public void UpdateData()
    {
        if (ExerciseOrder == null)
        {
            ExerciseOrder = new ArrayList<String>();
        }

        if(Sets.isEmpty())
        {
            Sets.clear();
            Exercises.clear();
            ExerciseOrder.clear();
            DayVolume = 0.0;
            Date = "0000-00-00";
            Reps = 0;
            return;
        }

        Date = Sets.get(0).getDate();

        ArrayList day_sets = Sets;
        ArrayList<WorkoutExercise> Day_Exercises = new ArrayList<WorkoutExercise>();
        Double Day_Volume = 0.0;

        // Exercices réellement présents ce jour (encounter order, seulement utilisé
        // comme repli pour un nom pas encore dans ExerciseOrder - voir plus bas).
        Set<String> Exercises_Present = new LinkedHashSet<String>();

        for(int j = 0; j < day_sets.size(); j++)
        {
            WorkoutSet temp_set = (WorkoutSet) day_sets.get(j);
            Day_Volume = Day_Volume + temp_set.getVolume();
            Exercises_Present.add(temp_set.getExerciseName());
        }

        // ExerciseOrder est la source de vérité de l'ordre d'affichage (retour Romain
        // 05/09/2026 : réordonnable à la main, "comme FitNotes" - avant cet ajout,
        // l'ordre était toujours alphabétique, jamais mémorisé). On la retaille sur les
        // exercices réellement présents : un exercice entièrement supprimé du jour sort
        // de la liste, un exercice tout juste ajouté rejoint la fin (nouvel exercice ->
        // en bas, jamais inséré au milieu d'un ordre choisi à la main).
        ExerciseOrder.retainAll(Exercises_Present);
        for (String exercise_name : Exercises_Present)
        {
            if (!ExerciseOrder.contains(exercise_name))
            {
                ExerciseOrder.add(exercise_name);
            }
        }

        // Iterate Day's Performed Exercises, dans l'ordre d'affichage
        Iterator<String> itt = ExerciseOrder.iterator();
        while (itt.hasNext())
        {
            String exercise_name = itt.next();
            WorkoutExercise day_exercise = new WorkoutExercise();

            day_exercise.setExercise(exercise_name);
            Double exercise_volume = 0.0;
            Double exercise_one_rep_max = 0.0;
            Double exercise_max_reps = 0.0;
            Double exercise_max_weight = 0.0;
            Double exercise_total_reps = 0.0;
            Double exercise_total_sets = 0.0;
            Double exercise_actual_one_rep_max = 0.0;
            String exercise_date = "";
            double exercise_max_set_volume = 0.0;
            String exercise_comment = "";

            WorkoutSet exercise_max_volume_set = new WorkoutSet();
            WorkoutSet exercise_max_reps_set = new WorkoutSet();
            WorkoutSet exercise_max_weight_set = new WorkoutSet();

            ArrayList<WorkoutSet> exercise_sets = new ArrayList<WorkoutSet>();

            for(int j = 0; j < day_sets.size(); j++)
            {
                WorkoutSet temp_set = (WorkoutSet) day_sets.get(j);
                if(temp_set.getExerciseName().equals(exercise_name))
                {
                    // Cumulative Values
                    exercise_volume = exercise_volume + temp_set.getVolume();
                    exercise_total_reps = exercise_total_reps + temp_set.getReps();
                    exercise_total_sets = exercise_total_sets + 1;
                    exercise_sets.add(temp_set);
                    exercise_date = temp_set.getDate();
                    exercise_comment = temp_set.getComment(); // This should be done once but this still works

                    // Max Values
                    if(temp_set.getEplayOneRepMax() > exercise_one_rep_max)
                    {
                        exercise_one_rep_max = temp_set.getEplayOneRepMax();
                    }
                    if(temp_set.getReps() == 1 && temp_set.getWeight() > exercise_actual_one_rep_max)
                    {
                        exercise_actual_one_rep_max = temp_set.getWeight();
                    }
                    if(temp_set.getReps() > exercise_max_reps)
                    {
                        exercise_max_reps = temp_set.getReps();
                        exercise_max_reps_set = temp_set;
                    }
                    if(temp_set.getWeight() > exercise_max_weight)
                    {
                        exercise_max_weight = temp_set.getWeight();
                        exercise_max_weight_set = temp_set;
                    }
                    if((temp_set.getReps()* temp_set.getWeight()) > exercise_max_set_volume)
                    {
                        exercise_max_set_volume = temp_set.getReps()* temp_set.getWeight();
                        exercise_max_volume_set = temp_set;
                    }
                }

                // Update exercise object
                day_exercise.setVolume(exercise_volume);
                day_exercise.setEstimatedOneRepMax(exercise_one_rep_max);
                day_exercise.setMaxReps(exercise_max_reps);
                day_exercise.setMaxWeight(exercise_max_weight);
                day_exercise.setTotalReps(exercise_total_reps);
                day_exercise.setTotalSets(exercise_total_sets);
                day_exercise.setSets(exercise_sets);
                day_exercise.setDate(exercise_date);
                day_exercise.setMaxSetVolume(exercise_max_set_volume);
                day_exercise.setActualOneRepMax(exercise_actual_one_rep_max);
                day_exercise.setComment(exercise_comment);
                day_exercise.setMaxVolumeSet(exercise_max_volume_set);
                day_exercise.setMaxWeightSet(exercise_max_weight_set);
                day_exercise.setMaxRepsSet(exercise_max_reps_set);
            }
            Day_Exercises.add(day_exercise);
        }

        Exercises = Day_Exercises;
        DayVolume = Day_Volume;
        Reps = 0;

        // Calculate Total Daily Reps
        for(int i = 0; i < Exercises.size(); i++)
        {
            Reps = Reps + (int)Math.round(Exercises.get(i).getTotalReps());
        }

    }

    public int getReps()
    {
        return this.Reps;
    }

    // Methods
    public ArrayList<WorkoutSet> getSets() {
        return Sets;
    }
    public void setSets(ArrayList<WorkoutSet> sets) {
        this.Sets = sets;
    }
    public void setExercises(ArrayList<WorkoutExercise> exercises) {
        this.Exercises = exercises;
        Reps = 0;
        // Calculate Total Daily Reps
        for(int i = 0; i < Exercises.size(); i++)
        {
            Reps = Reps + (int)Math.round(Exercises.get(i).getTotalReps());
        }

    }
    public void setDate(String date) {
        Date = date;
    }
    public String getDate() {
        return Date;
    }
    public ArrayList<WorkoutExercise> getExercises() {
        return Exercises;
    }
    public Double getDayVolume() {
        return DayVolume;
    }
    public void setDayVolume(Double volume) {
        DayVolume = volume;
    }

}
