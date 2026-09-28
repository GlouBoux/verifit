package com.example.verifit.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import com.example.verifit.DataStorage;
import com.example.verifit.ExerciseNotesImporter;
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

    // "Import Notes" (retour Romain 28/09/2026) - meme convention de request code que
    // DayActivity.IMPORT_SESSION_REQUEST_CODE pour "Import Session".
    public static final int IMPORT_NOTES_REQUEST_CODE = 78;

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
        else if(item.getItemId() == R.id.import_notes)
        {
            fileSearchImportNotes();
        }
        return super.onOptionsItemSelected(item);
    }

    // Opens the system file picker so the user can pick a JSON file mapping exercise
    // names to notes ({"exercise name": "notes", ...}), typically produced by a
    // Coaching-side script from an old FitNotes backup. "*/*" rather than
    // "application/json" for the same reason as DayActivity.fileSearchImportSession() -
    // some file providers don't report .json under that exact mime type.
    public void fileSearchImportNotes()
    {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, IMPORT_NOTES_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == IMPORT_NOTES_REQUEST_CODE && resultCode == RESULT_OK && data != null)
        {
            Uri uri = data.getData();

            if(uri == null)
            {
                return;
            }

            ExerciseNotesImporter.Result result;
            try
            {
                result = ExerciseNotesImporter.importFromUri(uri, this, MainActivity.dataStorage);
            }
            catch (Exception e)
            {
                // Belt-and-suspenders, same reasoning as DayActivity's Import Session
                // handler: this is fed by an external file the user picked, an
                // unexpected shape should show an error toast instead of crashing.
                Toast.makeText(this, "Import failed: " + e.toString(), Toast.LENGTH_LONG).show();
                return;
            }

            if(result.success)
            {
                DataStorage.NotesImportSummary summary = result.summary;
                String message = summary.applied + " exercise note(s) imported";
                if(summary.skippedAlreadyHasNotes > 0)
                {
                    message += ", " + summary.skippedAlreadyHasNotes + " skipped (already had notes)";
                }
                if(summary.notFound > 0)
                {
                    message += ", " + summary.notFound + " not found in your exercise list";
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();

                // Refresh the list so newly-imported notes are reflected right away
                // (e.g. if "Show Details" is later extended to surface them).
                onCreateStuff();
            }
            else
            {
                Toast.makeText(this, "Import failed: " + result.errorMessage, Toast.LENGTH_LONG).show();
            }
        }
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