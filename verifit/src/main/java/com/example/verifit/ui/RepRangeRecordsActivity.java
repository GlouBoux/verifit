package com.example.verifit.ui;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.verifit.R;
import com.example.verifit.RepRangeHistoryRow;
import com.example.verifit.RepRangePREvent;
import com.example.verifit.adapters.RepRangeHistoryAdapter;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

// Ecran "Historique des PR par nombre de reps" (retour Romain 06/09/2026 : "Un PR
// c'est un record pour ce rep range (reps) pour ce poids (kgs) [...] Fitnotes garde un
// historique de PR pour chaque exercice"). Ouvert soit depuis le menu contextuel
// (long-press) d'une carte de PersonalRecordsActivity, soit depuis l'icone trophee de
// la fiche de l'exercice (AddExerciseActivity). Une ligne par nombre de reps, triee du
// plus petit au plus grand (table type "rep-max"), montrant le record ACTUEL - grise
// s'il est deduit par transitivite (retour Romain : "20 kgs pour 8 reps est egalement
// un PR pour 7 reps s'il n'y a pas de valeur [...] les PR deduits sont
// grises/non mis en avant"). Le detail complet (record actuel + precedents, avec
// leur nombre de reps source et leur date) s'affiche dans une popup au clic sur une
// ligne - voir RepRangeHistoryAdapter. Tout est calcule par
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

    // Une ligne par nombre de reps, triee du plus petit au plus grand.
    private ArrayList<RepRangeHistoryRow> buildRows(String exerciseName)
    {
        ArrayList<RepRangeHistoryRow> rows = new ArrayList<>();

        TreeMap<Integer, ArrayList<RepRangePREvent>> history =
                MainActivity.dataStorage.calculateRepRangeHistory(exerciseName);

        for (Map.Entry<Integer, ArrayList<RepRangePREvent>> entry : history.entrySet())
        {
            rows.add(new RepRangeHistoryRow(entry.getKey(), entry.getValue()));
        }

        return rows;
    }
}
