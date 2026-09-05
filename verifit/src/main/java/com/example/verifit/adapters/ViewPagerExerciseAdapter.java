package com.example.verifit.adapters;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.verifit.R;
import com.example.verifit.model.WorkoutExercise;
import com.example.verifit.ui.AddExerciseActivity;
import com.example.verifit.ui.MainActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


// Adapter for WorkoutExercise Class
public class ViewPagerExerciseAdapter extends RecyclerView.Adapter<ViewPagerExerciseAdapter.MyViewHolder> {

    Context ct;
    ArrayList<WorkoutExercise> Exercises;

    // Multi-select delete + drag reorder (retour Romain 05/09/2026), même mécanique que
    // DayExerciseAdapter (écran DayActivity) - voir ce fichier pour le détail des choix.
    // Ici c'est l'onglet Workout (accueil), l'écran qu'on ouvre tous les jours.
    //
    // Suivi par NOM d'exercice plutôt que par position : la poignée de réorganisation
    // reste active pendant la sélection multiple, donc une sélection par position
    // deviendrait fausse dès qu'un glisser-déposer change l'ordre de la liste pendant
    // qu'une sélection est en cours.
    private boolean selectionMode = false;
    private final Set<String> selectedExerciseNames = new HashSet<>();
    private OnSelectionChangedListener selectionChangedListener;
    private OnStartDragListener dragListener;

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);
    }

    public interface OnStartDragListener {
        void onStartDrag(RecyclerView.ViewHolder viewHolder);
    }

    public ViewPagerExerciseAdapter(Context ct, ArrayList<WorkoutExercise> Exercises)
    {
        this.ct = ct;
        this.Exercises = new ArrayList<>(Exercises);
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener)
    {
        this.selectionChangedListener = listener;
    }

    public void setOnStartDragListener(OnStartDragListener listener)
    {
        this.dragListener = listener;
    }

    public void enterSelectionMode()
    {
        selectionMode = true;
        selectedExerciseNames.clear();
        notifyDataSetChanged();
    }

    public void exitSelectionMode()
    {
        selectionMode = false;
        selectedExerciseNames.clear();
        notifyDataSetChanged();
    }

    public boolean isSelectionMode()
    {
        return selectionMode;
    }

    public int getSelectedCount()
    {
        return selectedExerciseNames.size();
    }

    public List<String> getSelectedExerciseNames()
    {
        return new ArrayList<>(selectedExerciseNames);
    }

    private void toggleSelection(int position)
    {
        if (position < 0 || position >= Exercises.size())
        {
            return;
        }

        String exerciseName = Exercises.get(position).getExercise();
        if (selectedExerciseNames.contains(exerciseName))
        {
            selectedExerciseNames.remove(exerciseName);
        }
        else
        {
            selectedExerciseNames.add(exerciseName);
        }
        notifyItemChanged(position);

        if (selectionChangedListener != null)
        {
            selectionChangedListener.onSelectionChanged(selectedExerciseNames.size());
        }
    }

    // Appelé par l'ItemTouchHelper.Callback (ViewPagerWorkoutDayAdapter) à chaque étape
    // du drag - seulement la mise à jour visuelle/locale de la liste ; la persistance
    // dans WorkoutDay.moveExercise() se fait une seule fois, à la fin du geste.
    public void moveItem(int fromPosition, int toPosition)
    {
        WorkoutExercise moved = Exercises.remove(fromPosition);
        Exercises.add(toPosition, moved);
        notifyItemMoved(fromPosition, toPosition);
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        LayoutInflater inflater = LayoutInflater.from(this.ct);
        View view = inflater.inflate(R.layout.view_pager_exercise_row,parent,false);
        return new MyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position)
    {
        // Change TextView text
        holder.tv_exercise_name.setText(Exercises.get(position).getExercise());

        // Recycler View Stuff
        // Change RecyclerView items
        WorkoutSetAdapter workoutSetAdapter = new WorkoutSetAdapter(ct, Exercises.get(position).getSets());
        holder.recyclerView.setAdapter(workoutSetAdapter);
        holder.recyclerView.setLayoutManager(new LinearLayoutManager(ct));

        holder.checkbox.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        holder.imageView.setVisibility(selectionMode ? View.GONE : View.VISIBLE);
        holder.checkbox.setChecked(selectedExerciseNames.contains(Exercises.get(position).getExercise()));

        // Retour Romain 05/09/2026 : la poignée de réorganisation reste disponible
        // PENDANT la sélection multiple aussi (avant, elle disparaissait en sélection).
        holder.dragHandle.setVisibility(View.VISIBLE);

        // Retour Romain 05/09/2026 : replie les séries de chaque exercice pendant la
        // sélection multiple - plus facile de repérer/cocher plusieurs exercices ou de
        // les glisser-déposer sans le détail de chacun affiché. Revient à l'affichage
        // normal (déplié) une fois la sélection terminée.
        if (selectionMode)
        {
            holder.recyclerView.setVisibility(View.GONE);
            holder.blue_line.setVisibility(View.INVISIBLE);
        }
        else
        {
            holder.recyclerView.setVisibility(View.VISIBLE);
            holder.blue_line.setVisibility(View.VISIBLE);
        }

        // Navigate to AddActivity, sauf en mode sélection où le tap coche/décoche.
        holder.cardview_exercise2.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                if (selectionMode)
                {
                    toggleSelection(holder.getAdapterPosition());
                    return;
                }

                Intent in = new Intent(ct, AddExerciseActivity.class);
                in.putExtra("exercise",Exercises.get(holder.getAdapterPosition()).getExercise());
                MainActivity.dateSelected = Exercises.get(holder.getAdapterPosition()).getDate();
                ct.startActivity(in);
            }
        });

        holder.dragHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && dragListener != null)
            {
                dragListener.onStartDrag(holder);
            }
            return false;
        });

        // Change exercise color accordingly
        setCategoryIconTint(holder,Exercises.get(position).getExercise());


    }

    // Simple
    public void setCategoryIconTint(MyViewHolder holder, String exercise_name)
    {
        String exercise_category = MainActivity.dataStorage.getExerciseCategory(exercise_name);

        if(exercise_category.equals("Shoulders"))
        {
            holder.imageView.setColorFilter(Color.argb(255, 	0, 116, 189)); // Primary Color
        }
        else if(exercise_category.equals("Back"))
        {
            holder.imageView.setColorFilter(Color.argb(255, 40, 176, 192));
        }
        else if(exercise_category.equals("Chest"))
        {
            holder.imageView.setColorFilter(Color.argb(255, 	92, 88, 157));
        }
        else if(exercise_category.equals("Biceps"))
        {
            holder.imageView.setColorFilter(Color.argb(255, 	255, 50, 50));
        }
        else if(exercise_category.equals("Triceps"))
        {
            holder.imageView.setColorFilter(Color.argb(255,    204, 154, 0));
        }
        else if(exercise_category.equals("Legs"))
        {
            holder.imageView.setColorFilter(Color.argb(255, 	212, 	25, 97));
        }
        else if(exercise_category.equals("Abs"))
        {
            holder.imageView.setColorFilter(Color.argb(255, 	255, 153, 171));
        }
        else
        {
            holder.imageView.setColorFilter(Color.argb(255, 	52, 58, 64)); // Grey AF
        }
    }

    @Override
    public int getItemCount()
    {
        return this.Exercises.size();
    }

    public class MyViewHolder extends  RecyclerView.ViewHolder
    {
        TextView tv_exercise_name;
        RecyclerView recyclerView;
        View blue_line;
        CardView cardview_exercise2;
        ImageView imageView;
        CheckBox checkbox;
        ImageView dragHandle;

        public MyViewHolder(@NonNull View itemView) {
            super(itemView);
            tv_exercise_name = itemView.findViewById(R.id.tv_date);
            recyclerView = itemView.findViewById(R.id.recycler_view_day);
            blue_line = itemView.findViewById(R.id.blue_line);
            cardview_exercise2 = itemView.findViewById(R.id.cardview_exercise_history);
            imageView = itemView.findViewById(R.id.imageView3);
            checkbox = itemView.findViewById(R.id.exercise_checkbox);
            dragHandle = itemView.findViewById(R.id.drag_handle);
        }
    }
}
