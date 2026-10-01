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
import com.example.verifit.model.SupersetGroup;
import com.example.verifit.model.WorkoutDay;
import com.example.verifit.model.WorkoutExercise;
import com.example.verifit.ui.AddExerciseActivity;
import com.example.verifit.ui.MainActivity;

import java.util.ArrayList;
import java.util.List;


// Adapter for WorkoutExercise Class
public class ViewPagerExerciseAdapter extends RecyclerView.Adapter<ViewPagerExerciseAdapter.MyViewHolder> {

    Context ct;
    ArrayList<WorkoutExercise> Exercises;

    // Selection multiple et repli des cartes : voir ExerciseSelection (partage avec
    // l'autre adapter d'exercices).
    private final ExerciseSelection selection = new ExerciseSelection();
    private OnSelectionChangedListener selectionChangedListener;
    private OnStartDragListener dragListener;

    // WorkoutDay de cette page (Vague 2, retour Romain 07/09/2026) - uniquement pour
    // retrouver le groupe de superset de chaque exercice (barre coloree), meme role
    // que sur DayExerciseAdapter. Mis a jour a chaque bind (voir
    // ViewPagerWorkoutDayAdapter.onBindViewHolder()) puisque les ViewHolder sont
    // recycles en swipant entre les jours.
    private WorkoutDay day;

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

    // Voir le commentaire du champ "day" ci-dessus.
    public void setWorkoutDay(WorkoutDay day)
    {
        this.day = day;
        notifyDataSetChanged();
    }

    public void enterSelectionMode()
    {
        selection.enter();
        notifyDataSetChanged();
    }

    public void exitSelectionMode()
    {
        selection.exit();
        notifyDataSetChanged();
    }

    public boolean isSelectionMode()
    {
        return selection.isActive();
    }

    public void setDragging(boolean dragging)
    {
        if (!dragging)
        {
            // Fin du geste : volontairement rien, tout reste replie (voir ExerciseSelection).
            return;
        }
        selection.collapseAll(Exercises);
        notifyDataSetChanged();
    }

    private void toggleCollapse(int position)
    {
        if (position < 0 || position >= Exercises.size())
        {
            return;
        }
        selection.toggleCollapsed(Exercises.get(position).getExercise());
        notifyItemChanged(position);
    }

    public int getSelectedCount()
    {
        return selection.count();
    }

    // Dans l'ordre d'AFFICHAGE actuel (voir DayExerciseAdapter, meme correctif) - sert
    // desormais aussi d'ordre d'enchainement quand on cree un superset depuis cet
    // ecran (MainActivity.groupSelectedExercises()).
    public List<String> getSelectedExerciseNames()
    {
        return selection.selectedInOrder(Exercises);
    }

    private void toggleSelection(int position)
    {
        if (position < 0 || position >= Exercises.size())
        {
            return;
        }
        selection.toggle(Exercises.get(position).getExercise());
        notifyItemChanged(position);
        notifySelectionChanged();
    }

    // "All" (retour Romain 08/09/2026), meme mecanique que DayExerciseAdapter -
    // reclic alors que tout est deja selectionne -> desactive tout (retour Romain
    // 08/09/2026, bis).
    public void selectAll()
    {
        selection.selectAllOrNone(Exercises);
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    private void notifySelectionChanged()
    {
        if (selectionChangedListener != null)
        {
            selectionChangedListener.onSelectionChanged(selection.count());
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

        holder.checkbox.setVisibility(selection.isActive() ? View.VISIBLE : View.GONE);
        holder.imageView.setVisibility(selection.isActive() ? View.GONE : View.VISIBLE);
        holder.checkbox.setChecked(selection.isSelected(Exercises.get(position).getExercise()));

        // Retour Romain 05/09/2026 : la poignée de réorganisation reste disponible
        // PENDANT la sélection multiple aussi (avant, elle disparaissait en sélection).
        holder.dragHandle.setVisibility(View.VISIBLE);

        // Retour Romain 05/09/2026 : replie les séries de chaque exercice pendant la
        // sélection multiple (repli temporaire, revient à l'état individuel de chacun à
        // la sortie du mode sélection) et/ou si cette série précise a été repliée
        // manuellement ou par un glisser-déposer (repli persistant, voir
        // collapsedExerciseNames - se rouvre uniquement en tapant dessus).
        boolean collapsed = selection.isCollapsed(Exercises.get(position).getExercise());
        if (collapsed)
        {
            holder.recyclerView.setVisibility(View.GONE);
            holder.blue_line.setVisibility(View.INVISIBLE);
        }
        else
        {
            holder.recyclerView.setVisibility(View.VISIBLE);
            holder.blue_line.setVisibility(View.VISIBLE);
        }

        // Navigate to AddActivity, sauf en mode sélection où le tap coche/décoche, et
        // sauf si la série est repliée (retour Romain 05/09/2026) où le tap la déplie
        // d'abord plutôt que de naviguer directement - il faut pouvoir "cliquer pour la
        // rouvrir" après un repli par glisser-déposer.
        holder.cardview_exercise2.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                if (selection.isActive())
                {
                    toggleSelection(holder.getAdapterPosition());
                    return;
                }

                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition < 0 || adapterPosition >= Exercises.size())
                {
                    return;
                }

                String exerciseName = Exercises.get(adapterPosition).getExercise();
                if (selection.isCollapsed(exerciseName))
                {
                    toggleCollapse(adapterPosition);
                    return;
                }

                Intent in = new Intent(ct, AddExerciseActivity.class);
                in.putExtra("exercise", exerciseName);
                MainActivity.dateSelected = Exercises.get(adapterPosition).getDate();
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

        // Barre coloree de superset (Vague 2, retour Romain 07/09/2026).
        SupersetGroup group = (day != null) ? day.getSupersetGroupForExercise(Exercises.get(position).getExercise()) : null;
        if (group != null)
        {
            holder.supersetBar.setVisibility(View.VISIBLE);
            holder.supersetBar.setBackgroundColor(group.getColor());
        }
        else
        {
            holder.supersetBar.setVisibility(View.GONE);
        }
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
        View supersetBar;

        public MyViewHolder(@NonNull View itemView) {
            super(itemView);
            tv_exercise_name = itemView.findViewById(R.id.tv_date);
            recyclerView = itemView.findViewById(R.id.recycler_view_day);
            blue_line = itemView.findViewById(R.id.blue_line);
            cardview_exercise2 = itemView.findViewById(R.id.cardview_exercise_history);
            imageView = itemView.findViewById(R.id.imageView3);
            checkbox = itemView.findViewById(R.id.exercise_checkbox);
            dragHandle = itemView.findViewById(R.id.drag_handle);
            supersetBar = itemView.findViewById(R.id.v_superset_bar);
        }
    }
}
