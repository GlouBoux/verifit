package com.example.verifit;

import com.example.verifit.model.ImportedExercise;
import com.example.verifit.model.ImportedSession;
import com.example.verifit.model.ImportedSet;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Apercu d'une seance JSON AVANT de l'importer (lot D, etape D7, 02/10/2026) : date visee,
 * exercices (nouveaux ou deja connus), series valides, ce qui sera ignore, et ce qui
 * empeche l'import. Sert a l'ecran ouvert par "Ouvrir avec FitEngine" (ImportSessionActivity).
 *
 * Classe sans aucune dependance Android, donc testable en JUnit local. Reprend EXACTEMENT
 * les regles de DataStorage.mergeImportedSession() pour ce qui sera ou non importe : un
 * exercice sans nom est ignore, une serie sans poids ou sans reps est ignoree.
 */
public final class SessionPreview
{
    // Un exercice du fichier, tel que l'import le traitera.
    public static final class ExerciseLine
    {
        public final String name;
        public final boolean isNew;        // inconnu de l'app (sera cree), faux si on ne sait pas
        public final int validSets;
        public final int skippedSets;
        public final String detail;        // "100.0 x 5, 100.0 x 5" (series valides)

        ExerciseLine(String name, boolean isNew, int validSets, int skippedSets, String detail)
        {
            this.name = name;
            this.isNew = isNew;
            this.validSets = validSets;
            this.skippedSets = skippedSets;
            this.detail = detail;
        }
    }

    private final String date;
    private final boolean dateFromFile;
    private final boolean dateValid;
    private final List<ExerciseLine> exercises;
    private final int validSetCount;
    private final int skippedSetCount;
    private final int unnamedExerciseCount;

    private SessionPreview(String date, boolean dateFromFile, boolean dateValid, List<ExerciseLine> exercises,
                           int validSetCount, int skippedSetCount, int unnamedExerciseCount)
    {
        this.date = date;
        this.dateFromFile = dateFromFile;
        this.dateValid = dateValid;
        this.exercises = Collections.unmodifiableList(exercises);
        this.validSetCount = validSetCount;
        this.skippedSetCount = skippedSetCount;
        this.unnamedExerciseCount = unnamedExerciseCount;
    }

    // session peut etre null (fichier vide). fallbackDate : jour utilise quand le fichier
    // n'a pas de "date" (aujourd'hui, pour un fichier ouvert depuis une autre appli).
    // knownExerciseNames : exercices deja connus de l'app, ou null pour ne pas marquer
    // les nouveaux.
    public static SessionPreview of(ImportedSession session, String fallbackDate, Set<String> knownExerciseNames)
    {
        String fileDate = session == null ? "" : session.getDate();
        boolean fromFile = !fileDate.isEmpty();
        String date = fromFile ? fileDate : (fallbackDate == null ? "" : fallbackDate);

        ArrayList<ExerciseLine> lines = new ArrayList<ExerciseLine>();
        int valid = 0;
        int skipped = 0;
        int unnamed = 0;

        if (session != null)
        {
            for (ImportedExercise exercise : session.getExercises())
            {
                String name = exercise.getName();
                if (name.isEmpty())
                {
                    unnamed++;
                    continue;
                }

                StringBuilder detail = new StringBuilder();
                int exerciseValid = 0;
                int exerciseSkipped = 0;
                for (ImportedSet set : exercise.getSets())
                {
                    if (set.getWeight() == null || set.getReps() == null)
                    {
                        exerciseSkipped++;
                        continue;
                    }
                    if (exerciseValid > 0)
                    {
                        detail.append(", ");
                    }
                    detail.append(formatSet(set.getWeight(), set.getReps()));
                    exerciseValid++;
                }

                boolean isNew = knownExerciseNames != null && !knownExerciseNames.contains(name);
                lines.add(new ExerciseLine(name, isNew, exerciseValid, exerciseSkipped, detail.toString()));
                valid += exerciseValid;
                skipped += exerciseSkipped;
            }
        }

        return new SessionPreview(date, fromFile, isValidIsoDate(date), lines, valid, skipped, unnamed);
    }

    // "100.0 x 5" : meme ecriture du poids que le reste de l'app (Double tel quel), reps
    // arrondies a l'entier.
    static String formatSet(double weight, double reps)
    {
        return weight + " x " + Math.round(reps);
    }

    // Strictement "AAAA-MM-JJ" et une vraie date (pas de 2026-02-30, ni de 2026-1-5) : le
    // jour sert de cle dans les donnees de l'app.
    static boolean isValidIsoDate(String value)
    {
        if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}"))
        {
            return false;
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setLenient(false);
        ParsePosition position = new ParsePosition(0);
        return format.parse(value, position) != null && position.getIndex() == value.length();
    }

    public String getDate() { return date; }
    public boolean isDateFromFile() { return dateFromFile; }
    public boolean isDateValid() { return dateValid; }
    public List<ExerciseLine> getExercises() { return exercises; }
    public int getExerciseCount() { return exercises.size(); }
    public int getValidSetCount() { return validSetCount; }
    public int getSkippedSetCount() { return skippedSetCount; }
    public int getUnnamedExerciseCount() { return unnamedExerciseCount; }

    public int getNewExerciseCount()
    {
        int n = 0;
        for (ExerciseLine line : exercises)
        {
            if (line.isNew)
            {
                n++;
            }
        }
        return n;
    }

    public boolean isImportable()
    {
        return problem() == null;
    }

    // Ce qui empeche d'importer, ou null si l'import est possible.
    public String problem()
    {
        if (!dateValid)
        {
            return "Date invalide : « " + date + " » (format attendu : AAAA-MM-JJ).";
        }
        if (exercises.isEmpty())
        {
            return "Aucun exercice dans ce fichier.";
        }
        if (validSetCount == 0)
        {
            return "Aucune série valide dans ce fichier (poids ou reps manquant).";
        }
        return null;
    }

    // Texte affiche sous le titre : date, totaux, un bloc par exercice, puis les
    // avertissements (ce qui sera ignore).
    public String toText()
    {
        StringBuilder text = new StringBuilder();
        text.append("Date : ").append(date.isEmpty() ? "(aucune)" : date);
        if (!dateFromFile)
        {
            text.append(" (date du jour, absente du fichier)");
        }
        text.append('\n');
        text.append(count(exercises.size(), "exercice", "exercices")).append(", ")
                .append(count(validSetCount, "série", "séries"));

        for (ExerciseLine line : exercises)
        {
            text.append("\n\n").append(line.name);
            if (line.isNew)
            {
                text.append(" (nouvel exercice)");
            }
            if (line.validSets > 0)
            {
                text.append("\n  ").append(count(line.validSets, "série", "séries")).append(" : ").append(line.detail);
            }
            else
            {
                text.append("\n  aucune série valide");
            }
        }

        if (skippedSetCount > 0)
        {
            text.append("\n\nAttention : ").append(skippedSetCount == 1
                    ? "1 série incomplète (poids ou reps manquant) sera ignorée."
                    : skippedSetCount + " séries incomplètes (poids ou reps manquant) seront ignorées.");
        }
        if (unnamedExerciseCount > 0)
        {
            text.append("\n\nAttention : ").append(unnamedExerciseCount == 1
                    ? "1 exercice sans nom sera ignoré."
                    : unnamedExerciseCount + " exercices sans nom seront ignorés.");
        }
        return text.toString();
    }

    private static String count(int n, String singular, String plural)
    {
        return n + " " + (n > 1 ? plural : singular);
    }
}
