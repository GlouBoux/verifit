package com.example.verifit;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.verifit.model.WorkoutSet;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Dialogues ouverts depuis une ligne de serie, partages par WorkoutSetAdapter (onglet
 * Workout, resume du jour) et AddExerciseWorkoutSetAdapter (ecran de saisie).
 *
 * Lot C, etape C.3 (30/09/2026) : ces dialogues etaient copies a l'identique dans les
 * deux adapters ; le bug du trophee du 17/09/2026 avait ete corrige d'un cote seulement.
 * Une seule version desormais. La regle "cette serie est-elle un PR ?" est dans
 * DataStorage.isRepRangePR() (testee en JUnit).
 */
public final class SetDialogs
{
    private SetDialogs() {}

    // Serie a cette position, ou null si la position n'est plus valide (ligne supprimee
    // entre l'affichage et le tap, getAdapterPosition() == NO_POSITION...).
    public static WorkoutSet setAt(List<WorkoutSet> sets, int position)
    {
        return (position < 0 || position >= sets.size()) ? null : sets.get(position);
    }

    // Detail "Prevu / Realise" d'une serie importee dont le realise a change depuis
    // l'import (retour Romain 06/09/2026, point 3 : "badge discret + detail au tap").
    // Lecture seule : le realise se modifie par l'edition normale de la serie.
    public static void showDiscrepancy(Context context, WorkoutSet workoutSet)
    {
        if (workoutSet == null || !workoutSet.hasDiscrepancy())
        {
            return;
        }

        View view = LayoutInflater.from(context).inflate(R.layout.set_discrepancy_dialog, null);
        final AlertDialog alertDialog = new AlertDialog.Builder(context).setView(view).create();

        TextView exerciseName = view.findViewById(R.id.tv_discrepancy_exercise);
        TextView planned = view.findViewById(R.id.tv_discrepancy_planned);
        TextView actual = view.findViewById(R.id.tv_discrepancy_actual);
        MaterialButton closeButton = view.findViewById(R.id.bt_close_discrepancy);

        exerciseName.setText(workoutSet.getExerciseName());
        planned.setText("Prevu : " + formatSetValue(workoutSet.getPlannedWeight(), workoutSet.getPlannedReps()));
        actual.setText("Realise : " + formatSetValue(workoutSet.getWeight(), workoutSet.getReps()));

        closeButton.setOnClickListener(v -> alertDialog.dismiss());
        alertDialog.show();
    }

    private static String formatSetValue(Double weight, Double reps)
    {
        int repsRounded = (int) Math.round(reps);
        return weight + " kg x " + repsRounded + " reps";
    }

    // Popup "Personal Record History" pour le nombre de reps exact de cette serie (tap
    // sur le trophee, retour Romain 16/09/2026). Meme rendu que
    // RepRangeHistoryAdapter.showHistoryDialog() (ecran RepRangeRecordsActivity, tableau
    // complet), qui reste separe.
    public static void showPersonalRecordHistory(Context context, DataStorage dataStorage, WorkoutSet workoutSet)
    {
        if (workoutSet == null || workoutSet.getExerciseName() == null || workoutSet.getReps() == null)
        {
            return;
        }

        int reps = (int) Math.round(workoutSet.getReps());
        TreeMap<Integer, ArrayList<RepRangePREvent>> history = dataStorage.calculateRepRangeHistory(workoutSet.getExerciseName());
        ArrayList<RepRangePREvent> events = history.get(reps);
        if (events == null || events.isEmpty())
        {
            return;
        }

        RepRangeHistoryRow row = new RepRangeHistoryRow(reps, events);

        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.rep_range_history_dialog, null);
        final AlertDialog alertDialog = new AlertDialog.Builder(context).setView(view).create();

        TextView title = view.findViewById(R.id.tv_pr_history_title);
        LinearLayout currentContainer = view.findViewById(R.id.container_current_record);
        LinearLayout previousContainer = view.findViewById(R.id.container_previous_records);
        TextView previousLabel = view.findViewById(R.id.tv_previous_records_label);
        MaterialButton closeButton = view.findViewById(R.id.bt_close_pr_history);

        title.setText(row.getReps() + " RM");

        ArrayList<RepRangePREvent> allEvents = row.getAllEvents();

        View currentRow = inflater.inflate(R.layout.rep_range_history_entry_row, currentContainer, false);
        bindPersonalRecordRow(context, currentRow, row.getReps(), row.getCurrentEvent());
        currentContainer.addView(currentRow);

        if (allEvents.size() <= 1)
        {
            previousLabel.setVisibility(View.GONE);
            previousContainer.setVisibility(View.GONE);
        }
        else
        {
            for (int i = allEvents.size() - 2; i >= 0; i--)
            {
                RepRangePREvent previousEvent = allEvents.get(i);
                View previousRow = inflater.inflate(R.layout.rep_range_history_entry_row, previousContainer, false);
                bindPersonalRecordRow(context, previousRow, previousEvent.getSourceReps(), previousEvent);
                previousContainer.addView(previousRow);
            }
        }

        closeButton.setOnClickListener(v -> alertDialog.dismiss());
        alertDialog.show();
    }

    // Meme rendu qu'une ligne de RepRangeHistoryAdapter (grisee si l'evenement est
    // deduit par transitivite plutot que reel).
    private static void bindPersonalRecordRow(Context context, View rowView, int displayReps, RepRangePREvent event)
    {
        TextView repsView = rowView.findViewById(R.id.tv_entry_reps);
        TextView weightView = rowView.findViewById(R.id.tv_entry_weight);
        TextView dateView = rowView.findViewById(R.id.tv_entry_date);

        repsView.setText(displayReps + " RM");
        weightView.setText(String.format("%.1f", event.getWeight()) + " kgs");
        dateView.setText(event.getDate());

        int color = event.isDeduced()
                ? ContextCompat.getColor(context, R.color.core_grey_40)
                : ContextCompat.getColor(context, R.color.core_black);

        repsView.setTextColor(color);
        weightView.setTextColor(color);
        dateView.setTextColor(color);
    }
}
