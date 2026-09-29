package com.example.verifit.model;

public class Exercise {

    // Attributes
    private String Name;
    private String BodyPart;
    private Boolean favorite;

    // "Exercise Notes" (retour Romain 28/09/2026, feature deja presente dans FitNotes,
    // voir claude/fitnotes-features-workout-tracking.md §9 et
    // claude/fitnotes-features-exercises.md §2/§4) : zone de notes libres attachee a la
    // DEFINITION de l'exercice (persiste tant que l'exercice existe, visible a chaque
    // seance), a ne pas confondre avec WorkoutDay.getComment()/WorkoutSet.getComment()
    // qui sont des commentaires propres a UNE seance/UNE serie et ne survivent pas d'une
    // seance a l'autre. Jamais null pour un exercice cree apres ce changement (voir
    // constructeur ci-dessous) ; pour un exercice deserialise depuis un JSON plus ancien
    // (SharedPreferences via Gson), Gson laisse ce champ a null - voir la boucle de
    // migration douce dans DataStorage.loadKnownExercisesData() qui le remet a "".
    private String notes;

    public Boolean getFavorite() {
        return favorite;
    }

    public void setFavorite(Boolean favorite) {
        this.favorite = favorite;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }




    // Methods
    public Exercise(String name, String bodypart)
    {
        this.Name = name;
        this.BodyPart = bodypart;
        this.favorite = false;
        this.notes = "";
    }

    public String getBodyPart() {
        return BodyPart;
    }

    public void setBodyPart(String bodyPart) {
        BodyPart = bodyPart;
    }

    public String getName() {
        return Name;
    }

    public void setName(String name) {
        Name = name;
    }

}
