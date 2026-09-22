package com.example.verifit.model;

public class WorkoutSet {

    // Attributes
    private int id;
    private String date;
    private String exercise_name;
    private String category;
    private Double reps;
    private Double weight;
    // "Ma note" : le commentaire que Romain ecrit lui-meme (ou qui vient d'un backup
    // FitNotes migre). Ne contient plus le texte d'analyse du script generateur depuis
    // l'ajout de planComment ci-dessous.
    private String comment;

    // "Plan" (retour Romain 21/09/2026, voir claude/verifit-commentaires-serie-
    // propositions.md) : le commentaire pre-rempli par le script generateur
    // (workout_engine.py, ex. "S1 Ancrage - nouveau PR estime +4.5 kg ... - filet 2
    // reps"). Renseigne UNIQUEMENT par DataStorage.mergeImportedSession() a partir du
    // champ "comment" du JSON d'Import Session (contrat JSON inchange : pr_tracking.py
    // cote Coaching relit ce texte tel quel), lecture seule dans l'app - jamais
    // modifie par l'utilisateur, donc jamais melange a sa propre note. Reste null pour
    // toute serie sans plan (saisie manuelle, import CSV historique, series importees
    // avant l'ajout de ce champ : leur texte de script reste dans "comment", decision
    // Romain 21/09/2026 "laisser tel quel"). Gson retombe sur null pour les series
    // deja sauvegardees sur disque.
    private String planComment;

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

    // Horodatage (epoch millis) du moment ou cette serie a ete VALIDEE dans l'app
    // (retour Romain 06/09/2026). Renseigne UNIQUEMENT lors d'une saisie manuelle d'une
    // NOUVELLE serie (voir AddExerciseActivity.clickSave()) - jamais lors d'une
    // modification d'une serie existante (l'horodatage reste celui de la creation
    // initiale), jamais lors d'un import CSV d'historique ni d'un import de seance
    // generee (aucune heure reelle disponible dans ces cas). Reste null pour toute
    // serie deja sauvegardee avant l'ajout de ce champ - Gson retombe sur cette valeur
    // par defaut.
    //
    // N'est PLUS utilise pour la ligne "Time" de l'export de seance (retour Romain
    // 06/09/2026, correctif) : cette ligne se base desormais sur le chrono de session
    // manuel WorkoutDay.SessionStartTimestamp/SessionEndTimestamp (voir
    // WorkoutReportGenerator.buildTimeLine()) - "je ne sais ni ne peux controler ce
    // timer [par horodatages de series], il faut que je puisse y acceder [...] je dois
    // pouvoir le controler". Ce champ par-serie reste renseigne et disponible pour un
    // usage futur (ex. mesurer le repos reellement pris entre deux series, idee deja
    // notee dans docs/fitnotes-fork-todo.md).
    private Long timestamp;

    // "Serie faite" - checkbox de suivi (retour Romain 17/09/2026, "meme systeme de
    // checkbox que sur FitNotes pour tracker les series deja faites et a faire", voir
    // claude/fitnotes-feature-mark-sets-complete.md). Primitif (pas Boolean) : Gson
    // retombe naturellement sur `false` pour toute serie deja sauvegardee avant l'ajout
    // de ce champ (saisie manuelle historique, import CSV, import de seance generee) -
    // coherent avec la decision 1 de ce document ("toujours decochee a la creation").
    // Seule exception volontaire : un backup FitNotes reimporte via le CSV enrichi
    // (colonne "Is Completed", voir DataStorage.csvToSets()/scripts/
    // convert_fitnotes_to_verifit_csv.py) peut arriver directement a true, pour
    // refleter fidelement l'etat reel de l'epoque plutot que de tout remettre a zero.
    private boolean isCompleted;


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
    public void setPlanComment(String PlanComment) {this.planComment = PlanComment;}
    public void setId(int id) {this.id = id;}
    public void setUser_id(int user_id) {this.user_id = user_id;}
    public void setPlannedReps(Double PlannedReps) {this.plannedReps = PlannedReps;}
    public void setPlannedWeight(Double PlannedWeight) {this.plannedWeight = PlannedWeight;}
    public void setTimestamp(Long Timestamp) {this.timestamp = Timestamp;}
    public void setCompleted(boolean isCompleted) {this.isCompleted = isCompleted;}


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
    // Jamais null (voir le commentaire du champ planComment).
    public String getPlanComment() {return this.planComment == null ? "" : this.planComment;}
    public int getUser_id() {return user_id;}
    public int getId() {return id;}
    public Double getPlannedReps() {return this.plannedReps;}
    public Double getPlannedWeight() {return this.plannedWeight;}
    public Long getTimestamp() {return this.timestamp;}
    public boolean isCompleted() {return this.isCompleted;}

    // Vrai si cette serie a un horodatage reel connu (saisie manuelle depuis l'ajout de
    // ce champ) - faux pour tout ce qui a ete importe (CSV historique ou seance
    // generee) ou sauvegarde avant l'ajout du champ.
    public boolean hasTimestamp()
    {
        return this.timestamp != null;
    }


    // Vrai si la serie a un plan du script (planComment non vide).
    public boolean hasPlanComment()
    {
        return !getPlanComment().trim().isEmpty();
    }

    // Vrai si la serie a une note perso (comment non vide). Tient compte de la chaine
    // "null" que d'anciennes sauvegardes/exports ont pu ecrire a la place d'un vrai
    // null (meme garde que WorkoutReportGenerator.formatSetLine()).
    public boolean hasNote()
    {
        return this.comment != null && !this.comment.trim().isEmpty() && !this.comment.equals("null");
    }

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
