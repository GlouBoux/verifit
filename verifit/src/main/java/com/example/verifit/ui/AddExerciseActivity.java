package com.example.verifit.ui;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.verifit.KeyboardHider;
import com.example.verifit.LoadingDialog;
import com.example.verifit.MonthXAxisFormatter;
import com.example.verifit.RestTimerBarTicker;
import com.example.verifit.RestTimerReceiver;
import com.example.verifit.SessionTimerTicker;
import com.example.verifit.SnackBarWithMessage;
import com.example.verifit.WorkoutReportGenerator;
import com.example.verifit.adapters.AddExerciseWorkoutSetAdapter;
import com.example.verifit.adapters.ExerciseHistoryExerciseAdapter;
import com.example.verifit.adapters.NavPanelExerciseAdapter;
import com.example.verifit.R;
import com.example.verifit.model.SupersetGroup;
import com.example.verifit.model.WorkoutDay;
import com.example.verifit.model.WorkoutExercise;
import com.example.verifit.model.WorkoutSet;
import com.example.verifit.verifitrs.WorkoutSetsApi;
import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class AddExerciseActivity extends AppCompatActivity {

    // Helper Data Structures
    public RecyclerView recyclerView;
    public static String exercise_name;
    public static ArrayList<WorkoutSet> Todays_Exercise_Sets = new ArrayList<WorkoutSet>();
    public static AddExerciseWorkoutSetAdapter workoutSetAdapter2;
    public static int Clicked_Set = 0;
    public static Boolean isEditMode = false;

    // Add Exercise Activity Specifics
    public static EditText et_reps;
    public static EditText et_weight;
    public ImageButton plus_reps;
    public ImageButton minus_reps;
    public ImageButton plus_weight;
    public ImageButton minus_weight;
    public static Button bt_save;
    public static Button bt_clear;

    // For Alarm
    public long START_TIME_IN_MILLIS = 180000;
    public CountDownTimer countDownTimer;
    public boolean TimerRunning;
    public long TimeLeftInMillis = START_TIME_IN_MILLIS;

    // Retour Romain 06/09/2026 : "meme telephone verrouille, il sonne ... je dois
    // pouvoir lui faire confiance" - countDownTimer ci-dessus ne pilote plus QUE
    // l'affichage. La sonnerie fiable vient d'une alarme systeme independante
    // (scheduleTimerAlarm()/RestTimerReceiver, voir leurs commentaires), qui se
    // declenche meme app fermee ou ecran verrouille.
    private AlarmManager alarmManager;
    private static final int REST_TIMER_REQUEST_CODE = 424242;

    // Timer Dialog Components
    public EditText et_seconds;
    public ImageButton minus_seconds;
    public ImageButton plus_seconds;
    public Button bt_start;
    public Button bt_reset;
    // Retour Romain 06/09/2026 : "possible de gerer le volume directement depuis
    // l'app ?" (comme sur FitNotes) - voir setupTimer()/loadVolume() ci-dessous et
    // RestTimerReceiver.playRestTimerBeep() qui lit ce reglage au moment de sonner.
    public SeekBar sb_volume;

    // Retour Romain 06/09/2026 : "le bip est un peu court, possible de le rendre 2 fois
    // plus long ? ou m'offrir la possibilite de l'editer dans l'app ?" - duree du bip
    // (en ms) reglable depuis l'app, meme principe que sb_volume ci-dessus. Voir
    // setupTimer()/loadDuration()/RestTimerReceiver.DURATION_PREF_KEY.
    public SeekBar sb_beep_duration;

    // "Auto Start" du minuteur de REPOS (07/09/2026, feature FitNotes, vague 1 du plan
    // de migration `docs/fitnotes-migration-plan.md`) - distinct de
    // cb_workout_time_auto_start (chrono de SEANCE). Voir
    // autoStartRestTimerIfEnabled()/isRestTimerAutoStartEnabled() plus bas.
    public CheckBox cb_rest_timer_auto_start;

    // Chrono de la SEANCE entiere (retour Romain 06/09/2026, distinct du minuteur de
    // repos ci-dessus) - voir refreshSessionTimerBar()/toggleSessionTimer() plus bas et
    // le commentaire sur WorkoutDay.SessionStartTimestamp.
    private TextView tv_session_timer;
    private SessionTimerTicker sessionTimerTicker;

    // Barre persistante du minuteur de REPOS (retour Romain 24/09/2026 : "je veux voir
    // le temps restant avant de repartir [...] si je n'ai pas entendu le timer MAIS que
    // je vois le temps restant c'est ok. Si je n'ai rien entendu MAIS que je ne vois
    // plus le temps restant alors je peux y retourner.") - distincte du chrono de
    // SEANCE ci-dessus. S'appuie sur RestTimerReceiver.persistEndTimestamp() plutot que
    // sur countDownTimer/TimeLeftInMillis (attaches au cycle de vie de cette Activity,
    // donc perdus en changeant d'exercice via le volet de navigation - voir
    // RestTimerBarTicker pour le detail).
    private View restTimerBarContainer;
    private TextView tvRestTimerBar;
    private RestTimerBarTicker restTimerBarTicker;

    // Chrono dedie a la Duration DANS le dialogue "Workout Time" (retour Romain
    // 07/09/2026, voir showWorkoutTimeDialog()) - independant de sessionTimerTicker
    // ci-dessus (barre persistante) : demarre a l'ouverture du dialogue, arrete a sa
    // fermeture (setOnDismissListener), jamais actif quand le dialogue est ferme.
    private SessionTimerTicker workoutTimeDialogTicker;

    // Cle SharedPreferences du reglage "Auto Start" (retour Romain 07/09/2026,
    // dialogue Workout Time Settings, capture FitNotes fournie) - dupliquee a
    // l'identique dans DayActivity (meme convention que les autres methodes de ce
    // chrono, ex. toggleSessionTimer()) : les deux ecrans doivent lire/ecrire
    // exactement le meme reglage.
    private static final String AUTO_START_PREF_KEY = "session_auto_start";

    // Cle SharedPreferences du reglage "Auto Start" du minuteur de REPOS (07/09/2026) -
    // demande initiale de Romain dans fitnotes-fork-todo.md ("Nouvelles demandes",
    // 06/09/2026) : "ajouter start timer quand on valide la premiere serie". Feature
    // FitNotes confirmee dans fitnotes-features-workout-tools.md : demarre a CHAQUE
    // nouvelle serie (pas seulement la premiere), tant qu'on reste sur l'exercice. Pas
    // de reglage "Auto Stop" associe : FitNotes lui-meme n'en propose pas (meme
    // ambiguite que pour AUTO_START_PREF_KEY ci-dessus sur ce que "derniere serie"
    // signifierait). Cle distincte de AUTO_START_PREF_KEY (chrono de SEANCE) et de
    // RestTimerReceiver.VOLUME_PREF_KEY/DURATION_PREF_KEY (reglages du bip), memes
    // SharedPreferences. Desactive par defaut : contrairement au chrono de seance
    // (une seule fois par seance, sans gene si oublie), demarrer le minuteur de repos
    // à CHAQUE serie sans y avoir consenti serait plus intrusif - à activer
    // explicitement dans la boite de dialogue "Timer".
    private static final String REST_TIMER_AUTO_START_PREF_KEY = "rest_timer_auto_start";

    private AlertDialog currentDialog = null;

    // Multi-select delete (retour Romain 05/09/2026) : sélectionner plusieurs séries et
    // les supprimer en un coup, au lieu d'un "Clear" fastidieux série par série.
    private ActionMode selectionActionMode = null;

    // Réordonnement des séries par glisser-déposer (retour Romain 06/09/2026, "un appui
    // long sur la ligne lance l'utilitaire de réordonnement de série") - même mécanique
    // que DayActivity côté exercices.
    private ItemTouchHelper itemTouchHelper;
    private int dragStartPosition = -1;

    // Volet de navigation entre exercices (retour Romain 18/09/2026) - voir
    // claude/fitnotes-feature-navigation-panel.md et le commentaire en tete de
    // activity_add_exercise.xml. initNavPanel()/refreshNavPanel()/persistNavPanelOrder()
    // plus bas regroupent toute la logique du volet.
    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle navPanelDrawerToggle;
    private RecyclerView recyclerViewNavPanel;
    private NavPanelExerciseAdapter navPanelExerciseAdapter;
    private TextView tvNavPanelHeader;
    private ItemTouchHelper navPanelItemTouchHelper;
    private int navPanelDragStartPosition = -1;



    // Comment Items
    Button bt_save_comment;
    Button bt_clear_comment;
    EditText et_exercise_comment;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_exercise);

        // find views
        et_reps = findViewById(R.id.et_reps);
        et_weight = findViewById(R.id.et_seconds);
        plus_reps = findViewById(R.id.plus_reps);
        minus_reps = findViewById(R.id.minus_reps);
        plus_weight = findViewById(R.id.plus_weight);
        minus_weight = findViewById(R.id.minus_weight);
        bt_clear = findViewById(R.id.bt_clear);
        bt_save = findViewById(R.id.bt_login_signup);

        wireSaveClearButtonsVisibility();

        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        // Chrono de la seance entiere (retour Romain 06/09/2026) - demarrage automatique
        // a la premiere serie loggee (voir startOrResumeSessionTimer()), affichage gere
        // ici. L'etat reel (WorkoutDay) n'est connu qu'apres
        // initActivity()/MainActivity.dateSelected - le rafraichissement initial se fait
        // donc dans onResume() (appele juste apres onCreate()), pas ici.
        // Retour Romain 07/09/2026 : la barre n'a plus son propre bouton Stop/Resume
        // (voir activity_add_exercise.xml) - Stop/Resume se fait desormais uniquement
        // depuis le dialogue "Workout Time" ci-dessous, toggleSessionTimer() reste la
        // logique partagee que ce dialogue appelle.
        tv_session_timer = findViewById(R.id.tv_session_timer);
        sessionTimerTicker = new SessionTimerTicker(tv_session_timer);

        // Barre persistante du minuteur de repos (voir le commentaire sur le champ
        // restTimerBarTicker plus haut) - masquee par defaut (activity_add_exercise.xml,
        // android:visibility="gone"), affichee/rafraichie par onResume() ci-dessous et a
        // chaque changement d'etat du minuteur (startTimer()/pauseTimer()/resetTimer()).
        restTimerBarContainer = findViewById(R.id.rest_timer_bar);
        tvRestTimerBar = findViewById(R.id.tv_rest_timer_bar);
        restTimerBarTicker = new RestTimerBarTicker(this, restTimerBarContainer, tvRestTimerBar);

        // Retour Romain 24/09/2026 : "je veux que ca ouvre l'ecran du rest timer si je
        // clique dessus [...] c'est comme si j'avais clique sur l'icone" - meme
        // dialogue que l'icone "Timer" de la barre d'outils (menu "...", R.id.timer).
        restTimerBarContainer.setOnClickListener(v -> setupTimer());

        // Dialogue complet "Workout Time" (retour Romain 07/09/2026, apres captures
        // FitNotes fournies) : ouvert en tapant la barre, seul point d'entree pour
        // Stop/Resume depuis que la barre a perdu son propre bouton (voir
        // activity_add_exercise.xml).
        LinearLayout session_timer_bar = findViewById(R.id.session_timer_bar);
        session_timer_bar.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                showWorkoutTimeDialog();
            }
        });

        // Self Explanatory I guess
        initActivity();

        // Self Explanatory I guess
        initrecyclerView();

        // Volet de navigation entre exercices (retour Romain 18/09/2026) - voir le
        // commentaire sur les champs drawerLayout/... plus haut.
        initNavPanel();

        // User can modify data structures, possible race condition, thus temporary disable autobackup
        MainActivity.inAddExerciseActivity = true;

        com.example.verifit.SharedPreferences sharedPreferences = new com.example.verifit.SharedPreferences(getApplicationContext());
        sharedPreferences.save("true", "inAddExerciseActivity");
    }

    @Override
    protected void onResume() {
        super.onResume();
        // User can modify data structures, possible race condition, thus temporary disable autobackup
        MainActivity.inAddExerciseActivity = true;

        com.example.verifit.SharedPreferences sharedPreferences = new com.example.verifit.SharedPreferences(getApplicationContext());
        sharedPreferences.save("true", "inAddExerciseActivity");

        // Chrono de session (retour Romain 06/09/2026) : rafraichir l'etat affiche a
        // chaque retour sur cet ecran (une serie a pu etre loggee/supprimee ailleurs
        // entre-temps) et relancer le defilement de l'affichage.
        refreshSessionTimerBar();
        sessionTimerTicker.start();

        // Barre du minuteur de repos : relit l'instant de fin persiste (peut avoir ete
        // demarre/arrete depuis un autre exercice via le volet de navigation - voir
        // RestTimerBarTicker) et reprend le defilement de l'affichage.
        restTimerBarTicker.refresh();
        restTimerBarTicker.start();

        // Volet de navigation : le nombre de series par exercice (et l'exercice
        // courant surligne) a pu changer entre-temps (retour d'un autre ecran).
        refreshNavPanel();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Chrono de session : plus la peine de faire defiler un affichage qui n'est plus
        // visible - la valeur reelle reste sur WorkoutDay, pas sur ce Handler.
        sessionTimerTicker.stop();
        restTimerBarTicker.stop();
    }

    // Retour Romain 18/09/2026 (implicite, comportement standard d'un volet de
    // navigation) : un appui sur "retour" alors que le volet est ouvert le referme
    // d'abord, plutot que de quitter l'ecran directement.
    @Override
    public void onBackPressed()
    {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START))
        {
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }
        super.onBackPressed();
    }

    // Save / Update
    public void clickSave(View view)
    {
        KeyboardHider keyboardHider = new KeyboardHider(AddExerciseActivity.this);
        keyboardHider.hideKeyboard();

        // Let backup service know that something has changed
        MainActivity.autoBackupRequired = true;

        com.example.verifit.SharedPreferences sharedPreferences = new com.example.verifit.SharedPreferences(getApplicationContext());
        sharedPreferences.save("true", "autoBackupRequired");

        // Save Functionality
        if(!isEditMode)
        {
            if(et_weight.getText().toString().isEmpty() || et_reps.getText().toString().isEmpty())
            {
                Toast.makeText(getApplicationContext(),"Please write Weight and Reps",Toast.LENGTH_SHORT).show();
            }
            else
            {
                // Get user sets && reps
                Double reps = Double.parseDouble(et_reps.getText().toString());
                Double weight = Double.parseDouble(et_weight.getText().toString());

                // Create New Set Object
                WorkoutSet workoutSet = new WorkoutSet(MainActivity.dateSelected,exercise_name, MainActivity.dataStorage.getExerciseCategory(exercise_name),reps,weight);

                // Horodatage du moment de la validation (retour Romain 06/09/2026,
                // pour la ligne "Time" de l'export de seance) - uniquement pour une
                // NOUVELLE serie, jamais lors d'une modification (cf. WorkoutSet.java).
                workoutSet.setTimestamp(System.currentTimeMillis());

                // Ignore wrong input
                if(reps == 0 || weight == 0 || reps < 0 || weight < 0)
                {
                    Toast.makeText(getApplicationContext(),"Please write correct Weight and Reps",Toast.LENGTH_SHORT).show();
                }
                // Save set
                else
                {
                    // Find if workout day already exists
                    int position = MainActivity.dataStorage.getDayPosition(MainActivity.dateSelected);

                    // If workout day exists
                    if(position >= 0)
                    {
                        // Retour Romain 07/09/2026 : "quand j'ajoute une série, le
                        // commentaire de la série precedant est aussi copié [...] il
                        // ne faut pas." Il y avait ici une boucle sur toutes les
                        // series existantes de cet exercice qui recopiait le
                        // commentaire de la DERNIERE serie trouvee (donc la
                        // precedente, vu que les series sont ajoutees en fin de
                        // liste) sur la nouvelle serie - relique du code de base
                        // (avant que WorkoutSet.comment ne devienne un vrai
                        // commentaire INDIVIDUEL par serie, voir Fonctionnalite 3
                        // "Commentaire par serie" dans docs/fitnotes-fork-plan.md).
                        // Supprimee : une nouvelle serie part desormais toujours sans
                        // commentaire (comme deja pose par le constructeur de
                        // WorkoutSet ci-dessus), a editer individuellement si besoin
                        // via l'appui long (WorkoutSetAdapter).

                        // Offline
                        if(sharedPreferences.isOfflineMode())
                        {
                            addSetExistingWorkoutDay(workoutSet, position);
                        }
                        else
                        {
                            final LoadingDialog loadingDialog = new LoadingDialog(AddExerciseActivity.this);
                            loadingDialog.loadingAlertDialog();

                            WorkoutSetsApi workoutSetsApi = new WorkoutSetsApi(getApplicationContext(), getString(R.string.API_ENDPOINT));
                            workoutSetsApi.postWorkoutSet(workoutSet, new Callback() {
                                @Override
                                public void onFailure(Call call, IOException e) {
                                    loadingDialog.dismissDialog();
                                    showSnackbarMessage(e.toString());
                                }

                                @Override
                                public void onResponse(Call call, okhttp3.Response response) throws IOException {

                                    loadingDialog.dismissDialog();

                                    if (200 == response.code())
                                    {
                                        Integer set_id = getSetIdFromResponse(response);
                                        workoutSet.setId(set_id);
                                        addSetExistingWorkoutDay(workoutSet, position);
                                    }
                                    else
                                    {
                                        showSnackbarMessage(response.message().toString());
                                    }
                                }
                            });
                        }
                    }
                    // If not construct new workout day
                    else
                    {
                        // Offline
                        if(sharedPreferences.isOfflineMode())
                        {
                            addSetNewWorkoutDay(workoutSet);
                        }
                        else
                        {
                            final LoadingDialog loadingDialog = new LoadingDialog(AddExerciseActivity.this);
                            loadingDialog.loadingAlertDialog();

                            WorkoutSetsApi workoutSetsApi = new WorkoutSetsApi(getApplicationContext(), getString(R.string.API_ENDPOINT));
                            workoutSetsApi.postWorkoutSet(workoutSet, new Callback() {
                                @Override
                                public void onFailure(Call call, IOException e) {
                                    // Show error
                                    loadingDialog.dismissDialog();
                                    showSnackbarMessage(e.toString());
                                }

                                @Override
                                public void onResponse(Call call, okhttp3.Response response) throws IOException {

                                    loadingDialog.dismissDialog();

                                    if (200 == response.code())
                                    {
                                        Integer set_id = getSetIdFromResponse(response);
                                        workoutSet.setId(set_id);
                                        addSetNewWorkoutDay(workoutSet);
                                    }
                                    else
                                    {
                                        runOnUiThread(()->{
                                            showSnackbarMessage(response.message());
                                        });
                                    }
                                }
                            });
                        }
                    }
                }
            }

            // Fixed Myria induced bug
            AddExerciseActivity.Clicked_Set = Todays_Exercise_Sets.size()-1;
        }
        // Update Functionality
        else
        {
            WorkoutSet to_be_updated_set = Todays_Exercise_Sets.get(Clicked_Set);

            to_be_updated_set.getDate();
            to_be_updated_set.getExerciseName();

            // Find the set in main data structure and delete it
            for (int i = 0; i < MainActivity.dataStorage.getWorkoutDays().size(); i++)
            {
                for (int j = 0; j < MainActivity.dataStorage.getWorkoutDays().get(i).getSets().size(); j++)
                {
                    if (MainActivity.dataStorage.getWorkoutDays().get(i).getSets().get(j).equals(to_be_updated_set))
                    {
                        final int finalI = i;
                        final int finalJ = j;

                        Double reps = Double.parseDouble(String.valueOf(et_reps.getText()));
                        Double weight = Double.parseDouble(String.valueOf(et_weight.getText()));

                        // Create temp set
                        WorkoutSet set = new WorkoutSet();
                        set.setComment(to_be_updated_set.getComment());
                        set.setExerciseName(to_be_updated_set.getExerciseName());
                        set.setCategory(to_be_updated_set.getCategory());
                        set.setId(to_be_updated_set.getId());
                        set.setReps(reps);
                        set.setWeight(weight);

                        if(sharedPreferences.isOfflineMode())
                        {
                            updateSet(finalI, finalJ, reps, weight);
                        }
                        else
                        {
                            final LoadingDialog loadingDialog = new LoadingDialog(AddExerciseActivity.this);
                            loadingDialog.loadingAlertDialog();

                            WorkoutSetsApi workoutSetsApi = new WorkoutSetsApi(getApplicationContext(), getString(R.string.API_ENDPOINT));
                            workoutSetsApi.updateWorkoutSet(set, new Callback() {
                                @Override
                                public void onFailure(Call call, IOException e)
                                {
                                    loadingDialog.dismissDialog();
                                    showSnackbarMessage(e.toString());
                                }

                                @Override
                                public void onResponse(Call call, okhttp3.Response response) throws IOException {

                                    loadingDialog.dismissDialog();

                                    if (200 == response.code())
                                    {
                                        updateSet(finalI, finalJ, reps, weight);
                                    }
                                    else
                                    {
                                        showSnackbarMessage(response.message().toString());
                                    }
                                }
                            });
                        }
                        break;
                    }
                }
            }
        }

    }


    public void showSnackbarMessage(String message)
    {
        runOnUiThread(() -> {
            SnackBarWithMessage snackBarWithMessage = new SnackBarWithMessage(AddExerciseActivity.this);
            // Retour Romain 07/09/2026 : messages lies aux series (Set Added/Updated/
            // Deleted, Comment Logged...) affiches en haut de l'ecran plutot qu'en bas
            // - voir SnackBarWithMessage.showSnackbarAtTop().
            snackBarWithMessage.showSnackbarAtTop(message);
        });
    }

    public void updateSet(Integer finalI, Integer finalJ, Double reps, Double weight)
    {
        WorkoutSet updatedSet = MainActivity.dataStorage.getWorkoutDays().get(finalI).getSets().get(finalJ);

        // Retour UAT Romain 21/09/2026 (US 1.7) : "j'ai update 8 au lieu de 9 [...] je
        // dismiss" - le message "Set Updated" n'avait qu'un bouton "Dismiss" qui se
        // contentait de se fermer, sans annuler la modification. On garde donc les
        // valeurs d'AVANT pour que le bouton devienne un vrai "Undo" (meme principe que
        // "Set Deleted" + Undo). Les valeurs prevues (plannedReps/plannedWeight) ne sont
        // jamais touchees par un Update : l'ecart Prevu/Realise se recalcule tout seul.
        final Double previousReps = updatedSet.getReps();
        final Double previousWeight = updatedSet.getWeight();

        updatedSet.setReps(reps);
        updatedSet.setWeight(weight);

        // Manually update data because of bad design choices
        MainActivity.dataStorage.getWorkoutDays().get(finalI).UpdateData();

        // Let the user know I guess
        runOnUiThread(() -> {
            SnackBarWithMessage snackBarWithMessage = new SnackBarWithMessage(AddExerciseActivity.this);
            snackBarWithMessage.showSnackbarWithUndoAtTop("Set Updated", () -> undoUpdateSet(updatedSet, previousReps, previousWeight));
            updateTodaysExercises();
        });


        bt_save.setText("Save");
        bt_clear.setText("Clear");
        AddExerciseActivity.isEditMode = false;
    }

    // Annule un "Set Updated" (bouton "Undo" du message, retour UAT Romain 21/09/2026) :
    // remet reps/poids d'avant la modification sur le MEME objet WorkoutSet (donc memes
    // id/commentaire/valeurs prevues), recalcule le jour et sauvegarde tout de suite -
    // comme undoDeleteSet(), en local uniquement (pas de re-synchronisation vers l'API).
    private void undoUpdateSet(WorkoutSet set, Double previousReps, Double previousWeight)
    {
        set.setReps(previousReps);
        set.setWeight(previousWeight);

        int day_position = MainActivity.dataStorage.getDayPosition(set.getDate());
        if (day_position >= 0)
        {
            MainActivity.dataStorage.getWorkoutDays().get(day_position).UpdateData();
        }

        MainActivity.dataStorage.saveWorkoutData(getApplicationContext());

        // Let backup service know that something has changed
        MainActivity.autoBackupRequired = true;
        com.example.verifit.SharedPreferences sharedPreferences = new com.example.verifit.SharedPreferences(getApplicationContext());
        sharedPreferences.save("true", "autoBackupRequired");

        runOnUiThread(() -> {
            showSnackbarMessage("Set Restored");
            updateTodaysExercises();
        });
    }

    public void addSetExistingWorkoutDay(WorkoutSet workoutSet, Integer position)
    {
        WorkoutDay workoutDay = MainActivity.dataStorage.getWorkoutDays().get(position);
        workoutDay.addSet(workoutSet);
        startOrResumeSessionTimer(workoutDay);
        updateViewAndShowMessage();
    }

    public void addSetNewWorkoutDay(WorkoutSet workoutSet)
    {
        WorkoutDay workoutDay = new WorkoutDay();
        workoutDay.addSet(workoutSet);
        startOrResumeSessionTimer(workoutDay);
        MainActivity.dataStorage.getWorkoutDays().add(workoutDay);
        updateViewAndShowMessage();
    }

    // Retour Romain 06/09/2026 (chrono de session) : demarre le chrono a la toute
    // premiere serie du jour. Si une nouvelle serie est loggee apres un Stop manuel, la
    // seance est consideree reprise (le Stop precedent est efface) plutot que de laisser
    // un chrono "arrete" pendant qu'un entrainement continue visiblement - voir le
    // commentaire sur WorkoutDay.SessionStartTimestamp pour le detail du choix. Sauvegarde
    // immediatement (comme les autres actions explicites de cet ecran, ex.
    // deleteSetLogic()) plutot que de compter sur le flag autoBackupRequired differe,
    // pour ne jamais perdre ce chrono si l'app est tuee juste apres.
    private void startOrResumeSessionTimer(WorkoutDay workoutDay)
    {
        if (workoutDay.getSessionStartTimestamp() == null)
        {
            // Reglage "Auto Start" (retour Romain 07/09/2026, dialogue Workout Time
            // Settings) : ne s'applique qu'au tout premier demarrage automatique d'une
            // nouvelle seance - PAS a la reprise apres un Stop manuel ci-dessous, qui
            // reste toujours automatique quelle que soit ce reglage (portee identique a
            // la description "Auto Start" de FitNotes lui-meme : demarrer AU DEBUT
            // d'une seance, pas la re-demarrer apres coup).
            if (!isSessionAutoStartEnabled())
            {
                return;
            }

            workoutDay.setSessionStartTimestamp(System.currentTimeMillis());
        }
        else if (workoutDay.getSessionEndTimestamp() != null)
        {
            workoutDay.setSessionEndTimestamp(null);
        }
        else
        {
            return;
        }

        MainActivity.dataStorage.saveWorkoutData(getApplicationContext());
    }


    public void updateViewAndShowMessage()
    {
        runOnUiThread(()->{
            updateTodaysExercises();
            refreshSessionTimerBar();
            refreshNavPanel();
            autoStartRestTimerIfEnabled();

            if (advanceToNextSupersetExerciseIfApplicable())
            {
                // Ecran deja remplace par le prochain exercice du superset - voir
                // le commentaire de la methode ci-dessous. Inutile d'afficher "Set
                // Added" ici, ca apparaitrait de facon incoherente pendant la
                // transition vers un autre exercice.
                return;
            }

            showSnackbarMessage("Set Added");
        });
    }

    // Superset (Vague 2 du plan de migration, retour Romain 07/09/2026) : si
    // l'exercice courant appartient a un groupe de superset actif (AutoAdvance a
    // true, voir DayActivity.groupSelectedExercises()) pour le jour affiche,
    // relance cet ecran directement sur le PROCHAIN exercice du groupe (en
    // bouclant sur le premier apres le dernier). Meme lorsque le Navigation Panel
    // existe desormais (retour Romain 18/09/2026, voir switchToExercise()
    // ci-dessous qui reutilise exactement ce meme patron de relance), ni lui ni
    // Verifit n'offrent de bascule d'exercice EN PLACE (sans changer d'ecran) - on
    // relance donc une nouvelle instance de AddExerciseActivity plutot que de
    // basculer en place, en demarrant la suivante puis en finissant l'instance
    // courante : la
    // pile de retour ne grossit donc pas a chaque serie (chaque exercice du
    // superset REMPLACE le precedent, exactement comme si l'utilisateur avait tape
    // directement dessus depuis DayActivity), et overridePendingTransition(0, 0)
    // evite l'animation de transition standard pour que l'enchainement paraisse
    // aussi immediat que possible. Retourne true si un enchainement a eu lieu
    // (l'appelant doit alors s'abstenir de tout traitement supplementaire sur
    // cette instance, qui va etre detruite).
    private boolean advanceToNextSupersetExerciseIfApplicable()
    {
        int position = MainActivity.dataStorage.getDayPosition(MainActivity.dateSelected);
        WorkoutDay day = (position >= 0) ? MainActivity.dataStorage.getWorkoutDays().get(position) : null;
        if (day == null)
        {
            return false;
        }

        SupersetGroup group = day.getSupersetGroupForExercise(exercise_name);
        if (group == null || !group.isAutoAdvance())
        {
            return false;
        }

        String nextExercise = group.getNextExercise(exercise_name);
        if (nextExercise == null || nextExercise.equals(exercise_name))
        {
            return false;
        }

        Intent intent = new Intent(this, AddExerciseActivity.class);
        intent.putExtra("exercise", nextExercise);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
        return true;
    }

    // --- Volet de navigation entre exercices (retour Romain 18/09/2026, "le meme que
    // sur FitNotes [...] naviguer entre les series au sein d'une seance") ---
    // Voir claude/fitnotes-feature-navigation-panel.md pour le cadrage/les User
    // Stories et le commentaire en tete de activity_add_exercise.xml pour la
    // structure de layout.

    // Mise en place du volet (appelee une seule fois, depuis onCreate()) :
    // ActionBarDrawerToggle relie le DrawerLayout a l'icone hamburger de l'ActionBar
    // (ouverture au tap, voir onOptionsItemSelected()) - le swipe depuis le bord
    // gauche fonctionne nativement des qu'un DrawerLayout a un enfant gravity="start"
    // (activity_add_exercise.xml), aucun code supplementaire necessaire. Les lignes
    // "ADD EXERCISE"/"HOME" du pied du volet reutilisent exactement les memes
    // patrons de navigation que le reste de l'app : ExercisesActivity (bouton "+" de
    // DayActivity) et le retour a MainActivity depuis la barre de navigation basse
    // (ExercisesActivity.onNavigationItemSelected()), pour rester coherent avec le
    // reste de l'app plutot que d'inventer un nouveau comportement.
    private void initNavPanel()
    {
        drawerLayout = findViewById(R.id.drawer_layout);
        tvNavPanelHeader = findViewById(R.id.tv_nav_panel_header);
        recyclerViewNavPanel = findViewById(R.id.recycler_view_nav_panel);
        recyclerViewNavPanel.setLayoutManager(new LinearLayoutManager(this));

        // Retour Romain 24/09/2026 : "je dois slide 3/4 fois pour arriver a ouvrir le
        // volet" - le bouton hamburger de la toolbar (cense ouvrir le volet au tap,
        // cf commentaire plus haut) ne s'affichait en fait jamais. Cause : l'icone
        // dessinee par ActionBarDrawerToggle se pose dans le slot "home" de
        // l'ActionBar, qui reste invisible tant que setDisplayHomeAsUpEnabled()/
        // setHomeButtonEnabled() n'ont jamais ete appeles sur cette Activity (jamais
        // le cas ici avant ce correctif) - seul le swipe depuis le bord gauche
        // (comportement natif du DrawerLayout) fonctionnait donc, d'ou la difficulte
        // rapportee.
        if (getSupportActionBar() != null)
        {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        navPanelDrawerToggle = new ActionBarDrawerToggle(
                this, drawerLayout, R.string.nav_panel_open, R.string.nav_panel_close);
        drawerLayout.addDrawerListener(navPanelDrawerToggle);
        navPanelDrawerToggle.syncState();

        View rowAddExercise = findViewById(R.id.row_nav_panel_add_exercise);
        rowAddExercise.setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            Intent in = new Intent(AddExerciseActivity.this, ExercisesActivity.class);
            startActivity(in);
        });

        View rowHome = findViewById(R.id.row_nav_panel_home);
        rowHome.setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            Intent in = new Intent(AddExerciseActivity.this, MainActivity.class);
            startActivity(in);
            overridePendingTransition(0, 0);
        });

        refreshNavPanel();
    }

    // Repeuple la liste du volet (nombre d'exercices/de series, exercice courant
    // surligne) - a appeler a chaque fois que l'etat peut avoir change (onCreate() via
    // initNavPanel(), onResume(), a chaque serie loggee via updateViewAndShowMessage()).
    private void refreshNavPanel()
    {
        if (drawerLayout == null)
        {
            return;
        }

        int day_position = MainActivity.dataStorage.getDayPosition(MainActivity.dateSelected);
        WorkoutDay day = (day_position >= 0) ? MainActivity.dataStorage.getWorkoutDays().get(day_position) : null;
        ArrayList<WorkoutExercise> exercises = (day != null) ? day.getExercises() : new ArrayList<>();

        int count = exercises.size();
        tvNavPanelHeader.setText(count + (count == 1 ? " EXERCISE" : " EXERCISES"));

        if (navPanelExerciseAdapter == null)
        {
            navPanelExerciseAdapter = new NavPanelExerciseAdapter(this, exercises, exercise_name);
            navPanelExerciseAdapter.setOnStartDragListener(viewHolder -> {
                if (navPanelItemTouchHelper != null)
                {
                    navPanelItemTouchHelper.startDrag(viewHolder);
                }
            });
            navPanelExerciseAdapter.setOnExerciseClickListener(this::switchToExercise);
            recyclerViewNavPanel.setAdapter(navPanelExerciseAdapter);

            // Reorganisation par glisser-depose (drag & drop de la poignee, meme
            // mecanique que DayActivity/DayExerciseAdapter cote resume du jour - voir
            // persistNavPanelOrder() ci-dessous) : une seule persistence a la fin du
            // geste (clearView()), jamais a chaque etape intermediaire (onMove()).
            navPanelItemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                    ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0)
            {
                @Override
                public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder source, @NonNull RecyclerView.ViewHolder target)
                {
                    int from = source.getAdapterPosition();
                    int to = target.getAdapterPosition();

                    if (navPanelDragStartPosition == -1)
                    {
                        navPanelDragStartPosition = from;
                    }

                    navPanelExerciseAdapter.moveItem(from, to);
                    return true;
                }

                @Override
                public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction)
                {
                    // Swipe non utilise - drag uniquement (poignee dediee).
                }

                @Override
                public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder viewHolder)
                {
                    super.clearView(rv, viewHolder);

                    int finalPosition = viewHolder.getAdapterPosition();
                    if (navPanelDragStartPosition != -1 && finalPosition != -1 && finalPosition != navPanelDragStartPosition)
                    {
                        persistNavPanelOrder(navPanelDragStartPosition, finalPosition);
                    }
                    navPanelDragStartPosition = -1;
                }
            });
            navPanelItemTouchHelper.attachToRecyclerView(recyclerViewNavPanel);
        }
        else
        {
            navPanelExerciseAdapter.updateData(exercises, exercise_name);
        }
    }

    // Persiste l'ordre final d'un geste de drag dans le volet (meme convention que
    // DayActivity.persistExerciseOrder()) - une seule fois par geste.
    private void persistNavPanelOrder(int fromPosition, int toPosition)
    {
        int day_position = MainActivity.dataStorage.getDayPosition(MainActivity.dateSelected);
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

    // Changement d'exercice depuis le volet (US-6 du cadrage) : tap sur une ligne ->
    // relance cet ecran sur l'exercice choisi. Reutilise exactement le meme patron de
    // relance que advanceToNextSupersetExerciseIfApplicable() ci-dessus (Intent +
    // finish(), overridePendingTransition(0, 0)) plutot que d'en inventer un nouveau -
    // la pile de retour ne grossit donc pas a chaque changement d'exercice depuis le
    // volet. Aucun effet si l'exercice tape est deja celui affiche (evite de recharger
    // l'ecran pour rien, ex. re-tap accidentel sur la ligne surlignee).
    private void switchToExercise(String exerciseName)
    {
        drawerLayout.closeDrawer(GravityCompat.START);

        if (exerciseName == null || exerciseName.equals(exercise_name))
        {
            return;
        }

        Intent intent = new Intent(this, AddExerciseActivity.class);
        intent.putExtra("exercise", exerciseName);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }

    // Retrouve le WorkoutDay du jour affiche (peut etre null si aucune serie n'a encore
    // ete loggee aujourd'hui) et met a jour l'affichage du chrono de session en
    // consequence - a appeler a chaque fois que l'etat peut avoir change (onResume(),
    // nouvelle serie loggee, Stop/Resume manuel...).
    private void refreshSessionTimerBar()
    {
        int position = MainActivity.dataStorage.getDayPosition(MainActivity.dateSelected);
        WorkoutDay day = (position >= 0) ? MainActivity.dataStorage.getWorkoutDays().get(position) : null;

        sessionTimerTicker.setWorkoutDay(day);
    }

    // Start/Stop/Resume de la session (retour Romain 06/09/2026) : "il faut que je
    // puisse y acceder [...] je dois pouvoir le controler" - sauvegarde immediatement,
    // meme raisonnement que startOrResumeSessionTimer() ci-dessus. N'est plus appelee
    // que par le bouton du dialogue "Workout Time" (retour Romain 07/09/2026, la barre
    // persistante a perdu son propre bouton Stop/Resume - voir showWorkoutTimeDialog()
    // et activity_add_exercise.xml).
    private void toggleSessionTimer()
    {
        int position = MainActivity.dataStorage.getDayPosition(MainActivity.dateSelected);
        if (position < 0)
        {
            return;
        }

        WorkoutDay day = MainActivity.dataStorage.getWorkoutDays().get(position);

        if (day.getSessionStartTimestamp() == null)
        {
            // Demarrage manuel (retour Romain 07/09/2026) : plus besoin de logger puis
            // supprimer une serie bidon pour "debloquer" ce bouton.
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
    // Settings, capture FitNotes fournie) - lu par startOrResumeSessionTimer().
    // Active par defaut, comme sur FitNotes.
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

    // "Auto Start" du minuteur de REPOS (07/09/2026) - voir REST_TIMER_AUTO_START_PREF_KEY
    // ci-dessus pour le detail. Desactive par defaut.
    public boolean isRestTimerAutoStartEnabled()
    {
        SharedPreferences sharedPreferences = getSharedPreferences("shared preferences", MODE_PRIVATE);
        return sharedPreferences.getBoolean(REST_TIMER_AUTO_START_PREF_KEY, false);
    }

    public void setRestTimerAutoStartEnabled(boolean enabled)
    {
        SharedPreferences sharedPreferences = getSharedPreferences("shared preferences", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(REST_TIMER_AUTO_START_PREF_KEY, enabled);
        editor.apply();
    }

    // Appelee a chaque nouvelle serie loggee (voir updateViewAndShowMessage(), seul
    // appelant des deux methodes addSet*WorkoutDay ci-dessus) - demarre le minuteur de
    // repos avec la duree configuree si le reglage "Auto Start" est active, meme si un
    // repos etait deja en cours (une nouvelle serie signifie un nouveau repos qui
    // commence, cf. commentaire de REST_TIMER_AUTO_START_PREF_KEY). Reutilise
    // loadTimerDurationFromPrefs()/resetTimer()/startTimer() : fonctionnent deja sans
    // que la boite de dialogue "Timer" ait ete ouverte cette session (meme mecanique
    // que la barre de chrono de seance persistante), donc le minuteur se declenche en
    // arriere-plan (notification + bip a la fin, via RestTimerReceiver) sans avoir a
    // ouvrir ce dialogue.
    private void autoStartRestTimerIfEnabled()
    {
        if (!isRestTimerAutoStartEnabled())
        {
            return;
        }

        loadTimerDurationFromPrefs();
        resetTimer();
        startTimer();
    }

    // Dialogue complet "Workout Time" (retour Romain 07/09/2026, apres captures
    // FitNotes fournies - voir le OnClickListener de session_timer_bar dans
    // onCreate()) : Start Time / End Time / Duration, un bouton Stop/Resume qui
    // reutilise toggleSessionTimer(), et un menu "..." (Reglages / Annuler le
    // chrono - voir workout_time_dialog_menu.xml).
    private void showWorkoutTimeDialog()
    {
        int position = MainActivity.dataStorage.getDayPosition(MainActivity.dateSelected);
        WorkoutDay day = (position >= 0) ? MainActivity.dataStorage.getWorkoutDays().get(position) : null;

        if (day == null || day.getSessionStartTimestamp() == null)
        {
            // Rien a montrer tant qu'aucun chrono n'a demarre.
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

        // Duration : chrono dedie a ce TextView, demarre ici et arrete a la fermeture
        // du dialogue (setOnDismissListener plus bas) - voir le champ
        // workoutTimeDialogTicker.
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
            PopupMenu popupMenu = new PopupMenu(AddExerciseActivity.this, bt_overflow);
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

    // Sous-dialogue "Workout Time Settings" (menu "..." du dialogue Workout Time) -
    // seul reglage reproduit : "Auto Start" (voir workout_time_settings_dialog.xml
    // pour l'explication de l'absence volontaire d'un reglage "Auto Stop").
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

    // Confirmation avant d'annuler completement le chrono de la seance en cours
    // (retour Romain 07/09/2026, action "Cancel Timer" du menu "...", irreversible -
    // remet SessionStartTimestamp/SessionEndTimestamp a null, les series deja loggees
    // ne sont elles jamais touchees).
    private void confirmCancelSessionTimer(AlertDialog workoutTimeDialog)
    {
        // Wording en anglais (retour Romain 07/09/2026) : coherent avec le reste de
        // cette fonctionnalite ("Cancel Timer" vient du menu du dialogue Workout Time
        // lui-meme, cf. workout_time_dialog_menu.xml) plutot que de melanger les
        // langues dans une seule interaction.
        new AlertDialog.Builder(this)
                .setTitle("Cancel Timer")
                .setMessage("Cancel the timer for this workout? Sets already logged will not be deleted.")
                .setPositiveButton("Cancel Timer", (dlg, which) ->
                {
                    int position = MainActivity.dataStorage.getDayPosition(MainActivity.dateSelected);
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

    public Integer getSetIdFromResponse(okhttp3.Response response) throws IOException
    {
        String jsonString = response.body().string();
        Gson gson = new Gson();
        Type listType = new TypeToken<Integer>() {}.getType();
        Integer set_id = gson.fromJson(jsonString, listType);

        return  set_id;
    }

    // Clear (mode normal) / Delete (mode edition d'une serie existante). Retour Romain
    // 06/09/2026 : un tap sur une serie passe maintenant en mode edition (bouton du bas
    // = "Delete"), donc ce bouton doit vraiment supprimer la serie selectionnee dans ce
    // mode - avant ce correctif il ne faisait QUE vider les champs quel que soit son
    // libelle, la suppression n'etant accessible que via le menu Editer/Supprimer du
    // long-press.
    public void clickClear(View view)
    {
        if(isEditMode)
        {
            deleteSet(this);
        }
        else
        {
            bt_clear.setText("Clear");
            et_reps.setText("");
            et_weight.setText("");
        }
    }

    public static void deleteSet(Context ct)
    {
        // Let backup service know that something has changed
        MainActivity.autoBackupRequired = true;
        com.example.verifit.SharedPreferences sharedPreferences = new com.example.verifit.SharedPreferences(ct);
        sharedPreferences.save("true", "autoBackupRequired");

        // Show confirmation dialog  box
        // Prepare to show exercise dialog box
        LayoutInflater inflater = LayoutInflater.from(ct);
        View view1 = inflater.inflate(R.layout.delete_set_dialog,null);
        AlertDialog alertDialog = new AlertDialog.Builder(ct).setView(view1).create();

        Button bt_yes = view1.findViewById(R.id.bt_yes3);
        Button bt_no = view1.findViewById(R.id.bt_no3);

        // Dismiss dialog box
        bt_no.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                alertDialog.dismiss();
            }
        });

        // Actually Delete set and update local data structure
        bt_yes.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                // Get soon to be deleted set
                WorkoutSet to_be_removed_set = Todays_Exercise_Sets.get(Clicked_Set);

                // Find the set in main data structure and delete it
                for(int i = 0; i < MainActivity.dataStorage.getWorkoutDays().size(); i++)
                {
                    if(MainActivity.dataStorage.getWorkoutDays().get(i).getSets().contains(to_be_removed_set))
                    {
                        final int finalI = i;

                        if(sharedPreferences.isOfflineMode())
                        {
                            deleteSetLogic(ct, finalI, to_be_removed_set);
                            alertDialog.dismiss();
                        }
                        else
                        {
                            final LoadingDialog loadingDialog = new LoadingDialog((Activity) ct);
                            loadingDialog.loadingAlertDialog();

                            WorkoutSetsApi workoutSetsApi = new WorkoutSetsApi(ct, ct.getString(R.string.API_ENDPOINT));
                            workoutSetsApi.deleteWorkoutSet(to_be_removed_set, new Callback() {
                                @Override
                                public void onFailure(Call call, IOException e) {
                                    // Show error
                                    ((Activity) ct).runOnUiThread(() -> {
                                        loadingDialog.dismissDialog();
                                        alertDialog.dismiss();
                                        SnackBarWithMessage snackBarWithMessage = new SnackBarWithMessage(((Activity) ct));
                                        snackBarWithMessage.showSnackbar("Can't connect to server");
                                    });
                                }

                                @Override
                                public void onResponse(Call call, okhttp3.Response response) throws IOException {

                                    loadingDialog.dismissDialog();
                                    alertDialog.dismiss();

                                    if (200 == response.code())
                                    {
                                        deleteSetLogic(ct, finalI, to_be_removed_set);
                                    }
                                    else
                                    {
                                        ((Activity) ct).runOnUiThread(() -> {
                                            SnackBarWithMessage snackBarWithMessage = new SnackBarWithMessage(((Activity) ct));
                                            snackBarWithMessage.showSnackbar(response.message().toString());
                                        });
                                    }
                                }
                            });
                        }
                        break;
                    }
                }
            }
        });

        // Show delete confirmation dialog box
        alertDialog.show();
    }

    public static void deleteSetLogic(Context ct, int finalI, WorkoutSet to_be_removed_set)
    {
        // Retour Romain 06/09/2026 : capture de la position d'origine (avant suppression,
        // sinon indexOf() ne la retrouverait plus) pour que l'Undo puisse la remettre
        // exactement la ou elle etait - voir undoDeleteSet().
        int removedSetIndex = MainActivity.dataStorage.getWorkoutDays().get(finalI).getSets().indexOf(to_be_removed_set);

        // Bug signale par Romain 06/09/2026 ("si je supprime la derniere ligne du
        // dernier exercice ca fait planter l'app") : cet appel utilisait jusqu'ici
        // WorkoutDay.removeSet(), qui repose sur un assert (Sets.size() > 1) jamais
        // actif en production (les asserts Java sont desactives par defaut sur
        // Android) - supprimer l'unique serie restante du jour videait donc Sets sans
        // aucun garde-fou reel. Toutes les AUTRES suppressions de l'app (selection
        // multiple ici et dans DayActivity) avaient deja ete ecrites avec
        // WorkoutDay.removeSets() pour eviter exactement ce cas - seule cette
        // suppression au cas par cas (tap sur une serie -> mode edition -> "Delete")
        // ne l'utilisait pas. Alignee sur le meme motif : removeSets() (jamais
        // d'assert), puis verification explicite et suppression du jour devenu vide.
        WorkoutDay day = MainActivity.dataStorage.getWorkoutDays().get(finalI);
        day.removeSets(java.util.Collections.singletonList(to_be_removed_set));

        if (day.getSets().isEmpty())
        {
            MainActivity.dataStorage.getWorkoutDays().remove(finalI);
        }

        MainActivity.dataStorage.saveWorkoutData(ct);
        MainActivity.dataStorage.saveKnownExerciseData(ct);

        ((Activity) ct).runOnUiThread(() -> {
            // Retour Romain 06/09/2026 : la suppression vient toujours du bouton
            // "Delete" du mode edition (tap sur une serie -> editSet() -> ce bouton), il
            // faut donc sortir de ce mode et vider les champs du haut - sinon ils
            // gardent les valeurs de la serie qu'on vient d'effacer et un "Save"
            // ulterieur recreerait une nouvelle serie avec ces valeurs perimees.
            isEditMode = false;
            bt_save.setText("Save");
            et_reps.setText("");
            et_weight.setText("");

            // Retour Romain 06/09/2026 : "j'ai bien l'idee de garder le revert" - le
            // bouton "Dismiss" du message existant ne faisait que le fermer, sans
            // jamais annuler la suppression. Devient "Undo" et restaure vraiment la
            // serie (meme objet, donc mêmes id/commentaire/valeurs prevues) si cliqué.
            // Fonctionne en local uniquement (pas de re-synchronisation vers l'API
            // verifit_rs en mode compte en ligne, comme pour les autres mutations
            // ajoutees depuis - de toute facon vouee a disparaitre, voir la TODO).
            SnackBarWithMessage snackBarWithMessage = new SnackBarWithMessage(((Activity) ct));
            snackBarWithMessage.showSnackbarWithUndoAtTop("Set Deleted", () -> undoDeleteSet(ct, to_be_removed_set, removedSetIndex));
            updateTodaysExercises();
        });
    }

    // Restaure une serie tout juste supprimee (bouton "Undo" du message "Set Deleted"),
    // en la rajoutant au bon WorkoutDay - recree ce jour s'il a ete supprime entre
    // temps parce que c'etait sa derniere serie (voir le nettoyage juste au-dessus dans
    // deleteSetLogic()). Retour Romain 06/09/2026 : remise a sa position d'origine
    // (originalIndex, capture avant suppression dans deleteSetLogic()) plutot qu'en
    // derniere position comme le faisait addSet().
    private static void undoDeleteSet(Context ct, WorkoutSet removedSet, int originalIndex)
    {
        int day_position = MainActivity.dataStorage.getDayPosition(removedSet.getDate());

        if (day_position >= 0)
        {
            MainActivity.dataStorage.getWorkoutDays().get(day_position).insertSetAt(originalIndex, removedSet);
        }
        else
        {
            WorkoutDay workoutDay = new WorkoutDay();
            workoutDay.addSet(removedSet);
            MainActivity.dataStorage.getWorkoutDays().add(workoutDay);
        }

        MainActivity.dataStorage.saveWorkoutData(ct);
        MainActivity.dataStorage.saveKnownExerciseData(ct);

        ((Activity) ct).runOnUiThread(() -> {
            SnackBarWithMessage snackBarWithMessage = new SnackBarWithMessage(((Activity) ct));
            snackBarWithMessage.showSnackbarAtTop("Set Restored");
            updateTodaysExercises();
        });
    }

    // --- Multi-select delete (retour Romain 05/09/2026) ---
    // Entrée dans le mode sélection via une ActionMode dédiée plutôt qu'en réinterprétant
    // le tap/long-press existants (tap = édition d'une série, long-press = son
    // réordonnement par glisser-déposer, retour Romain 06/09/2026).
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
                inflater.inflate(R.menu.set_selection_action_menu, menu);
                mode.setTitle("0 selected");
                workoutSetAdapter2.enterSelectionMode();
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (item.getItemId() == R.id.delete_selected)
                {
                    confirmDeleteSelectedSets(mode);
                    return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                workoutSetAdapter2.exitSelectionMode();
                selectionActionMode = null;
            }
        });
    }

    private void confirmDeleteSelectedSets(ActionMode mode)
    {
        List<WorkoutSet> selected = workoutSetAdapter2.getSelectedSets();

        if (selected.isEmpty())
        {
            Toast.makeText(this, "No set selected", Toast.LENGTH_SHORT).show();
            return;
        }

        // Réutilise delete_set_dialog.xml (même confirmation que la suppression d'une
        // seule série), juste avec un libellé qui reflète le nombre sélectionné.
        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.delete_set_dialog, null);
        AlertDialog alertDialog = new AlertDialog.Builder(this).setView(view).create();

        TextView title = view.findViewById(R.id.tv_date);
        title.setText(selected.size() + " sets selected. Delete them?");

        Button bt_yes = view.findViewById(R.id.bt_yes3);
        Button bt_no = view.findViewById(R.id.bt_no3);

        bt_no.setOnClickListener(v -> alertDialog.dismiss());

        bt_yes.setOnClickListener(v -> {
            alertDialog.dismiss();
            deleteSelectedSets(selected, mode);
        });

        alertDialog.show();
    }

    private void deleteSelectedSets(List<WorkoutSet> setsToDelete, ActionMode mode)
    {
        // Let backup service know that something has changed
        MainActivity.autoBackupRequired = true;
        com.example.verifit.SharedPreferences sharedPreferences = new com.example.verifit.SharedPreferences(getApplicationContext());
        sharedPreferences.save("true", "autoBackupRequired");

        if (sharedPreferences.isOfflineMode())
        {
            deleteSetsLocally(getApplicationContext(), setsToDelete);
            showSnackbarMessage(setsToDelete.size() + " sets deleted");
            mode.finish();
        }
        else
        {
            final LoadingDialog loadingDialog = new LoadingDialog(AddExerciseActivity.this);
            loadingDialog.loadingAlertDialog();

            // /sets/bulk : même endpoint déjà utilisé pour la suppression d'un exercice
            // entier (ExerciseAdapter.locallyDeleteExercise) - un seul appel réseau pour
            // toute la sélection, pas un par série.
            WorkoutSetsApi workoutSetsApi = new WorkoutSetsApi(getApplicationContext(), getString(R.string.API_ENDPOINT));
            workoutSetsApi.deleteWorkoutSets(setsToDelete, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    loadingDialog.dismissDialog();
                    showSnackbarMessage("Can't connect to server");
                }

                @Override
                public void onResponse(Call call, okhttp3.Response response) throws IOException {
                    loadingDialog.dismissDialog();

                    if (200 == response.code())
                    {
                        deleteSetsLocally(getApplicationContext(), setsToDelete);
                        runOnUiThread(() -> {
                            showSnackbarMessage(setsToDelete.size() + " sets deleted");
                            mode.finish();
                        });
                    }
                    else
                    {
                        showSnackbarMessage(response.message());
                    }
                }
            });
        }
    }

    // Local removal for a batch of sets, all belonging to the current exercise/date
    // (Todays_Exercise_Sets is already scoped to both) - so there is exactly one
    // WorkoutDay to touch, unlike deleteExerciseGetSets which spans every day.
    // WorkoutDay.removeSets() does one UpdateData() for the whole batch rather than via
    // WorkoutDay.removeSet() per item, both to avoid recomputing it N times and to
    // sidestep removeSet()'s assert (size > 1) when the selection empties the day down
    // to its last set.
    private static void deleteSetsLocally(Context ct, List<WorkoutSet> setsToDelete)
    {
        int day_position = MainActivity.dataStorage.getDayPosition(MainActivity.dateSelected);

        if (day_position >= 0)
        {
            WorkoutDay day = MainActivity.dataStorage.getWorkoutDays().get(day_position);
            day.removeSets(setsToDelete);

            if (day.getSets().isEmpty())
            {
                MainActivity.dataStorage.getWorkoutDays().remove(day_position);
            }
        }

        MainActivity.dataStorage.saveWorkoutData(ct);
        MainActivity.dataStorage.saveKnownExerciseData(ct);

        updateTodaysExercises();
    }

    public static void editSet(AddExerciseWorkoutSetAdapter.MyViewHolder holder, View view, int position)
    {
        System.out.println("Edit Set on position " + position + " clicked");

        AddExerciseActivity.bt_clear.setText("Delete");
        AddExerciseActivity.bt_save.setText("Update");

        // Populate Edit Texts
        AddExerciseActivity.Clicked_Set = position;
        UpdateViewOnClick();

        AddExerciseActivity.isEditMode = true;

        // Rafraichit la liste pour faire apparaitre la surbrillance de cette ligne
        // (retour Romain 07/09/2026, "je ne sais pas sur quelle ligne je me trouve") -
        // voir AddExerciseWorkoutSetAdapter.onBindViewHolder().
        workoutSetAdapter2.notifyDataSetChanged();
    }

    // Desselectionne la serie en cours d'edition (retour Romain 07/09/2026, retap sur
    // une ligne deja selectionnee - voir AddExerciseWorkoutSetAdapter, le
    // OnClickListener de cardView) : remet les champs et les boutons a l'etat neutre
    // ("nouvelle serie"), sans rien modifier ni supprimer. Vider et_reps/et_weight ici
    // masque aussi automatiquement Save/Clear (voir updateSaveClearButtonsVisibility(),
    // declenchee par le TextWatcher sur ces deux champs).
    public static void cancelEditSet()
    {
        AddExerciseActivity.isEditMode = false;
        AddExerciseActivity.bt_save.setText("Save");
        AddExerciseActivity.bt_clear.setText("Clear");
        AddExerciseActivity.et_reps.setText("");
        AddExerciseActivity.et_weight.setText("");

        workoutSetAdapter2.notifyDataSetChanged();
    }

    // Allege l'IHM (retour Romain 07/09/2026, "les boutons UPDATE et DELETE de la
    // serie ne soit visible que si on set une valeur dans weight and reps") : branche
    // Save/Update et Clear/Delete sur le contenu des deux champs, pour qu'ils ne
    // s'affichent que quand Weight ET Reps sont renseignes - a l'appel initial (fields
    // vides au demarrage de l'ecran) comme a chaque frappe/effacement.
    private void wireSaveClearButtonsVisibility()
    {
        TextWatcher watcher = new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s)
            {
                updateSaveClearButtonsVisibility();
            }
        };

        et_weight.addTextChangedListener(watcher);
        et_reps.addTextChangedListener(watcher);

        updateSaveClearButtonsVisibility();
    }

    // Grace au TextWatcher ci-dessus, tout setText() programmatique sur et_weight/
    // et_reps (+/-, Clear, Delete, cancelEditSet(), pre-remplissage d'edition...)
    // reevalue automatiquement cette visibilite, sans appel manuel supplementaire.
    private void updateSaveClearButtonsVisibility()
    {
        boolean hasValues = !et_weight.getText().toString().trim().isEmpty()
                && !et_reps.getText().toString().trim().isEmpty();

        int visibility = hasValues ? View.VISIBLE : View.GONE;
        bt_save.setVisibility(visibility);
        bt_clear.setVisibility(visibility);
    }

    // Update this activity when a set is clicked
    public static void UpdateViewOnClick()
    {
        // Get selected set
        WorkoutSet clicked_set = Todays_Exercise_Sets.get(AddExerciseActivity.Clicked_Set);

        // Update Edit Texts
        et_weight.setText(clicked_set.getWeight().toString());
        et_reps.setText(String.valueOf(clicked_set.getReps().intValue()));
    }

    // Save Changes in main data structure, save data structure in shared preferences
    @Override
    protected void onStop() {
        super.onStop();
        // Sort Before Saving
        MainActivity.dataStorage.sortWorkoutDaysDate();

        // Actually Save Changes in shared preferences
        MainActivity.dataStorage.saveWorkoutData(getApplicationContext());

        // User cannot modify data structures, thus we can let service auto backup without race conditions
        MainActivity.inAddExerciseActivity = false;

        com.example.verifit.SharedPreferences sharedPreferences = new com.example.verifit.SharedPreferences(getApplicationContext());
        sharedPreferences.save("false", "inAddExerciseActivity");
    }

    // Do I even need to explain this?
    public void clickPlusWeight(View view)
    {
        if(!et_weight.getText().toString().isEmpty())
        {
            Double weight = Double.parseDouble(et_weight.getText().toString());
            weight = weight + 1;
            et_weight.setText(weight.toString());
        }
        else
        {
            et_weight.setText("1.0");
        }

    }

    // Do I even need to explain this?
    public void clickPlusReps(View view)
    {
        if(!et_reps.getText().toString().isEmpty())
        {
            int reps = Integer.parseInt(et_reps.getText().toString());
            reps = reps + 1;
            et_reps.setText(String.valueOf(reps));
        }
        else
        {
            et_reps.setText("1");
        }

    }

    // Do I even need to explain this?
    public void clickMinusWeight(View view)
    {
        if(!et_weight.getText().toString().isEmpty())
        {
            Double weight = Double.parseDouble(et_weight.getText().toString());
            weight = weight - 1;
            if(weight < 0)
            {
                weight = 0.0;
            }
            et_weight.setText(weight.toString());
        }
    }

    // Do I even need to explain this?
    public void clickMinusReps(View view)
    {
        if(!et_reps.getText().toString().isEmpty())
        {
            int reps = Integer.parseInt(et_reps.getText().toString());
            reps = reps - 1;
            if(reps < 0)
            {
                reps = 0;
            }
            et_reps.setText(String.valueOf(reps));
        }

    }

    // Handles Intent Stuff
    public void initActivity()
    {
        Intent in = getIntent();
        exercise_name = in.getStringExtra("exercise");
        getSupportActionBar().setTitle(exercise_name);
    }

    // Updates Local Data Structure
    public static void updateTodaysExercises()
    {
        // Clear since we don't want duplicates
        Todays_Exercise_Sets.clear();

        // Find Sets for a specific date and exercise
        for(int i = 0; i < MainActivity.dataStorage.getWorkoutDays().size(); i++)
        {
            // If date matches
            if(MainActivity.dataStorage.getWorkoutDays().get(i).getDate().equals(MainActivity.dateSelected))
            {
                for(int j = 0; j < MainActivity.dataStorage.getWorkoutDays().get(i).getSets().size(); j++)
                {
                    // If exercise matches
                    if(AddExerciseActivity.exercise_name.equals(MainActivity.dataStorage.getWorkoutDays().get(i).getSets().get(j).getExerciseName()))
                    {
                        Todays_Exercise_Sets.add(MainActivity.dataStorage.getWorkoutDays().get(i).getSets().get(j));
                    }
                }
            }
        }

        bt_clear.setText("Clear");

        // Retour Romain 11/09/2026 (badge Personal Record) : re-derive apres chaque
        // ajout/edition/suppression de serie, puisqu'une serie qui vient d'etre loggee
        // (ou dont le poids/les reps viennent d'etre modifies) peut desormais etre - ou
        // ne plus etre - un record. Doit precéder notifyDataSetChanged() pour que le
        // premier rendu voie deja le bon etat des badges.
        AddExerciseActivity.workoutSetAdapter2.refreshPRSets(AddExerciseActivity.exercise_name);

        // Update Recycler View
        AddExerciseActivity.workoutSetAdapter2.notifyDataSetChanged();
    }

    // Initialize Recycler View Object
    public void initrecyclerView()
    {
        // Clear since we don't want duplicates
        Todays_Exercise_Sets.clear();

        // Find Sets for a specific date and exercise
        for(int i = 0; i < MainActivity.dataStorage.getWorkoutDays().size(); i++)
        {
            // If date matches
            if(MainActivity.dataStorage.getWorkoutDays().get(i).getDate().equals(MainActivity.dateSelected))
            {
                for(int j = 0; j < MainActivity.dataStorage.getWorkoutDays().get(i).getSets().size(); j++)
                {
                    // If exercise matches
                    if(exercise_name.equals(MainActivity.dataStorage.getWorkoutDays().get(i).getSets().get(j).getExerciseName()))
                    {
                        Todays_Exercise_Sets.add(MainActivity.dataStorage.getWorkoutDays().get(i).getSets().get(j));
                    }
                }
            }
        }

        // Find Recycler View Object
        recyclerView = findViewById(R.id.recycler_view);
        workoutSetAdapter2 = new AddExerciseWorkoutSetAdapter(this,Todays_Exercise_Sets);
        workoutSetAdapter2.refreshPRSets(exercise_name);
        workoutSetAdapter2.setOnSelectionChangedListener(count -> {
            if (selectionActionMode != null)
            {
                selectionActionMode.setTitle(count + " selected");
            }
        });
        recyclerView.setAdapter(workoutSetAdapter2);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Réordonnement par glisser-déposer (retour Romain 06/09/2026) : contrairement
        // à DayExerciseAdapter/ViewPagerExerciseAdapter (poignée dédiée), le drag
        // démarre ici directement par appui long sur la ligne - la poignée n'a pas de
        // sens sur cet écran puisque le tap sert déjà à sélectionner la série pour
        // édition. Désactivé pendant le mode sélection multiple pour ne pas entrer en
        // conflit avec le (dé)cochage des séries.
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

                workoutSetAdapter2.moveItem(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction)
            {
                // Swipe non utilisé - drag uniquement.
            }

            @Override
            public boolean isLongPressDragEnabled()
            {
                return !workoutSetAdapter2.isSelectionMode();
            }

            @Override
            public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder viewHolder)
            {
                super.clearView(rv, viewHolder);

                int finalPosition = viewHolder.getAdapterPosition();
                if (dragStartPosition != -1 && finalPosition != -1 && finalPosition != dragStartPosition)
                {
                    persistSetOrder();
                }
                dragStartPosition = -1;
            }
        });
        itemTouchHelper.attachToRecyclerView(recyclerView);

        // Set Edit Text values to max set volume if possible
        initEditTexts();

        bt_clear.setText("Clear");

        // Initialize Integer position or else we get a crash
        AddExerciseActivity.Clicked_Set = Todays_Exercise_Sets.size() - 1;

    }

    // Persiste l'ordre final d'un geste de drag sur les séries (retour Romain
    // 06/09/2026) - une seule fois par geste, jamais à chaque étape intermédiaire (voir
    // ItemTouchHelper.Callback.onMove ci-dessus). Todays_Exercise_Sets a déjà le bon
    // ordre visuel à ce stade (mis à jour en continu par
    // AddExerciseWorkoutSetAdapter.moveItem()) ; il ne reste qu'à l'écrire dans le
    // WorkoutDay sous-jacent pour qu'il survive à un rafraîchissement de l'écran.
    private void persistSetOrder()
    {
        int day_position = MainActivity.dataStorage.getDayPosition(MainActivity.dateSelected);
        if (day_position < 0)
        {
            return;
        }

        WorkoutDay day = MainActivity.dataStorage.getWorkoutDays().get(day_position);
        day.reorderSetsForExercise(exercise_name, Todays_Exercise_Sets);
        MainActivity.dataStorage.saveWorkoutData(getApplicationContext());

        MainActivity.autoBackupRequired = true;
        com.example.verifit.SharedPreferences sharedPreferences = new com.example.verifit.SharedPreferences(getApplicationContext());
        sharedPreferences.save("true", "autoBackupRequired");
    }

    // Set Edit Text values to max set volume if sets exist
    public void initEditTexts()
    {
        Double max_weight = 0.0;
        int max_reps = 0;
        Double max_exercise_volume = 0.0;

        // Find Max Weight and Reps for a specific exercise
        for(int i = 0; i < MainActivity.dataStorage.getWorkoutDays().size(); i++)
        {
            for(int j = 0; j < MainActivity.dataStorage.getWorkoutDays().get(i).getSets().size(); j++)
            {
                if(MainActivity.dataStorage.getWorkoutDays().get(i).getSets().get(j).getVolume() > max_exercise_volume && MainActivity.dataStorage.getWorkoutDays().get(i).getSets().get(j).getExerciseName().equals(exercise_name))
                {
                    max_exercise_volume = MainActivity.dataStorage.getWorkoutDays().get(i).getSets().get(j).getVolume();
                    max_reps = (int)Math.round(MainActivity.dataStorage.getWorkoutDays().get(i).getSets().get(j).getReps());
                    max_weight = MainActivity.dataStorage.getWorkoutDays().get(i).getSets().get(j).getWeight();
                }
            }
        }

        // If never performed the exercise leave Edit Texts blank
        if(max_reps == 0 || max_weight == 0.0)
        {
            et_reps.setText("");
            et_weight.setText("");
        }else
        {
            et_reps.setText(String.valueOf(max_reps));
            et_weight.setText(max_weight.toString());
        }
    }

    // Retour Romain 07/09/2026 (suite du correctif du titre tronque) : items de la
    // barre d'outils pouvant etre "epingles" a cote du menu "..." via le reglage
    // "Toolbar Settings" (voir showToolbarSettingsDialog()) - le reste des 6 items du
    // menu (add_exercise_activity_menu.xml) reste dans l'overflow par defaut.
    private static final int[] PINNABLE_TOOLBAR_ITEM_IDS = {
            R.id.history, R.id.graph, R.id.rep_range_history, R.id.timer, R.id.comment, R.id.select_sets
    };

    private static final String TOOLBAR_PIN_PREF_PREFIX = "toolbar_pinned_";

    private boolean isToolbarItemPinned(int itemId)
    {
        SharedPreferences sharedPreferences = getSharedPreferences("shared preferences", MODE_PRIVATE);
        return sharedPreferences.getBoolean(TOOLBAR_PIN_PREF_PREFIX + getResources().getResourceEntryName(itemId), false);
    }

    private void setToolbarItemPinned(int itemId, boolean pinned)
    {
        SharedPreferences sharedPreferences = getSharedPreferences("shared preferences", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(TOOLBAR_PIN_PREF_PREFIX + getResources().getResourceEntryName(itemId), pinned);
        editor.apply();
    }

    // Menu Stuff
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.add_exercise_activity_menu,menu);

        // Applique le choix de Romain (reglage "Toolbar Settings") par-dessus la
        // valeur par defaut "never" du XML - un item epingle redevient "always",
        // visible directement a cote du menu "..." plutot que dans l'overflow.
        for (int itemId : PINNABLE_TOOLBAR_ITEM_IDS)
        {
            MenuItem menuItem = menu.findItem(itemId);
            if (menuItem != null)
            {
                menuItem.setShowAsAction(isToolbarItemPinned(itemId)
                        ? MenuItem.SHOW_AS_ACTION_ALWAYS
                        : MenuItem.SHOW_AS_ACTION_NEVER);
            }
        }

        return super.onCreateOptionsMenu(menu);
    }

    // Dialogue "Toolbar Settings" (retour Romain 07/09/2026) : "il faudrait que j'ai un
    // settings [...] qui me demande quels boutons je veux display. Une fois selectionne,
    // ces boutons/icone serait visible juste a cote des 3 points (et donc mangerait un
    // peu de place sur le nom d'exo et c'est ok)." Une case a cocher par item
    // epinglable - chaque changement est applique tout de suite via
    // invalidateOptionsMenu() (pas besoin de rouvrir l'ecran pour voir l'effet).
    private void showToolbarSettingsDialog()
    {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.toolbar_settings_dialog, null);

        wireToolbarPinCheckbox(dialogView.findViewById(R.id.cb_toolbar_history), R.id.history);
        wireToolbarPinCheckbox(dialogView.findViewById(R.id.cb_toolbar_graph), R.id.graph);
        wireToolbarPinCheckbox(dialogView.findViewById(R.id.cb_toolbar_rep_range_history), R.id.rep_range_history);
        wireToolbarPinCheckbox(dialogView.findViewById(R.id.cb_toolbar_timer), R.id.timer);
        wireToolbarPinCheckbox(dialogView.findViewById(R.id.cb_toolbar_comment), R.id.comment);
        wireToolbarPinCheckbox(dialogView.findViewById(R.id.cb_toolbar_select_sets), R.id.select_sets);

        MaterialButton bt_close = dialogView.findViewById(R.id.bt_toolbar_settings_close);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        bt_close.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void wireToolbarPinCheckbox(CheckBox checkBox, int itemId)
    {
        checkBox.setChecked(isToolbarItemPinned(itemId));
        checkBox.setOnCheckedChangeListener((buttonView, isChecked) ->
        {
            setToolbarItemPinned(itemId, isChecked);
            invalidateOptionsMenu();
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item)
    {
        // Volet de navigation entre exercices (retour Romain 18/09/2026) : l'icone
        // hamburger (ActionBarDrawerToggle, voir initNavPanel()) remplace l'indicateur
        // "up" par defaut de l'ecran - a intercepter en premier, avant la chaine
        // d'items existante ci-dessous.
        if (navPanelDrawerToggle != null && navPanelDrawerToggle.onOptionsItemSelected(item))
        {
            return true;
        }

        // Select sets (multi-delete, retour Romain 05/09/2026)
        if(item.getItemId() == R.id.select_sets)
        {
            startSelectionMode();
        }

        // Timer
        else if(item.getItemId() == R.id.timer)
        {

            setupTimer();
        }

        // Exercise History
        else if(item.getItemId() == R.id.history)
        {
            return setupExerciseHistory(item);
        }

        // Retour Romain 06/09/2026 : "je voudrai pouvoir y acceder depuis la fiche de
        // l'exercice [...] un petit trophee comme sur fitnotes qui m'amene vers mon
        // tableau de PR" - historique des PR par nombre de reps pour cet exercice
        // (voir DataStorage.calculateRepRangeHistory()).
        else if(item.getItemId() == R.id.rep_range_history)
        {
            Intent intent = new Intent(this, RepRangeRecordsActivity.class);
            intent.putExtra(RepRangeRecordsActivity.EXTRA_EXERCISE_NAME, exercise_name);
            startActivity(intent);
        }

        // Exercise Stats Chart
        else if (item.getItemId() == R.id.graph) {


            // Prepare to show exercise history dialog box
            LayoutInflater inflater = LayoutInflater.from(AddExerciseActivity.this);
            View view = inflater.inflate(R.layout.exercise_graph_dialog, null);
            AlertDialog alertDialog = new AlertDialog.Builder(AddExerciseActivity.this)
                    .setView(view)
                    .create();

            // Get Chart Object
            LineChart lineChart = view.findViewById(R.id.lineChart);

            // Create Array List that will hold graph data
            ArrayList<Entry> volumeValues = new ArrayList<>();
            ArrayList<Entry> maxWeightValues = new ArrayList<>();
            ArrayList<Entry> totalRepsValues = new ArrayList<>();


            ArrayList<String> workoutDates = new ArrayList<>();
            ArrayList<String> workoutMonths = new ArrayList<>();

            int x = 0;

            // Get Exercise Volume
            for (int i = 0; i < MainActivity.dataStorage.getWorkoutDays().size(); i++) {
                for (int j = 0; j < MainActivity.dataStorage.getWorkoutDays().get(i).getExercises().size(); j++) {
                    WorkoutExercise currentExercise = MainActivity.dataStorage.getWorkoutDays().get(i).getExercises().get(j);

                    if (currentExercise.getExercise().equals(exercise_name)) {
                        volumeValues.add(new Entry(x, currentExercise.getVolume().floatValue()));

                        maxWeightValues.add(new Entry(x, currentExercise.getMaxWeight().floatValue()));

                        totalRepsValues.add(new Entry(x, currentExercise.getTotalReps().floatValue()));

                        // Add the date corresponding to the data point
                        workoutDates.add(MainActivity.dataStorage.getWorkoutDays().get(i).getDate());

                        // Add the month corresponding to the data point
                        String date = MainActivity.dataStorage.getWorkoutDays().get(i).getDate();
                        String month = getMonthFromDateString(date);
                        workoutMonths.add(month);

                        x++;
                    }
                }
            }

            if(x == 0)
            {
                SnackBarWithMessage snackBarWithMessage = new SnackBarWithMessage(AddExerciseActivity.this);
                snackBarWithMessage.showSnackbar("No data found");
                return super.onOptionsItemSelected(item);
            }


            // Chart Data Spinner
            Spinner graph_value_spinner = view.findViewById(R.id.graph_spinner);
            String[] graph_value_spinner_options = {"Volume", "Weight", "Reps"};
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, graph_value_spinner_options);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            graph_value_spinner.setAdapter(adapter);
            graph_value_spinner.setSelection(0);


            // Chart Timeframe Spinner
            Spinner graph_time_spinner = view.findViewById(R.id.graph_time_spinner);
            String[] graph_time_spinner_options = {"All Time", "Last Year", "6 months", "3 months", "1 month"};
            ArrayAdapter<String> adapter1 = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, graph_time_spinner_options);
            adapter1.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            graph_time_spinner.setAdapter(adapter1);
            graph_time_spinner.setSelection(0);


            graph_value_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                    String selectedOption1 = graph_time_spinner.getSelectedItem().toString();
                    String selectedOption2 = graph_value_spinner_options[i];

                    setupChartWithOptions(selectedOption1, selectedOption2, volumeValues, maxWeightValues,totalRepsValues, workoutDates, workoutMonths, alertDialog, lineChart);
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {
                    // Do nothing
                }
            });


            graph_time_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                    String selectedOption1 = graph_time_spinner_options[i];
                    String selectedOption2 = graph_value_spinner.getSelectedItem().toString();

                    setupChartWithOptions(selectedOption1, selectedOption2, volumeValues, maxWeightValues,totalRepsValues, workoutDates, workoutMonths, alertDialog, lineChart);
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {
                    // Do nothing
                }
            });


            // Default option is volume
            setupLineChart(alertDialog, volumeValues, lineChart, workoutMonths, workoutDates,"kg", false);
        }

        // Retour Romain 07/09/2026 : reglage "quels boutons je veux display" a cote du
        // menu "..." (voir showToolbarSettingsDialog() / onCreateOptionsMenu()).
        else if(item.getItemId() == R.id.toolbar_settings)
        {
            showToolbarSettingsDialog();
        }

        // Exercise Comments
        else if(item.getItemId() == R.id.comment)
        {
            // Prepare to show exercise history dialog box
            LayoutInflater inflater = LayoutInflater.from(AddExerciseActivity.this);
            View view = inflater.inflate(R.layout.add_exercise_comment_dialog,null);
            AlertDialog alertDialog = new AlertDialog.Builder(AddExerciseActivity.this).setView(view).create();


            bt_save_comment = view.findViewById(R.id.bt_save_comment);
            bt_clear_comment = view.findViewById(R.id.bt_clear_comment);
            et_exercise_comment = view.findViewById(R.id.et_exercise_comment);

            // Check if exercise exists (to show the comment if it has one)
            // Find if workout day already exists
            int exercise_position = MainActivity.dataStorage.getExercisePosition(MainActivity.dateSelected,exercise_name);

            // Exists, then show the comment
            if(exercise_position >= 0)
            {
                System.out.println("We can comment, exercise exists");

                int day_position = MainActivity.dataStorage.getDayPosition(MainActivity.dateSelected);

                String comment = MainActivity.dataStorage.getWorkoutDays().get(day_position).getExercises().get(exercise_position).getComment();

                et_exercise_comment.setText(comment);
            }



            bt_clear_comment.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {


                    clearComment();
                }
            });

            bt_save_comment.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    saveComment(alertDialog);
                }
            });

            // Show Chart Dialog box
            alertDialog.show();

        }

        return super.onOptionsItemSelected(item);
    }

    private void setupChartWithOptions(String selectedOption1, String selectedOption2, ArrayList<Entry> volumeValues, ArrayList<Entry> maxWeightValues,ArrayList<Entry> totalRepsValues, ArrayList<String> workoutDates, ArrayList<String> workoutMonths, AlertDialog alertDialog, LineChart lineChart){
        String label = new String();
        ArrayList<Entry> actualValues = new ArrayList<>();
        ArrayList<Entry> finalValues = new ArrayList<>();


        if(selectedOption2.equals("Weight"))
        {
            actualValues = maxWeightValues;
            label = "kg";
        }
        else if(selectedOption2.equals("Volume"))
        {
            actualValues = volumeValues;
            label = "kg";
        }
        else if(selectedOption2.equals("Reps"))
        {
            actualValues = totalRepsValues;
            label = "reps";
        }

        int index = -1;

        if(selectedOption1.equals("All Time"))
        {
            index = 0;
        }
        else if(selectedOption1.equals("Last Year"))
        {
            index = getIndexOfLastXMonths(workoutDates, 12);
        }
        else if(selectedOption1.equals("6 months"))
        {
            index = getIndexOfLastXMonths(workoutDates, 6);
        }
        else if(selectedOption1.equals("3 months"))
        {
            index = getIndexOfLastXMonths(workoutDates, 3);
        }
        else if(selectedOption1.equals("1 month"))
        {
            index = getIndexOfLastXMonths(workoutDates, 1);
        }

        // Show all
        if(index == -1)
        {
            index = actualValues.size();
        }

        finalValues = new ArrayList<>(actualValues.subList(index, actualValues.size()));
        setupLineChart(alertDialog, finalValues , lineChart, workoutMonths, workoutDates, label, true);
    }

    private int getIndexOfLastXMonths(ArrayList<String> workoutDates, int months){
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -months);
        Date oneMonthBefore = calendar.getTime();

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        String givenDate = dateFormat.format(oneMonthBefore);
        return findFirstDateAfter(workoutDates, givenDate);
    }

    public int findFirstDateAfter(ArrayList<String> workoutDates, String givenDate) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date givenDateObj;
        try {
            givenDateObj = dateFormat.parse(givenDate);
        } catch (ParseException e) {
            e.printStackTrace();
            return -1; // If the given date cannot be parsed
        }

        for (int i = 0; i < workoutDates.size(); i++) {
            Date currentDateObj;
            try {
                currentDateObj = dateFormat.parse(workoutDates.get(i));
            } catch (ParseException e) {
                e.printStackTrace();
                continue;
            }

            if (currentDateObj.after(givenDateObj)) {
                return i;
            }
        }

        return -1; // If no date is found after the given date
    }

    private String getMonthFromDateString(String dateString) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date date = sdf.parse(dateString);
            SimpleDateFormat monthFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
            return monthFormat.format(date);
        } catch (ParseException e)
        {
            e.printStackTrace();
            return "";
        }
    }


    public boolean setupExerciseHistory(MenuItem item) {
        // Prepare to show exercise history dialog box
        LayoutInflater inflater = LayoutInflater.from(AddExerciseActivity.this);
        View view = inflater.inflate(R.layout.exercise_history_dialog,null);
        AlertDialog alertDialog = new AlertDialog.Builder(AddExerciseActivity.this).setView(view).create();


        // Declare local data structure
        ArrayList<WorkoutExercise> allPerformedSessions = new ArrayList<>();

        // Find all performed sessions of a specific exercise and add them to local data structure
        for(int i = MainActivity.dataStorage.getWorkoutDays().size()-1; i >= 0; i--)
        {
            for(int j = 0; j < MainActivity.dataStorage.getWorkoutDays().get(i).getExercises().size(); j++)
            {
                if(MainActivity.dataStorage.getWorkoutDays().get(i).getExercises().get(j).getExercise().equals(exercise_name))
                {
                    allPerformedSessions.add(MainActivity.dataStorage.getWorkoutDays().get(i).getExercises().get(j));
                }
            }
        }

        if(allPerformedSessions.size() == 0){
            SnackBarWithMessage snackBarWithMessage = new SnackBarWithMessage(AddExerciseActivity.this);
            snackBarWithMessage.showSnackbar("No data found");
            return super.onOptionsItemSelected(item);
        }


        // Set Exercise Name
        TextView tv_exercise_name = view.findViewById(R.id.tv_exercise_name);
        tv_exercise_name.setText(exercise_name);


        // Set Exercise History Recycler View
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView_Exercise_History);
        ExerciseHistoryExerciseAdapter workoutExerciseAdapter4 = new ExerciseHistoryExerciseAdapter(AddExerciseActivity.this, allPerformedSessions);


        // Crash Here
        recyclerView.setAdapter(workoutExerciseAdapter4);
        recyclerView.setLayoutManager(new LinearLayoutManager(AddExerciseActivity.this));


        alertDialog.show();

        return super.onOptionsItemSelected(item);
    }


    public void setupLineChart(AlertDialog alertDialog, ArrayList<Entry> volumeValues, LineChart lineChart, ArrayList<String> workoutMonths, ArrayList<String> workoutDates, String label, boolean showYaxis) {
        LineDataSet volumeSet = new LineDataSet(volumeValues, label);
        LineData data = new LineData(volumeSet);

        // Style the line and the values
        volumeSet.setLineWidth(3f);
        volumeSet.setColor(ContextCompat.getColor(AddExerciseActivity.this, R.color.colorPrimary));
        volumeSet.setCircleColor(ContextCompat.getColor(AddExerciseActivity.this, R.color.colorPrimary));
        volumeSet.setCircleRadius(2f);
        volumeSet.setCircleHoleColor(ContextCompat.getColor(AddExerciseActivity.this, R.color.colorPrimary));
        volumeSet.setValueTextSize(10f);
        volumeSet.setValueTextColor(Color.BLACK);
//        volumeSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        // Remove circles around data points
        volumeSet.setDrawCircles(true);

        // Hide data values next to points
        volumeSet.setDrawValues(false);

        // Style the chart
        lineChart.setData(data);
        lineChart.getDescription().setEnabled(false);
        lineChart.setDrawGridBackground(false);
        lineChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        lineChart.getXAxis().setDrawGridLines(false);
        lineChart.getAxisLeft().setDrawGridLines(false);
        lineChart.getAxisRight().setEnabled(false);

        // Enable horizontal grid lines
        lineChart.getAxisLeft().setDrawGridLines(true);
        lineChart.getAxisLeft().setGridColor(Color.LTGRAY);
        lineChart.getAxisLeft().setGridLineWidth(1f);


        // Disable Y-axis line
        lineChart.getAxisLeft().setDrawAxisLine(false);
        lineChart.getAxisLeft().setDrawLabels(false);

        // Hide Y-axis values
        lineChart.getAxisLeft().setEnabled(true);
        lineChart.getAxisLeft().setTextSize(0f);
        lineChart.getXAxis().setEnabled(true);

        // Set the custom X-axis value formatter
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setValueFormatter(new MonthXAxisFormatter(workoutMonths));
        xAxis.setGranularity(2f); // Set minimum interval to 1
        xAxis.setGranularityEnabled(true); // Enable granularity

        xAxis.setLabelCount(5, false); // Display only 5 labels on the X-axis


        // Enable pinch zooming
        lineChart.setPinchZoom(true);

        // Enable scaling (zooming) on both X and Y axes
        lineChart.setScaleEnabled(true);

        lineChart.animateXY(1000, 1000, Easing.EaseInOutCubic);

        // Reset chart
        lineChart.fitScreen();
        lineChart.getViewPortHandler().refresh(new Matrix(), lineChart, true);
        lineChart.invalidate();


        // Zoom in to show the last X points
//        int pointsToShow = 10;
//        lineChart.setVisibleXRange(pointsToShow, pointsToShow);
//        lineChart.moveViewToX(volumeValues.size() - pointsToShow);


        // Style legend
        Legend legend = lineChart.getLegend();
        legend.setOrientation(Legend.LegendOrientation.VERTICAL);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.LEFT);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.CENTER);
        legend.setDrawInside(true);
        legend.setXOffset(30f); // Adjust this value to move the legend horizontally
        legend.setYOffset(-270); // Adjust this value to move the legend vertically
        legend.setTextSize(12f);
        legend.setTextColor(Color.BLACK);
        legend.setForm(Legend.LegendForm.LINE);
        legend.setFormLineWidth(3f);
        legend.setFormSize(14f);

        lineChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                popupChartAlertDialog(e, workoutMonths, label);
            }

            @Override
            public void onNothingSelected() {
                // Do nothing
            }
        });


        // Show Chart Dialog box
        alertDialog.show();
    }

    public void popupChartAlertDialog(Entry e, ArrayList<String> workoutMonths, String label) {
        float xValue = e.getX();

        // Dismiss the current dialog if it is showing
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(AddExerciseActivity.this);
        builder.setTitle(workoutMonths.get((int) xValue));

        LayoutInflater inflater = LayoutInflater.from(AddExerciseActivity.this);
        View dialogView = inflater.inflate(R.layout.custom_alert_dialog, null);
        builder.setView(dialogView);

        TextView messageTextView = dialogView.findViewById(R.id.message_text_view);
        messageTextView.setText(String.valueOf(e.getY()) + " " + label);

        AlertDialog dialog = builder.create();

        // Set custom width (in pixels)
        int customWidth = 600; // You can change this value to your desired width

        // Set the layout parameters
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.copyFrom(dialog.getWindow().getAttributes());
        layoutParams.width = customWidth;
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;

        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.WHITE));


        // Show the dialog
        dialog.show();

        // Apply the layout parameters
        dialog.getWindow().setAttributes(layoutParams);

        // Set the current dialog to the newly created dialog
        currentDialog = dialog;
    }

    public void setupTimer() {

        // Prepare to show timer dialog box
        LayoutInflater inflater = LayoutInflater.from(AddExerciseActivity.this);
        View view = inflater.inflate(R.layout.timer_dialog,null);
        AlertDialog alertDialog = new AlertDialog.Builder(AddExerciseActivity.this).setView(view).create();

        // Get Objects (use view because dialog box from menu)
        et_seconds = view.findViewById(R.id.et_seconds);
        minus_seconds = view.findViewById(R.id.minus_seconds);
        plus_seconds = view.findViewById(R.id.plus_seconds);
        bt_start = view.findViewById(R.id.bt_start);
        bt_reset = view.findViewById(R.id.bt_close);
        sb_volume = view.findViewById(R.id.sb_volume);
        sb_beep_duration = view.findViewById(R.id.sb_beep_duration);
        cb_rest_timer_auto_start = view.findViewById(R.id.cb_rest_timer_auto_start);

        // Barre de minuteur persistante (retour Romain 06/09/2026) : si le minuteur a ete
        // demarre/mis en pause depuis la barre persistante avant l'ouverture de ce
        // dialogue, bt_start doit refleter l'etat reel (sinon il affiche toujours "Start"
        // par defaut, meme si le minuteur tourne deja).
        updateTimerButtonsLabel();

        // Set default seconds value to 180 i.e 3 minutes
        if(!TimerRunning)
        {
            // Derive String value from chosen start time
            // et_seconds.setText(String.valueOf((int) START_TIME_IN_MILLIS /1000));
            loadSeconds();
        }
        else
        {
            updateCountDownText();
        }

        loadVolume();
        loadDuration();
        cb_rest_timer_auto_start.setChecked(isRestTimerAutoStartEnabled());
        cb_rest_timer_auto_start.setOnCheckedChangeListener(
                (buttonView, isChecked) -> setRestTimerAutoStartEnabled(isChecked));

        // Retour Romain 06/09/2026 : volume du bip de fin de repos reglable depuis
        // l'app ("ajuster le volume suivant le bruit ambiant du jour") - persiste des
        // que l'utilisateur relache le curseur (pas a chaque pixel de deplacement),
        // lu par RestTimerReceiver.playRestTimerBeep() au moment ou le repos se
        // termine (memes SharedPreferences que la duree du minuteur).
        sb_volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar)
            {
                saveVolume(seekBar.getProgress());
            }
        });

        // Retour Romain 06/09/2026 : "le bip est un peu court, possible de me laisser
        // l'editer dans l'app ?" - duree du bip reglable en ms, meme principe que
        // sb_volume ci-dessus (le curseur va de RestTimerReceiver.MIN_DURATION_MS a
        // MAX_DURATION_MS, cf loadDuration()/saveDuration() plus bas pour la conversion).
        sb_beep_duration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar)
            {
                saveDuration(seekBar.getProgress() + RestTimerReceiver.MIN_DURATION_MS);
            }
        });

        // Reset Timer Button
        bt_reset.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                resetTimer();
            }
        });

        // Start Timer Button
        bt_start.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                if(TimerRunning)
                {
                    pauseTimer();
                }
                else
                {
                    saveSeconds();
                    startTimer();
                }

            }
        });

        // Minus Button
        // Retour Romain 24/09/2026 : increment de 10s au lieu de 1s (trop lent a
        // ajuster pour un temps de repos, qui se regle typiquement par paliers de
        // 10-30s) - meme logique, juste le pas qui change.
        minus_seconds.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                if(!et_seconds.getText().toString().isEmpty())
                {
                    Double seconds  = Double.parseDouble(et_seconds.getText().toString());
                    seconds = seconds - 10;
                    if(seconds < 0)
                    {
                        seconds = 0.0;
                    }
                    int seconds_int = seconds.intValue();
                    et_seconds.setText(String.valueOf(seconds_int));
                }
            }
        });

        // Plus Button
        plus_seconds.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                if(!et_seconds.getText().toString().isEmpty())
                {
                    Double seconds  = Double.parseDouble(et_seconds.getText().toString());
                    seconds = seconds + 10;
                    if(seconds < 0)
                    {
                        seconds = 0.0;
                    }
                    int seconds_int = seconds.intValue();
                    et_seconds.setText(String.valueOf(seconds_int));
                }
            }
        });

        // Show Timer Dialog Box
        alertDialog.show();
    }


        // Makes necessary checks and saves comment
    public void saveComment(AlertDialog alertDialog)
    {
        // Let backup service know that something has changed
        MainActivity.autoBackupRequired = true;
        com.example.verifit.SharedPreferences sharedPreferences = new com.example.verifit.SharedPreferences(getApplicationContext());
        sharedPreferences.save("true", "autoBackupRequired");

        // Check if exercise exists (cannot comment on non-existant exercise)
        // Find if workout day already exists
        int exercise_position = MainActivity.dataStorage.getExercisePosition(MainActivity.dateSelected,exercise_name);

        if(exercise_position >= 0)
        {
            System.out.println("We can comment, exercise exists");
        }
        else
        {
            System.out.println("We can't comment, exercise doesn't exist");
            Toast.makeText(getApplicationContext(),"Can't comment without sets",Toast.LENGTH_SHORT).show();
            return;
        }

        String comment;

        if(et_exercise_comment.getText().toString().isEmpty())
        {
            comment = "";
        }
        else
        {
            comment = et_exercise_comment.getText().toString(); // Get user comment
        }

        // Get the date for today
        int day_position = MainActivity.dataStorage.getDayPosition(MainActivity.dateSelected);


        // Modify the data structure to add the comment
        MainActivity.dataStorage.getWorkoutDays().get(day_position).getExercises().get(exercise_position).setComment(comment);


        final int finalSize = MainActivity.dataStorage.getWorkoutDays().get(day_position).getExercises().get(exercise_position).getSets().size();

        // Also modify individual sets
        for(int i = 0; i < MainActivity.dataStorage.getWorkoutDays().get(day_position).getExercises().get(exercise_position).getSets().size(); i++)
        {
            final int finalI = i;

            WorkoutSet set_to_be_updated = MainActivity.dataStorage.getWorkoutDays().get(day_position).getExercises().get(exercise_position).getSets().get(i);
            set_to_be_updated.setComment(comment);

            if(sharedPreferences.isOfflineMode())
            {
                updateCommentInSet(day_position, exercise_position, finalI, finalSize, comment);
                alertDialog.dismiss();
            }
            else
            {
                final LoadingDialog loadingDialog = new LoadingDialog(AddExerciseActivity.this);
                loadingDialog.loadingAlertDialog();

                WorkoutSetsApi workoutSetsApi = new WorkoutSetsApi(getApplicationContext(), getString(R.string.API_ENDPOINT));
                workoutSetsApi.updateWorkoutSet(set_to_be_updated, new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e)
                    {
                        loadingDialog.dismissDialog();
                        alertDialog.dismiss();
                        showSnackbarMessage(e.toString());
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        loadingDialog.dismissDialog();
                        alertDialog.dismiss();

                        if(200 == response.code())
                        {
                            updateCommentInSet(day_position, exercise_position, finalI, finalSize, comment);

                        }
                        else
                        {
                            showSnackbarMessage(response.message().toString());
                        }
                    }
                });
            }
        }
    }

    public void updateCommentInSet(int day_position, int exercise_position, int finalI, int finalSize, String comment)
    {
        MainActivity.dataStorage.getWorkoutDays().get(day_position).getExercises().get(exercise_position).getSets().get(finalI).setComment(comment);

        runOnUiThread(() -> {
            updateTodaysExercises();
        });

        if (finalI == finalSize-1) // Show popup only when last set comment is saved
        {
            showSnackbarMessage("Comment Logged");
        }
    }


    // Makes necessary checks and clears comment
    public void clearComment()
    {
        // Let backup service know that something has changed
        MainActivity.autoBackupRequired = true;
        com.example.verifit.SharedPreferences sharedPreferences = new com.example.verifit.SharedPreferences(getApplicationContext());
        sharedPreferences.save("true", "autoBackupRequired");
        et_exercise_comment.setText("");
    }

    public void startTimer()
    {
        countDownTimer = new CountDownTimer(TimeLeftInMillis, 1000)
        {
            @Override
            public void onTick(long MillisUntilFinish)
            {
                TimeLeftInMillis = MillisUntilFinish;
                updateCountDownText();
            }

            @Override
            public void onFinish()
            {
                // Ce onFinish() ne pilote que l'affichage (bouton, texte) - la sonnerie
                // fiable vient de l'alarme systeme programmee ci-dessous par
                // scheduleTimerAlarm(), qui se declenche independamment (voir son
                // commentaire), meme si ce CountDownTimer a ete throttle/tue entre
                // temps. Cache aussi la barre persistante tout de suite (app restee au
                // premier plan) plutot que d'attendre RestTimerReceiver.onReceive()
                // (qui la nettoie de toute facon, y compris si l'app a ete tuee
                // entre-temps).
                TimerRunning = false;
                updateTimerButtonsLabel();
                RestTimerReceiver.clearPersistedEndTimestamp(AddExerciseActivity.this);
                RestTimerReceiver.cancelOngoingNotification(AddExerciseActivity.this);
                restTimerBarTicker.refresh();
            }
        }.start();

        TimerRunning = true;
        updateTimerButtonsLabel();

        // Meme instant de fin partage par l'alarme systeme (son fiable), la barre
        // persistante a l'ecran (RestTimerBarTicker) et la notification en direct
        // ci-dessous - une seule source, calculee une fois, pour eviter tout ecart
        // entre les trois.
        long endTimestamp = System.currentTimeMillis() + TimeLeftInMillis;

        scheduleTimerAlarm(endTimestamp);

        // Barre persistante (retour Romain 24/09/2026) : persiste l'instant de fin pour
        // que RestTimerBarTicker le retrouve depuis n'importe quel ecran/redemarrage -
        // voir le commentaire du champ restTimerBarTicker plus haut.
        RestTimerReceiver.persistEndTimestamp(this, endTimestamp);
        restTimerBarTicker.refresh();

        // Retour Romain 24/09/2026 (captures de l'appli horloge OnePlus 13, decompte
        // visible en permanence - barre de statut/ecran verrouille) : notification
        // "en direct" avec chronometre natif Android (setUsesChronometer/
        // setChronometerCountDown), silencieuse et non-glissable tant que le repos
        // tourne - voir RestTimerReceiver.postOngoingNotification() pour le detail.
        // Remplacee (meme id de notification) par l'alerte "Repos termine" habituelle
        // quand l'alarme se declenche (RestTimerReceiver.onReceive()).
        RestTimerReceiver.postOngoingNotification(this, endTimestamp);
    }

    // bt_start (bouton du dialogue "Timer" du menu) reste null tant que ce dialogue n'a
    // jamais ete ouvert cette session - toujours verifier avant de l'utiliser.
    private void updateTimerButtonsLabel()
    {
        if (bt_start != null)
        {
            bt_start.setText(TimerRunning ? "Pause" : "Start");
        }
    }

    // Retour Romain 06/09/2026 : "meme telephone verrouille, il sonne ... je dois
    // pouvoir lui faire confiance sur le fait de sonner". Programme une alarme systeme
    // independante du CountDownTimer/du cycle de vie de l'Activity, recue par
    // RestTimerReceiver (son + vibration + notification) a l'instant ou le repos se
    // termine. setAlarmClock() plutot que setExactAndAllowWhileIdle() : exempte des
    // restrictions Doze/App Standby ET de la permission SCHEDULE_EXACT_ALARM
    // (Android 12+) sans demarche supplementaire, car traite par le systeme comme une
    // vraie alarme (petite icone de reveil dans la barre de statut tant qu'elle est
    // programmee - comportement voulu, gage de fiabilite visible).
    private void scheduleTimerAlarm(long triggerAtMillis)
    {
        if (alarmManager == null)
        {
            return;
        }

        // Correctif 06/09/2026 (crash signale par Romain au demarrage du timer) :
        // setAlarmClock() leve une SecurityException si la permission SCHEDULE_EXACT_ALARM
        // n'est pas accordee (declaree dans le Manifest depuis ce correctif, normalement
        // auto-accordee vu targetSdkVersion 31 - voir le commentaire du Manifest) - non
        // rattrapee, cette exception faisait planter TOUTE l'app. canScheduleExactAlarms()
        // n'existe qu'a partir d'Android 12 (S) ; en dessous, aucune permission requise.
        // Le try/catch est une securite supplementaire (ex. permission revoquee a la main
        // par l'utilisateur apres coup, comportement specifique a certains fabricants...) :
        // au pire, le minuteur reste fiable uniquement pendant que l'app est au premier
        // plan (CountDownTimer, comportement d'avant ce chantier), plutot que de crasher.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms())
        {
            return;
        }

        try
        {
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent showIntent = PendingIntent.getActivity(
                    this, REST_TIMER_REQUEST_CODE, new Intent(this, MainActivity.class), flags);

            alarmManager.setAlarmClock(
                    new AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
                    getTimerAlarmPendingIntent());
        }
        catch (SecurityException e)
        {
            e.printStackTrace();
        }
    }

    // Annule l'alarme programmee par scheduleTimerAlarm() (pause ou reset) - sans effet
    // si aucune n'est en attente (timer jamais demarre, ou deja sonnee).
    private void cancelTimerAlarm()
    {
        if (alarmManager == null)
        {
            return;
        }

        try
        {
            alarmManager.cancel(getTimerAlarmPendingIntent());
        }
        catch (SecurityException e)
        {
            e.printStackTrace();
        }
    }

    private PendingIntent getTimerAlarmPendingIntent()
    {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(
                this, REST_TIMER_REQUEST_CODE, new Intent(this, RestTimerReceiver.class), flags);
    }

    // Retour Romain 06/09/2026 (barre de minuteur persistante) : partie de l'ancien
    // loadSeconds() qui ne touche PAS a et_seconds (dialogue "Timer" du menu, null tant
    // que ce dialogue n'a jamais ete ouvert) - appelable depuis onCreate() pour que
    // Start/Pause/Reset depuis la barre persistante utilisent la duree configuree
    // (SharedPreferences) meme si le dialogue n'a jamais ete ouvert cette session.
    private void loadTimerDurationFromPrefs()
    {
        SharedPreferences sharedPreferences = getSharedPreferences("shared preferences",MODE_PRIVATE);
        String seconds = sharedPreferences.getString("seconds","180");

        // Change actual values that timer uses
        START_TIME_IN_MILLIS = Integer.parseInt(seconds) * 1000;
        TimeLeftInMillis = START_TIME_IN_MILLIS;
    }

    public void loadSeconds()
    {
        loadTimerDurationFromPrefs();

        SharedPreferences sharedPreferences = getSharedPreferences("shared preferences",MODE_PRIVATE);
        String seconds = sharedPreferences.getString("seconds","180");
        et_seconds.setText(seconds);
    }

    // Retour Romain 06/09/2026 : volume du bip de fin de repos reglable depuis l'app -
    // memes SharedPreferences que la duree du minuteur (loadSeconds()/saveSeconds()
    // ci-dessus), cle partagee avec RestTimerReceiver.VOLUME_PREF_KEY. Valeur par
    // defaut a 100% (RestTimerReceiver.DEFAULT_VOLUME_PERCENT) : le besoin exprime est
    // d'entendre le bip par-dessus la musique de la salle de sport, donc plutot
    // reduire le volume les jours ou l'ambiance est calme que l'inverse.
    public void loadVolume()
    {
        SharedPreferences sharedPreferences = getSharedPreferences("shared preferences", MODE_PRIVATE);
        int volumePercent = sharedPreferences.getInt(
                RestTimerReceiver.VOLUME_PREF_KEY, RestTimerReceiver.DEFAULT_VOLUME_PERCENT);
        sb_volume.setProgress(volumePercent);
    }

    public void saveVolume(int volumePercent)
    {
        SharedPreferences sharedPreferences = getSharedPreferences("shared preferences", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(RestTimerReceiver.VOLUME_PREF_KEY, volumePercent);
        editor.apply();
    }

    // Retour Romain 06/09/2026 : "le bip est un peu court, possible de le rendre 2 fois
    // plus long ? ou m'offrir la possibilite de l'editer dans l'app ?" - duree du bip
    // (en ms) reglable depuis l'app, lue par RestTimerReceiver.playRestTimerBeep().
    // Le SeekBar (sb_beep_duration) va de 0 a (MAX_DURATION_MS - MIN_DURATION_MS) pour
    // rester compatible avec les anciennes versions d'Android (SeekBar.setMin()
    // n'existe qu'a partir de l'API 26) ; on ajoute/retranche MIN_DURATION_MS ici pour
    // convertir entre la progression du curseur et la vraie duree en ms.
    public void loadDuration()
    {
        SharedPreferences sharedPreferences = getSharedPreferences("shared preferences", MODE_PRIVATE);
        int durationMs = sharedPreferences.getInt(
                RestTimerReceiver.DURATION_PREF_KEY, RestTimerReceiver.DEFAULT_DURATION_MS);
        durationMs = Math.max(RestTimerReceiver.MIN_DURATION_MS, Math.min(RestTimerReceiver.MAX_DURATION_MS, durationMs));
        sb_beep_duration.setMax(RestTimerReceiver.MAX_DURATION_MS - RestTimerReceiver.MIN_DURATION_MS);
        sb_beep_duration.setProgress(durationMs - RestTimerReceiver.MIN_DURATION_MS);
    }

    public void saveDuration(int durationMs)
    {
        SharedPreferences sharedPreferences = getSharedPreferences("shared preferences", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(RestTimerReceiver.DURATION_PREF_KEY, durationMs);
        editor.apply();
    }

    public void saveSeconds()
    {
        SharedPreferences sharedPreferences = getSharedPreferences("shared preferences",MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        if(!et_seconds.getText().toString().isEmpty())
        {
            String seconds = et_seconds.getText().toString();

            // Change actual values that timer uses
            START_TIME_IN_MILLIS = Integer.parseInt(seconds) * 1000;
            TimeLeftInMillis = START_TIME_IN_MILLIS;

            // Save to shared preferences
            editor.putString("seconds",et_seconds.getText().toString());
            editor.apply();
        }
    }

    public void pauseTimer()
    {
        countDownTimer.cancel();
        cancelTimerAlarm();
        TimerRunning = false;
        updateTimerButtonsLabel();

        // Barre persistante + notification en direct (retour Romain 24/09/2026) : plus
        // de minuteur en cours, cachees immediatement plutot que d'attendre le prochain
        // tick/l'alarme.
        RestTimerReceiver.clearPersistedEndTimestamp(this);
        RestTimerReceiver.cancelOngoingNotification(this);
        restTimerBarTicker.refresh();
    }

    // Retour Romain 06/09/2026 : "je dois pouvoir lui faire confiance" - Reset ne
    // faisait rien tant que le timer n'etait pas activement en train de tourner
    // (`if(TimerRunning)` uniquement) : en pause ou jamais demarre, cliquer Reset
    // n'avait aucun effet visible. TimeLeftInMillis/le texte affiche sont maintenant
    // toujours remis a zero ; seul l'arret du CountDownTimer (pauseTimer(), qui
    // appellerait countDownTimer.cancel() sur un objet potentiellement jamais cree)
    // reste conditionne a TimerRunning - l'alarme programmee est annulee dans tous les
    // cas (cancelTimerAlarm() est un no-op sans effet si aucune n'est en attente).
    public void resetTimer()
    {
        if(TimerRunning)
        {
            pauseTimer();
        }
        else
        {
            cancelTimerAlarm();
        }

        // Barre persistante + notification (retour Romain 24/09/2026) : deja fait par
        // pauseTimer() ci-dessus si le minuteur tournait - repete ici sans condition pour
        // couvrir aussi le cas "jamais demarre"/"deja en pause". Sans effet si deja vide.
        RestTimerReceiver.clearPersistedEndTimestamp(this);
        RestTimerReceiver.cancelOngoingNotification(this);
        restTimerBarTicker.refresh();

        TimeLeftInMillis = START_TIME_IN_MILLIS;
        updateCountDownText();
    }

    public void updateCountDownText()
    {
        int seconds = (int) TimeLeftInMillis / 1000;
        int minutes = (int) seconds / 60;

        if (et_seconds != null)
        {
            et_seconds.setText(String.valueOf(seconds));
        }
    }

}