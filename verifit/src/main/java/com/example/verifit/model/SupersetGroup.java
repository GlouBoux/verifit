package com.example.verifit.model;

import java.util.ArrayList;
import java.util.UUID;

// Groupe de superset (Vague 2 du plan de migration, retour Romain 07/09/2026 -
// captures FitNotes "Analysis"/Workout Tracking section 7 : enchainer plusieurs
// exercices sans repos entre eux, barre coloree dans la liste du jour, passage
// automatique a l'exercice suivant apres chaque serie). Rattache a UN WorkoutDay
// (voir WorkoutDay.SupersetGroups) - un exercice n'appartient qu'a un seul groupe
// le meme jour (WorkoutDay.getSupersetGroupForExercise() garantit ca a la
// lecture ; WorkoutDay.addToSupersetGroup() le garantit a l'ecriture).
public class SupersetGroup {

    private String Id;
    private String Name; // Optionnel - "Superset" + numero si vide, voir getDisplayName()
    private int Color; // ARGB, voir SupersetColours
    private ArrayList<String> ExerciseNames = new ArrayList<String>(); // Ordre = ordre d'enchainement

    // Passage automatique a l'exercice suivant du groupe apres chaque serie loggee
    // (voir AddExerciseActivity.advanceToNextSupersetExerciseIfApplicable()).
    // Actif par defaut a la creation - c'est la raison d'etre du groupe - mais
    // reglable par groupe (case a cocher du dialogue de creation, DayActivity) au
    // cas ou Romain ne voudrait ce comportement que pour certains groupes apres
    // l'avoir teste en pratique.
    private boolean AutoAdvance = true;

    public SupersetGroup()
    {
        Id = UUID.randomUUID().toString();
    }

    public SupersetGroup(String name, int color, ArrayList<String> exerciseNames)
    {
        this();
        Name = name;
        Color = color;
        ExerciseNames = exerciseNames;
    }

    public String getId() { return Id; }

    public String getName() { return Name; }
    public void setName(String name) { Name = name; }

    public int getColor() { return Color; }
    public void setColor(int color) { Color = color; }

    public ArrayList<String> getExerciseNames() { return ExerciseNames; }
    public void setExerciseNames(ArrayList<String> exerciseNames) { ExerciseNames = exerciseNames; }

    public boolean isAutoAdvance() { return AutoAdvance; }
    public void setAutoAdvance(boolean autoAdvance) { AutoAdvance = autoAdvance; }

    // Nom affiche si Name est vide/absent - "displayIndex" est la position 1-based
    // du groupe dans WorkoutDay.getSupersetGroups(), fournie par l'appelant.
    public String getDisplayName(int displayIndex)
    {
        if (Name == null || Name.trim().isEmpty())
        {
            return "Superset " + displayIndex;
        }
        return Name;
    }

    // Exercice suivant dans l'enchainement, en bouclant sur le premier apres le
    // dernier (spec FitNotes : "cycles back to the first exercise after a full
    // round"). Retourne null si l'exercice n'appartient pas (ou plus) a ce groupe,
    // ou si le groupe n'a qu'un seul membre (rien a enchainer).
    public String getNextExercise(String currentExerciseName)
    {
        int index = ExerciseNames.indexOf(currentExerciseName);
        if (index < 0 || ExerciseNames.size() < 2)
        {
            return null;
        }
        int nextIndex = (index + 1) % ExerciseNames.size();
        return ExerciseNames.get(nextIndex);
    }
}
