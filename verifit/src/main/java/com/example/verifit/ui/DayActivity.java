package com.example.verifit.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.example.verifit.DataStorage;
import com.example.verifit.SessionImporter;
import com.example.verifit.adapters.DayExerciseAdapter;
import com.example.verifit.R;
import com.example.verifit.model.WorkoutExercise;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class DayActivity extends AppCompatActivity {

    public RecyclerView recyclerView;
    public DayExerciseAdapter workoutExerciseAdapter;
    String date_clicked;

    // Request code for the "Import Session" file picker (see fileSearchImportSession()).
    public static final int IMPORT_SESSION_REQUEST_CODE = 77;

    FloatingActionButton fab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_day);

        // Self Explanatory I guess
        initActivity();
    }


    // Haven't we said that already?
    public void initActivity()
    {

        fab = findViewById(R.id.floatingActionButton);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent in = new Intent(DayActivity.this, ExercisesActivity.class);
                startActivity(in);
            }
        });

        // Recycler View Stuff
        recyclerView = findViewById(R.id.recycler_view_day);

        // From Main Activity
        Intent mIntent = getIntent();
        Bundle extras = mIntent.getExtras();
        date_clicked = extras.getString("date");

        //Date Stuff
        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd");
        Date date = null;

        try {
            date = parser.parse(date_clicked);
            SimpleDateFormat formatter = new SimpleDateFormat("EEEE, MMM dd YYYY");
            String formated_date = formatter.format(date);
            getSupportActionBar().setTitle(formated_date);

            ArrayList<WorkoutExercise> Today_Execrises = new ArrayList<WorkoutExercise>();

            for(int i = 0; i < MainActivity.dataStorage.getWorkoutDays().size(); i++)
            {
                if(date_clicked.equals(MainActivity.dataStorage.getWorkoutDays().get(i).getDate()))
                {
                    Today_Execrises = MainActivity.dataStorage.getWorkoutDays().get(i).getExercises();
                }
            }


            // Set Recycler View
            workoutExerciseAdapter = new DayExerciseAdapter(this, Today_Execrises);
            recyclerView.setAdapter(workoutExerciseAdapter);
            recyclerView.setLayoutManager(new LinearLayoutManager(this));

            // Notify User
            if(Today_Execrises.isEmpty())
            {
                Toast.makeText(getApplicationContext(),"No Logged Exercises",Toast.LENGTH_SHORT).show();
            }


        } catch (ParseException e) {
            e.printStackTrace();
        }

    }

    // When back button is pressed by another app
    @Override
    protected void onRestart() {
        super.onRestart();

        // Self Explanatory I guess
        initActivity();
    }

    // Menu Methods
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.day_activity_menu,menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if(item.getItemId() == R.id.import_session)
        {
            fileSearchImportSession();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Opens the system file picker so the user can pick a JSON file describing a
    // session (see docs/session-import-format.md), typically produced by an external
    // workout-generator tool. "*/*" is used rather than "application/json" because some
    // file providers don't report .json files under that exact mime type and would get
    // filtered out of the picker.
    public void fileSearchImportSession()
    {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, IMPORT_SESSION_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == IMPORT_SESSION_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null)
        {
            Uri uri = data.getData();

            if(uri == null)
            {
                return;
            }

            SessionImporter.Result result = SessionImporter.importFromUri(uri, this, MainActivity.dataStorage, date_clicked);

            if(result.success)
            {
                DataStorage.ImportSummary summary = result.summary;
                String message = summary.setsImported + " set(s) imported into " + summary.date;
                if(summary.exercisesCreated > 0)
                {
                    message += " (" + summary.exercisesCreated + " new exercise(s) created)";
                }
                if(summary.setsSkipped > 0)
                {
                    message += " - " + summary.setsSkipped + " incomplete set(s) skipped";
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();

                // Refresh this screen, and reload the imported day if it wasn't the one
                // currently open (the JSON file is allowed to carry its own "date").
                // initActivity() re-reads "date" from the intent extras every time it
                // runs (see onRestart()), so update those extras rather than just the
                // field, otherwise this would be overwritten right back.
                getIntent().putExtra("date", summary.date);
                initActivity();
            }
            else
            {
                Toast.makeText(this, "Import failed: " + result.errorMessage, Toast.LENGTH_LONG).show();
            }
        }
    }
}