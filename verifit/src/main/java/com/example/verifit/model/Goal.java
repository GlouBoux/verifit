package com.example.verifit.model;

// "Goals" (Vague 4 du plan de migration, item 14, retour Romain 08/09/2026 : "passe
// a la vague 4") - fonctionnalite absente de Vérifit avant ce chantier (aucun socle
// existant identifie dans fitnotes-fork-todo.md), entierement nouvelle. Reduction de
// perimetre assumee par rapport au referentiel FitNotes complet : 4 types d'objectif
// (les plus utiles/naturels a suivre) plutot que la liste complete des graphiques
// disponibles cote Progress Graphs (Max Distance/Time/Speed/Pace... n'ont pas de sens
// pour un exercice Weight/Reps, seul type gere par Vérifit a ce jour).
public class Goal
{
    public enum GoalType
    {
        MAX_WEIGHT("Max Weight"),
        MAX_REPS("Max Reps"),
        TOTAL_VOLUME("Total Volume"),
        ESTIMATED_1RM("Estimated 1RM");

        private final String label;

        GoalType(String label)
        {
            this.label = label;
        }

        @Override
        public String toString()
        {
            return label;
        }
    }

    private int id;
    private String exerciseName;
    private GoalType type;
    private Double targetValue;

    // Constructeur vide requis par Gson pour la (de)serialisation JSON (meme motif
    // de persistance que DataStorage.saveKnownExerciseData()/loadKnownExercisesData()
    // - un blob JSON dans les SharedPreferences).
    public Goal()
    {
    }

    public Goal(int id, String exerciseName, GoalType type, Double targetValue)
    {
        this.id = id;
        this.exerciseName = exerciseName;
        this.type = type;
        this.targetValue = targetValue;
    }

    public int getId()
    {
        return id;
    }

    public void setId(int id)
    {
        this.id = id;
    }

    public String getExerciseName()
    {
        return exerciseName;
    }

    public void setExerciseName(String exerciseName)
    {
        this.exerciseName = exerciseName;
    }

    public GoalType getType()
    {
        return type;
    }

    public void setType(GoalType type)
    {
        this.type = type;
    }

    public Double getTargetValue()
    {
        return targetValue;
    }

    public void setTargetValue(Double targetValue)
    {
        this.targetValue = targetValue;
    }
}
