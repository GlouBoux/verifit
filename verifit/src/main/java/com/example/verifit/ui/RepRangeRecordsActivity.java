package com.example.verifit.ui;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.verifit.R;
import com.example.verifit.RepRangeHistoryRow;
import com.example.verifit.adapters.RepRangeHistoryAdapter;
import com.example.verifit.model.WorkoutSet;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

// Ecran "Historique des PR par nombre de reps" (retour Romain 06/09/2026 : "Un PR
// c'est un record pour ce rep range (reps) pour ce poids (kgs) [...] Fitnotes garde un
// historique de PR pour chaque exercice"). Ouvert depuis le menu contextuel (long-press)
// d'une carte de PersonalRecordsActivity ("Historique par reps"). Lecture seule : pour
// chaque nombre de reps deja realise sur cet exercice, affiche le record actuel puis,
// en dessous, les records precedents qui ont ete battus - le tout calcule par
// DataStorage.calculateRepRangeHistory() (source de verite partagee avec le tag [PR]
// de l'export de seance).
public class RepRangeRecordsActivity extends AppCompatActivity
{
    public static final String EXTRA_EXERCISE_NAME = "exercise_name";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rep_range_records);

        String exerciseName = getIntent().getStringExtra(EXTRA_EXERCISE_NAME);
        if (exerciseName == null)
        {
            exerciseName = "";
        }

        setTitle(exerciseName);

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        View emptyState = findViewById(R.id.tv_rep_range_empty);

        ArrayList<RepRangeHistoryRow> rows = buildRows(exerciseName);

        if (rows.isEmpty())
        {
            recyclerView.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        }
        else
        {
            recyclerView.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);

            RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getApplicationContext());
            recyclerView.setLayoutManager(layoutManager);
            recyclerView.setItemAnimator(new DefaultItemAnimator());
            recyclerView.setAdapter(new RepRangeHistoryAdapter(this, rows));
        }
    }

    // Trie les nombres de reps du plus petit au plus grand (table de records "type
    // rep-max" classique), et pour chaque nombre de reps : le record actuel en
    // premier, puis les records precedents du plus recent au plus ancien.
    private ArrayList<RepRangeHistoryRow> buildRows(String exerciseName)
    {
        ArrayList<RepRangeHistoryRow> rows = new ArrayList<>();

        TreeMap<Double, ArrayList<WorkoutSet>> history =
                MainActivity.dataStorage.calculateRepRangeHistory(exerciseName);

        for (Map.Entry<Double, ArrayList<WorkoutSet>> entry : history.entrySet())
        {
            Double reps = entry.getKey();
            ArrayList<WorkoutSet> chronological = entry.getValue();

            rows.add(RepRangeHistoryRow.newHeader(reps));

            for (int i = chronological.size() - 1; i >= 0; i--)
            {
                boolean isCurrent = (i == chronological.size() - 1);
                rows.add(RepRangeHistoryRow.newEntry(reps, chronological.get(i), isCurrent));
            }
        }

        return rows;
    }
}
