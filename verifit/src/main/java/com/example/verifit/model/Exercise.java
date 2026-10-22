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

    // Affichage assistance / lest (retour Romain 20/10/2026, voir AssistDisplay) :
    // poids de corps de reference en kg si l'exercice est assiste, null sinon. N'agit
    // QUE sur l'affichage : la charge enregistree reste la charge effective, jamais
    // convertie (PR, export Coaching et backup CSV inchanges). Null pour tout exercice
    // deserialise depuis une sauvegarde plus ancienne, donc rien ne change tant que
    // l'exercice n'est pas coche dans le dialogue Edit.
    private Double assistRefKg;

    // Assistance aussi affichee par cote (meme charge de chaque cote de la machine).
    private boolean assistPerSide;

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

    public Double getAssistRefKg() {
        return assistRefKg;
    }

    public void setAssistRefKg(Double assistRefKg) {
        this.assistRefKg = assistRefKg;
    }

    public boolean isAssistPerSide() {
        return assistPerSide;
    }

    public void setAssistPerSide(boolean assistPerSide) {
        this.assistPerSide = assistPerSide;
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
