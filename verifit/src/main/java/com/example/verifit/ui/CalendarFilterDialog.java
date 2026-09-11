package com.example.verifit.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;

import com.example.verifit.DataStorage;
import com.example.verifit.R;
import com.example.verifit.model.CalendarFilter;
import com.example.verifit.model.CategoryColours;
import com.example.verifit.model.Exercise;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// "Calendar : Category Filter + Exercise Filter" (Vague 3 du plan de migration, item
// 11, retour 08/09/2026) - panneau de filtre du calendrier, ouvert uniquement en mode
// Browse (voir CalendarPickerDialog.enableBrowsingFeatures()), jamais en mode "Pick a
// day" (Copy/Move Workout). Edite une COPIE des criteres passes en entree ("Save" les
// renvoie via le listener, "Reset" repart d'un filtre vide, fermer sans "Save" - bouton
// retour - laisse le filtre precedent inchange cote CalendarPickerDialog).
public class CalendarFilterDialog extends Dialog
{
    public interface OnFilterAppliedListener
    {
        void onFilterApplied(CalendarFilter filter);
    }

    private final DataStorage dataStorage;
    private final CalendarFilter initialFilter;
    private final OnFilterAppliedListener listener;

    private final Map<String, CheckBox> categoryCheckboxes = new HashMap<>();
    private CheckBox matchAllCheckbox;
    private AutoCompleteTextView exerciseField;
    private Spinner weightComparisonSpinner;
    private EditText weightValueField;
    private Spinner repsComparisonSpinner;
    private EditText repsValueField;

    public CalendarFilterDialog(Context context, DataStorage dataStorage, CalendarFilter currentFilter, OnFilterAppliedListener listener)
    {
        super(context);
        this.dataStorage = dataStorage;
        this.initialFilter = currentFilter;
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.calendar_filter_dialog);

        // Meme correctif que GoalDialog/ExerciseStatsPeriodDialog (retour Romain,
        // screenshot sur le dialogue Goals) : sans ceci la fenetre d'un Dialog
        // personnalise peut se retrouver minuscule sur certains telephones/ROMs
        // (constate sur MIUI), meme si le layout interne est en match_parent.
        if (getWindow() != null)
        {
            int dialogWidth = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.9);
            getWindow().setLayout(dialogWidth, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        LinearLayout categoriesContainer = findViewById(R.id.calendar_filter_categories_container);
        matchAllCheckbox = findViewById(R.id.calendar_filter_match_all);
        exerciseField = findViewById(R.id.calendar_filter_exercise);
        weightComparisonSpinner = findViewById(R.id.calendar_filter_weight_comparison);
        weightValueField = findViewById(R.id.calendar_filter_weight_value);
        repsComparisonSpinner = findViewById(R.id.calendar_filter_reps_comparison);
        repsValueField = findViewById(R.id.calendar_filter_reps_value);
        Button resetButton = findViewById(R.id.calendar_filter_reset);
        Button saveButton = findViewById(R.id.calendar_filter_save);

        // Une CheckBox par entree de R.array.Categories plutot que codees en dur dans
        // le layout - reste synchronise si ce tableau change un jour (voir aussi
        // CategoryColours, meme liste de categories).
        String[] categories = getContext().getResources().getStringArray(R.array.Categories);
        for (String category : categories)
        {
            CheckBox checkBox = new CheckBox(getContext());
            checkBox.setText(category);
            checkBox.setTextColor(CategoryColours.getColour(category));
            categoriesContainer.addView(checkBox);
            categoryCheckboxes.put(category, checkBox);
        }

        ArrayAdapter<CalendarFilter.Comparison> comparisonAdapter = new ArrayAdapter<>(
                getContext(), android.R.layout.simple_spinner_item, CalendarFilter.Comparison.values());
        comparisonAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        weightComparisonSpinner.setAdapter(comparisonAdapter);
        repsComparisonSpinner.setAdapter(comparisonAdapter);

        List<String> exerciseNames = new ArrayList<>();
        for (Exercise exercise : dataStorage.getKnownExercises())
        {
            exerciseNames.add(exercise.getName());
        }
        Collections.sort(exerciseNames, String.CASE_INSENSITIVE_ORDER);
        exerciseField.setAdapter(new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, exerciseNames));
        exerciseField.setThreshold(1);

        populateFrom(initialFilter);

        resetButton.setOnClickListener(v -> {
            listener.onFilterApplied(new CalendarFilter());
            dismiss();
        });

        saveButton.setOnClickListener(v -> {
            listener.onFilterApplied(buildFilterFromFields());
            dismiss();
        });
    }

    private void populateFrom(CalendarFilter filter)
    {
        for (Map.Entry<String, CheckBox> entry : categoryCheckboxes.entrySet())
        {
            entry.getValue().setChecked(filter.getCategories().contains(entry.getKey()));
        }
        matchAllCheckbox.setChecked(filter.isMatchAll());

        if (filter.getExerciseName() != null)
        {
            exerciseField.setText(filter.getExerciseName());
        }

        if (filter.getWeightComparison() != null)
        {
            weightComparisonSpinner.setSelection(filter.getWeightComparison().ordinal());
        }
        if (filter.getWeightThreshold() != null)
        {
            weightValueField.setText(formatThreshold(filter.getWeightThreshold()));
        }

        if (filter.getRepsComparison() != null)
        {
            repsComparisonSpinner.setSelection(filter.getRepsComparison().ordinal());
        }
        if (filter.getRepsThreshold() != null)
        {
            repsValueField.setText(formatThreshold(filter.getRepsThreshold()));
        }
    }

    private String formatThreshold(double value)
    {
        if (value == Math.floor(value))
        {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private CalendarFilter buildFilterFromFields()
    {
        CalendarFilter filter = new CalendarFilter();

        Set<String> selectedCategories = new HashSet<>();
        for (Map.Entry<String, CheckBox> entry : categoryCheckboxes.entrySet())
        {
            if (entry.getValue().isChecked())
            {
                selectedCategories.add(entry.getKey());
            }
        }
        filter.setCategories(selectedCategories);
        filter.setMatchAll(matchAllCheckbox.isChecked());

        String exerciseName = exerciseField.getText().toString().trim();
        if (!TextUtils.isEmpty(exerciseName))
        {
            filter.setExerciseName(exerciseName);

            Double weightThreshold = parseDouble(weightValueField.getText().toString());
            if (weightThreshold != null)
            {
                filter.setWeightThreshold(weightThreshold);
                filter.setWeightComparison((CalendarFilter.Comparison) weightComparisonSpinner.getSelectedItem());
            }

            Double repsThreshold = parseDouble(repsValueField.getText().toString());
            if (repsThreshold != null)
            {
                filter.setRepsThreshold(repsThreshold);
                filter.setRepsComparison((CalendarFilter.Comparison) repsComparisonSpinner.getSelectedItem());
            }
        }

        return filter;
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
