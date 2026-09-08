package com.example.verifit.adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;


// Adapter for WorkoutExercise Class
public class DayExerciseAdapter extends RecyclerView.Adapter<DayExerciseAdapter.MyViewHolder> {

    Context ct;
    ArrayList<WorkoutExercise> Exercises;

    // Multi-select delete (retour Romain 05/09/2026), même mécanique que
    // AddExerciseWorkoutSetAdapter côté séries individuelles.
    //
    // Suivi par NOM d'exercice plutôt que par position (retour Romain 05/09/2026) :
    // depuis que la poignée de réorganisation reste active pendant la sélection
    // multiple (voir plus bas), une sélection par position deviendrait fausse dès qu'un
    // glisser-déposer change l'ordre de la liste pendant qu'une sélection est en cours.
    private boolean selectionMode = false;
    private final Set<String> selectedExerciseNames = new HashSet<>();
    private OnSelectionChangedListener selectionChangedListener;

    // Retour Romain 05/09/2026 : le repli des séries doit aussi se déclencher quand on
    // drague directement (poignée) SANS être passé par le bouton "Select" - avant, le
    // repli ne dépendait que de selectionMode, or la poignée reste utilisable même hors
    // sélection multiple.
    //
    // Retour Romain 05/09/2026 (bis) : un premier essai remettait tout en "déplié" dès
    // la fin du geste de drag (clearView) - Romain voyait donc le repli disparaître
    // tout seul après une seconde. Ce qu'il veut : ça replie au démarrage du drag, et ça
    // RESTE replié une fois le drag terminé, jusqu'à ce qu'il tape sur la série pour la
    // rouvrir manuellement - exactement le même mécanisme qu'un repli manuel. D'où
    // collapsedExerciseNames (suivi par NOM, comme la sélection) plutôt qu'un simple
    // booléen "dragging" : setDragging(true) replie (et mémorise) toutes les séries au
    // début du geste ; setDragging(false), à la fin du geste, ne les rouvre plus.
    private final Set<String> collapsedExerciseNames = new HashSet<>();

    // Drag & drop pour réordonner les exercices ("comme FitNotes", retour Romain
    // 05/09/2026) - la poignée de chaque ligne démarre le drag via ce listener,
    // l'ItemTouchHelper lui-même vit dans DayActivity (il a besoin d'accéder au
    // WorkoutDay pour persister l'ordre final).
    private OnStartDragListener dragListener;

