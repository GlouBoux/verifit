package com.example.verifit.adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.verifit.R;
import com.example.verifit.model.WorkoutSet;
import com.example.verifit.ui.MainActivity;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

// Adapter for WorkoutSet Class
public class WorkoutSetAdapter extends RecyclerView.Adapter<WorkoutSetAdapter.MyViewHolder> {

    Context ct;
    ArrayList<WorkoutSet> Workout_Sets;

    // Badge "Personal Record" (Vague IHM, IHM-2, retour Romain 11/09/2026). Contrairement
    // a AddExerciseWorkoutSetAdapter (champ persistant refraichi explicitement), cet
    // adapter est recree a chaque bind par DayExerciseAdapter/ViewPagerExerciseAdapter
    // (un WorkoutSetAdapter par carte exercice, jamais reutilise) - calculer une seule
    // fois ici, au constructeur, est donc deja toujours a jour sans hook supplementaire.
    // Nom d'exercice lu directement sur la premiere serie (WorkoutSet.getExerciseName())
    // plutot que demande en parametre : toutes les series de cette liste partagent le
    // meme exercice (une carte = un exercice), meme logique que
    // getRepRangePRSets()/getComment() deja lus directement sur chaque WorkoutSet.
    //
    // Matching par CLE VALEUR (DataStorage.repRangePRKey()), pas par identite d'objet
    // Java (retour Romain 17/09/2026 : trophee disparu sur le resume du jour/onglet
    // Workout pour une journee de quelques jours, alors qu'il fonctionnait pour une
    // journee du jour meme) - Workout_Sets vient ici de WorkoutExercise.getSets(), une
    // liste DERIVEE qui, apres tout redemarrage de l'app depuis la derniere
    // sauvegarde, contient des instances WorkoutSet DIFFERENTES de celles utilisees
    // par DataStorage.calculateRepRangeHistory() (WorkoutDay.Sets et
    // WorkoutDay.Exercises[].Sets sont deserialisees separement par Gson - voir le
    // commentaire detaille sur DataStorage.getRepRangePRKeys()). Un simple
    // Set<WorkoutSet> (comparaison par reference) marchait donc par coincidence pour
    // une journee jamais encore rechargee, mais echouait systematiquement des qu'un
    // cycle sauvegarde/chargement avait eu lieu.
    private final HashSet<String> prSetKeys;

    public WorkoutSetAdapter(Context ct, ArrayList<WorkoutSet> Workout_Sets)
    {
        this.ct = ct;
        this.Workout_Sets = Workout_Sets;
        this.prSetKeys = (!Workout_Sets.isEmpty() && Workout_Sets.get(0).getExerciseName() != null)
                ? MainActivity.dataStorage.getRepRangePRKeys(Workout_Sets.get(0).getExerciseName())
                : new HashSet<String>();
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

        // Badge "Personal Record" (retour Romain 11/09/2026) - meme logique de
        // detection que le tag "[PR]" de l'export texte (DataStorage.getRepRangePRSets(),
        // comparaison par reference puisque WorkoutSet ne redefinit pas equals()).
        holder.prBadge.setVisibility(
            isPersonalRecord(Workout_Sets.get(position)) ? View.VISIBLE : View.GONE
        );

        // Retour Romain 16/09/2026 : un tap sur le trophee doit ouvrir directement le
        // popup "Personal Record History" pour le nombre de reps exact de cette serie,
        // sans passer par l'ecran RepRangeRecordsActivity. Listener propre au badge
        // (independant du tap/long-press du reste de la carte ci-dessous) - jusqu'ici
        // le trophee n'avait aucun OnClickListener, donc le tap retombait sur celui de
        // la carte (ouverture du commentaire).
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

    // Popup "Personal Record History" pour le nombre de reps exact de cette serie
    // (retour Romain 16/09/2026, poursuite du point d'acces trophee deja en place dans
    // la barre d'outils d'AddExerciseActivity qui ouvre RepRangeRecordsActivity pour le
    // tableau complet - les deux points d'acces coexistent). Reprend exactement la
    // logique de RepRangeHistoryAdapter.showHistoryDialog(), dupliquee ici (meme
    // convention que showSetCommentDialog()/showDiscrepancyDialog() ci-dessus) plutot
    // que de toucher a l'API de RepRangeHistoryAdapter (reste inchange, toujours
    // utilise par RepRangeRecordsActivity pour le tableau complet).
    public void showPersonalRecordHistoryDialog(int position)
    {
        if (position < 0 || position >= Workout_Sets.size())
        {
            return;
        }

        WorkoutSet workoutSet = Workout_Sets.get(position);
        if (workoutSet.getExerciseName() == null || workoutSet.getReps() == null)
        {
            return;
        }

        int reps = (int) Math.round(workoutSet.getReps());
        java.util.TreeMap<Integer, ArrayList<com.example.verifit.RepRangePREvent>> history =
                MainActivity.dataStorage.calculateRepRangeHistory(workoutSet.getExerciseName());
        ArrayList<com.example.verifit.RepRangePREvent> events = history.get(reps);
        if (events == null || events.isEmpty())
        {
            return;
        }

        com.example.verifit.RepRangeHistoryRow row = new com.example.verifit.RepRangeHistoryRow(reps, events);

        LayoutInflater inflater = LayoutInflater.from(ct);
        View view = inflater.inflate(R.layout.rep_range_history_dialog, null);
        AlertDialog alertDialog = new AlertDialog.Builder(ct).setView(view).create();

        TextView title = view.findViewById(R.id.tv_pr_history_title);
        android.widget.LinearLayout currentContainer = view.findViewById(R.id.container_current_record);
        android.widget.LinearLayout previousContainer = view.findViewById(R.id.container_previous_records);
        TextView previousLabel = view.findViewById(R.id.tv_previous_records_label);
        MaterialButton closeButton = view.findViewById(R.id.bt_close_pr_history);

        title.setText(row.getReps() + " RM");

        ArrayList<com.example.verifit.RepRangePREvent> allEvents = row.getAllEvents();

        View currentRow = inflater.inflate(R.layout.rep_range_history_entry_row, currentContainer, false);
        bindPersonalRecordRow(currentRow.findViewById(R.id.tv_entry_reps), currentRow.findViewById(R.id.tv_entry_weight),
                currentRow.findViewById(R.id.tv_entry_date), row.getReps(), row.getCurrentEvent());
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
                com.example.verifit.RepRangePREvent previousEvent = allEvents.get(i);
                View previousRow = inflater.inflate(R.layout.rep_range_history_entry_row, previousContainer, false);
                bindPersonalRecordRow(previousRow.findViewById(R.id.tv_entry_reps), previousRow.findViewById(R.id.tv_entry_weight),
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

    // Meme rendu qu'une ligne de RepRangeHistoryAdapter (grisee si l'evenement est
    // deduit par transitivite plutot que reel).
    private void bindPersonalRecordRow(TextView repsView, TextView weightView, TextView dateView, int displayReps, com.example.verifit.RepRangePREvent event)
    {
        repsView.setText(displayReps + " RM");
        weightView.setText(String.format("%.1f", event.getWeight()) + " kgs");
        dateView.setText(event.getDate());

        int color = event.isDeduced()
                ? androidx.core.content.ContextCompat.getColor(ct, R.color.core_grey_40)
                : androidx.core.content.ContextCompat.getColor(ct, R.color.core_black);

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
