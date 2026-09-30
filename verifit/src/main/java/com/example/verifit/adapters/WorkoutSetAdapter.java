package com.example.verifit.adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.verifit.R;
import com.example.verifit.SetCommentSheet;
import com.example.verifit.SetDialogs;
import com.example.verifit.model.WorkoutSet;
import com.example.verifit.ui.MainActivity;

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

        // Numero de serie (retour Romain 18/09/2026, "pertinent si on en a beaucoup
        // d'affiche et qu'on ne sait plus combien de serie on a deja fait") - calcule
        // depuis la position AFFICHEE. Pas de souci de rafraichissement ici : cet
        // adapter est recree entierement (new WorkoutSetAdapter(...)) a chaque bind
        // cote DayExerciseAdapter, donc toujours a jour.
        holder.tv_set_number.setText(String.valueOf(position + 1));

        // Small indicator so a set with a comment is visible at a glance, without
        // having to open it - useful for reviewing imported data too. Deux etats
        // (retour Romain 21/09/2026) : bleu = une note perso, gris = plan du script
        // seul - voir SetCommentSheet.bindIndicator().
        SetCommentSheet.bindIndicator(ct, holder.commentIndicator, Workout_Sets.get(position), false);

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
                SetDialogs.showDiscrepancy(ct, SetDialogs.setAt(Workout_Sets, holder.getAdapterPosition()));
            }
        });

        // Badge "Personal Record" (retour Romain 11/09/2026) - meme logique de
        // detection que le tag "[PR]" du Share (DataStorage.isRepRangePR(), cles
        // "date#reps#poids" de getRepRangePRKeys()).
        holder.prBadge.setVisibility(
            com.example.verifit.DataStorage.isRepRangePR(prSetKeys, Workout_Sets.get(position)) ? View.VISIBLE : View.GONE
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
                SetDialogs.showPersonalRecordHistory(ct, MainActivity.dataStorage, SetDialogs.setAt(Workout_Sets, holder.getAdapterPosition()));
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

    // Opens the comment panel of one specific set (retour Romain 21/09/2026 : le petit
    // dialogue a une seule ligne qui defilait est remplace par SetCommentSheet - bloc
    // "Plan" du script en lecture seule + champ multiligne "My note"). Le nom de la
    // methode est conserve : c'est le meme point d'entree (tap sur la carte).
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
        TextView tv_set_number;
        CardView cardView;
        ImageView commentIndicator;
        ImageView discrepancyBadge;
        ImageView prBadge;


        public MyViewHolder(@NonNull View itemView) {
            super(itemView);

            tv_reps = itemView.findViewById(R.id.set_reps);
            tv_weight = itemView.findViewById(R.id.tv_date);
            tv_set_number = itemView.findViewById(R.id.tv_set_number);
            cardView = itemView.findViewById(R.id.cardview_set);
            commentIndicator = itemView.findViewById(R.id.set_comment_indicator);
            discrepancyBadge = itemView.findViewById(R.id.set_discrepancy_badge);
            prBadge = itemView.findViewById(R.id.set_pr_badge);

        }
    }
}
