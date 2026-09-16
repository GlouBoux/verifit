package com.example.verifit.adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.verifit.R;
import com.example.verifit.RepRangeHistoryRow;
import com.example.verifit.RepRangePREvent;
import com.example.verifit.model.WorkoutSet;
import com.example.verifit.ui.MainActivity;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.TreeMap;

// Adapter for WorkoutSet Class
public class WorkoutSetAdapter extends RecyclerView.Adapter<WorkoutSetAdapter.MyViewHolder> {

    Context ct;
    ArrayList<WorkoutSet> Workout_Sets;

    // Series qui sont un PR reel pour cet exercice (retour Romain : badge trophee sur
    // la serie -> tap = historique de PR direct pour son nombre de reps). Cet adapter
    // est recree a chaque bind (une instance par carte "un jour + un exercice" - voir
    // DayExerciseAdapter/ViewPagerExerciseAdapter/ExerciseHistoryExerciseAdapter), donc
    // calculer une seule fois ici au constructeur suffit - pas besoin d'un refresh
    // explicite comme sur AddExerciseWorkoutSetAdapter (adapter unique qui vit toute la
    // duree de l'ecran de saisie). Workout_Sets ne contient que les series d'UN SEUL
    // exercice (WorkoutExercise.getSets()) - le nom est donc lu une seule fois.
    //
    // Matching par CLE VALEUR (DataStorage.repRangePRKey()), pas par identite d'objet
    // Java (retour Romain 16/09/2026 : badge totalement absent sur cet ecran malgre des
    // PR reels) - Workout_Sets vient ici de WorkoutExercise.getSets(), une liste
    // DERIVEE qui, apres un redemarrage a froid de l'app, contient des instances
    // WorkoutSet DIFFERENTES de celles utilisees par
    // DataStorage.calculateRepRangeHistory() (WorkoutDay.Sets et WorkoutDay.Exercises[].
    // Sets sont deserialisees separement par Gson - voir le commentaire detaille sur
    // DataStorage.getRepRangePRKeys()). Un simple HashSet<WorkoutSet> (comparaison par
    // reference) echouait donc systematiquement ici.
    private final HashSet<String> prSetKeys;

    public WorkoutSetAdapter(Context ct, ArrayList<WorkoutSet> Workout_Sets)
    {
        this.ct = ct;
        this.Workout_Sets = Workout_Sets;

        HashSet<String> computedPrSetKeys = new HashSet<>();
        if (!Workout_Sets.isEmpty() && Workout_Sets.get(0).getExerciseName() != null)
        {
            computedPrSetKeys = MainActivity.dataStorage.getRepRangePRKeys(Workout_Sets.get(0).getExerciseName());
        }
        this.prSetKeys = computedPrSetKeys;
    }

