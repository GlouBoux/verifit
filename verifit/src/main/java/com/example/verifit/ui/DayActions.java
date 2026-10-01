package com.example.verifit.ui;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;

import com.example.verifit.DataStorage;
import com.example.verifit.R;
import com.example.verifit.WorkoutReportGenerator;
import com.example.verifit.model.SupersetColours;
import com.example.verifit.model.SupersetGroup;
import com.example.verifit.model.WorkoutDay;
import com.example.verifit.model.WorkoutExercise;

import java.util.ArrayList;
import java.util.List;

/**
 * Actions sur un jour d'entrainement, partagees par l'onglet Workout (MainActivity, jour
 * affiche dans le ViewPager) et l'ecran d'un jour (DayActivity, ouvert depuis le
 * calendrier) : copier/deplacer des exercices, partager, commenter la seance, grouper en
 * superset, degrouper, supprimer des exercices.
 *
 * Lot C, etape C.3 (30/09/2026) : ces 8 methodes etaient copiees presque a l'identique
 * dans les deux ecrans. Seule difference : le rafraichissement de l'ecran apres
 * modification, passe ici en parametre (onChanged : initViewPager ou initActivity).
 * Toutes les actions s'executent depuis le thread principal (menu, dialogue).
 */
public final class DayActions
{
    private DayActions() {}

    private static DataStorage data()
    {
        return MainActivity.dataStorage;
    }

    // ------------------------------------------------------------ copier / deplacer

    // "Copy Previous Workout" (Vague 3, retour Romain 07/09/2026) : reprend directement le
    // jour avec des series le plus recent avant celui affiche (voir
    // DataStorage.getMostRecentWorkoutDateBefore()).
    public static void copyPreviousWorkout(AppCompatActivity activity, String date, Runnable onChanged)
    {
        String sourceDate = data().getMostRecentWorkoutDateBefore(date);
        if (sourceDate == null)
        {
            Toast.makeText(activity, "No previous workout found", Toast.LENGTH_SHORT).show();
            return;
        }
        promptCopyOrMoveExercises(activity, sourceDate, date, false, onChanged);
    }

