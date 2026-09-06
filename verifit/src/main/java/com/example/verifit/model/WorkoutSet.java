package com.example.verifit.model;

public class WorkoutSet {

    // Attributes
    private int id;
    private String date;
    private String exercise_name;
    private String category;
    private Double reps;
    private Double weight;
    private String comment;

    private int user_id;

    // "Prevu" (planned) values, distincts du couple reps/weight ci-dessus qui
    // represente le "realise" (modifiable a tout moment par l'utilisateur).
    // Renseignes UNIQUEMENT au moment de l'import d'une seance generee (voir
    // DataStorage.mergeImportedSession()) - restent null pour toute autre serie
    // (saisie manuelle, import CSV d'historique, anciennes donnees deja
    // sauvegardees avant l'ajout de cette fonctionnalite). Gson retombe sur
    // cette valeur par defaut (null) pour les sets deja sauvegardes sur disque
    // qui ne connaissent pas encore ces champs.
    private Double plannedReps;
    private Double plannedWeight;


    public WorkoutSet()
    {

    }

    public WorkoutSet(String Date, String Exercise, String Category, Double Reps, Double Weight)
    {
        this.date = Date;
        this.exercise_name = Exercise;
        this.category = Category;
        this.reps = Reps;
        this.weight = Weight;
        this.comment = "";
    }
    public WorkoutSet(String Date, String Exercise, String Category, Double Reps, Double Weight,String Comment)
    {
        this.date = Date;
        this.exercise_name = Exercise;
        this.category = Category;
        this.reps = Reps;
        this.weight = Weight;
        this.comment = Comment;
    }


    // Methods
    // Setters
    public void setDate(String Date)
    {
        this.date = Date;
    }
    public void setExerciseName(String Exercise)
    {
        this.exercise_name = Exercise;
    }
    public void setCategory(String Category)
    {
        this.category = Category;
    }
    public void setReps(Double Reps) {
        this.reps = Reps;
    }
    public void setWeight(Double Weight)
    {
        this.weight = Weight;
    }
    public void setComment(String Comment){this.comment = Comment;}
    public void setId(int id) {this.id = id;}
    public void setUser_id(int user_id) {this.user_id = user_id;}
    public void setPlannedReps(Double PlannedReps) {this.plannedReps = PlannedReps;}
    public void setPlannedWeight(Double PlannedWeight) {this.plannedWeight = PlannedWeight;}


    // Getters
    public String getDate()
    {
        return this.date;
    }
    public String getExerciseName()
    {
        return this.exercise_name;
    }
    public String getCategory()
    {
        return this.category;
    }
    public Double getReps()
    {
        return this.reps;
    }
    public Double getWeight()
    {
        return this.weight;
    }
    public String getComment() {return this.comment;}
    public int getUser_id() {return user_id;}
    public int getId() {return id;}
    public Double getPlannedReps() {return this.plannedReps;}
    public Double getPlannedWeight() {return this.plannedWeight;}


    // Other
    public Double getVolume()
    {
        return this.reps * this.weight;
    }
    public Double getEplayOneRepMax(){return this.weight *(1+(this.reps /30));}

    // Vrai si cette serie vient d'une seance importee (elle a un "prevu" enregistre),
    // qu'elle ait ete modifiee depuis ou non.
    public boolean hasPlannedValues()
    {
        return this.plannedReps != null && this.plannedWeight != null;
    }

    // Vrai si le "realise" (reps/weight actuels) s'ecarte du "prevu" enregistre a
    // l'import. Sert a decider si le badge d'ecart doit s'afficher sur cette serie -
    // aucun ecart tant qu'il n'y a pas de valeur prevue (saisie manuelle, import CSV
    // d'historique) ou tant que le realise n'a pas ete modifie depuis l'import.
    public boolean hasDiscrepancy()
    {
        if (!hasPlannedValues() || this.reps == null || this.weight == null)
        {
            return false;
        }

        return !this.plannedReps.equals(this.reps) || !this.plannedWeight.equals(this.weight);
    }
}
