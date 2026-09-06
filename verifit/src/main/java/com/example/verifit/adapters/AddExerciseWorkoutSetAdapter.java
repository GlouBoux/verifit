package com.example.verifit.adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.verifit.R;
import com.example.verifit.model.WorkoutSet;
import com.example.verifit.ui.AddExerciseActivity;
import com.example.verifit.ui.MainActivity;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Adapter for WorkoutSet Class
public class AddExerciseWorkoutSetAdapter extends RecyclerView.Adapter<AddExerciseWorkoutSetAdapter.MyViewHolder> {

    Context ct;
    ArrayList<WorkoutSet> Workout_Sets;

    // Multi-select ("delete several sets at once", retour Romain 05/09/2026). Positions
    // rather than WorkoutSet references: simpler, and safe here because the list never
    // changes shape *during* a selection - it's only mutated once, in a single batch,
    // when the user confirms the delete (see AddExerciseActivity.deleteSelectedSets).
    private boolean selectionMode = false;
    private final Set<Integer> selectedPositions = new HashSet<>();
    private OnSelectionChangedListener selectionChangedListener;

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);
    }

    public AddExerciseWorkoutSetAdapter(Context ct, ArrayList<WorkoutSet> Workout_Sets)
    {
        this.ct = ct;
        this.Workout_Sets = Workout_Sets;
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener)
    {
        this.selectionChangedListener = listener;
    }

    public void enterSelectionMode()
    {
        selectionMode = true;
        selectedPositions.clear();
        notifyDataSetChanged();
    }

    public void exitSelectionMode()
    {
        selectionMode = false;
        selectedPositions.clear();
        notifyDataSetChanged();
    }

    public boolean isSelectionMode()
    {
        return selectionMode;
    }

    public int getSelectedCount()
    {
        return selectedPositions.size();
    }

    // Snapshot of the currently selected sets, resolved now (positions are only valid
    // while the underlying list hasn't changed, i.e. before the batch delete happens).
    public List<WorkoutSet> getSelectedSets()
    {
        List<WorkoutSet> result = new ArrayList<>();
        for (Integer position : selectedPositions)
        {
            if (position >= 0 && position < Workout_Sets.size())
            {
                result.add(Workout_Sets.get(position));
            }
        }
        return result;
    }

    private void toggleSelection(int position)
    {
        if (selectedPositions.contains(position))
        {
            selectedPositions.remove(position);
        }
        else
        {
            selectedPositions.add(position);
        }
        notifyItemChanged(position);

        if (selectionChangedListener != null)
        {
            selectionChangedListener.onSelectionChanged(selectedPositions.size());
        }
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
        // Double -> String
        holder.tv_weight.setText(Workout_Sets.get(position).getWeight().toString());

        // Double -> Integer -> String
        holder.tv_reps.setText(String.valueOf(Workout_Sets.get(position).getReps().intValue()));

        holder.checkbox.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        holder.checkbox.setChecked(selectedPositions.contains(position));

        // Retour Romain 05/09/2026 : cet écran (AddExerciseActivity, atteint aussi
        // depuis l'onglet Sessions) n'affichait pas du tout l'icône de commentaire par
        // série, contrairement à l'onglet Workout (WorkoutSetAdapter, même layout
        // workout_set_row.xml donc même id set_comment_indicator) - or Romain en a
        // besoin ici aussi. Un tap dessus ouvre/édite le commentaire, même mécanique
        // que WorkoutSetAdapter.showSetCommentDialog().
        String comment = Workout_Sets.get(position).getComment();
        holder.commentIndicator.setVisibility(
            (comment != null && !comment.trim().isEmpty()) ? View.VISIBLE : View.GONE
        );
        holder.commentIndicator.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                showSetCommentDialog(holder.getAdapterPosition());
            }
        });

        // Badge discret "Ecart Prevu/Realise" (retour Romain 06/09/2026), meme mecanique
        // que WorkoutSetAdapter - visible ici aussi car cet ecran (Sessions > exercice
        // d'un jour) peut afficher des series importees.
        holder.discrepancyBadge.setVisibility(
            Workout_Sets.get(position).hasDiscrepancy() ? View.VISIBLE : View.GONE
        );
        holder.discrepancyBadge.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                showDiscrepancyDialog(holder.getAdapterPosition());
            }
        });

        // In selection mode, tapping/long-pressing a row toggles it instead of the
        // normal single-set edit/delete flow below.
        holder.cardView.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view) {
                if (selectionMode)
                {
                    toggleSelection(holder.getAdapterPosition());
                }
                else
                {
                    updateView(holder.getAdapterPosition());
                }
            }
        });


        holder.cardView.setOnLongClickListener(new View.OnLongClickListener()
        {
            @Override
            public boolean onLongClick(View view)
            {
                if (selectionMode)
                {
                    toggleSelection(holder.getAdapterPosition());
                    return true;
                }

                AddExerciseActivity.Clicked_Set = holder.getAdapterPosition();
                showSetPopupMenu(holder, view, holder.getAdapterPosition());
                return true;
            }
        });

    }

    // Notify AddExerciseActivity of the clicked position
    public void updateView(int position)
    {
        AddExerciseActivity.bt_clear.setText("Clear");
        AddExerciseActivity.bt_save.setText("Save");

        // Updates the position of the user selected set in AddExerciseActivity
        AddExerciseActivity.Clicked_Set = position;

        // Updates ets buttons and sets in AddExerciseActivity
        AddExerciseActivity.UpdateViewOnClick();
    }

    // Ouvre un petit dialogue pour voir/éditer/effacer le commentaire d'une série
    // précise - copie de WorkoutSetAdapter.showSetCommentDialog() (même dialogue
    // add_exercise_comment_dialog.xml), adaptée à cet adapter qui n'avait jusqu'ici
    // aucune notion de commentaire par série (retour Romain 05/09/2026).
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

    // Detail "Prevu / Realise" (retour Romain 06/09/2026) - copie de
    // WorkoutSetAdapter.showDiscrepancyDialog(), lecture seule.
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

    // To Do: Implement Delete functionality
    private void showSetPopupMenu(AddExerciseWorkoutSetAdapter.MyViewHolder holder, View view, int position)
    {
        PopupMenu popupMenu = new PopupMenu(view.getContext(), view, Gravity.NO_GRAVITY, R.attr.actionOverflowMenuStyle, 0);

        popupMenu.inflate(R.menu.set_add_exercise_activity_menu);
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item)
            {

                if(item.getItemId() == R.id.delete)
                {
                    System.out.println("Delete Set Clicked");
                    AddExerciseActivity.deleteSet(view.getContext());
                }
                else if(item.getItemId() == R.id.edit)
                {
                    System.out.println("Edit Set Clicked");

                    // To Do:
                    // Keep Set highlighted in Recyclerview
                    // Save ---> Update and change color
                    // Notify data changed in adapter

                    AddExerciseActivity.editSet(holder, view, position);
                }

                return false;
            }
        });
        popupMenu.show();
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
        CheckBox checkbox;
        ImageView commentIndicator;
        ImageView discrepancyBadge;

        public MyViewHolder(@NonNull View itemView)
        {
            super(itemView);

            tv_reps = itemView.findViewById(R.id.set_reps);
            tv_weight = itemView.findViewById(R.id.tv_date);
            cardView = itemView.findViewById(R.id.cardview_set);
            checkbox = itemView.findViewById(R.id.set_checkbox);
            commentIndicator = itemView.findViewById(R.id.set_comment_indicator);
            discrepancyBadge = itemView.findViewById(R.id.set_discrepancy_badge);
        }
    }
}
