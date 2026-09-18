package com.example.verifit.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.verifit.R;
import com.example.verifit.model.WorkoutExercise;

import java.util.ArrayList;

// Adapter du volet de navigation entre exercices (retour Romain 18/09/2026, "le meme
// que sur FitNotes [...] naviguer entre les series au sein d'une seance") - voir
// claude/fitnotes-feature-navigation-panel.md. Calque sur DayExerciseAdapter (meme
// mecanique de poignee de reorganisation + OnStartDragListener, l'ItemTouchHelper
// lui-meme vit dans AddExerciseActivity qui a besoin d'acceder au WorkoutDay pour
// persister l'ordre final - voir AddExerciseActivity.persistNavPanelOrder()) mais
// simplifie : pas de mode selection multiple, pas de repli, pas de barre de superset -
// juste nom + nombre de series (US-4) et surbrillance de l'exercice courant (US-5).
public class NavPanelExerciseAdapter extends RecyclerView.Adapter<NavPanelExerciseAdapter.MyViewHolder> {

    private final Context ct;
    private ArrayList<WorkoutExercise> exercises;
    private String currentExerciseName;

    public interface OnStartDragListener {
        void onStartDrag(RecyclerView.ViewHolder viewHolder);
    }

    public interface OnExerciseClickListener {
        void onExerciseClick(String exerciseName);
    }

    private OnStartDragListener dragListener;
    private OnExerciseClickListener clickListener;

    public NavPanelExerciseAdapter(Context ct, ArrayList<WorkoutExercise> exercises, String currentExerciseName)
    {
        this.ct = ct;
        this.exercises = new ArrayList<>(exercises);
        this.currentExerciseName = currentExerciseName;
    }

    public void setOnStartDragListener(OnStartDragListener listener)
    {
        this.dragListener = listener;
    }

    public void setOnExerciseClickListener(OnExerciseClickListener listener)
    {
        this.clickListener = listener;
    }

    // Rafraichit la liste (nouvelles series loggees, exercice courant change) sans
    // reconstruire l'adapter - appele par AddExerciseActivity.refreshNavPanel().
    public void updateData(ArrayList<WorkoutExercise> exercises, String currentExerciseName)
    {
        this.exercises = new ArrayList<>(exercises);
        this.currentExerciseName = currentExerciseName;
        notifyDataSetChanged();
    }

    public ArrayList<WorkoutExercise> getExercises()
    {
        return exercises;
    }

    // Appele par l'ItemTouchHelper.Callback (AddExerciseActivity) a chaque etape du
    // drag - seulement la mise a jour visuelle/locale de la liste ; la persistance
    // dans WorkoutDay.moveExercise() se fait une seule fois, a la fin du geste (meme
    // convention que DayExerciseAdapter.moveItem()/DayActivity.persistExerciseOrder()).
    public void moveItem(int fromPosition, int toPosition)
    {
        WorkoutExercise moved = exercises.remove(fromPosition);
        exercises.add(toPosition, moved);
        notifyItemMoved(fromPosition, toPosition);
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        LayoutInflater inflater = LayoutInflater.from(this.ct);
        View view = inflater.inflate(R.layout.nav_panel_exercise_row, parent, false);
        return new MyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position)
    {
        WorkoutExercise exercise = exercises.get(position);

        holder.tvExerciseName.setText(exercise.getExercise());

        int setCount = exercise.getSets() != null ? exercise.getSets().size() : 0;
        holder.tvSetCount.setText(setCount + (setCount == 1 ? " set" : " sets"));

        boolean isCurrent = exercise.getExercise().equals(currentExerciseName);
        holder.itemView.setBackgroundColor(isCurrent
                ? ContextCompat.getColor(ct, R.color.row_highlight)
                : ContextCompat.getColor(ct, R.color.core_white));

        holder.dragHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && dragListener != null)
            {
                dragListener.onStartDrag(holder);
            }
            return false;
        });

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null)
            {
                clickListener.onExerciseClick(exercise.getExercise());
            }
        });
    }

    @Override
    public int getItemCount()
    {
        return exercises.size();
    }

    public class MyViewHolder extends RecyclerView.ViewHolder
    {
        TextView tvExerciseName;
        TextView tvSetCount;
        ImageView dragHandle;

        public MyViewHolder(@NonNull View itemView)
        {
            super(itemView);
            tvExerciseName = itemView.findViewById(R.id.tv_nav_panel_exercise_name);
            tvSetCount = itemView.findViewById(R.id.tv_nav_panel_exercise_set_count);
            dragHandle = itemView.findViewById(R.id.nav_panel_drag_handle);
        }
    }
}
