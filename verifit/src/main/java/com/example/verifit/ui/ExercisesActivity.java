package com.example.verifit.ui;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;

import com.example.verifit.adapters.ExerciseAdapter;
import com.example.verifit.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class ExercisesActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener{

    public RecyclerView recyclerView;
    public ExerciseAdapter exerciseAdapter;
    public String date_clicked;

    // "Show Exercise Details" (Vague 1 du plan de migration, feature FitNotes) - meme
    // SharedPreferences que le reste de l'app, cle dediee. Voir onCreateOptionsMenu()/
    // onOptionsItemSelected() et ExerciseAdapter.setShowDetails().
    private static final String SHOW_EXERCISE_DETAILS_PREF_KEY = "show_exercise_details";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exercises);

        onCreateStuff();

    }

    @Override
    protected void onRestart()
    {
        super.onRestart();
        onCreateStuff();
    }

    public void onCreateStuff()
    {
        // Intent from DayActivity
        Intent in = getIntent();
        date_clicked = in.getStringExtra("date");


        // Bottom Navigation Bar Intents
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation_view);
        bottomNavigationView.setSelectedItemId(R.id.exercises);
        bottomNavigationView.setOnNavigationItemSelectedListener(this);


        // Find Recycler View Object
        recyclerView = findViewById(R.id.recycler_view_exercises);
        exerciseAdapter = new ExerciseAdapter(this, MainActivity.dataStorage.getKnownExercises());
        exerciseAdapter.setShowDetails(isShowExerciseDetailsEnabled());
        recyclerView.setAdapter(exerciseAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    // "Show Exercise Details" (Vague 1) - voir SHOW_EXERCISE_DETAILS_PREF_KEY. Masque
    // par defaut.
    private boolean isShowExerciseDetailsEnabled()
    {
        android.content.SharedPreferences sharedPreferences =
                getSharedPreferences("shared preferences", MODE_PRIVATE);
        return sharedPreferences.getBoolean(SHOW_EXERCISE_DETAILS_PREF_KEY, false);
    }

    private void setShowExerciseDetailsEnabled(boolean enabled)
    {
        android.content.SharedPreferences sharedPreferences =
                getSharedPreferences("shared preferences", MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(SHOW_EXERCISE_DETAILS_PREF_KEY, enabled);
        editor.apply();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.exercises_activity_menu,menu);

        // "Show Exercise Details" (Vague 1) - reflete l'etat persiste a l'ouverture du menu.
        menu.findItem(R.id.show_details).setChecked(isShowExerciseDetailsEnabled());

        // Search Stuff
        MenuItem searchItem = menu.findItem(R.id.search);
        androidx.appcompat.widget.SearchView searchView = (androidx.appcompat.widget.SearchView) searchItem.getActionView();

        searchView.setImeOptions(EditorInfo.IME_ACTION_DONE);
        searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String s)
            {
                exerciseAdapter.getFilter().filter(s);
                return false;
            }
        });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        if(item.getItemId() == R.id.add)
        {
            Intent in = new Intent(this, CustomExerciseActivity.class);
            startActivity(in);
        }
        else if(item.getItemId() == R.id.show_details)
        {
            boolean enabled = !item.isChecked();
            item.setChecked(enabled);
            setShowExerciseDetailsEnabled(enabled);
            exerciseAdapter.setShowDetails(enabled);
        }
        else if(item.getItemId() == R.id.settings)
        {
            Intent in = new Intent(getApplicationContext(), SettingsActivity.class);
            startActivity(in);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {

        if(item.getItemId() == R.id.home)
        {
            Intent in = new Intent(this,MainActivity.class);
            startActivity(in);
            overridePendingTransition(0,0);
        }
        else if(item.getItemId() == R.id.exercises)
        {
            Intent in = new Intent(this,ExercisesActivity.class);
            startActivity(in);
            overridePendingTransition(0,0);
        }
        else if(item.getItemId() == R.id.diary)
        {
            Intent in = new Intent(this, DiaryActivity.class);
            startActivity(in);
            overridePendingTransition(0,0);
        }
        else if(item.getItemId() == R.id.charts)
        {
            Intent in = new Intent(this, ChartsActivity.class);
            startActivity(in);
            overridePendingTransition(0,0);
        }
        else if(item.getItemId() == R.id.me)
        {
            Intent in = new Intent(this, SettingsActivity.class);
            startActivity(in);
            overridePendingTransition(0,0);
        }
        return true;
    }
}