    // "Copy a Workout" / "Move a Workout" (Vague 3, retour Romain 07/09/2026). Selection par
    // EXERCICE entier (tout pre-coche), pas par serie : un ecran dedie aurait ete
    // disproportionne pour le besoin ("je refais une seance deja loggee" / "je me suis
    // trompe de date"). Chaque serie copiee est un WorkoutSet neuf (voir
    // DataStorage.copySetsToDay()), jamais partage avec l'original.
    public static void promptCopyOrMoveExercises(AppCompatActivity activity, String sourceDate, String destinationDate,
                                                 boolean move, Runnable onChanged)
    {
        if (sourceDate.equals(destinationDate))
        {
            Toast.makeText(activity, "Choose a different day", Toast.LENGTH_SHORT).show();
            return;
        }

        int sourceDayPosition = data().getDayPosition(sourceDate);
        if (sourceDayPosition < 0 || data().getWorkoutDays().get(sourceDayPosition).getExercises().isEmpty())
        {
            Toast.makeText(activity, "No workout to copy on that day", Toast.LENGTH_SHORT).show();
            return;
        }

        List<WorkoutExercise> sourceExercises = data().getWorkoutDays().get(sourceDayPosition).getExercises();
        final String[] exerciseNames = new String[sourceExercises.size()];
        final boolean[] checked = new boolean[sourceExercises.size()];
        for (int i = 0; i < sourceExercises.size(); i++)
        {
            exerciseNames[i] = sourceExercises.get(i).getExercise();
            checked[i] = true;
        }

        new AlertDialog.Builder(activity)
                .setTitle((move ? "Move from " : "Copy from ") + WorkoutReportGenerator.formatDateHeader(sourceDate))
                .setMultiChoiceItems(exerciseNames, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(move ? "Move" : "Copy", (dialog, which) ->
                {
                    List<String> selectedNames = new ArrayList<>();
                    for (int i = 0; i < exerciseNames.length; i++)
                    {
                        if (checked[i])
                        {
                            selectedNames.add(exerciseNames[i]);
                        }
                    }

                    if (selectedNames.isEmpty())
                    {
                        Toast.makeText(activity, "No exercise selected", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int copiedCount = data().copySetsToDay(sourceDate, destinationDate, selectedNames);
                    if (move)
                    {
                        data().removeExerciseSetsFromDay(sourceDate, selectedNames);
                    }

                    data().saveWorkoutData(activity.getApplicationContext());

                    Toast.makeText(activity, copiedCount + " set(s) " + (move ? "moved" : "copied"), Toast.LENGTH_SHORT).show();
                    onChanged.run();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ------------------------------------------------------------ partager / commenter

    // "Share workout" (retour Romain 06/09/2026) : rapport texte du jour
    // (WorkoutReportGenerator) vers le selecteur de partage Android (usage principal :
    // Discord ; le selecteur propose aussi "Copier").
    public static void shareWorkout(AppCompatActivity activity, String date)
    {
        int dayPosition = data().getDayPosition(date);
        if (dayPosition < 0)
        {
            Toast.makeText(activity, "No Logged Exercises", Toast.LENGTH_SHORT).show();
            return;
        }

        WorkoutDay day = data().getWorkoutDays().get(dayPosition);
        String report = WorkoutReportGenerator.generateReport(activity, day);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, report);
        activity.startActivity(Intent.createChooser(shareIntent, "Share workout"));
    }

    // "Comment a Workout" (Vague 3, retour Romain 07/09/2026) : commentaire de la SEANCE
    // entiere, distinct de la note par serie. Cree le jour s'il n'existe pas encore (jour
    // sans serie affichable dans les deux ecrans) ; un jour cree ici qui ne porte
    // finalement ni serie ni commentaire est retire (Save avec texte vide, ou Cancel),
    // pour ne pas laisser de jour fantome.
    public static void showCommentWorkoutDialog(AppCompatActivity activity, String date, Runnable onChanged)
    {
        int dayPosition = data().getDayPosition(date);
        final WorkoutDay day;
        if (dayPosition >= 0)
        {
            day = data().getWorkoutDays().get(dayPosition);
        }
        else
        {
            day = new WorkoutDay();
            day.setDate(date);
            data().getWorkoutDays().add(day);
        }

        final EditText input = new EditText(activity);
        input.setHint("Comment (optional)");
        input.setText(day.getComment());
        int paddingPx = (int) (16 * activity.getResources().getDisplayMetrics().density);
        input.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        new AlertDialog.Builder(activity)
                .setTitle("Comment Workout")
                .setView(input)
                .setPositiveButton("Save", (dlg, which) ->
                {
                    day.setComment(input.getText().toString().trim());
                    removeIfEmpty(day);
                    data().saveWorkoutData(activity.getApplicationContext());
                    onChanged.run();
                })
                .setNegativeButton("Cancel", (dlg, which) -> removeIfEmpty(day))
                .show();
    }

    private static void removeIfEmpty(WorkoutDay day)
    {
        if (day.getSets().isEmpty() && day.getComment().isEmpty())
        {
            data().getWorkoutDays().remove(day);
        }
    }

    // ------------------------------------------------------------ selection d'exercices

    // Groupe les exercices selectionnes en superset (Vague 2, retour Romain 07/09/2026).
    // Si la selection contient un exercice deja membre d'un groupe, ce bouton le
    // renomme/reconfigure plutot que d'en creer un second (voir
    // WorkoutDay.addToSupersetGroup()). L'ordre de selectedNames (ordre d'affichage)
    // devient l'ordre d'enchainement. La case "passage automatique" (cochee par defaut)
    // pilote AddExerciseActivity.advanceToNextSupersetExerciseIfApplicable().
    public static void groupSelectedExercises(AppCompatActivity activity, String date, List<String> selectedNames,
                                              ActionMode mode, Runnable onChanged)
    {
        if (selectedNames.size() < 2)
        {
            Toast.makeText(activity, "Select at least 2 exercises to group", Toast.LENGTH_SHORT).show();
            return;
        }

        int dayPosition = data().getDayPosition(date);
        if (dayPosition < 0)
        {
            mode.finish();
            return;
        }

        WorkoutDay day = data().getWorkoutDays().get(dayPosition);

        final SupersetGroup existingGroup = day.getSupersetGroupForExercise(selectedNames.get(0));
        String prefillName = (existingGroup != null && existingGroup.getName() != null) ? existingGroup.getName() : "";

        LinearLayout dialogLayout = new LinearLayout(activity);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        int paddingPx = (int) (16 * activity.getResources().getDisplayMetrics().density);
        dialogLayout.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        final EditText input = new EditText(activity);
        input.setHint("Superset name (optional)");
        input.setText(prefillName);
        dialogLayout.addView(input);

        final CheckBox cbAutoAdvance = new CheckBox(activity);
        cbAutoAdvance.setText("Automatically move to next exercise after each set");
        cbAutoAdvance.setChecked(existingGroup == null || existingGroup.isAutoAdvance());
        dialogLayout.addView(cbAutoAdvance);

        new AlertDialog.Builder(activity)
                .setTitle(selectedNames.size() + " exercise(s) selected")
                .setView(dialogLayout)
                .setPositiveButton("Group", (dlg, which) ->
                {
                    String name = input.getText().toString().trim();
                    int color = (existingGroup != null)
                            ? existingGroup.getColor()
                            : SupersetColours.getNextAvailableColor(day.getSupersetGroups());

                    SupersetGroup savedGroup = day.addToSupersetGroup(new ArrayList<>(selectedNames), name, color);
                    savedGroup.setAutoAdvance(cbAutoAdvance.isChecked());

                    data().saveWorkoutData(activity.getApplicationContext());
                    mode.finish();
                    onChanged.run();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Retire les exercices selectionnes de leur superset : non destructif (aucune serie
    // supprimee), donc sans confirmation.
    public static void ungroupSelectedExercises(AppCompatActivity activity, String date, List<String> selectedNames,
                                                ActionMode mode, Runnable onChanged)
    {
        if (selectedNames.isEmpty())
        {
            Toast.makeText(activity, "No exercise selected", Toast.LENGTH_SHORT).show();
            return;
        }

        int dayPosition = data().getDayPosition(date);
        if (dayPosition < 0)
        {
            mode.finish();
            return;
        }

        WorkoutDay day = data().getWorkoutDays().get(dayPosition);
        for (String exerciseName : selectedNames)
        {
            day.removeFromSupersetGroup(exerciseName);
        }
        data().saveWorkoutData(activity.getApplicationContext());

        mode.finish();
        onChanged.run();
    }

    // Supprime, apres confirmation, toutes les series du jour pour les exercices
    // selectionnes (pas seulement leur ligne dans la liste). Le jour disparait s'il ne
    // reste plus aucune serie (DataStorage.removeExerciseSetsFromDay()).
    public static void confirmDeleteSelectedExercises(AppCompatActivity activity, String date, List<String> selectedNames,
                                                      ActionMode mode, Runnable onChanged)
    {
        if (selectedNames.isEmpty())
        {
            Toast.makeText(activity, "No exercise selected", Toast.LENGTH_SHORT).show();
            return;
        }

        View view = LayoutInflater.from(activity).inflate(R.layout.delete_set_dialog, null);
        AlertDialog alertDialog = new AlertDialog.Builder(activity).setView(view).create();

        TextView title = view.findViewById(R.id.tv_date);
        title.setText(selectedNames.size() + " exercise(s) selected. Delete all their sets for this day?");

        Button btYes = view.findViewById(R.id.bt_yes3);
        Button btNo = view.findViewById(R.id.bt_no3);

        btNo.setOnClickListener(v -> alertDialog.dismiss());
        btYes.setOnClickListener(v ->
        {
            alertDialog.dismiss();
            deleteSelectedExercises(activity, date, selectedNames, mode, onChanged);
        });

        alertDialog.show();
    }

    private static void deleteSelectedExercises(AppCompatActivity activity, String date, List<String> exerciseNames,
                                                ActionMode mode, Runnable onChanged)
    {
        int removed = data().removeExerciseSetsFromDay(date, exerciseNames);
        if (removed == 0)
        {
            mode.finish();
            return;
        }

        data().saveWorkoutData(activity.getApplicationContext());
        data().saveKnownExerciseData(activity.getApplicationContext());
        onChanged.run();

        Toast.makeText(activity, exerciseNames.size() + " exercise(s) deleted", Toast.LENGTH_SHORT).show();
        mode.finish();
    }
}
