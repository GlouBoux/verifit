package com.example.verifit.adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.verifit.R;
import com.example.verifit.model.WorkoutSet;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;

// Liste en lecture seule des series dont le realise a fini par differer de la
// valeur prevue a l'import (ecran dedie "Ecarts", retour Romain 06/09/2026, point 3).
// N'affiche que des WorkoutSet pour lesquels hasDiscrepancy() est vrai - c'est
// DiscrepancyHistoryActivity qui filtre et trie avant de construire cet adapter.
public class DiscrepancyAdapter extends RecyclerView.Adapter<DiscrepancyAdapter.MyViewHolder> {

    Context ct;
    ArrayList<WorkoutSet> discrepancies;

    public DiscrepancyAdapter(Context ct, ArrayList<WorkoutSet> discrepancies)
    {
        this.ct = ct;
        this.discrepancies = discrepancies;
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        LayoutInflater inflater = LayoutInflater.from(this.ct);
        View view = inflater.inflate(R.layout.discrepancy_history_row, parent, false);
        return new MyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position)
    {
        WorkoutSet workoutSet = discrepancies.get(position);

        holder.exercise.setText(workoutSet.getExerciseName());
        holder.date.setText(workoutSet.getDate());
        holder.planned.setText("Prevu : " + formatSetValue(workoutSet.getPlannedWeight(), workoutSet.getPlannedReps()));
        holder.actual.setText("Realise : " + formatSetValue(workoutSet.getWeight(), workoutSet.getReps()));

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDetailDialog(holder.getAdapterPosition());
            }
        });
    }

    private void showDetailDialog(int position)
    {
        if (position < 0 || position >= discrepancies.size())
        {
            return;
        }

        WorkoutSet workoutSet = discrepancies.get(position);

        LayoutInflater inflater = LayoutInflater.from(ct);
        View view = inflater.inflate(R.layout.set_discrepancy_dialog, null);
        AlertDialog alertDialog = new AlertDialog.Builder(ct).setView(view).create();

        TextView exerciseName = view.findViewById(R.id.tv_discrepancy_exercise);
        TextView planned = view.findViewById(R.id.tv_discrepancy_planned);
        TextView actual = view.findViewById(R.id.tv_discrepancy_actual);
        MaterialButton closeButton = view.findViewById(R.id.bt_close_discrepancy);

        exerciseName.setText(workoutSet.getExerciseName() + " - " + workoutSet.getDate());
        planned.setText("Prevu : " + formatSetValue(workoutSet.getPlannedWeight(), workoutSet.getPlannedReps()));
        actual.setText("Realise : " + formatSetValue(workoutSet.getWeight(), workoutSet.getReps()));

        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alertDialog.dismiss();
            }
        });

        alertDialog.show();
    }

    private String formatSetValue(Double weight, Double reps)
    {
        int repsRounded = (int) Math.round(reps);
        return weight + " kg x " + repsRounded + " reps";
    }

    @Override
    public int getItemCount()
    {
        return discrepancies.size();
    }

    public class MyViewHolder extends RecyclerView.ViewHolder
    {
        TextView exercise;
        TextView date;
        TextView planned;
        TextView actual;

        public MyViewHolder(@NonNull View itemView)
        {
            super(itemView);

            exercise = itemView.findViewById(R.id.tv_row_exercise);
            date = itemView.findViewById(R.id.tv_row_date);
            planned = itemView.findViewById(R.id.tv_row_planned);
            actual = itemView.findViewById(R.id.tv_row_actual);
        }
    }
}
