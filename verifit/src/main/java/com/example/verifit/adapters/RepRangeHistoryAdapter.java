package com.example.verifit.adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.verifit.R;
import com.example.verifit.RepRangeHistoryRow;
import com.example.verifit.RepRangePREvent;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;

// Adapter de l'ecran "Historique des PR par nombre de reps" (retour Romain
// 06/09/2026). Une ligne par nombre de reps (le record actuel, grise si deduit par
// transitivite - cf. RepRangePREvent.isDeduced()). Cliquer une ligne ouvre une popup
// "Personal Record History" (retour Romain, inspire d'un screenshot Fitnotes) avec le
// record actuel puis les records precedents (du plus recent au plus ancien), chacun
// affichant le nombre de reps de la serie source (peut differer de la case si deduit).
public class RepRangeHistoryAdapter extends RecyclerView.Adapter<RepRangeHistoryAdapter.MyViewHolder>
{
    Context ct;
    ArrayList<RepRangeHistoryRow> rows;

    public RepRangeHistoryAdapter(Context ct, ArrayList<RepRangeHistoryRow> rows)
    {
        this.ct = ct;
        this.rows = rows;
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        LayoutInflater inflater = LayoutInflater.from(this.ct);
        View view = inflater.inflate(R.layout.rep_range_history_entry_row, parent, false);
        return new MyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position)
    {
        RepRangeHistoryRow row = rows.get(position);
        bindEventRow(holder.reps, holder.weight, holder.date, row.getReps(), row.getCurrentEvent());

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
                showHistoryDialog(row);
            }
        });
    }

    // Peuple une ligne (liste principale OU popup de detail) pour un evenement donne.
    // displayReps est le nombre de reps de la CASE concernee (affiche en tete de
    // ligne, ex "5 RM") - peut differer de event.getSourceReps() quand l'evenement est
    // deduit (ex : une case "5 RM" dont le record vient d'une serie de 6 reps).
    private void bindEventRow(TextView repsView, TextView weightView, TextView dateView, int displayReps, RepRangePREvent event)
    {
        repsView.setText(displayReps + " RM");
        weightView.setText(formatWeight(event.getWeight()) + " kgs");
        dateView.setText(event.getDate());

        int color = event.isDeduced()
                ? ContextCompat.getColor(ct, R.color.core_grey_40)
                : ContextCompat.getColor(ct, R.color.core_black);

        repsView.setTextColor(color);
        weightView.setTextColor(color);
        dateView.setTextColor(color);
    }

    private void showHistoryDialog(RepRangeHistoryRow row)
    {
        LayoutInflater inflater = LayoutInflater.from(ct);
        View view = inflater.inflate(R.layout.rep_range_history_dialog, null);
        AlertDialog alertDialog = new AlertDialog.Builder(ct).setView(view).create();

        TextView title = view.findViewById(R.id.tv_pr_history_title);
        LinearLayout currentContainer = view.findViewById(R.id.container_current_record);
        LinearLayout previousContainer = view.findViewById(R.id.container_previous_records);
        TextView previousLabel = view.findViewById(R.id.tv_previous_records_label);
        MaterialButton closeButton = view.findViewById(R.id.bt_close_pr_history);

        title.setText(row.getReps() + " RM");

        ArrayList<RepRangePREvent> allEvents = row.getAllEvents();

        // Record actuel = le dernier evenement chronologique.
        View currentRow = inflater.inflate(R.layout.rep_range_history_entry_row, currentContainer, false);
        bindEventRow(currentRow.findViewById(R.id.tv_entry_reps), currentRow.findViewById(R.id.tv_entry_weight),
                currentRow.findViewById(R.id.tv_entry_date), row.getReps(), row.getCurrentEvent());
        currentContainer.addView(currentRow);

        // Records precedents, du plus recent au plus ancien (ordre chronologique inverse).
        if (allEvents.size() <= 1)
        {
            previousLabel.setVisibility(View.GONE);
            previousContainer.setVisibility(View.GONE);
        }
        else
        {
            for (int i = allEvents.size() - 2; i >= 0; i--)
            {
                // Retour Romain : les records precedents affichent le nombre de reps
                // REEL de la serie source (ex "6 RM"), pas la case en cours de
                // consultation (ex "5 RM") - c'est ce qui explique d'ou vient
                // chaque record dans l'historique, cf. le screenshot Fitnotes fourni.
                RepRangePREvent previousEvent = allEvents.get(i);
                View previousRow = inflater.inflate(R.layout.rep_range_history_entry_row, previousContainer, false);
                bindEventRow(previousRow.findViewById(R.id.tv_entry_reps), previousRow.findViewById(R.id.tv_entry_weight),
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

    private String formatWeight(Double weight)
    {
        return String.format("%.1f", weight);
    }

    @Override
    public int getItemCount()
    {
        return rows.size();
    }

    public class MyViewHolder extends RecyclerView.ViewHolder
    {
        TextView reps;
        TextView weight;
        TextView date;

        public MyViewHolder(@NonNull View itemView)
        {
            super(itemView);
            reps = itemView.findViewById(R.id.tv_entry_reps);
            weight = itemView.findViewById(R.id.tv_entry_weight);
            date = itemView.findViewById(R.id.tv_entry_date);
        }
    }
}
