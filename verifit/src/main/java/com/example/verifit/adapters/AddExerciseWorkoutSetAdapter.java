package com.example.verifit.adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
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

    // Badge "Personal Record" (Vague IHM, IHM-2, retour Romain 11/09/2026). Recalcule a
    // la demande via refreshPRSets() plutot qu'a chaque bind (calculateRepRangeHistory()
    // reparcourt tout l'historique de l'exercice - trop couteux pour tourner sur chaque
    // ligne a chaque scroll). Cet adapter est un champ statique persistant cote
    // AddExerciseActivity (contrairement a WorkoutSetAdapter, recree a chaque bind cote
    // DayExerciseAdapter/ViewPagerExerciseAdapter), donc pas de calcul dans le
    // constructeur ici : refreshPRSets() doit etre appelee explicitement (voir
    // AddExerciseActivity.initrecyclerView()/updateTodaysExercises()) a chaque fois que
    // Todays_Exercise_Sets change (nouvelle serie, edition, suppression).
    private Set<WorkoutSet> prSets = new HashSet<>();

    public void refreshPRSets(String exerciseName)
    {
        prSets = (exerciseName != null)
                ? MainActivity.dataStorage.getRepRangePRSets(exerciseName)
                : new HashSet<WorkoutSet>();
    }

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

    // Appelé par l'ItemTouchHelper.Callback (AddExerciseActivity) à chaque étape du
    // drag - seulement la mise à jour visuelle/locale de la liste ; la persistance
    // dans WorkoutDay.reorderSetsForExercise() se fait une seule fois, à la fin du
    // geste (retour Romain 06/09/2026, "utilitaire de réordonnement de série").
    public void moveItem(int fromPosition, int toPosition)
    {
        WorkoutSet moved = Workout_Sets.remove(fromPosition);
        Workout_Sets.add(toPosition, moved);
        notifyItemMoved(fromPosition, toPosition);
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

        // Badge "Personal Record" (retour Romain 11/09/2026) - meme logique de
        // detection que le tag "[PR]" de l'export texte (DataStorage.getRepRangePRSets(),
        // comparaison par reference puisque WorkoutSet ne redefinit pas equals()).
        holder.prBadge.setVisibility(
            prSets.contains(Workout_Sets.get(position)) ? View.VISIBLE : View.GONE
        );

        // Surbrillance de la ligne actuellement en edition (retour Romain 07/09/2026,
        // "je ne sais pas sur quelle ligne je me trouve et je dois regarder weight and
        // reps value pour le savoir") - meme condition que le retap ci-dessous : cette
        // position est celle chargee dans les champs du haut.
        //
        // Bug corrige (retour Romain, "la surbrillance ne reste qu'une fraction de
        // seconde") : cardview_set colore normalement via android:backgroundTint dans
        // workout_set_row.xml, pas via app:cardBackgroundColor - un premier essai
        // utilisait setCardBackgroundColor(), une API DIFFERENTE qui modifie le
        // remplissage interne de la CardView mais est ensuite recouverte/ecrasee par ce
        // backgroundTint statique des qu'Android reevalue l'etat du drawable (relachement
        // du tap, fin du ripple...) - d'ou le flash suivi d'un retour a la couleur
        // normale. Corrige en passant par setBackgroundTintList(), le meme mecanisme que
        // le XML, pour qu'il n'y ait plus deux systemes de coloration en concurrence -
        // via ViewCompat (pas View.setBackgroundTintList() directement, natif API 21
        // seulement) pour rester compatible avec le minSdk 16 du projet.
        boolean isBeingEdited = !selectionMode
                && AddExerciseActivity.isEditMode
                && AddExerciseActivity.Clicked_Set == position;
        ViewCompat.setBackgroundTintList(holder.cardView, ColorStateList.valueOf(ContextCompat.getColor(
                ct, isBeingEdited ? R.color.row_highlight : R.color.custom_row)));

        // Retour Romain 06/09/2026 ("comme sur FitNotes") : en dehors du mode selection,
        // un tap simple selectionne directement la serie pour edition - remplit les
        // champs du haut avec ses valeurs et fait passer le bouton du bas de "Save" a
        // "Update" (AddExerciseActivity.editSet(), jusqu'ici accessible uniquement via le
        // menu Editer du long-press). Avant ce changement, un tap appelait updateView()
        // qui pre-remplissait bien les champs mais SANS passer isEditMode a true : cliquer
        // "Save" ensuite creait une nouvelle serie en double au lieu de mettre a jour
        // celle-ci - d'ou l'impression que "le tap ne fait rien" d'utile.
        //
        // Retour Romain 07/09/2026 ("il faudrait une solution pour gerer les boutons et
        // qu'ils ne s'affichent pas / ne restent pas") : un retap sur la ligne DEJA
        // selectionnee desselectionne desormais (AddExerciseActivity.cancelEditSet()) -
        // plutot que d'obliger a passer par Update ou Delete pour sortir du mode
        // edition. Taper une AUTRE serie pendant qu'une edition est en cours bascule
        // directement dessus, sans etape de desselection intermediaire.
        holder.cardView.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view) {
                if (selectionMode)
                {
                    toggleSelection(holder.getAdapterPosition());
                }
                else if (AddExerciseActivity.isEditMode && AddExerciseActivity.Clicked_Set == holder.getAdapterPosition())
                {
                    AddExerciseActivity.cancelEditSet();
                }
                else
                {
                    AddExerciseActivity.editSet(holder, view, holder.getAdapterPosition());
                }
            }
        });


        // Retour Romain 06/09/2026 : la boite de dialogue Editer/Supprimer ouverte ici
        // jusqu'a present n'est plus pertinente - "Editer" fait desormais exactement ce
        // que fait le tap simple ci-dessus, et Romain propose de reutiliser le
        // long-press pour demarrer le reordonnement (drag & drop) d'une serie a la
        // place. En mode selection multiple, le long-press continue de (dé)selectionner
        // comme le tap - le drag y est desactive (voir isLongPressDragEnabled() cote
        // AddExerciseActivity) pour ne pas entrer en conflit avec la selection. Hors
        // selection, on ne consomme plus l'evenement ici : c'est l'ItemTouchHelper
        // (isLongPressDragEnabled() = true) qui demarre le drag nativement.
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

                return false;
            }
        });

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
        ImageView prBadge;

        public MyViewHolder(@NonNull View itemView)
        {
            super(itemView);

            tv_reps = itemView.findViewById(R.id.set_reps);
            tv_weight = itemView.findViewById(R.id.tv_date);
            cardView = itemView.findViewById(R.id.cardview_set);
            checkbox = itemView.findViewById(R.id.set_checkbox);
            commentIndicator = itemView.findViewById(R.id.set_comment_indicator);
            discrepancyBadge = itemView.findViewById(R.id.set_discrepancy_badge);
            prBadge = itemView.findViewById(R.id.set_pr_badge);
        }
    }
}
