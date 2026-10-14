package com.example.verifit.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.PopupMenu;

import com.example.verifit.ExerciseNameRules;
import com.example.verifit.R;
import com.example.verifit.model.Exercise;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Actions sur la DEFINITION d'un exercice (Edit = nom/categorie/notes, Duplicate),
// partagees par tous les points d'entree (retour Romain 14/10/2026 : "quand je suis
// sur un exo je dois pouvoir travailler a partir de lui") :
//  - liste Exercises, menu de l'exercice (ExerciseAdapter) ;
//  - fiche exercice (AddExerciseActivity) : menu "..." et tap sur le nom en haut ;
//  - appui long sur une carte d'exercice (onglet Workout et ecran d'un jour).
// Meme logique que DayActions pour la journee : un seul code, plusieurs ecrans.
// Le controle du nom (vide, doublon, espaces) est dans ExerciseNameRules (teste en JUnit).
public final class ExerciseActions
{
    public interface OnEdited
    {
        // newName == oldName si seuls la categorie ou les notes ont change.
        void onEdited(String oldName, String newName);
    }

    public interface OnDuplicated
    {
        void onDuplicated(Exercise duplicate);
    }

    private ExerciseActions() {}

    // Dialogue "Edit Exercise" (nom, categorie, notes). Ne fait rien si l'exercice
    // n'existe pas (ex. nom obsolete apres un renommage fait ailleurs).
    public static void showEditDialog(Context ct, String exerciseName, OnEdited callback)
    {
        Exercise exercise = findExercise(exerciseName);
        if (exercise == null)
        {
            Toast.makeText(ct, "Exercise not found", Toast.LENGTH_SHORT).show();
            return;
        }

        View view = LayoutInflater.from(ct).inflate(R.layout.edit_exercise_dialog, null);
        AlertDialog alertDialog = new AlertDialog.Builder(ct).setView(view).create();

        Button bt_save = view.findViewById(R.id.bt_login_signup);
        Button bt_cancel = view.findViewById(R.id.bt_cancel);
        EditText et_exercise_name = view.findViewById(R.id.et_exercise_name);
        EditText et_exercise_notes = view.findViewById(R.id.et_exercise_notes);
        Spinner spinner = view.findViewById(R.id.spinner);

        setupCategorySpinner(ct, spinner, exercise.getBodyPart());
        et_exercise_name.setText(exercise.getName());
        et_exercise_name.setSelection(exercise.getName().length());
        et_exercise_notes.setText(exercise.getNotes());

        bt_cancel.setOnClickListener(v -> alertDialog.dismiss());

        bt_save.setOnClickListener(v ->
        {
            ExerciseNameRules.Check check = ExerciseNameRules.check(
                    et_exercise_name.getText().toString(), exerciseName, knownExerciseNames());
            if (!showErrorIfAny(ct, check))
            {
                return;
            }

            String newName = check.name;
            String newCategory = selectedCategory(spinner, exercise.getBodyPart());

            // Les notes sont portees par l'objet Exercise (partage avec
            // DataStorage.getKnownExercises()) : a muter AVANT editExercise(), qui
            // renomme ce meme objet en place, puis persister le tout.
            exercise.setNotes(et_exercise_notes.getText().toString());
            MainActivity.dataStorage.editExercise(exerciseName, newName, newCategory);
            MainActivity.dataStorage.saveWorkoutData(ct);
            MainActivity.dataStorage.saveKnownExerciseData(ct);
            MainActivity.dataStorage.saveGoalsData(ct); // editExercise() renomme aussi les Goals (14/10/2026)

            alertDialog.dismiss();

            if (!newName.equals(exerciseName))
            {
                // Le cote Coaching (Excel, CSV de correspondance, journal) ne suit pas
                // tout seul : rappel du script prevu pour ca (voir CLAUDE.md Coaching).
                Toast.makeText(ct, "Exercise renamed. Coaching: run rename_exercise.py",
                        Toast.LENGTH_LONG).show();
            }

            if (callback != null)
            {
                callback.onEdited(exerciseName, newName);
            }
        });

        alertDialog.show();
    }

