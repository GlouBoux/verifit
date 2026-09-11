package com.example.verifit.ui;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.verifit.R;
import com.example.verifit.SnackBarWithMessage;
import com.example.verifit.adapters.GoalsAdapter;
import com.example.verifit.model.Goal;

import java.util.Iterator;

// "Goals" (Vague 4 du plan de migration, item 14, retour Romain 08/09/2026 : "passe
// a la vague 4") - liste des objectifs (une carte par objectif via GoalsAdapter, voir
// ce fichier pour le detail de la barre de progression), acces via le nouveau menu
// "Goals" de PersonalRecordsActivity. Fonctionnalite entierement nouvelle, aucun
// socle existant identifie dans fitnotes-fork-todo.md.
public class GoalsActivity extends AppCompatActivity implements GoalsAdapter.OnGoalClickListener, GoalDialog.OnGoalDialogResultListener
{
    private RecyclerView recyclerView;
    private GoalsAdapter goalsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_goals);

        MainActivity.dataStorage.loadGoalsData(getApplicationContext());

        recyclerView = findViewById(R.id.recycler_view_goals);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        refreshAdapter();
    }

    @Override
    protected void onStop()
    {
        super.onStop();
        // Filet de securite (meme motif que PersonalRecordsActivity.onStop()) - les
        // mutations sont deja sauvegardees immediatement dans onGoalSaved()/
        // onGoalDeleted() ci-dessous, mais un onStop() coherent avec le reste de
        // l'app ne coute rien.
        MainActivity.dataStorage.saveGoalsData(this);
    }

    private void refreshAdapter()
    {
        goalsAdapter = new GoalsAdapter(this, MainActivity.dataStorage.getGoals(), this);
        recyclerView.setAdapter(goalsAdapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.goals_activity_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item)
    {
        if (item.getItemId() == R.id.add_goal)
        {
            new GoalDialog(this, null, this).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onGoalClicked(Goal goal)
    {
        new GoalDialog(this, goal, this).show();
    }

    @Override
    public void onGoalSaved(Goal goal)
    {
        // Remplace l'objectif existant (meme id, cas edition) ou ajoute un nouvel
        // objectif (cas creation) - GoalDialog attribue deja le bon id dans les deux
        // cas (voir GoalDialog.saveButton.setOnClickListener()).
        boolean replaced = false;
        for (int i = 0; i < MainActivity.dataStorage.getGoals().size(); i++)
        {
            if (MainActivity.dataStorage.getGoals().get(i).getId() == goal.getId())
            {
                MainActivity.dataStorage.getGoals().set(i, goal);
                replaced = true;
                break;
            }
        }
        if (!replaced)
        {
            MainActivity.dataStorage.getGoals().add(goal);
        }

        MainActivity.dataStorage.saveGoalsData(this);
        goalsAdapter.notifyDataSetChanged();

        SnackBarWithMessage snackBarWithMessage = new SnackBarWithMessage(this);
        snackBarWithMessage.showSnackbar("Goal saved");
    }

    @Override
    public void onGoalDeleted(Goal goal)
    {
        Iterator<Goal> iterator = MainActivity.dataStorage.getGoals().iterator();
        while (iterator.hasNext())
        {
            if (iterator.next().getId() == goal.getId())
            {
                iterator.remove();
                break;
            }
        }

        MainActivity.dataStorage.saveGoalsData(this);
        goalsAdapter.notifyDataSetChanged();

        SnackBarWithMessage snackBarWithMessage = new SnackBarWithMessage(this);
        snackBarWithMessage.showSnackbar("Goal deleted");
    }
}
