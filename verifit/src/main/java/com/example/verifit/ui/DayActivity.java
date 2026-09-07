package com.example.verifit.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.verifit.DataStorage;
import com.example.verifit.LoadingDialog;
import com.example.verifit.SessionImporter;
import com.example.verifit.SessionTimerTicker;
import com.example.verifit.WorkoutReportGenerator;
import com.example.verifit.adapters.DayExerciseAdapter;
import com.example.verifit.R;
import com.example.verifit.model.WorkoutDay;
import com.example.verifit.model.WorkoutExercise;
import com.example.verifit.model.WorkoutSet;
import com.example.verifit.verifitrs.WorkoutSetsApi;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;

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
    private TextView tv_session_timer;
    private SessionTimerTicker sessionTimerTicker;

    // Chrono dedie a la Duration DANS le dialogue "Workout Time" (retour Romain
    // 07/09/2026, voir showWorkoutTimeDialog()) - meme principe que sur
    // AddExerciseActivity (voir ce fichier pour le detail).
    private SessionTimerTicker workoutTimeDialogTicker;

    // Cle SharedPreferences du reglage "Auto Start" - DOIT rester strictement
    // identique a AddExerciseActivity.AUTO_START_PREF_KEY (les deux ecrans
    // lisent/ecrivent le meme reglage, voir showWorkoutTimeSettingsDialog()).
    private static final String AUTO_START_PREF_KEY = "session_auto_start";

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
        refreshSessionTimerBar();
        sessionTimerTicker.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        sessionTimerTicker.stop();
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
        tv_session_timer = findViewById(R.id.tv_session_timer);
        sessionTimerTicker = new SessionTimerTicker(tv_session_timer);

        LinearLayout session_timer_bar = findViewById(R.id.session_timer_bar);
        session_timer_bar.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                showWorkoutTimeDialog();
            }
        });

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

        MainActivity.autoBackupRequired = true;
        com.example.verifit.SharedPreferences sharedPreferences = new com.example.verifit.SharedPreferences(getApplicationContext());
        sharedPreferences.save("true", "autoBackupRequired");
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
                if (item.getItemId() == R.id.delete_selected_exercises)
                {
                    confirmDeleteSelectedExercises(mode);
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

    private void confirmDeleteSelectedExercises(ActionMode mode)
    {
        List<String> selectedNames = workoutExerciseAdapter.getSelectedExerciseNames();

        if (selectedNames.isEmpty())
        {
            Toast.makeText(this, "No exercise selected", Toast.LENGTH_SHORT).show();
            return;
        }

        // Réutilise delete_set_dialog.xml (même confirmation que côté
        // AddExerciseActivity), avec un libellé qui précise que ça supprime toutes les
        // séries du jour pour ces exercices, pas juste leur ligne dans la liste.
        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.delete_set_dialog, null);
        AlertDialog alertDialog = new AlertDialog.Builder(this).setView(view).create();

        TextView title = view.findViewById(R.id.tv_date);
        title.setText(selectedNames.size() + " exercise(s) selected. Delete all their sets for this day?");

        Button bt_yes = view.findViewById(R.id.bt_yes3);
        Button bt_no = view.findViewById(R.id.bt_no3);

        bt_no.setOnClickListener(v -> alertDialog.dismiss());

        bt_yes.setOnClickListener(v -> {
            alertDialog.dismiss();
            deleteSelectedExercises(selectedNames, mode);
        });

        alertDialog.show();
    }

    private void deleteSelectedExercises(List<String> exerciseNames, ActionMode mode)
    {
        MainActivity.autoBackupRequired = true;
        com.example.verifit.SharedPreferences sharedPreferences = new com.example.verifit.SharedPreferences(getApplicationContext());
        sharedPreferences.save("true", "autoBackupRequired");

        int day_position = MainActivity.dataStorage.getDayPosition(date_clicked);
        if (day_position < 0)
        {
            mode.finish();
            return;
        }

        WorkoutDay day = MainActivity.dataStorage.getWorkoutDays().get(day_position);
        List<WorkoutSet> setsToDelete = new ArrayList<>();
        for (WorkoutSet set : day.getSets())
        {
            if (exerciseNames.contains(set.getExerciseName()))
            {
                setsToDelete.add(set);
            }
        }

        if (setsToDelete.isEmpty())
        {
            mode.finish();
            return;
        }

        if (sharedPreferences.isOfflineMode())
        {
            deleteExerciseSetsLocally(day_position, setsToDelete);
            Toast.makeText(this, exerciseNames.size() + " exercise(s) deleted", Toast.LENGTH_SHORT).show();
            mode.finish();
        }
        else
        {
            final LoadingDialog loadingDialog = new LoadingDialog(DayActivity.this);
            loadingDialog.loadingAlertDialog();

            // Même endpoint /sets/bulk que AddExerciseActivity.deleteSelectedSets - un
            // seul appel réseau pour toute la sélection.
            WorkoutSetsApi workoutSetsApi = new WorkoutSetsApi(getApplicationContext(), getString(R.string.API_ENDPOINT));
            workoutSetsApi.deleteWorkoutSets(setsToDelete, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    loadingDialog.dismissDialog();
                    runOnUiThread(() -> Toast.makeText(DayActivity.this, "Can't connect to server", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(Call call, okhttp3.Response response) throws IOException {
                    loadingDialog.dismissDialog();

                    if (200 == response.code())
                    {
                        deleteExerciseSetsLocally(day_position, setsToDelete);
                        runOnUiThread(() -> {
                            Toast.makeText(DayActivity.this, exerciseNames.size() + " exercise(s) deleted", Toast.LENGTH_SHORT).show();
                            mode.finish();
                        });
                    }
                    else
                    {
                        runOnUiThread(() -> Toast.makeText(DayActivity.this, response.message(), Toast.LENGTH_SHORT).show());
                    }
                }
            });
        }
    }

    // Supprime toutes les séries d'un jour pour les exercices sélectionnés (pas
    // seulement "aujourd'hui" au sens calendaire - date_clicked est le jour ouvert
    // dans cet écran, qui peut être n'importe quel jour passé).
    private void deleteExerciseSetsLocally(int day_position, List<WorkoutSet> setsToDelete)
    {
        WorkoutDay day = MainActivity.dataStorage.getWorkoutDays().get(day_position);
        day.removeSets(setsToDelete);

        if (day.getSets().isEmpty())
        {
            MainActivity.dataStorage.getWorkoutDays().remove(day_position);
        }

        MainActivity.dataStorage.saveWorkoutData(getApplicationContext());
        MainActivity.dataStorage.saveKnownExerciseData(getApplicationContext());

        runOnUiThread(this::initActivity);
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
            shareWorkout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // "Share workout" (retour Romain 06/09/2026, fonctionnalite qu'il avait sur
    // FitNotes) : genere le rapport texte de la seance affichee
    // (WorkoutReportGenerator) et ouvre le selecteur de partage standard Android -
    // usage principal de Romain : partager vers Discord. Le selecteur systeme
    // (Android 10+) propose aussi une action "Copier" integree.
    private void shareWorkout()
    {
        int day_position = MainActivity.dataStorage.getDayPosition(date_clicked);
        if (day_position < 0)
        {
            Toast.makeText(this, "No Logged Exercises", Toast.LENGTH_SHORT).show();
            return;
        }

        WorkoutDay day = MainActivity.dataStorage.getWorkoutDays().get(day_position);
        String report = WorkoutReportGenerator.generateReport(day);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, report);
        startActivity(Intent.createChooser(shareIntent, "Share workout"));
    }

    // Retrouve le WorkoutDay du jour affiche (peut etre null si aucune serie n'a encore
    // ete loggee ce jour-la) et met a jour l'affichage du chrono de session - meme
    // logique que AddExerciseActivity.refreshSessionTimerBar(), voir ce fichier pour le
    // detail du choix (retour Romain 06/09/2026).
    private void refreshSessionTimerBar()
    {
        int position = MainActivity.dataStorage.getDayPosition(date_clicked);
        WorkoutDay day = (position >= 0) ? MainActivity.dataStorage.getWorkoutDays().get(position) : null;

        sessionTimerTicker.setWorkoutDay(day);
    }

    // Start/Stop/Resume du chrono de session, depuis la vue d'ensemble du jour - meme
    // logique que AddExerciseActivity.toggleSessionTimer(). N'est plus appelee que par
    // le bouton du dialogue "Workout Time" (retour Romain 07/09/2026, la barre a perdu
    // son propre bouton Stop/Resume).
    private void toggleSessionTimer()
    {
        int position = MainActivity.dataStorage.getDayPosition(date_clicked);
        if (position < 0)
        {
            return;
        }

        WorkoutDay day = MainActivity.dataStorage.getWorkoutDays().get(position);

        if (day.getSessionStartTimestamp() == null)
        {
            day.setSessionStartTimestamp(System.currentTimeMillis());
        }
        else if (day.isSessionTimerRunning())
        {
            day.setSessionEndTimestamp(System.currentTimeMillis());
        }
        else
        {
            day.setSessionEndTimestamp(null);
        }

        MainActivity.dataStorage.saveWorkoutData(getApplicationContext());

        sessionTimerTicker.setWorkoutDay(day);
    }

    // "Auto Start" (retour Romain 07/09/2026, reglage du dialogue Workout Time
    // Settings) - lu ici uniquement pour pre-cocher la case dans
    // showWorkoutTimeSettingsDialog() : DayActivity ne loggue jamais de serie
    // elle-meme (voir AddExerciseActivity.startOrResumeSessionTimer() pour l'endroit
    // ou ce reglage conditionne reellement le demarrage automatique).
    private boolean isSessionAutoStartEnabled()
    {
        SharedPreferences sharedPreferences = getSharedPreferences("shared preferences", MODE_PRIVATE);
        return sharedPreferences.getBoolean(AUTO_START_PREF_KEY, true);
    }

    private void setSessionAutoStartEnabled(boolean enabled)
    {
        SharedPreferences sharedPreferences = getSharedPreferences("shared preferences", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(AUTO_START_PREF_KEY, enabled);
        editor.apply();
    }

    // Dialogue complet "Workout Time" (retour Romain 07/09/2026) - meme logique que
    // AddExerciseActivity.showWorkoutTimeDialog(), voir ce fichier pour le detail.
    private void showWorkoutTimeDialog()
    {
        int position = MainActivity.dataStorage.getDayPosition(date_clicked);
        WorkoutDay day = (position >= 0) ? MainActivity.dataStorage.getWorkoutDays().get(position) : null;

        if (day == null || day.getSessionStartTimestamp() == null)
        {
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.workout_time_dialog, null);

        TextView tv_date = dialogView.findViewById(R.id.tv_workout_time_date);
        TextView tv_start = dialogView.findViewById(R.id.tv_workout_time_start);
        TextView tv_end = dialogView.findViewById(R.id.tv_workout_time_end);
        TextView tv_duration = dialogView.findViewById(R.id.tv_workout_time_duration);
        ImageButton bt_overflow = dialogView.findViewById(R.id.bt_workout_time_overflow);
        MaterialButton bt_toggle = dialogView.findViewById(R.id.bt_workout_time_toggle);
        MaterialButton bt_close = dialogView.findViewById(R.id.bt_workout_time_close);

        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        tv_date.setText(WorkoutReportGenerator.formatDateHeader(day.getDate()));
        tv_start.setText(timeFormat.format(new Date(day.getSessionStartTimestamp())));
        tv_end.setText(day.getSessionEndTimestamp() != null
                ? timeFormat.format(new Date(day.getSessionEndTimestamp()))
                : "In progress");
        bt_toggle.setText(day.isSessionTimerRunning() ? "Stop Timer" : "Resume Timer");

        workoutTimeDialogTicker = new SessionTimerTicker(tv_duration);
        workoutTimeDialogTicker.setWorkoutDay(day);
        workoutTimeDialogTicker.start();

        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();

        bt_toggle.setOnClickListener(v ->
        {
            toggleSessionTimer();
            dialog.dismiss();
        });

        bt_close.setOnClickListener(v -> dialog.dismiss());

        bt_overflow.setOnClickListener(v ->
        {
            PopupMenu popupMenu = new PopupMenu(DayActivity.this, bt_overflow);
            popupMenu.inflate(R.menu.workout_time_dialog_menu);
            popupMenu.setOnMenuItemClickListener(item ->
            {
                int itemId = item.getItemId();
                if (itemId == R.id.workout_time_settings)
                {
                    showWorkoutTimeSettingsDialog();
                    return true;
                }
                else if (itemId == R.id.workout_time_cancel_timer)
                {
                    confirmCancelSessionTimer(dialog);
                    return true;
                }
                return false;
            });
            popupMenu.show();
        });

        dialog.setOnDismissListener(d ->
        {
            if (workoutTimeDialogTicker != null)
            {
                workoutTimeDialogTicker.stop();
                workoutTimeDialogTicker = null;
            }
        });

        dialog.show();
    }

    // Sous-dialogue "Workout Time Settings" - meme logique que
    // AddExerciseActivity.showWorkoutTimeSettingsDialog().
    private void showWorkoutTimeSettingsDialog()
    {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.workout_time_settings_dialog, null);

        CheckBox cb_auto_start = dialogView.findViewById(R.id.cb_workout_time_auto_start);
        MaterialButton bt_close = dialogView.findViewById(R.id.bt_workout_time_settings_close);

        cb_auto_start.setChecked(isSessionAutoStartEnabled());

        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();

        cb_auto_start.setOnCheckedChangeListener((buttonView, isChecked) -> setSessionAutoStartEnabled(isChecked));
        bt_close.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    // Confirmation avant d'annuler le chrono (retour Romain 07/09/2026) - meme
    // logique que AddExerciseActivity.confirmCancelSessionTimer().
    private void confirmCancelSessionTimer(AlertDialog workoutTimeDialog)
    {
        new AlertDialog.Builder(this)
                .setTitle("Cancel Timer")
                .setMessage("Cancel the timer for this workout? Sets already logged will not be deleted.")
                .setPositiveButton("Cancel Timer", (dlg, which) ->
                {
                    int position = MainActivity.dataStorage.getDayPosition(date_clicked);
                    if (position < 0)
                    {
                        return;
                    }

                    WorkoutDay day = MainActivity.dataStorage.getWorkoutDays().get(position);
                    day.setSessionStartTimestamp(null);
                    day.setSessionEndTimestamp(null);
                    MainActivity.dataStorage.saveWorkoutData(getApplicationContext());

                    sessionTimerTicker.setWorkoutDay(day);

                    workoutTimeDialog.dismiss();
                })
                .setNegativeButton("Back", null)
                .show();
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

            SessionImporter.Result result;
            try
            {
                result = SessionImporter.importFromUri(uri, this, MainActivity.dataStorage, date_clicked);
            }
            catch (Exception e)
            {
                // Belt-and-suspenders: SessionImporter already catches the JSON parsing
                // failures we know about, but this is fed by an external file the user
                // picked (typically from a workout-generator script), so an unexpected
                // shape should show an error toast instead of crashing the app.
                Toast.makeText(this, "Import failed: " + e.toString(), Toast.LENGTH_LONG).show();
                return;
            }

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