package com.example.verifit;

import com.example.verifit.model.WorkoutDay;
import com.example.verifit.model.WorkoutExercise;
import com.example.verifit.model.WorkoutSet;
import com.example.verifit.ui.MainActivity;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;

// Genere le rapport texte "Share workout" d'une journee, dans le format que Romain
// utilisait sur FitNotes (retour Romain 06/09/2026, template fourni verbatim - voir
// docs/fitnotes-fork-todo.md, item "Partager une seance"). Exemple :
//
//   FitNotes Workout - vendredi 4 septembre 2026
//   Time: 17:43 – 22:15 (4h 31m)
//   ** Assisted HSPU FP **
//   - 40.0 kgs x 4 reps
//   - 50.0 kgs x 2 reps [Echec d'un 7. <commentaire libre de la serie>]
//   - 41.0 kgs x 14 reps [PR]
//   ** Assisted Pelican **
//   ...
//
// Deux points laisses a l'appreciation de Claude, Romain ne s'etant pas prononce :
// - Format de la ligne de date : FitNotes affichait un melange bizarre d'ordinal
//   anglais et de mois francais ("vendredi 4th septembre 2026", probablement un bug
//   de locale cote FitNotes) - reproduit ici avec un format francais propre a la
//   place ("vendredi 4 septembre 2026").
// - Duree des minutes non paddee a 2 chiffres (ex "4h 5m", pas "4h 05m") - c'est une
//   duree, pas une heure d'horloge.
public class WorkoutReportGenerator
{
    // Genere le rapport complet pour un WorkoutDay donne.
    public static String generateReport(WorkoutDay day)
    {
        StringBuilder report = new StringBuilder();

        report.append("FitNotes Workout - ").append(formatDateHeader(day.getDate())).append("\n");

        // "Comment a Workout" (Vague 3 du plan de migration, retour Romain 07/09/2026) -
        // affiche juste sous l'en-tete, comme sur FitNotes ("affiche au-dessus de la
        // seance").
        String workoutComment = day.getComment();
        if (!workoutComment.trim().isEmpty())
        {
            report.append(workoutComment.trim()).append("\n");
        }

        String timeLine = buildTimeLine(day);
        if (timeLine != null)
        {
            report.append(timeLine).append("\n");
        }

        for (WorkoutExercise exercise : day.getExercises())
        {
            report.append("** ").append(exercise.getExercise()).append(" **\n");

            // Source de verite unique pour le tag [PR] (retour Romain 06/09/2026 :
            // "Un PR c'est un record [...] pour ce rep range [...] pour ce poids" -
            // voir DataStorage.getRepRangePRSets()/calculateRepRangeHistory(), deja
            // utilisee par l'ecran "Historique des PR par nombre de reps") - garantit
            // que l'ecran et cet export s'accordent toujours sur ce qui est un PR.
            HashSet<WorkoutSet> prSets = MainActivity.dataStorage.getRepRangePRSets(exercise.getExercise());

            for (WorkoutSet set : exercise.getSets())
            {
                report.append(formatSetLine(set, prSets)).append("\n");
            }
        }

        return report.toString().trim();
    }

    // "vendredi 4 septembre 2026" - premiere lettre en majuscule pour un rendu
    // d'en-tete propre (FitNotes lui-meme l'affichait en minuscule, "vendredi...").
    // Public (pas seulement private) : reutilisee telle quelle par le dialogue
    // "Workout Time" (AddExerciseActivity/DayActivity.showWorkoutTimeDialog(), retour
    // Romain 07/09/2026) pour afficher la date du jour avec le meme format francais
    // propre que ce rapport, plutot que de dupliquer cette logique.
    public static String formatDateHeader(String isoDate)
    {
        try
        {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.FRENCH);
            Date date = parser.parse(isoDate);

            SimpleDateFormat formatter = new SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRENCH);
            String formatted = formatter.format(date);

            return Character.toUpperCase(formatted.charAt(0)) + formatted.substring(1);
        }
        catch (ParseException e)
        {
            // Repli defensif improbable (Date est normalement toujours "yyyy-MM-dd",
            // cf. WorkoutDay/WorkoutSet) - ne doit jamais empecher de generer le reste
            // du rapport.
            return isoDate;
        }
    }

    // "Time: 17:43 – 22:15 (4h 31m)", ou null si le chrono de session n'a jamais ete
    // demarre pour ce jour (voir WorkoutDay.SessionStartTimestamp - jours anciens/
    // importes, ou seance jamais loggee via l'app).
    //
    // Retour Romain 06/09/2026 (correctif) : la premiere version de cette ligne se
    // basait sur le plus petit/plus grand WorkoutSet.timestamp du jour - "je ne sais ni
    // ne peux controler ce timer, il faut que je puisse y acceder [...] je dois pouvoir
    // le controler". Remplace par le chrono de session manuel (demarre automatiquement
    // a la premiere serie, arrete manuellement par l'utilisateur - voir
    // AddExerciseActivity/DayActivity), choisi par Romain comme SEULE source (pas de
    // repli automatique sur l'ancien calcul) pour en garder le controle total. Si le
    // chrono n'a pas encore ete arrete au moment du partage, la fin de journee est prise
    // comme "maintenant" (instantane de l'etat actuel de la seance).
    private static String buildTimeLine(WorkoutDay day)
    {
        Long start = day.getSessionStartTimestamp();
        if (start == null)
        {
            return null;
        }

        long startMillis = start;
        long endMillis = (day.getSessionEndTimestamp() != null) ? day.getSessionEndTimestamp() : System.currentTimeMillis();

        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.FRENCH);
        String startStr = timeFormat.format(new Date(startMillis));
        String endStr = timeFormat.format(new Date(endMillis));

        long durationMinutesTotal = (endMillis - startMillis) / (60 * 1000);
        long hours = durationMinutesTotal / 60;
        long minutes = durationMinutesTotal % 60;

        // Tiret cadratin (–), pas un simple tiret, pour reproduire a l'identique le
        // format observe dans le template fourni par Romain ("Time: 17:43 - 22:15
        // (4h 31m)" avec un vrai en-dash).
        return "Time: " + startStr + " – " + endStr + " (" + hours + "h " + minutes + "m)";
    }

    // "- 40.0 kgs x 4 reps", avec l'annotation entre crochets quand pertinente :
    // "[PR]" (record), "[<commentaire>]" (commentaire libre de la serie), ou
    // "[PR. <commentaire>]" quand les deux sont presents (retour Romain 06/09/2026,
    // PR en premier - c'est le motif observe dans son propre export FitNotes).
    private static String formatSetLine(WorkoutSet set, HashSet<WorkoutSet> prSets)
    {
        int reps = (int) Math.round(set.getReps());
        String weight = set.getWeight().toString();

        StringBuilder line = new StringBuilder("- ").append(weight).append(" kgs x ").append(reps).append(" reps");

        boolean isPR = prSets.contains(set);
        String comment = set.getComment();
        boolean hasComment = comment != null && !comment.trim().isEmpty() && !comment.equals("null");

        if (isPR || hasComment)
        {
            line.append(" [");
            if (isPR)
            {
                line.append("PR");
                if (hasComment)
                {
                    line.append(". ");
                }
            }
            if (hasComment)
            {
                line.append(comment);
            }
            line.append("]");
        }

        return line.toString();
    }
}
