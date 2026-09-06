package com.example.verifit.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.verifit.R;
import com.example.verifit.RepRangeHistoryRow;
import com.example.verifit.model.WorkoutSet;

import java.util.ArrayList;

// Adapter de l'ecran "Historique des PR par nombre de reps" (retour Romain 06/09/2026).
// Deux types de lignes : en-tete de section (un nombre de reps) et entree d'historique
// (un record atteint a une date donnee pour ce nombre de reps). RepRangeRecordsActivity
// construit et trie la liste a plat avant de la passer ici.
public class RepRangeHistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
{
    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_ENTRY = 1;

    Context ct;
    ArrayList<RepRangeHistoryRow> rows;

    public RepRangeHistoryAdapter(Context ct, ArrayList<RepRangeHistoryRow> rows)
    {
        this.ct = ct;
        this.rows = rows;
    }

    @Override
    public int getItemViewType(int position)
    {
        return rows.get(position).isHeader() ? VIEW_TYPE_HEADER : VIEW_TYPE_ENTRY;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        LayoutInflater inflater = LayoutInflater.from(this.ct);

        if (viewType == VIEW_TYPE_HEADER)
        {
            View view = inflater.inflate(R.layout.rep_range_history_header_row, parent, false);
            return new HeaderViewHolder(view);
        }
        else
        {
            View view = inflater.inflate(R.layout.rep_range_history_entry_row, parent, false);
            return new EntryViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position)
    {
        RepRangeHistoryRow row = rows.get(position);

        if (holder instanceof HeaderViewHolder)
        {
            ((HeaderViewHolder) holder).label.setText(formatReps(row.getReps()) + " reps");
        }
        else if (holder instanceof EntryViewHolder)
        {
            EntryViewHolder entryHolder = (EntryViewHolder) holder;
            WorkoutSet set = row.getSet();

            entryHolder.weight.setText(formatWeight(set.getWeight()) + " kgs");
            entryHolder.date.setText(set.getDate());
            entryHolder.currentBadge.setVisibility(row.isCurrent() ? View.VISIBLE : View.GONE);
        }
    }

    private String formatReps(Double reps)
    {
        if (reps == Math.floor(reps))
        {
            return String.valueOf((int) Math.round(reps));
        }
        return String.valueOf(reps);
    }

    private String formatWeight(Double weight)
    {
        return String.format("%.1f", weight);
    }

    @Override
    public int getItemCount()
    {
        return rows.size();
    }

    public class HeaderViewHolder extends RecyclerView.ViewHolder
    {
        TextView label;

        public HeaderViewHolder(@NonNull View itemView)
        {
            super(itemView);
            label = (TextView) itemView;
        }
    }

    public class EntryViewHolder extends RecyclerView.ViewHolder
    {
        TextView weight;
        TextView date;
        TextView currentBadge;

        public EntryViewHolder(@NonNull View itemView)
        {
            super(itemView);
            weight = itemView.findViewById(R.id.tv_entry_weight);
            date = itemView.findViewById(R.id.tv_entry_date);
            currentBadge = itemView.findViewById(R.id.tv_entry_current_badge);
        }
    }
}
