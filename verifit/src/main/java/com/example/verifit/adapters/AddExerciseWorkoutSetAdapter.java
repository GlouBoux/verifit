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
import com.example.verifit.SetCommentSheet;
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
    //
    // Matching par CLE VALEUR (DataStorage.repRangePRKey()), pas par identite d'objet
    // Java - harmonisation avec WorkoutSetAdapter (retour Romain 17/09/2026 : trophee
    // disparu sur le resume du jour, cf. le commentaire detaille sur
    // DataStorage.getRepRangePRKeys()). Todays_Exercise_Sets est ici construit
    // directement depuis WorkoutDay.getSets() (voir
    // AddExerciseActivity.updateTodaysExercises()), la meme liste que celle lue par
    // calculateRepRangeHistory() - cet adapter n'etait donc pas touche par le bug
    // d'identite en pratique, mais matcher par valeur ici aussi evite toute
    // divergence de comportement future entre les deux adapters.
    private HashSet<String> prSetKeys = new HashSet<>();

    public void refreshPRSets(String exerciseName)
    {
        prSetKeys = (exerciseName != null)
                ? MainActivity.dataStorage.getRepRangePRKeys(exerciseName)
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

        // Retour Romain 18/09/2026 : le numero de serie affiche (tv_set_number,
        // calcule depuis la position) doit rester juste PENDANT le glisser-deposer,
        // pas seulement une fois le geste termine. notifyItemMoved() seul anime le
        // deplacement mais ne redeclenche PAS onBindViewHolder pour les lignes
        // intermediaires dont la position a change sans qu'elles soient elles-memes
        // la ligne deplacee (comportement standard RecyclerView) - sans ce
        // notifyItemRangeChanged, leur numero resterait affiche a l'ancienne valeur
        // jusqu'a un rebind sans rapport (save/update/delete plus tard, ou recyclage
        // en sortie/entree d'ecran).
        int rangeStart = Math.min(fromPosition, toPosition);
        int rangeCount = Math.abs(toPosition - fromPosition) + 1;
        notifyItemRangeChanged(rangeStart, rangeCount);
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

        // Numero de serie (retour Romain 18/09/2026, "pertinent si on en a beaucoup
        // d'affiche et qu'on ne sait plus combien de serie on a deja fait") - calcule
        // depuis la position AFFICHEE, jamais stocke : reste automatiquement correct
        // apres une suppression (notifyDataSetChanged, deja appele partout ailleurs
        // dans cet adapter) et pendant un glisser-deposer (voir moveItem() plus bas,
        // qui force desormais le rebind de la plage concernee pour la meme raison).
        holder.tv_set_number.setText(String.valueOf(position + 1));

        holder.checkbox.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        holder.checkbox.setChecked(selectedPositions.contains(position));

        // "Serie faite" (retour Romain 17/09/2026, voir
        // claude/fitnotes-feature-mark-sets-complete.md) : meme emplacement que la case
        // de selection multiple ci-dessus, donc masquee pendant qu'elle est affichee.
        // OnClickListener (pas OnCheckedChangeListener) : ce dernier se redeclencherait
        // a tort a chaque recyclage de vue lors du setChecked() programmatique
        // ci-dessous (bug classique RecyclerView), alors qu'un OnClickListener ne
        // reagit qu'a un vrai tap utilisateur.
        holder.completedCheckbox.setVisibility(selectionMode ? View.GONE : View.VISIBLE);
        holder.completedCheckbox.setChecked(Workout_Sets.get(position).isCompleted());
        holder.completedCheckbox.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION)
                {
                    return;
                }

                boolean isCompleted = holder.completedCheckbox.isChecked();
                Workout_Sets.get(adapterPosition).setCompleted(isCompleted);

                MainActivity.autoBackupRequired = true;
                com.example.verifit.SharedPreferences sharedPreferences = new com.example.verifit.SharedPreferences(ct);
                sharedPreferences.save("true", "autoBackupRequired");

                MainActivity.dataStorage.saveWorkoutData(ct);
            }
        });

        // Retour Romain 05/09/2026 : cet écran (AddExerciseActivity, atteint aussi
        // depuis l'onglet Sessions) n'affichait pas du tout l'icône de commentaire par
        // série, contrairement à l'onglet Workout (WorkoutSetAdapter, même layout
        // workout_set_row.xml donc même id set_comment_indicator) - or Romain en a
        // besoin ici aussi. Un tap dessus ouvre/édite le commentaire, même mécanique
        // que WorkoutSetAdapter.showSetCommentDialog().
        //
        // Retour Romain 21/09/2026 : deux etats (bleu = note perso, gris = plan du script
        // seul, voir SetCommentSheet.bindIndicator()), et l'icone est aussi affichee
        // (en gris) sur la serie EN COURS D'EDITION meme sans aucun commentaire : c'est
        // le point d'entree pour ajouter une note a une serie qui n'en a pas (avant, il
        // n'existait ici que "Comment" de la barre d'outils, qui ecrase le commentaire de
        // TOUTES les series de l'exercice). Le tap sur la ligne entre bien en mode edition
        // (editSet() -> notifyDataSetChanged()), ce qui rebind la ligne et fait apparaitre
        // l'icone.
        boolean editingThisSet = !selectionMode
                && AddExerciseActivity.isEditMode
                && AddExerciseActivity.Clicked_Set == position;
        SetCommentSheet.bindIndicator(ct, holder.commentIndicator, Workout_Sets.get(position), editingThisSet);
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
            isPersonalRecord(Workout_Sets.get(position)) ? View.VISIBLE : View.GONE
        );

        // Retour Romain 16/09/2026 : un tap sur le trophee doit ouvrir directement le
        // popup "Personal Record History" pour le nombre de reps exact de cette serie,
        // sans passer par l'ecran RepRangeRecordsActivity. Listener propre au badge
        // (independant du tap/long-press du reste de la carte ci-dessous) - jusqu'ici
        // le trophee n'avait aucun OnClickListener, donc le tap retombait sur celui de
        // la carte (selection/edition de la serie).
        holder.prBadge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPersonalRecordHistoryDialog(holder.getAdapterPosition());
            }
        });

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

    // Ouvre le panneau de commentaire d'une série précise (SetCommentSheet : bloc "Plan"
    // du script en lecture seule + champ multiligne "My note"). Avant le 21/09/2026 :
    // copie du dialogue de WorkoutSetAdapter (add_exercise_comment_dialog.xml), retour
    // Romain 05/09/2026 - les deux adapters partagent désormais le même helper.
    public void showSetCommentDialog(final int position)
    {
        if(position < 0 || position >= Workout_Sets.size())
        {
            return;
        }

        SetCommentSheet.show(ct, Workout_Sets.get(position), new SetCommentSheet.OnSavedListener() {
            @Override
            public void onSaved() {
                notifyItemChanged(position);
            }
        });
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

    @Override
    public int getItemCount()
    {
        return Workout_Sets.size();
    }

    public class MyViewHolder extends  RecyclerView.ViewHolder
    {
        TextView tv_reps;
        TextView tv_weight;
        TextView tv_set_number;
        CardView cardView;
        CheckBox checkbox;
        CheckBox completedCheckbox;
        ImageView commentIndicator;
        ImageView discrepancyBadge;
        ImageView prBadge;

        public MyViewHolder(@NonNull View itemView)
        {
            super(itemView);

            tv_reps = itemView.findViewById(R.id.set_reps);
            tv_weight = itemView.findViewById(R.id.tv_date);
            tv_set_number = itemView.findViewById(R.id.tv_set_number);
            cardView = itemView.findViewById(R.id.cardview_set);
            checkbox = itemView.findViewById(R.id.set_checkbox);
            completedCheckbox = itemView.findViewById(R.id.set_completed_checkbox);
            commentIndicator = itemView.findViewById(R.id.set_comment_indicator);
            discrepancyBadge = itemView.findViewById(R.id.set_discrepancy_badge);
            prBadge = itemView.findViewById(R.id.set_pr_badge);
        }
    }
}
