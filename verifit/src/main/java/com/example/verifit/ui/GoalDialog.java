package com.example.verifit.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.verifit.R;
import com.example.verifit.SnackBarWithMessage;
import com.example.verifit.model.Exercise;
import com.example.verifit.model.Goal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// "Goals" (Vague 4 du plan de migration, item 14, retour Romain 08/09/2026 : "passe
// a la vague 4") - dialogue de creation/edition d'un objectif, ouvert depuis le "+"
// du menu de GoalsActivity (creation, existingGoal == null) ou depuis le tap sur une
// carte de GoalsAdapter (edition, existingGoal != null - bouton "Delete" visible
// uniquement dans ce cas). Champ exercice en AutoCompleteTextView, meme motif que
// CalendarFilterDialog (item 11) pour rester coherent avec le reste de l'app plutot
// que d'introduire un nouveau widget de selection d'exercice.
public class GoalDialog extends Dialog
{
    public interface OnGoalDialogResultListener
    {
        void onGoalSaved(Goal goal);

        void onGoalDeleted(Goal goal);
    }

    private final Goal existingGoal; // null = creation
    private final OnGoalDialogResultListener listener;

    private AutoCompleteTextView exerciseField;
    private Spinner typeSpinner;
    private EditText targetField;

    public GoalDialog(Context context, Goal existingGoal, OnGoalDialogResultListener listener)
    {
        super(context);
        this.existingGoal = existingGoal;
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.goal_dialog);

        // Retour Romain (screenshot) : sans ceci, la fenetre d'un Dialog personnalise
        // (par opposition a AlertDialog.Builder, qui gere ca tout seul) se retrouve
        // minuscule sur certains telephones/ROMs (constate sur MIUI) - le layout
        // interne a beau etre en match_parent, la FENETRE du Dialog, elle, reste en
        // wrap_content par defaut et se reduit au strict minimum. Force explicitement
        // une largeur de 90% de l'ecran.
        if (getWindow() != null)
        {
            int dialogWidth = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.9);
            getWindow().setLayout(dialogWidth, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView title = findViewById(R.id.goal_dialog_title);
        title.setText(existingGoal == null ? "New Goal" : "Edit Goal");

        exerciseField = findViewById(R.id.goal_dialog_exercise);
        typeSpinner = findViewById(R.id.goal_dialog_type);
        targetField = findViewById(R.id.goal_dialog_target);
        Button deleteButton = findViewById(R.id.goal_dialog_delete);
        Button cancelButton = findViewById(R.id.goal_dialog_cancel);
        Button saveButton = findViewById(R.id.goal_dialog_save);

        List<String> exerciseNames = new ArrayList<>();
        for (Exercise exercise : MainActivity.dataStorage.getKnownExercises())
        {
            exerciseNames.add(exercise.getName());
        }
        Collections.sort(exerciseNames, String.CASE_INSENSITIVE_ORDER);
        exerciseField.setAdapter(new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, exerciseNames));
        exerciseField.setThreshold(1);

        ArrayAdapter<Goal.GoalType> typeAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, Goal.GoalType.values());
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(typeAdapter);

        if (existingGoal != null)
        {
            exerciseField.setText(existingGoal.getExerciseName());
            typeSpinner.setSelection(existingGoal.getType().ordinal());
            targetField.setText(formatValue(existingGoal.getTargetValue()));
            deleteButton.setVisibility(View.VISIBLE);
        }
        else
        {
            deleteButton.setVisibility(View.GONE);
        }

        deleteButton.setOnClickListener(v ->
        {
            listener.onGoalDeleted(existingGoal);
            dismiss();
        });

        cancelButton.setOnClickListener(v -> dismiss());

        saveButton.setOnClickListener(v ->
        {
            String exerciseName = exerciseField.getText().toString().trim();
            Double target = parseDouble(targetField.getText().toString());

            if (TextUtils.isEmpty(exerciseName) || target == null || target <= 0.0)
            {
                SnackBarWithMessage snackBarWithMessage = new SnackBarWithMessage(getContext());
                snackBarWithMessage.showSnackbar("Choisis un exercice et une cible valide");
                return;
            }

            Goal.GoalType type = (Goal.GoalType) typeSpinner.getSelectedItem();

            int id = existingGoal != null ? existingGoal.getId() : MainActivity.dataStorage.getNextGoalId();
            Goal goal = new Goal(id, exerciseName, type, target);

            listener.onGoalSaved(goal);
            dismiss();
        });
    }

    private String formatValue(Double value)
    {
        if (value == null)
        {
            return "";
        }
        if (value == Math.floor(value))
        {
            return String.valueOf((long) (double) value);
        }
        return String.valueOf(value);
    }

    private Double parseDouble(String text)
    {
        if (TextUtils.isEmpty(text))
        {
            return null;
        }
        try
        {
            return Double.parseDouble(text.trim());
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }
}
