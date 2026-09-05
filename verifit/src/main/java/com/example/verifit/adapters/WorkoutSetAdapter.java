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

// Adapter for WorkoutSet Class
public class WorkoutSetAdapter extends RecyclerView.Adapter<WorkoutSetAdapter.MyViewHolder> {

    Context ct;
    ArrayList<WorkoutSet> Workout_Sets;

    public WorkoutSetAdapter(Context ct, ArrayList<WorkoutSet> Workout_Sets)
    {
        this.ct = ct;
        this.Workout_Sets = Workout_Sets;
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

        // Shows Set Stats when Clicked
        holder.cardView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showSetDialog(position);
            }
        });

        // Long-press to view/edit this set's own comment, independent from the other
        // sets of the same exercise (unlike "Exercise Comments" in AddExerciseActivity,
        // which applies one comment to every set of the exercise for the day).
        holder.cardView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                showSetCommentDialog(holder.getAdapterPosition());
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


        public MyViewHolder(@NonNull View itemView) {
            super(itemView);

            tv_reps = itemView.findViewById(R.id.set_reps);
            tv_weight = itemView.findViewById(R.id.tv_date);
            cardView = itemView.findViewById(R.id.cardview_set);
            commentIndicator = itemView.findViewById(R.id.set_comment_indicator);

        }
    }
}