    // Dialogue "Duplicate Exercise" : nouvel exercice connu (meme categorie, notes
    // copiees, aucun historique), l'original et ses series restent intacts.
    public static void showDuplicateDialog(Context ct, String sourceName, OnDuplicated callback)
    {
        Exercise source = findExercise(sourceName);
        if (source == null)
        {
            Toast.makeText(ct, "Exercise not found", Toast.LENGTH_SHORT).show();
            return;
        }

        View view = LayoutInflater.from(ct).inflate(R.layout.edit_exercise_dialog, null);
        AlertDialog alertDialog = new AlertDialog.Builder(ct).setView(view).create();

        TextView tv_dialog_title = view.findViewById(R.id.tv_date);
        Button bt_save = view.findViewById(R.id.bt_login_signup);
        Button bt_cancel = view.findViewById(R.id.bt_cancel);
        EditText et_exercise_name = view.findViewById(R.id.et_exercise_name);
        EditText et_exercise_notes = view.findViewById(R.id.et_exercise_notes);
        Spinner spinner = view.findViewById(R.id.spinner);

        tv_dialog_title.setText("Duplicate Exercise");
        bt_save.setText("Duplicate");

        setupCategorySpinner(ct, spinner, source.getBodyPart());
        et_exercise_notes.setText(source.getNotes());

        // Suffixe "(copy)" pour ne pas entrer en collision avec l'original, curseur en
        // fin de champ pour taper directement la vraie variante.
        String suggestedName = source.getName() + " (copy)";
        et_exercise_name.setText(suggestedName);
        et_exercise_name.setSelection(suggestedName.length());

        bt_cancel.setOnClickListener(v -> alertDialog.dismiss());

        bt_save.setOnClickListener(v ->
        {
            ExerciseNameRules.Check check = ExerciseNameRules.check(
                    et_exercise_name.getText().toString(), null, knownExerciseNames());
            if (!showErrorIfAny(ct, check))
            {
                return;
            }

            Exercise duplicated = MainActivity.dataStorage.duplicateExercise(source.getName(), check.name);
            if (duplicated == null)
            {
                alertDialog.dismiss();
                return;
            }

            duplicated.setBodyPart(selectedCategory(spinner, source.getBodyPart()));
            duplicated.setNotes(et_exercise_notes.getText().toString());
            MainActivity.dataStorage.saveKnownExerciseData(ct);

            alertDialog.dismiss();

            if (callback != null)
            {
                callback.onDuplicated(duplicated);
            }
        });

        alertDialog.show();
    }

    // Appui long sur une carte d'exercice (onglet Workout, ecran d'un jour) : petit menu
    // Edit / Duplicate ancre sur la carte. onEdited sert a rafraichir la liste qui
    // affiche la carte (les WorkoutExercise sont renommes en place par editExercise()).
    public static void showCardMenu(Context ct, View anchor, String exerciseName, Runnable onEdited)
    {
        final int EDIT = 1;
        final int DUPLICATE = 2;

        PopupMenu popupMenu = new PopupMenu(ct, anchor);
        popupMenu.getMenu().add(Menu.NONE, EDIT, 0, "Edit Exercise");
        popupMenu.getMenu().add(Menu.NONE, DUPLICATE, 1, "Duplicate Exercise");
        popupMenu.setOnMenuItemClickListener(item ->
        {
            if (item.getItemId() == EDIT)
            {
                showEditDialog(ct, exerciseName, (oldName, newName) ->
                {
                    if (onEdited != null) onEdited.run();
                });
                return true;
            }
            if (item.getItemId() == DUPLICATE)
            {
                showDuplicateDialog(ct, exerciseName, duplicated ->
                        Toast.makeText(ct, "Exercise Duplicated", Toast.LENGTH_SHORT).show());
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    // Toast d'erreur si le nom est refuse. Renvoie true si le nom est valide.
    private static boolean showErrorIfAny(Context ct, ExerciseNameRules.Check check)
    {
        if (check.result == ExerciseNameRules.Result.EMPTY)
        {
            Toast.makeText(ct, "Please enter a name", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (check.result == ExerciseNameRules.Result.ALREADY_EXISTS)
        {
            Toast.makeText(ct, "\"" + check.name + "\" already exists", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private static Exercise findExercise(String name)
    {
        if (name == null) return null;
        for (Exercise exercise : MainActivity.dataStorage.getKnownExercises())
        {
            if (name.equals(exercise.getName()))
            {
                return exercise;
            }
        }
        return null;
    }

    private static List<String> knownExerciseNames()
    {
        List<String> names = new ArrayList<>();
        for (Exercise exercise : MainActivity.dataStorage.getKnownExercises())
        {
            names.add(exercise.getName());
        }
        return names;
    }

    // Categories de R.array.Categories, plus la categorie actuelle de l'exercice si elle
    // n'y figure pas (ex. import) : sinon un simple Save la remplacerait en silence par
    // la premiere de la liste.
    private static void setupCategorySpinner(Context ct, Spinner spinner, String currentCategory)
    {
        List<String> categories = new ArrayList<>(Arrays.asList(ct.getResources().getStringArray(R.array.Categories)));
        if (currentCategory != null && !currentCategory.isEmpty() && !categories.contains(currentCategory))
        {
            categories.add(currentCategory);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(ct, android.R.layout.simple_spinner_item, categories);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        int position = categories.indexOf(currentCategory);
        spinner.setSelection(Math.max(position, 0));
    }

    // Lu directement sur le Spinner au moment du Save (l'ancien code passait par un
    // OnItemSelectedListener et un champ qui pouvait rester a la valeur d'un dialogue
    // precedent).
    private static String selectedCategory(Spinner spinner, String fallback)
    {
        Object selected = spinner.getSelectedItem();
        return (selected != null && !selected.toString().isEmpty()) ? selected.toString() : fallback;
    }
}
