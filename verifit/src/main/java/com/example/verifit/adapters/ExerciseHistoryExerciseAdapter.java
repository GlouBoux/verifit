package com.example.verifit.adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.verifit.DataStorage;
import com.example.verifit.R;
import com.example.verifit.SetCommentSheet;
import com.example.verifit.SetDialogs;
import com.example.verifit.model.WorkoutExercise;
import com.example.verifit.model.WorkoutSet;
import com.example.verifit.ui.MainActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;

/**
 * Historique d'un exercice (menu "Exercise History"), refonte du 06/10/2026
 * (retour Romain : "l'onglet history n'est pas aligne, ce n'est pas propre. Fais comme
 * FitNotes [...] scroll infini c'est mieux, aligne etc").
 *
 * Avant : une carte par jour contenant un RecyclerView de WorkoutSetAdapter (la ligne de
 * l'ecran Workout, aux colonnes absolues de 194dp + case a cocher), donc trop large pour
 * le dialogue : "reps" passait a la ligne et les colonnes se decalaient.
 *
 * Maintenant : UNE SEULE liste a plat, deux types de lignes (en-tete de date a la
 * FitNotes + serie, layouts dedies history_date_header_row / history_set_row aux colonnes
 * alignees), et un chargement PAR PAGES de PAGE_SIZE jours : on ajoute la page suivante
 * quand l'utilisateur approche de la fin de la liste (scroll infini). Les donnees sont
 * deja en memoire (DataStorage) : la pagination ne limite que le nombre de vues/lignes
 * construites, pour que l'ouverture reste instantanee meme avec des centaines de seances.
 *
 * Les gestes sont ceux de la ligne Workout : tap = commentaire de la serie, appui long =
 * stats de la serie, tap sur le trophee = historique du record, tap sur le badge d'ecart =
 * prevu/realise ; tap sur la date = stats du jour.
 */
public class ExerciseHistoryExerciseAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_SET = 1;

    // Nombre de jours ajoutes a chaque page.
    private static final int PAGE_SIZE = 15;
    // On charge la page suivante quand la derniere ligne visible est a moins de ce nombre
    // de lignes de la fin de la liste deja construite.
    private static final int PRELOAD_THRESHOLD = 20;

    private final Context ct;
    private final ArrayList<WorkoutExercise> Exercises;

    // Liste a plat : HeaderRow / SetRow, dans l'ordre d'affichage (jour le plus recent
    // d'abord, comme AddExerciseActivity.setupExerciseHistory() la fournit).
    private final ArrayList<Object> rows = new ArrayList<>();
    private int loadedDays = 0;
    private boolean loading = false;

    // Cles des series qui sont un record reel (meme logique que WorkoutSetAdapter) -
    // calculees une seule fois : toutes les entrees portent le meme exercice.
    private final HashSet<String> prSetKeys;
    private final int currentYear;

    private static final class HeaderRow {
        final int exerciseIndex;

        HeaderRow(int exerciseIndex) {
            this.exerciseIndex = exerciseIndex;
        }
    }

    private static final class SetRow {
        final int exerciseIndex;
        final int setIndex;

        SetRow(int exerciseIndex, int setIndex) {
            this.exerciseIndex = exerciseIndex;
            this.setIndex = setIndex;
        }
    }

    public ExerciseHistoryExerciseAdapter(Context ct, ArrayList<WorkoutExercise> Exercises)
    {
        this.ct = ct;
        this.Exercises = new ArrayList<>(Exercises);
        this.currentYear = Calendar.getInstance().get(Calendar.YEAR);

        String name = this.Exercises.isEmpty() ? null : this.Exercises.get(0).getExercise();
        this.prSetKeys = (name != null)
                ? MainActivity.dataStorage.getRepRangePRKeys(name)
                : new HashSet<String>();

        appendNextPage();
    }

    // Construit les lignes des PAGE_SIZE prochains jours. Retourne le nombre de lignes
    // ajoutees (a passer a notifyItemRangeInserted par l'appelant).
    private int appendNextPage()
    {
        int before = rows.size();
        int end = Math.min(loadedDays + PAGE_SIZE, Exercises.size());

        for (int i = loadedDays; i < end; i++)
        {
            rows.add(new HeaderRow(i));
            ArrayList<WorkoutSet> sets = Exercises.get(i).getSets();
            int n = (sets == null) ? 0 : sets.size();
            for (int s = 0; s < n; s++)
            {
                rows.add(new SetRow(i, s));
            }
        }

        loadedDays = end;
        return rows.size() - before;
    }

    private boolean hasMore()
    {
        return loadedDays < Exercises.size();
    }

    // Scroll infini : surveille le defilement et ajoute la page suivante a l'approche de la
    // fin. Le meme controle est relance apres chaque ajout (et au premier affichage), au
    // cas ou la fenetre serait assez haute pour que la premiere page ne la remplisse pas.
    @Override
    public void onAttachedToRecyclerView(@NonNull final RecyclerView recyclerView)
    {
        super.onAttachedToRecyclerView(recyclerView);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                loadMoreIfNeeded(rv);
            }
        });

        recyclerView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                loadMoreIfNeeded(recyclerView);
            }
        });
    }

    private void loadMoreIfNeeded(final RecyclerView rv)
    {
        if (loading || !hasMore())
        {
            return;
        }

        RecyclerView.LayoutManager lm = rv.getLayoutManager();
        if (!(lm instanceof LinearLayoutManager))
        {
            return;
        }

        int last = ((LinearLayoutManager) lm).findLastVisibleItemPosition();
        if (last == RecyclerView.NO_POSITION || last < getItemCount() - PRELOAD_THRESHOLD)
        {
            return;
        }

        loading = true;

        // notifyItemRangeInserted ne doit pas etre appele pendant un scroll/layout en
        // cours : on le differe d'un tour de boucle.
        rv.post(new Runnable() {
            @Override
            public void run() {
                int start = rows.size();
                int added = appendNextPage();
                if (added > 0)
                {
                    notifyItemRangeInserted(start, added);
                }
                loading = false;

                // Relance apres le prochain layout (la page vient peut-etre de ne pas
                // suffire a remplir la fenetre).
                rv.post(new Runnable() {
                    @Override
                    public void run() {
                        loadMoreIfNeeded(rv);
                    }
                });
            }
        });
    }

    @Override
    public int getItemViewType(int position)
    {
        return (rows.get(position) instanceof HeaderRow) ? TYPE_HEADER : TYPE_SET;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        LayoutInflater inflater = LayoutInflater.from(this.ct);

        if (viewType == TYPE_HEADER)
        {
            final HeaderHolder holder = new HeaderHolder(
                    inflater.inflate(R.layout.history_date_header_row, parent, false));

            holder.root.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int pos = holder.getAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return;
                    Object row = rows.get(pos);
                    if (row instanceof HeaderRow)
                    {
                        showExerciseHistoryStatsDialog(((HeaderRow) row).exerciseIndex);
                    }
                }
            });

            return holder;
        }

        final SetHolder holder = new SetHolder(
                inflater.inflate(R.layout.history_set_row, parent, false));

        // Tap : commentaire / plan de cette serie.
        holder.root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final int pos = holder.getAdapterPosition();
                WorkoutSet set = setAt(pos);
                if (set == null) return;

                SetCommentSheet.show(ct, set, new SetCommentSheet.OnSavedListener() {
                    @Override
                    public void onSaved() {
                        notifyItemChanged(pos);
                    }
                });
            }
        });

        // Appui long : stats de la serie (meme dialogue que sur l'ecran Workout).
        holder.root.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                int pos = holder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return false;
                Object row = rows.get(pos);
                if (!(row instanceof SetRow)) return false;

                SetRow setRow = (SetRow) row;
                ArrayList<WorkoutSet> sets = Exercises.get(setRow.exerciseIndex).getSets();
                new WorkoutSetAdapter(ct, sets).showSetDialog(setRow.setIndex);
                return true;
            }
        });

        // Trophee : historique du record pour ce nombre de reps.
        holder.prBadge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                WorkoutSet set = setAt(holder.getAdapterPosition());
                if (set == null) return;
                SetDialogs.showPersonalRecordHistory(ct, MainActivity.dataStorage, set);
            }
        });

        // Badge d'ecart : detail prevu/realise.
        holder.discrepancyBadge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                WorkoutSet set = setAt(holder.getAdapterPosition());
                if (set == null) return;
                SetDialogs.showDiscrepancy(ct, set);
            }
        });

        return holder;
    }

    // Serie correspondant a une position d'adaptateur (null si ce n'est pas une ligne de
    // serie ou si la position est invalide).
    private WorkoutSet setAt(int adapterPosition)
    {
        if (adapterPosition < 0 || adapterPosition >= rows.size()) return null;
        Object row = rows.get(adapterPosition);
        if (!(row instanceof SetRow)) return null;

        SetRow setRow = (SetRow) row;
        ArrayList<WorkoutSet> sets = Exercises.get(setRow.exerciseIndex).getSets();
        if (sets == null || setRow.setIndex >= sets.size()) return null;
        return sets.get(setRow.setIndex);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position)
    {
        Object row = rows.get(position);

        if (row instanceof HeaderRow)
        {
            HeaderHolder holder = (HeaderHolder) viewHolder;
            holder.date.setText(formatDate(Exercises.get(((HeaderRow) row).exerciseIndex).getDate()));
            return;
        }

        SetRow setRow = (SetRow) row;
        SetHolder holder = (SetHolder) viewHolder;
        WorkoutSet set = Exercises.get(setRow.exerciseIndex).getSets().get(setRow.setIndex);

        holder.weight.setText(set.getWeight().toString());
        holder.reps.setText(String.valueOf((int) Math.round(set.getReps())));
        holder.setNumber.setText(String.valueOf(setRow.setIndex + 1));

        SetCommentSheet.bindIndicator(ct, holder.commentIcon, set, false);

        holder.discrepancyBadge.setVisibility(set.hasDiscrepancy() ? View.VISIBLE : View.GONE);
        holder.prBadge.setVisibility(DataStorage.isRepRangePR(prSetKeys, set) ? View.VISIBLE : View.GONE);
    }

    // "LUNDI, SEPTEMBRE 14" (la mise en majuscules est faite par le layout). L'annee n'est
    // ajoutee que pour les seances d'une annee passee, sinon l'historique long devient
    // ambigu.
    private String formatDate(String raw)
    {
        try
        {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(raw);
            Calendar c = Calendar.getInstance();
            c.setTime(d);
            String pattern = (c.get(Calendar.YEAR) == currentYear) ? "EEEE, MMMM d" : "EEEE, MMMM d, yyyy";
            return new SimpleDateFormat(pattern, Locale.getDefault()).format(d);
        }
        catch (Exception e)
        {
            return raw == null ? "" : raw;
        }
    }

    // Blatant copy of Fitnotes but ohh well ;)
    public void showExerciseHistoryStatsDialog(int position) {

        // Prepare to show exercise dialog box
        LayoutInflater inflater = LayoutInflater.from(ct);
        View view = inflater.inflate(R.layout.exercise_history_stats_dialog,null);
        AlertDialog alertDialog = new AlertDialog.Builder(ct).setView(view).create();

        // Get TextViews
        TextView totalsets = view.findViewById(R.id.volume);
        TextView totalreps = view.findViewById(R.id.totalreps);
        TextView totalvolume = view.findViewById(R.id.totalvolume);
        TextView maxweight = view.findViewById(R.id.maxweight);
        TextView maxreps = view.findViewById(R.id.maxreps);
        TextView maxsetvolume = view.findViewById(R.id.maxsetvolume);
        TextView name = view.findViewById(R.id.tv_date);
        TextView onerepmax = view.findViewById(R.id.onerepmax);

        // Set Values

        // Double -> Integer
        int sets = (int)Math.round(Exercises.get(position).getTotalSets());
        int reps = (int)Math.round(Exercises.get(position).getTotalReps());
        int max_reps = (int)Math.round(Exercises.get(position).getMaxReps());

        totalsets.setText(String.valueOf(sets));
        totalreps.setText(String.valueOf(reps));
        maxreps.setText(String.valueOf(max_reps));


        totalvolume.setText(Exercises.get(position).getVolume().toString());
        maxweight.setText(Exercises.get(position).getMaxWeight().toString());
        onerepmax.setText(Exercises.get(position).getEstimatedOneRepMax().toString());
        name.setText(Exercises.get(position).getExercise());
        maxsetvolume.setText(Exercises.get(position).getMaxSetVolume().toString());

        // Show Exercise Dialog Box
        alertDialog.show();

    }

    @Override
    public int getItemCount()
    {
        return rows.size();
    }

    private static class HeaderHolder extends RecyclerView.ViewHolder
    {
        final View root;
        final TextView date;

        HeaderHolder(@NonNull View itemView)
        {
            super(itemView);
            root = itemView.findViewById(R.id.hist_header_root);
            date = itemView.findViewById(R.id.hist_date);
        }
    }

    private static class SetHolder extends RecyclerView.ViewHolder
    {
        final View root;
        final ImageView commentIcon;
        final ImageView discrepancyBadge;
        final ImageView prBadge;
        final TextView setNumber;
        final TextView weight;
        final TextView reps;

        SetHolder(@NonNull View itemView)
        {
            super(itemView);
            root = itemView.findViewById(R.id.hist_set_root);
            commentIcon = itemView.findViewById(R.id.hist_comment_icon);
            discrepancyBadge = itemView.findViewById(R.id.hist_discrepancy_badge);
            prBadge = itemView.findViewById(R.id.hist_pr_badge);
            setNumber = itemView.findViewById(R.id.hist_set_number);
            weight = itemView.findViewById(R.id.hist_weight);
            reps = itemView.findViewById(R.id.hist_reps);
        }
    }
}
