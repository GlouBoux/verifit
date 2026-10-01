package com.example.verifit.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.ItemTouchHelper;
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
import android.widget.TextView;
import android.widget.Toast;

import com.example.verifit.DataStorage;
import com.example.verifit.SessionImporter;
import com.example.verifit.adapters.DayExerciseAdapter;
import com.example.verifit.R;
import com.example.verifit.model.WorkoutDay;
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

    // Multi-select delete + drag reorder (retour Romain 05/09/2026), même mécanique
    // que sur AddExerciseActivity (voir ce fichier pour le détail des choix).
    private ActionMode selectionActionMode = null;
    private ItemTouchHelper itemTouchHelper;

    // Position de départ du geste de drag en cours - capturée une fois au début du
    // geste (voir ItemTouchHelper.Callback ci-dessous), comparée à la position
    // d'arrivée pour ne persister l'ordre qu'une seule fois, au relâchement, plutôt
    // qu'à chaque étape intermédiaire du drag.
    private int dragStartPosition = -1;

    // Chrono de la SEANCE entiere (retour Romain 06/09/2026, voir le meme chrono sur
    // AddExerciseActivity - detail du choix dans le commentaire sur
    // WorkoutDay.SessionStartTimestamp). Affiche ici aussi car cet ecran (vue
    // d'ensemble des exercices du jour) est l'autre endroit ou Romain regarde sa
    // seance en cours, pas seulement pendant la saisie d'une serie.
    // Barre + dialogue "Workout Time", partages avec AddExerciseActivity.
    private SessionTimerController sessionTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_day);

        // Self Explanatory I guess
        initActivity();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Chrono de session : rafraichir a chaque retour sur cet ecran (une serie a pu
        // etre loggee/supprimee depuis AddExerciseActivity entre-temps) et relancer le
        // defilement de l'affichage.
        sessionTimer.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        sessionTimer.stop();
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

        // Chrono de session (retour Romain 06/09/2026) - l'etat reel n'est connu qu'une
        // fois date_clicked lu plus bas ; le rafraichissement initial se fait donc dans
        // onResume() (appele juste apres onCreate()), pas ici.
        // Retour Romain 07/09/2026 : la barre n'a plus son propre bouton Stop/Resume
        // (voir activity_day.xml) - un tap ouvre directement le dialogue "Workout Time"
        // ci-dessous, seul endroit desormais pour Stop/Resume.
        // Un tap sur la barre ouvre le dialogue "Workout Time" (SessionTimerController).
        sessionTimer = new SessionTimerController(this, findViewById(R.id.tv_session_timer),
                findViewById(R.id.session_timer_bar), () -> date_clicked);

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
            WorkoutDay currentDay = null;

            for(int i = 0; i < MainActivity.dataStorage.getWorkoutDays().size(); i++)
            {
                if(date_clicked.equals(MainActivity.dataStorage.getWorkoutDays().get(i).getDate()))
                {
                    currentDay = MainActivity.dataStorage.getWorkoutDays().get(i);
                    Today_Execrises = currentDay.getExercises();
                }
            }


            // Commentaire de la seance entiere (Vague 3, retour Romain 07/09/2026) -
            // currentDay est null si aucune serie n'a jamais ete loggee ce jour-la.
            TextView tv_workout_comment = findViewById(R.id.tv_workout_comment);
            String workoutComment = (currentDay != null) ? currentDay.getComment() : "";
            if (workoutComment.trim().isEmpty())
            {
                tv_workout_comment.setVisibility(View.GONE);
            }
            else
            {
                tv_workout_comment.setVisibility(View.VISIBLE);
                tv_workout_comment.setText(workoutComment);
            }

            // Set Recycler View
            workoutExerciseAdapter = new DayExerciseAdapter(this, Today_Execrises);
            // Superset (Vague 2, retour Romain 07/09/2026) : necessaire pour que
            // l'adapter retrouve le groupe de chaque exercice (barre coloree).
            workoutExerciseAdapter.setWorkoutDay(currentDay);
            workoutExerciseAdapter.setOnSelectionChangedListener(count -> {
                if (selectionActionMode != null)
                {
                    selectionActionMode.setTitle(count + " selected");
                }
            });
            workoutExerciseAdapter.setOnStartDragListener(viewHolder -> {
                if (itemTouchHelper != null)
                {
                    itemTouchHelper.startDrag(viewHolder);
                }
            });
            recyclerView.setAdapter(workoutExerciseAdapter);
            recyclerView.setLayoutManager(new LinearLayoutManager(this));

            itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                    ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0)
            {
                @Override
                public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder source, @NonNull RecyclerView.ViewHolder target)
                {
                    int from = source.getAdapterPosition();
                    int to = target.getAdapterPosition();

                    if (dragStartPosition == -1)
                    {
                        dragStartPosition = from;
                    }

                    workoutExerciseAdapter.moveItem(from, to);
                    return true;
                }

                @Override
                public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction)
                {
                    // Swipe non utilisé - drag uniquement (poignée dédiée).
                }

                @Override
                public boolean isLongPressDragEnabled()
                {
                    // Le drag démarre uniquement via la poignée (voir
                    // DayExerciseAdapter.dragHandle), jamais par long-press sur toute
                    // la ligne - ça entrerait en conflit avec le mode sélection.
                    return false;
                }

                @Override
                public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState)
                {
                    super.onSelectedChanged(viewHolder, actionState);

                    // Retour Romain 05/09/2026 : replie les séries de tous les exercices
                    // dès qu'un glisser-déposer démarre, même hors mode sélection (avant,
                    // le repli ne se déclenchait qu'en cliquant sur "Select").
                    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG)
                    {
                        workoutExerciseAdapter.setDragging(true);
                    }
                }

                @Override
                public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder viewHolder)
                {
                    super.clearView(rv, viewHolder);

                    int finalPosition = viewHolder.getAdapterPosition();
                    if (dragStartPosition != -1 && finalPosition != -1 && finalPosition != dragStartPosition)
                    {
                        persistExerciseOrder(dragStartPosition, finalPosition);
                    }
                    dragStartPosition = -1;
                    workoutExerciseAdapter.setDragging(false);
                }
            });
            itemTouchHelper.attachToRecyclerView(recyclerView);

            // Notify User
            if(Today_Execrises.isEmpty())
            {
                Toast.makeText(getApplicationContext(),"No Logged Exercises",Toast.LENGTH_SHORT).show();
            }


        } catch (ParseException e) {
            e.printStackTrace();
        }

    }

    // Persiste l'ordre final d'un geste de drag (retour Romain 05/09/2026, "comme
    // FitNotes") - une seule fois par geste, jamais à chaque étape intermédiaire
    // (voir ItemTouchHelper.Callback.onMove ci-dessus).
    private void persistExerciseOrder(int fromPosition, int toPosition)
    {
        int day_position = MainActivity.dataStorage.getDayPosition(date_clicked);
        if (day_position < 0)
        {
            return;
        }

        WorkoutDay day = MainActivity.dataStorage.getWorkoutDays().get(day_position);
        day.moveExercise(fromPosition, toPosition);
        MainActivity.dataStorage.saveWorkoutData(getApplicationContext());
    }

    // --- Multi-select delete (retour Romain 05/09/2026) ---
    // Même mécanique que AddExerciseActivity.startSelectionMode() côté séries
    // individuelles : une ActionMode dédiée plutôt que de réinterpréter un geste
    // existant (ici, le tap sur la carte qui replie/déplie les séries).
    public void startSelectionMode()
    {
        if (selectionActionMode != null)
        {
            return;
        }
        selectionActionMode = startSupportActionMode(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.exercise_selection_action_menu, menu);
                mode.setTitle("0 selected");
                workoutExerciseAdapter.enterSelectionMode();
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (item.getItemId() == R.id.select_all_exercises)
                {
                    workoutExerciseAdapter.selectAll();
                    return true;
                }
                else if (item.getItemId() == R.id.delete_selected_exercises)
                {
                    DayActions.confirmDeleteSelectedExercises(DayActivity.this, date_clicked, workoutExerciseAdapter.getSelectedExerciseNames(), mode, DayActivity.this::initActivity);
                    return true;
                }
                else if (item.getItemId() == R.id.group_selected_exercises)
                {
                    DayActions.groupSelectedExercises(DayActivity.this, date_clicked, workoutExerciseAdapter.getSelectedExerciseNames(), mode, DayActivity.this::initActivity);
                    return true;
                }
                else if (item.getItemId() == R.id.ungroup_selected_exercises)
                {
                    DayActions.ungroupSelectedExercises(DayActivity.this, date_clicked, workoutExerciseAdapter.getSelectedExerciseNames(), mode, DayActivity.this::initActivity);
                    return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                workoutExerciseAdapter.exitSelectionMode();
                selectionActionMode = null;
            }
        });
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
        else if(item.getItemId() == R.id.select_exercises)
        {
            startSelectionMode();
            return true;
        }
        else if(item.getItemId() == R.id.share_workout)
        {
            DayActions.shareWorkout(this, date_clicked);
            return true;
        }
        else if(item.getItemId() == R.id.comment_workout)
        {
            DayActions.showCommentWorkoutDialog(this, date_clicked, this::initActivity);
            return true;
        }
        else if(item.getItemId() == R.id.copy_workout)
        {
            new CalendarPickerDialog(this, MainActivity.dataStorage, date_clicked,
                    dateKey -> DayActions.promptCopyOrMoveExercises(this, dateKey, date_clicked, false, this::initActivity)).show();
            return true;
        }
        else if(item.getItemId() == R.id.copy_previous_workout)
        {
            DayActions.copyPreviousWorkout(this, date_clicked, this::initActivity);
            return true;
        }
        else if(item.getItemId() == R.id.move_workout)
        {
            new CalendarPickerDialog(this, MainActivity.dataStorage, date_clicked,
                    dateKey -> DayActions.promptCopyOrMoveExercises(this, dateKey, date_clicked, true, this::initActivity)).show();
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

            // Point 1.5 de la revue du 28/09/2026 : confirmation si la seance a deja ete
            // importee ce jour-la (voir SessionImporter.importWithDuplicateCheck()).
            SessionImporter.importWithDuplicateCheck(this, uri, MainActivity.dataStorage, date_clicked, this::showImportSessionResult);
        }
    }

    private void showImportSessionResult(SessionImporter.Result result)
    {
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