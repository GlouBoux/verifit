package com.example.verifit.ui;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.verifit.R;
import com.example.verifit.adapters.DiscrepancyAdapter;
import com.example.verifit.model.WorkoutDay;
import com.example.verifit.model.WorkoutSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

// Ecran dedie "Ecarts Prevu/Realise" (retour Romain 06/09/2026, point 3 : en plus du
// badge discret pendant la seance, un historique complet pour analyse retrospective -
// potentiellement exploitable plus tard depuis workout_engine.py). Lecture seule :
// liste chaque serie importee dont le realise (reps/weight actuels) a fini par
// s'ecarter du prevu fige a l'import, toutes seances confondues, la plus recente en
// premier.
public class DiscrepancyHistoryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discrepancy_history);

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        View emptyState = findViewById(R.id.tv_discrepancy_empty);

        ArrayList<WorkoutSet> discrepancies = collectDiscrepancies();

        if (discrepancies.isEmpty())
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
            recyclerView.setAdapter(new DiscrepancyAdapter(this, discrepancies));
        }
    }

    // Parcourt tous les jours enregistres pour en extraire les series avec ecart -
    // dates au format "yyyy-MM-dd" (voir MainActivity), donc un simple tri de chaines
    // suffit pour un ordre chronologique correct.
    private ArrayList<WorkoutSet> collectDiscrepancies()
    {
        ArrayList<WorkoutSet> result = new ArrayList<>();

        for (WorkoutDay day : MainActivity.dataStorage.getWorkoutDays())
        {
            for (WorkoutSet set : day.getSets())
            {
                if (set.hasDiscrepancy())
                {
                    result.add(set);
                }
            }
        }

        Collections.sort(result, new Comparator<WorkoutSet>() {
            @Override
            public int compare(WorkoutSet a, WorkoutSet b) {
                return b.getDate().compareTo(a.getDate());
            }
        });

        return result;
    }
}