    // WorkoutDay du jour affiche (Vague 2 du plan de migration, retour Romain
    // 07/09/2026) - uniquement pour retrouver le groupe de superset de chaque
    // exercice (barre coloree, voir onBindViewHolder()/setWorkoutDay()). Peut etre
    // null tant que DayActivity.initActivity() n'a pas encore trouve le WorkoutDay
    // correspondant (jour vide).
    private WorkoutDay day;

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);
    }

    public interface OnStartDragListener {
        void onStartDrag(RecyclerView.ViewHolder viewHolder);
    }

    public DayExerciseAdapter(Context ct, ArrayList<WorkoutExercise> Exercises)
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

    // Voir le commentaire du champ "day" ci-dessus. A appeler par DayActivity a
    // chaque (re)chargement de l'ecran (initActivity()).
    public void setWorkoutDay(WorkoutDay day)
    {
        this.day = day;
        notifyDataSetChanged();
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

    public void setDragging(boolean dragging)
    {
        if (!dragging)
        {
            // Fin du geste : volontairement un no-op, voir le commentaire sur
            // collapsedExerciseNames plus haut - tout reste replié.
            return;
        }

        for (WorkoutExercise exercise : Exercises)
        {
            collapsedExerciseNames.add(exercise.getExercise());
        }
        notifyDataSetChanged();
    }

    private void toggleCollapse(int position)
    {
        if (position < 0 || position >= Exercises.size())
        {
            return;
        }

        String exerciseName = Exercises.get(position).getExercise();
        if (collapsedExerciseNames.contains(exerciseName))
        {
            collapsedExerciseNames.remove(exerciseName);
        }
        else
        {
            collapsedExerciseNames.add(exerciseName);
        }
        notifyItemChanged(position);
    }

    public int getSelectedCount()
    {
        return selectedExerciseNames.size();
    }

    // Retour Romain implicite (Vague 2, 07/09/2026) : dans l'ordre d'AFFICHAGE
    // actuel (celui de Exercises, deja pilote par WorkoutDay.ExerciseOrder), pas
    // l'ordre d'iteration non garanti du HashSet interne - important pour
    // DayActivity.groupSelectedExercises(), qui utilise cet ordre comme ordre
    // d'enchainement du superset cree. Ne change rien pour le seul autre appelant
    // (suppression groupee), qui ne se soucie pas de l'ordre.
    public List<String> getSelectedExerciseNames()
    {
        List<String> ordered = new ArrayList<>();
        for (WorkoutExercise exercise : Exercises)
        {
            if (selectedExerciseNames.contains(exercise.getExercise()))
            {
                ordered.add(exercise.getExercise());
            }
        }
        return ordered;
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

    // "All" (retour Romain 08/09/2026) : sélectionne d'un coup tous les exercices
    // actuellement affichés, sans avoir à cocher chaque case une par une - utile
    // avant un Group ou une suppression groupée qui couvre toute la séance.
    // Retour Romain 08/09/2026 (bis) : un reclic alors que tout est déjà sélectionné
    // désélectionne tout d'un coup plutôt que de ne rien faire - comportement "case à
    // cocher" classique (tout / rien) plutôt qu'une action à sens unique.
    public void selectAll()
    {
        if (!Exercises.isEmpty() && selectedExerciseNames.size() == Exercises.size())
        {
            selectedExerciseNames.clear();
        }
        else
        {
            for (WorkoutExercise exercise : Exercises)
            {
                selectedExerciseNames.add(exercise.getExercise());
            }
        }
        notifyDataSetChanged();

        if (selectionChangedListener != null)
        {
            selectionChangedListener.onSelectionChanged(selectedExerciseNames.size());
        }
    }

    // Appelé par l'ItemTouchHelper.Callback (DayActivity) à chaque étape du drag -
    // seulement la mise à jour visuelle/locale de la liste ; la persistance dans
    // WorkoutDay.moveExercise() se fait une seule fois, à la fin du geste.
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
        View view = inflater.inflate(R.layout.day_exercise_row,parent,false);
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
        // PENDANT la sélection multiple aussi (avant, elle disparaissait en sélection -
        // ça empêchait de réordonner et sélectionner dans la même passe, "mode reorg" et
        // "mode sélection" ne faisant qu'un pour Romain, comme dans FitNotes).
        holder.dragHandle.setVisibility(View.VISIBLE);

        // Retour Romain 05/09/2026 : replie les séries de CHAQUE exercice pendant la
        // sélection multiple (repli temporaire, revient à l'état individuel de chacun à
        // la sortie du mode sélection) et/ou si cette série précise a été repliée
        // manuellement ou par un glisser-déposer (repli persistant, voir
        // collapsedExerciseNames - se rouvre uniquement en tapant dessus).
        boolean collapsed = selectionMode || collapsedExerciseNames.contains(Exercises.get(position).getExercise());
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

        holder.editButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                if (!selectionMode)
                {
                    startIntent(holder.getAdapterPosition());
                }
            }
        });

        holder.dragHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && dragListener != null)
            {
                dragListener.onStartDrag(holder);
            }
            return false;
        });

        // Colorize exercise icon accordingly
        setCategoryIconTint(holder,Exercises.get(position).getExercise());

        // Barre coloree de superset (Vague 2 du plan de migration, retour Romain
        // 07/09/2026) - voir le champ "day" plus haut.
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

    // Go to Add Exercise
    public void startIntent(int position)
    {
        Intent in = new Intent(ct, AddExerciseActivity.class);
        in.putExtra("exercise",Exercises.get(position).getExercise());
        ct.startActivity(in);
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
        ImageButton editButton;
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
            editButton = itemView.findViewById(R.id.editButton);
            blue_line = itemView.findViewById(R.id.blue_line);
            cardview_exercise2 = itemView.findViewById(R.id.cardview_exercise_history);
            imageView = itemView.findViewById(R.id.imageView2);
            checkbox = itemView.findViewById(R.id.exercise_checkbox);
            dragHandle = itemView.findViewById(R.id.drag_handle);
            supersetBar = itemView.findViewById(R.id.v_superset_bar);

                // Bascule la sélection en mode sélection multiple, sinon replie/déplie
                // cette série précise (voir toggleCollapse/collapsedExerciseNames).
                cardview_exercise2.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (selectionMode)
                    {
                        toggleSelection(getAdapterPosition());
                        return;
                    }

                    toggleCollapse(getAdapterPosition());
                }
            });

        }
    }
}
