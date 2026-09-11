package com.example.verifit.adapters;

import android.content.Context;
import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.verifit.R;
import com.example.verifit.model.Goal;
import com.example.verifit.ui.MainActivity;

import java.util.ArrayList;

// "Goals" (Vague 4 du plan de migration, item 14, retour Romain 08/09/2026 : "passe
// a la vague 4") - un objectif par carte, avec la valeur actuelle ("depuis toujours",
// voir DataStorage.calculateGoalCurrentValue()) comparee a la cible sous forme de
// barre de progression. Tap sur une carte -> edition/suppression (voir
// GoalsActivity.showGoalDialog()), meme motif que "Toggle Favorite" en long-press sur
// PersonalRecordsActivity mais ici en simple tap (pas de menu contextuel a
// construire pour une seule action).
public class GoalsAdapter extends RecyclerView.Adapter<GoalsAdapter.MyViewHolder>
{
    public interface OnGoalClickListener
    {
        void onGoalClicked(Goal goal);
    }

    private final Context ct;
    private final ArrayList<Goal> goals;
    private final OnGoalClickListener listener;

    public GoalsAdapter(Context ct, ArrayList<Goal> goals, OnGoalClickListener listener)
    {
        this.ct = ct;
        this.goals = goals;
        this.listener = listener;
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        LayoutInflater inflater = LayoutInflater.from(ct);
        View view = inflater.inflate(R.layout.goal_row, parent, false);
        return new MyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position)
    {
        Goal goal = goals.get(position);

        holder.exerciseName.setText(goal.getExerciseName());
        holder.goalType.setText(goal.getType().toString());

        double target = goal.getTargetValue() != null ? goal.getTargetValue() : 0.0;
        double current = MainActivity.dataStorage.calculateGoalCurrentValue(goal);

        int percent = 0;
        if (target > 0.0)
        {
            percent = (int) Math.round(Math.min(100.0, (current / target) * 100.0));
        }

        // Pas de android:progressTint en XML (attribut API 21+, minSdk de l'app =
        // 16) - meme motif de "tinting compatible" que ViewCompat.setBackgroundTintList()
        // deja utilise ailleurs dans l'app, ici via le drawable de la ProgressBar
        // directement (fonctionne a tous les niveaux d'API).
        holder.progressBar.getProgressDrawable().setColorFilter(ContextCompat.getColor(ct, R.color.colorPrimary), PorterDuff.Mode.SRC_IN);
        holder.progressBar.setProgress(percent);
        holder.progressLabel.setText(formatValue(current) + " / " + formatValue(target) + " (" + percent + "%)");

        holder.cardview_goal.setOnClickListener(v -> listener.onGoalClicked(goal));
    }

    // Evite l'affichage "50.0" pour une cible entiere ("50") tout en gardant les
    // decimales quand elles comptent (Total Volume, notamment) - meme convention que
    // CalendarFilterDialog.formatThreshold().
    private String formatValue(double value)
    {
        if (value == Math.floor(value))
        {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    @Override
    public int getItemCount()
    {
        return goals.size();
    }

    public class MyViewHolder extends RecyclerView.ViewHolder
    {
        TextView exerciseName;
        TextView goalType;
        TextView progressLabel;
        ProgressBar progressBar;
        CardView cardview_goal;

        public MyViewHolder(@NonNull View itemView)
        {
            super(itemView);
            exerciseName = itemView.findViewById(R.id.goal_row_exercise_name);
            goalType = itemView.findViewById(R.id.goal_row_type);
            progressLabel = itemView.findViewById(R.id.goal_row_progress_label);
            progressBar = itemView.findViewById(R.id.goal_row_progress_bar);
            cardview_goal = itemView.findViewById(R.id.cardview_goal);
        }
    }
}