    // true si cette serie precise est un PR reel - voir le commentaire sur prSetKeys.
    private boolean isPersonalRecord(WorkoutSet set)
    {
        if (set.getDate() == null || set.getReps() == null || set.getWeight() == null)
        {
            return false;
        }
        return prSetKeys.contains(com.example.verifit.DataStorage.repRangePRKey(
                set.getDate(), (int) Math.round(set.getReps()), set.getWeight()));
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        LayoutInflater inflater = LayoutInflater.from(this.ct);
        View view = inflater.inflate(R.layout.workout_set_row,parent,false);
        return new MyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position)
    {

        holder.tv_weight.setText(Workout_Sets.get(position).getWeight().toString());

        // Double -> Integer
        int reps = (int)Math.round(Workout_Sets.get(position).getReps());
        holder.tv_reps.setText(String.valueOf(reps));

        // Small indicator so a set with its own comment is visible at a glance,
        // without having to open it - useful for reviewing imported data too.
        String comment = Workout_Sets.get(position).getComment();
        holder.commentIndicator.setVisibility(
            (comment != null && !comment.trim().isEmpty()) ? View.VISIBLE : View.GONE
        );

        // Badge discret "Ecart Prevu/Realise" (retour Romain 06/09/2026) : visible
        // uniquement pour une serie importee dont le realise actuel differe de la
        // valeur prevue figee a l'import. Un tap dessus - independant du tap/long-press
        // de la carte - ouvre le detail Prevu/Realise.
        holder.discrepancyBadge.setVisibility(
            Workout_Sets.get(position).hasDiscrepancy() ? View.VISIBLE : View.GONE
        );
        holder.discrepancyBadge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDiscrepancyDialog(holder.getAdapterPosition());
            }
        });

        // Badge trophee "Personal Record" : visible uniquement si cette serie precise
        // est un record REEL (meme source de verite que le tag [PR] de l'export -
        // DataStorage.getRepRangePRSets()). Un tap dessus - independant du tap/long-press
        // de la carte - ouvre directement l'historique de PR pour le nombre de reps
        // exact de cette serie.
        holder.prBadge.setVisibility(
            isPersonalRecord(Workout_Sets.get(position)) ? View.VISIBLE : View.GONE
        );
        holder.prBadge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPersonalRecordHistoryDialog(holder.getAdapterPosition());
            }
        });

        // Retour Romain 05/09/2026 : inversion volontaire par rapport au comportement
        // d'origine (tap = stats, long-press = commentaire) - c'est le commentaire que
        // Romain veut voir en un tap rapide, les stats (reps/charge/1RM) l'intéressent
        // moins et passent donc en second (long-press).
        //
        // Appui court : voir/éditer le commentaire de cette série précise, indépendant
        // des autres séries du même exercice (contrairement à "Exercise Comments" dans
        // AddExerciseActivity, qui applique un seul commentaire à toutes les séries de
        // l'exercice pour la journée).
        holder.cardView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showSetCommentDialog(holder.getAdapterPosition());
            }
        });

        // Appui long : stats de la série (reps/charge/volume/1RM estimé), l'ancien
        // comportement du tap court.
        holder.cardView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                showSetDialog(holder.getAdapterPosition());
                return true;
            }
        });

    }

    // Opens a small dialog to view/edit/clear the comment of one specific set. Reuses
    // add_exercise_comment_dialog.xml (title + EditText + Save/Clear buttons) - same
    // shape as the existing exercise-level comment dialog, just scoped to one set.
    public void showSetCommentDialog(int position)
    {
        if(position < 0 || position >= Workout_Sets.size())
        {
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(ct);
        View view = inflater.inflate(R.layout.add_exercise_comment_dialog, null);
        AlertDialog alertDialog = new AlertDialog.Builder(ct).setView(view).create();

        TextView title = view.findViewById(R.id.tv_date);
        EditText commentInput = view.findViewById(R.id.et_exercise_comment);
        MaterialButton saveButton = view.findViewById(R.id.bt_save_comment);
        MaterialButton clearButton = view.findViewById(R.id.bt_clear_comment);

        title.setText("Set comment");

        String existingComment = Workout_Sets.get(position).getComment();
        if(existingComment != null && !existingComment.equals("null"))
        {
            commentInput.setText(existingComment);
        }

        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                commentInput.setText("");
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newComment = commentInput.getText().toString();

                Workout_Sets.get(position).setComment(newComment);

                // Let the backup service know something changed, same as every other
                // mutation in the app.
                MainActivity.autoBackupRequired = true;
                com.example.verifit.SharedPreferences sharedPreferences = new com.example.verifit.SharedPreferences(ct);
                sharedPreferences.save("true", "autoBackupRequired");

                MainActivity.dataStorage.saveWorkoutData(ct);

                notifyItemChanged(position);
                Toast.makeText(ct, "Comment saved", Toast.LENGTH_SHORT).show();
                alertDialog.dismiss();
            }
        });

        alertDialog.show();
    }

    // Detail "Prevu / Realise" d'une serie importee dont le realise a change depuis
    // l'import (retour Romain 06/09/2026, point 3 : "badge discret + detail au tap").
    // Lecture seule - modifier le realise se fait toujours via l'edition normale de la
    // serie, pas depuis ce dialogue.
    public void showDiscrepancyDialog(int position)
    {
        if(position < 0 || position >= Workout_Sets.size())
        {
            return;
        }

        WorkoutSet workoutSet = Workout_Sets.get(position);
        if(!workoutSet.hasDiscrepancy())
        {
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(ct);
        View view = inflater.inflate(R.layout.set_discrepancy_dialog, null);
        AlertDialog alertDialog = new AlertDialog.Builder(ct).setView(view).create();

        TextView exerciseName = view.findViewById(R.id.tv_discrepancy_exercise);
        TextView planned = view.findViewById(R.id.tv_discrepancy_planned);
        TextView actual = view.findViewById(R.id.tv_discrepancy_actual);
        MaterialButton closeButton = view.findViewById(R.id.bt_close_discrepancy);

        exerciseName.setText(workoutSet.getExerciseName());
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

    // Ouvre directement le popup "Personal Record History" (rep_range_history_dialog.xml)
    // pour le nombre de reps EXACT de la serie tapee - retour Romain : "taper l'icone
    // trophee d'une serie devrait ouvrir directement le popup [...] pour le rep-count
    // exact de cette serie, sans passer par un ecran intermediaire". Meme
    // dialogue/mecanique que RepRangeHistoryAdapter.showHistoryDialog() (dupliquee ici,
    // comme le sont deja showSetCommentDialog()/showDiscrepancyDialog() dans cet
    // adapter et son homologue AddExerciseWorkoutSetAdapter) car scopee a une seule
    // ligne plutot qu'a tout le tableau RepRangeRecordsActivity.
    public void showPersonalRecordHistoryDialog(int position)
    {
        if (position < 0 || position >= Workout_Sets.size())
        {
            return;
        }

        WorkoutSet workoutSet = Workout_Sets.get(position);
        if (workoutSet.getReps() == null || workoutSet.getExerciseName() == null)
        {
            return;
        }

        int reps = (int) Math.round(workoutSet.getReps());
        TreeMap<Integer, ArrayList<RepRangePREvent>> history =
                MainActivity.dataStorage.calculateRepRangeHistory(workoutSet.getExerciseName());
        ArrayList<RepRangePREvent> events = history.get(reps);

        if (events == null || events.isEmpty())
        {
            return;
        }

        RepRangeHistoryRow row = new RepRangeHistoryRow(reps, events);

        LayoutInflater inflater = LayoutInflater.from(ct);
        View view = inflater.inflate(R.layout.rep_range_history_dialog, null);
        AlertDialog alertDialog = new AlertDialog.Builder(ct).setView(view).create();

        TextView title = view.findViewById(R.id.tv_pr_history_title);
        LinearLayout currentContainer = view.findViewById(R.id.container_current_record);
        LinearLayout previousContainer = view.findViewById(R.id.container_previous_records);
        TextView previousLabel = view.findViewById(R.id.tv_previous_records_label);
        MaterialButton closeButton = view.findViewById(R.id.bt_close_pr_history);

        title.setText(row.getReps() + " RM");

        ArrayList<RepRangePREvent> allEvents = row.getAllEvents();

        // Record actuel = le dernier evenement chronologique.
        View currentRow = inflater.inflate(R.layout.rep_range_history_entry_row, currentContainer, false);
        bindPersonalRecordEventRow(currentRow.findViewById(R.id.tv_entry_reps), currentRow.findViewById(R.id.tv_entry_weight),
                currentRow.findViewById(R.id.tv_entry_date), row.getReps(), row.getCurrentEvent());
        currentContainer.addView(currentRow);

        // Records precedents, du plus recent au plus ancien.
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
                bindPersonalRecordEventRow(previousRow.findViewById(R.id.tv_entry_reps), previousRow.findViewById(R.id.tv_entry_weight),
                        previousRow.findViewById(R.id.tv_entry_date), previousEvent.getSourceReps(), previousEvent);
                previousContainer.addView(previousRow);
            }
        }

        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alertDialog.dismiss();
            }
        });

        alertDialog.show();
    }

    // Copie de RepRangeHistoryAdapter.bindEventRow() - meme rendu (grise si deduit).
    private void bindPersonalRecordEventRow(TextView repsView, TextView weightView, TextView dateView, int displayReps, RepRangePREvent event)
    {
        repsView.setText(displayReps + " RM");
        weightView.setText(String.format("%.1f", event.getWeight()) + " kgs");
        dateView.setText(event.getDate());

        int color = event.isDeduced()
                ? ContextCompat.getColor(ct, R.color.core_grey_40)
                : ContextCompat.getColor(ct, R.color.core_black);

        repsView.setTextColor(color);
        weightView.setTextColor(color);
        dateView.setTextColor(color);
    }

    public void showSetDialog(int position)
    {
        // Prepare to show exercise dialog box
        LayoutInflater inflater = LayoutInflater.from(ct);
        View view = inflater.inflate(R.layout.set_dialog,null);
        AlertDialog alertDialog = new AlertDialog.Builder(ct).setView(view).create();

        TextView volume = view.findViewById(R.id.volume);
        TextView onerepmax = view.findViewById(R.id.onerepmax);
        TextView reps = view.findViewById(R.id.reps);
        TextView kg = view.findViewById(R.id.tv_date);

        // Double -> Integer
        int repetitions = (int)Math.round(Workout_Sets.get(position).getReps());
        reps.setText(String.valueOf(repetitions));

        volume.setText(Workout_Sets.get(position).getVolume().toString());
        Double est1rm = Math.floor(Workout_Sets.get(position).getEplayOneRepMax());
        onerepmax.setText(est1rm.toString());

        kg.setText(Workout_Sets.get(position).getWeight().toString());

        // Show Exercise Dialog Box
        alertDialog.show();

    }


    @Override
    public int getItemCount()
    {
        return Workout_Sets.size();
    }

    public class MyViewHolder extends  RecyclerView.ViewHolder
    {
        TextView tv_reps;
        TextView tv_weight;
        CardView cardView;
        ImageView commentIndicator;
        ImageView discrepancyBadge;
        ImageView prBadge;


        public MyViewHolder(@NonNull View itemView) {
            super(itemView);

            tv_reps = itemView.findViewById(R.id.set_reps);
            tv_weight = itemView.findViewById(R.id.tv_date);
            cardView = itemView.findViewById(R.id.cardview_set);
            commentIndicator = itemView.findViewById(R.id.set_comment_indicator);
            discrepancyBadge = itemView.findViewById(R.id.set_discrepancy_badge);
            prBadge = itemView.findViewById(R.id.set_pr_badge);

        }
    }
}
