package com.example.verifit.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.verifit.BackupService;
import com.example.verifit.DataStorage;
import com.example.verifit.LoadingDialog;
import com.example.verifit.R;
import com.example.verifit.SnackBarWithMessage;
import com.example.verifit.WorkoutReportGenerator;
import com.example.verifit.model.WorkoutExercise;
import com.example.verifit.model.WorkoutSet;
import com.example.verifit.adapters.ViewPagerExerciseAdapter;
import com.example.verifit.adapters.ViewPagerWorkoutDayAdapter;
import com.example.verifit.adapters.WebdavAdapter;
import com.example.verifit.model.SupersetColours;
import com.example.verifit.model.SupersetGroup;
import com.example.verifit.model.WorkoutDay;
import com.example.verifit.verifitrs.WorkoutSetsApi;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener {

    public static DataStorage dataStorage = new DataStorage(); // Holds all Verifit data and handles file I/O
    public static String dateSelected; // Used for other activities to get the selected date, by default it's set to today
    public static ViewPager2 viewPager2; // View Pager that is used in main activity
    public static Boolean autoBackupRequired = false;
    public static Boolean inAddExerciseActivity = false;
    public static WebdavAdapter webdavAdapter;
    public static final int READ_REQUEST_CODE = 42;
    public static String EXPORT_FILENAME = "verifit_backup";

    public FloatingActionButton fab;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fab = findViewById(R.id.floatingActionButton);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent in = new Intent(MainActivity.this, ExercisesActivity.class);
                startActivity(in);
            }
        });

        com.example.verifit.SharedPreferences sharedPreferences = new com.example.verifit.SharedPreferences(getApplicationContext());

        // No need for backup, not adding exercises
        if(!doesSharedPreferenceExist("autoBackupRequired"))
        {
            sharedPreferences.save("false", "autoBackupRequired");
        }

        if(!doesSharedPreferenceExist("inAddExerciseActivity"))
        {
            sharedPreferences.save("false", "inAddExerciseActivity");
        }

        // Hacky way to have the same code run in onRestart() as well
        onCreateStuff();
    }

    public Boolean doesSharedPreferenceExist(String key)
    {
        SharedPreferences sharedPreferences = getApplicationContext().getSharedPreferences("shared preferences", MODE_PRIVATE);

        if(sharedPreferences.contains(key))
        {
            return true;
        }
        return false;
    }

    private boolean isMyServiceRunning(Class<?> serviceClass)
    {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE))
        {
            if (serviceClass.getName().equals(service.service.getClassName()))
            {
                return true;
            }
        }
        return false;
    }

    // General Initialization Stuff
    public void onCreateStuff()
    {
        initActivity();

        // If backup background service has not started, start it
        if(!isMyServiceRunning(BackupService.class))
        {
            // Start background service
            Intent intent = new Intent(this, BackupService.class);
            startService(intent);
        }

        // Bottom Navigation Bar Intents
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation_view);
        bottomNavigationView.setSelectedItemId(R.id.home);
        bottomNavigationView.setOnNavigationItemSelectedListener(this);


        // Date selected is by default today
        Date date_clicked = new Date();
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        dateSelected = dateFormat.format(date_clicked);
    }

    // Opens DayActivity for the given date ("yyyy-MM-dd") and remembers it as the
    // currently selected date. Used both when picking a day from CalendarPickerDialog
    // and (previously) from the plain DatePickerDialog's onDateSet() callback.
    private void openDay(String date_clicked)
    {
        MainActivity.dateSelected = date_clicked;

        // Start Intent
        Intent in = new Intent(getApplicationContext(), DayActivity.class);
        Bundle mBundle = new Bundle();

        // Send Date and start activity
        mBundle.putString("date", date_clicked);
        in.putExtras(mBundle);
        startActivity(in);
    }

    // You guessed it!
    public void initActivity()
    {
        setExportBackupName();

        // Retour Romain 05/09/2026 : "Verifit" ne voulait rien dire pour lui - renommé
        // "Workout" (l'app garde son nom "Verifit" par ailleurs, seul ce titre d'écran
        // change).
        getSupportActionBar().setTitle("Workout");

        // From Settings Activity when importing CSV
        Intent in = getIntent();

        String whatToDo = in.getStringExtra("doit");

        String message = in.getStringExtra("message");

        // If Intent coming from settings activity
        if(whatToDo != null)
        {
            if(whatToDo.equals("importcsv"))
            {
                fileSearch();
            }
            else if(whatToDo.equals("exportcsv"))
            {
                // Android 11 and above
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                {
                    dataStorage.writeFile(getApplicationContext());
                }

                // Or else nothing comes up
                initViewPager();
            }
            else if(whatToDo.equals("exportwebdav"))
            {
                // After Loading Data Initialize ViewPager
                initViewPager();
            }
            // Data already saved, just init view pager
            else if(whatToDo.equals("importwebdav"))
            {
                // After Loading Data Initialize ViewPager
                initViewPager();
            }
        }
        // No intent
        else
        {
            // Offline / Webdav Mode
            com.example.verifit.SharedPreferences sharedPreferences = new com.example.verifit.SharedPreferences(getApplicationContext());

            if(sharedPreferences.isOfflineMode())
            {
                sharedPreferences.save("offline", "mode");
                dataStorage.loadWorkoutData(getApplicationContext());
                dataStorage.loadKnownExercisesData(getApplicationContext());
                dataStorage.loadGoalsData(getApplicationContext()); // "Goals" (Vague 4, item 14)
                initViewPager();
            }
            // Caching: Update screen with local data
            else if(dataStorage.getWorkoutDays().size() > 0 && sharedPreferences.shouldUseCache())
            {
                initViewPager();
            }
            // Fetch data from Rest API and then update screen
            else
            {
                // Cloud Mode
                WorkoutSetsApi workoutSetsApi = new WorkoutSetsApi(getApplicationContext(), getString(R.string.API_ENDPOINT));
                workoutSetsApi.getAllWorkoutSets(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {

                        sharedPreferences.enableOfflineMode();

                        // Show error
                        runOnUiThread(() -> {
                            initViewPager();
                            SnackBarWithMessage snackBarWithMessage = new SnackBarWithMessage(MainActivity.this);
                            snackBarWithMessage.showSnackbar("Can't connect to server");
                        });
                    }

                    @Override
                    public void onResponse(Call call, okhttp3.Response response) throws IOException
                    {
                        if (200 == response.code())
                        {
                            String jsonString = response.body().string();
                            Gson gson = new Gson();
                            Type listType = new TypeToken<ArrayList<WorkoutSet>>() {}.getType();
                            ArrayList<WorkoutSet> sets = gson.fromJson(jsonString, listType);

                            MainActivity.dataStorage.readFromSets(sets, getApplicationContext());

                            // Data loaded successfully, enable caching from now on
                            sharedPreferences.enableCaching();

                            runOnUiThread(() -> {
                                initViewPager();
                            });
                        }
                        else
                        {
                            // If logged out login again
                            if(response.message().equals("Unauthorized"))
                            {
                                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                                startActivity(intent);
                            }
                            else if(response.message().equals("Bad Gateway"))
                            {
                                sharedPreferences.enableOfflineMode();
                                runOnUiThread(() -> {
                                    initViewPager();
                                });
                            }
                            else
                            {
                                sharedPreferences.enableOfflineMode();
                            }

                            runOnUiThread(() -> {
                                SnackBarWithMessage snackBarWithMessage = new SnackBarWithMessage(MainActivity.this);
                                snackBarWithMessage.showSnackbar(response.message().toString());
                            });
                        }
                    }
                });
            }
        }
        // Display login/logout messages
        if(message != null)
        {
            if(message.equals("verifit_rs_login"))
            {
                SnackBarWithMessage snackBarWithMessage = new SnackBarWithMessage(MainActivity.this);
                snackBarWithMessage.showSnackbar("Welcome back");
            }
            else if(message.equals("verifit_rs_logout"))
            {
                SnackBarWithMessage snackBarWithMessage = new SnackBarWithMessage(MainActivity.this);
                snackBarWithMessage.showSnackbar("Logged out");
            }
            else if(message.equals("verifit_rs_signup"))
            {
                com.example.verifit.SharedPreferences sharedPreferences = new com.example.verifit.SharedPreferences(getApplicationContext());
                String email = sharedPreferences.load("verifit_rs_username");
                SnackBarWithMessage snackBarWithMessage = new SnackBarWithMessage(MainActivity.this);
                snackBarWithMessage.showSnackbar("Account created for " + email);
            }
        }
    }


    @Override
    protected void onRestart()
    {
        // This was already there so I am not deleting it
        super.onRestart();

        // Get WorkoutDays from shared preferences
        dataStorage.loadWorkoutData(getApplicationContext());

        // Get Known Exercises from shared preferences
        dataStorage.loadKnownExercisesData(getApplicationContext());

        // Get Goals from shared preferences ("Goals", Vague 4, item 14)
        dataStorage.loadGoalsData(getApplicationContext());

        // After Loading Data Initialize ViewPager
        initViewPager();

        // Bottom Navigation Bar Intents
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation_view);
        bottomNavigationView.setSelectedItemId(R.id.home);
        bottomNavigationView.setOnNavigationItemSelectedListener(this);
    }

    // Initialize View pager object
    public void initViewPager()
    {
        // Remember whatever day was showing before this call. initViewPager() is called
        // again every time the activity restarts (e.g. after minimizing the app and
        // coming back to it) purely to refresh the data, and it used to always snap the
        // ViewPager back to today in the process - annoying if you'd scrolled elsewhere
        // to review a past session. The size of getInfiniteWorkoutDays() never changes
        // once built, so a saved index still points at the same date after a refresh.
        Integer previousPosition = null;
        if(viewPager2 != null && viewPager2.getAdapter() != null)
        {
            previousPosition = viewPager2.getCurrentItem();
        }

        // Skip creation of empty workouts if you don't have to
        if(dataStorage.getInfiniteWorkoutDays().isEmpty() || dataStorage.getInfiniteWorkoutDays() == null)
        {
            // "Infinite" Data Structure
            dataStorage.getInfiniteWorkoutDays().clear();

            // Find start and End Dates
            Calendar c = Calendar.getInstance();
            c.setTime(new Date());
            c.add(Calendar.YEAR, -5);
            Date startDate = c.getTime();
            c.add(Calendar.YEAR, +10);
            Date endDate = c.getTime();

            // Create Calendar Objects that represent start and end date
            Calendar start = Calendar.getInstance();
            start.setTime(startDate);
            Calendar end = Calendar.getInstance();
            end.setTime(endDate);

            // Construct 20 years worth of empty workout days
            for (Date date = start.getTime(); start.before(end); start.add(Calendar.DATE, 1), date = start.getTime())
            {
                // Get Date in String format
                String date_str = new SimpleDateFormat("yyyy-MM-dd").format(date);

                // Create new mostly empty object
                WorkoutDay today = new WorkoutDay();
                today.setDate(date_str);
                dataStorage.getInfiniteWorkoutDays().add(today);
            }
        }

        // Use View Pager with Infinite Days
        viewPager2 = findViewById(R.id.viewPager2);
        viewPager2.setAdapter(new ViewPagerWorkoutDayAdapter(this, dataStorage.getInfiniteWorkoutDays()));
        viewPager2.setVisibility(View.VISIBLE);

        if(previousPosition != null)
        {
            // Restore whichever day was open instead of jumping back to today.
            viewPager2.setCurrentItem(previousPosition, false);
        }
        else
        {
            viewPager2.setCurrentItem(((dataStorage.getInfiniteWorkoutDays().size()+1)/2)-1); // Navigate to today (first launch only)
        }

        viewPager2.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels);
                String dateScrolled = dataStorage.getInfiniteWorkoutDays().get(position).getDate();
                MainActivity.dateSelected = dateScrolled;
            }
        });

        ProgressBar progressBar = findViewById(R.id.progress_bar);
        progressBar.setVisibility(View.GONE);
    }

    // Formats backup name in case of export
    public static void setExportBackupName()
    {
        EXPORT_FILENAME = "verifit";
        Format formatter = new SimpleDateFormat("_yyyy-MM-dd_HH:mm:ss");
        String str_date = formatter.format(new Date());
        EXPORT_FILENAME = EXPORT_FILENAME + str_date;
    }

    // Select a file using the build in file manager
    public void fileSearch()
    {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        startActivityForResult(intent,READ_REQUEST_CODE);
    }

    // When File explorer stops this function runs
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK)
        {
            if (data != null)
            {
                Uri uri = data.getData();
                if (requestCode == READ_REQUEST_CODE)
                {
                    if(dataStorage.readFile(uri, getApplicationContext()))
                    {
                        initViewPager();
                    }
                }
            }
        }
    }


    // Navigates to given activity based on the selected menu item
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item)
    {
        if(item.getItemId() == R.id.home)
        {
            Intent in = new Intent(this,MainActivity.class);
            startActivity(in);
            overridePendingTransition(0,0);
        }
        else if(item.getItemId() == R.id.exercises)
        {
            Intent in = new Intent(this, ExercisesActivity.class);
            startActivity(in);
            overridePendingTransition(0,0);
        }
        else if(item.getItemId() == R.id.diary)
        {
            Intent in = new Intent(this,DiaryActivity.class);
            in.putExtra("date", dateSelected);
            startActivity(in);
            overridePendingTransition(0,0);
        }
        else if(item.getItemId() == R.id.charts)
        {
            Intent in = new Intent(this,ChartsActivity.class);
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

    // Menu Stuff
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_menu,menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item)
    {
        if(item.getItemId() == R.id.home)
        {
            // This used to just snap the ViewPager back to today. Now it opens a
            // calendar instead, like FitNotes' calendar icon, so any day can be reached
            // directly instead of swiping through one day at a time - and, unlike the
            // plain DatePicker, it marks which days already have a logged workout.
            showDatePickerForNavigation();
        }
        else if(item.getItemId() == R.id.settings)
        {
            Intent in = new Intent(this,SettingsActivity.class);
            startActivity(in);
        }
        else if(item.getItemId() == R.id.select_exercises)
        {
            startExerciseSelectionMode();
        }
        else if(item.getItemId() == R.id.comment_workout)
        {
            showCommentWorkoutDialog();
        }
        else if(item.getItemId() == R.id.copy_workout)
        {
            new CalendarPickerDialog(this, dataStorage, dateSelected,
                    dateKey -> promptCopyOrMoveExercises(dateKey, dateSelected, false)).show();
        }
        else if(item.getItemId() == R.id.copy_previous_workout)
        {
            copyPreviousWorkout();
        }
        else if(item.getItemId() == R.id.move_workout)
        {
            new CalendarPickerDialog(this, dataStorage, dateSelected,
                    dateKey -> promptCopyOrMoveExercises(dateKey, dateSelected, true)).show();
        }
        return super.onOptionsItemSelected(item);
    }

    // "Copy Previous Workout" (Vague 3 du plan de migration, retour Romain 07/09/2026) -
    // raccourci qui saute le choix manuel du jour source dans le calendrier (voir
    // DataStorage.getMostRecentWorkoutDateBefore()) : reprend directement le jour avec
    // des series le plus recent avant celui affiche.
    private void copyPreviousWorkout()
    {
        String sourceDate = dataStorage.getMostRecentWorkoutDateBefore(dateSelected);
        if (sourceDate == null)
        {
            Toast.makeText(this, "No previous workout found", Toast.LENGTH_SHORT).show();
            return;
        }
        promptCopyOrMoveExercises(sourceDate, dateSelected, false);
    }

    // "Copy a Workout" / "Move a Workout" (Vague 3 du plan de migration, retour Romain
    // 07/09/2026). Scope reduit par rapport a FitNotes : selection par EXERCICE entier
    // (case a cocher standard Android, "Select All" implicite car tout est pre-coche),
    // pas par serie individuelle avec bouton "Edit" avant validation - un ecran dedie
    // pour ce niveau de detail aurait ete disproportionne par rapport au besoin ("je
    // refais une seance deja loggee"/"je me suis trompe de date"). Chaque serie copiee
    // est un WorkoutSet tout neuf (voir DataStorage.copySetsToDay()), jamais partage
    // avec l'original.
    private void promptCopyOrMoveExercises(String sourceDate, String destinationDate, boolean move)
    {
        if (sourceDate.equals(destinationDate))
        {
            Toast.makeText(this, "Choose a different day", Toast.LENGTH_SHORT).show();
            return;
        }

        int sourceDayPosition = dataStorage.getDayPosition(sourceDate);
        if (sourceDayPosition < 0 || dataStorage.getWorkoutDays().get(sourceDayPosition).getExercises().isEmpty())
        {
            Toast.makeText(this, "No workout to copy on that day", Toast.LENGTH_SHORT).show();
            return;
        }

        List<WorkoutExercise> sourceExercises = dataStorage.getWorkoutDays().get(sourceDayPosition).getExercises();
        final String[] exerciseNames = new String[sourceExercises.size()];
        final boolean[] checked = new boolean[sourceExercises.size()];
        for (int i = 0; i < sourceExercises.size(); i++)
        {
            exerciseNames[i] = sourceExercises.get(i).getExercise();
            checked[i] = true;
        }

        new AlertDialog.Builder(this)
                .setTitle((move ? "Move from " : "Copy from ") + WorkoutReportGenerator.formatDateHeader(sourceDate))
                .setMultiChoiceItems(exerciseNames, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(move ? "Move" : "Copy", (dialog, which) ->
                {
                    List<String> selectedNames = new ArrayList<>();
                    for (int i = 0; i < exerciseNames.length; i++)
                    {
                        if (checked[i])
                        {
                            selectedNames.add(exerciseNames[i]);
                        }
                    }

                    if (selectedNames.isEmpty())
                    {
                        Toast.makeText(this, "No exercise selected", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int copiedCount = dataStorage.copySetsToDay(sourceDate, destinationDate, selectedNames);
                    if (move)
                    {
                        dataStorage.removeExerciseSetsFromDay(sourceDate, selectedNames);
                    }

                    dataStorage.saveWorkoutData(getApplicationContext());

                    autoBackupRequired = true;
                    com.example.verifit.SharedPreferences sharedPreferences = new com.example.verifit.SharedPreferences(getApplicationContext());
                    sharedPreferences.save("true", "autoBackupRequired");

                    Toast.makeText(this, copiedCount + " set(s) " + (move ? "moved" : "copied"), Toast.LENGTH_SHORT).show();
                    runOnUiThread(this::initViewPager);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // "Comment a Workout" (Vague 3 du plan de migration, retour Romain 07/09/2026) -
    // meme logique que DayActivity.showCommentWorkoutDialog() (voir ce fichier pour le
    // detail des choix), adaptee au jour actuellement affiche dans le ViewPager2
    // (dateSelected) plutot qu'a date_clicked.
    private void showCommentWorkoutDialog()
    {
        int day_position = dataStorage.getDayPosition(dateSelected);
        final WorkoutDay day;
        if (day_position >= 0)
        {
            day = dataStorage.getWorkoutDays().get(day_position);
        }
        else
        {
            day = new WorkoutDay();
            day.setDate(dateSelected);
            dataStorage.getWorkoutDays().add(day);
        }

        final EditText input = new EditText(this);
        input.setHint("Comment (optional)");
        input.setText(day.getComment());
        int paddingPx = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        new AlertDialog.Builder(this)
                .setTitle("Comment Workout")
                .setView(input)
                .setPositiveButton("Save", (dlg, which) ->
                {
                    day.setComment(input.getText().toString().trim());

                    if (day.getSets().isEmpty() && day.getComment().isEmpty())
                    {
                        dataStorage.getWorkoutDays().remove(day);
                    }

                    dataStorage.saveWorkoutData(getApplicationContext());
                    runOnUiThread(this::initViewPager);
                })
                .setNegativeButton("Cancel", (dlg, which) ->
                {
                    if (day.getSets().isEmpty() && day.getComment().isEmpty())
                    {
                        dataStorage.getWorkoutDays().remove(day);
                    }
                })
                .show();
    }

    // Opens a custom month calendar (see CalendarPickerDialog) pre-filled with whatever
    // day is currently showing, marking the days that already have a logged workout -
    // like FitNotes' own calendar navigation, which the plain Android DatePickerDialog
    // this replaced couldn't do. Picking a day opens it directly via openDay() above.
    private void showDatePickerForNavigation()
    {
        CalendarPickerDialog dialog = new CalendarPickerDialog(this, dataStorage, dateSelected, new CalendarPickerDialog.OnDaySelectedListener() {
            @Override
            public void onDaySelected(String dateKey) {
                openDay(dateKey);
            }
        });
        // Vague 3 du plan de migration, item 10/11 (retour 08/09/2026) : Category Dots
        // multicolores + Filter + List View - uniquement ici, le seul vrai point
        // d'entree "parcourir le calendrier" de l'app (voir CalendarPickerDialog,
        // commentaire de classe). Jamais active sur les autres appels de
        // CalendarPickerDialog (Copy/Move/Copy Previous Workout), de simples
        // selecteurs de jour source.
        dialog.enableBrowsingFeatures();
        dialog.show();
    }

    // --- Multi-select delete + drag reorder sur l'onglet Workout (retour Romain
    // 05/09/2026) --- Même mécanique que DayActivity, mais appliquée à la page
    // actuellement affichée dans le ViewPager2 : ce carrousel n'a pas UN seul adapter
    // d'exercices comme DayActivity, mais un par jour (recyclé au fil du swipe), donc il
    // faut d'abord retrouver le ViewHolder de la page visible avant de pouvoir démarrer
    // la sélection dessus.

    // ViewPager2 encapsule en interne une unique RecyclerView (son unique enfant direct)
    // - c'est la façon standard de retrouver le ViewHolder actuellement affiché, il n'y a
    // pas d'API publique dédiée sur ViewPager2 lui-même pour ça.
    private ViewPagerWorkoutDayAdapter.WorkoutDayViewHolder getCurrentDayViewHolder()
    {
        if (viewPager2 == null || viewPager2.getChildCount() == 0)
        {
            return null;
        }

        View child = viewPager2.getChildAt(0);
        if (!(child instanceof RecyclerView))
        {
            return null;
        }

        RecyclerView innerRecyclerView = (RecyclerView) child;
        RecyclerView.ViewHolder viewHolder =
                innerRecyclerView.findViewHolderForAdapterPosition(viewPager2.getCurrentItem());

        if (viewHolder instanceof ViewPagerWorkoutDayAdapter.WorkoutDayViewHolder)
        {
            return (ViewPagerWorkoutDayAdapter.WorkoutDayViewHolder) viewHolder;
        }
        return null;
    }

    private ActionMode workoutSelectionActionMode = null;

    private void startExerciseSelectionMode()
    {
        if (workoutSelectionActionMode != null)
        {
            return;
        }

        ViewPagerWorkoutDayAdapter.WorkoutDayViewHolder dayViewHolder = getCurrentDayViewHolder();
        if (dayViewHolder == null || dayViewHolder.getExerciseAdapter() == null)
        {
            Toast.makeText(this, "No day currently displayed", Toast.LENGTH_SHORT).show();
            return;
        }

        ViewPagerExerciseAdapter adapter = dayViewHolder.getExerciseAdapter();
        String date = dayViewHolder.getCurrentDate();

        workoutSelectionActionMode = startSupportActionMode(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.exercise_selection_action_menu, menu);
                mode.setTitle("0 selected");
                adapter.setOnSelectionChangedListener(count -> {
                    if (workoutSelectionActionMode != null)
                    {
                        workoutSelectionActionMode.setTitle(count + " selected");
                    }
                });
                adapter.enterSelectionMode();
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
                    adapter.selectAll();
                    return true;
                }
                else if (item.getItemId() == R.id.delete_selected_exercises)
                {
                    confirmDeleteSelectedExercises(adapter, date, mode);
                    return true;
                }
                else if (item.getItemId() == R.id.group_selected_exercises)
                {
                    groupSelectedExercises(adapter, date, mode);
                    return true;
                }
                else if (item.getItemId() == R.id.ungroup_selected_exercises)
                {
                    ungroupSelectedExercises(adapter, date, mode);
                    return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                adapter.exitSelectionMode();
                adapter.setOnSelectionChangedListener(null);
                workoutSelectionActionMode = null;
            }
        });
    }

    // Groupe les exercices selectionnes en superset (Vague 2 du plan de migration,
    // retour Romain 07/09/2026) - meme logique que DayActivity.groupSelectedExercises()
    // (voir ce fichier pour le detail des choix), adaptee a l'onglet Workout
    // (ViewPagerExerciseAdapter/date de la page affichee au lieu de
    // DayExerciseAdapter/date_clicked).
    private void groupSelectedExercises(ViewPagerExerciseAdapter adapter, String date, ActionMode mode)
    {
        List<String> selectedNames = adapter.getSelectedExerciseNames();

        if (selectedNames.size() < 2)
        {
            Toast.makeText(this, "Select at least 2 exercises to group", Toast.LENGTH_SHORT).show();
            return;
        }

        int day_position = dataStorage.getDayPosition(date);
        if (day_position < 0)
        {
            mode.finish();
            return;
        }

        WorkoutDay day = dataStorage.getWorkoutDays().get(day_position);

        final SupersetGroup existingGroup = day.getSupersetGroupForExercise(selectedNames.get(0));
        String prefillName = (existingGroup != null && existingGroup.getName() != null) ? existingGroup.getName() : "";

        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        int paddingPx = (int) (16 * getResources().getDisplayMetrics().density);
        dialogLayout.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        final EditText input = new EditText(this);
        input.setHint("Superset name (optional)");
        input.setText(prefillName);
        dialogLayout.addView(input);

        final CheckBox cb_auto_advance = new CheckBox(this);
        cb_auto_advance.setText("Automatically move to next exercise after each set");
        cb_auto_advance.setChecked(existingGroup == null || existingGroup.isAutoAdvance());
        dialogLayout.addView(cb_auto_advance);

        new AlertDialog.Builder(this)
                .setTitle(selectedNames.size() + " exercise(s) selected")
                .setView(dialogLayout)
                .setPositiveButton("Group", (dlg, which) ->
                {
                    String name = input.getText().toString().trim();
                    int color = (existingGroup != null)
                            ? existingGroup.getColor()
                            : SupersetColours.getNextAvailableColor(day.getSupersetGroups());

                    SupersetGroup savedGroup = day.addToSupersetGroup(new ArrayList<>(selectedNames), name, color);
                    savedGroup.setAutoAdvance(cb_auto_advance.isChecked());

                    dataStorage.saveWorkoutData(getApplicationContext());
                    mode.finish();
                    runOnUiThread(this::initViewPager);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Retire les exercices selectionnes de leur groupe de superset - voir
    // DayActivity.ungroupSelectedExercises().
    private void ungroupSelectedExercises(ViewPagerExerciseAdapter adapter, String date, ActionMode mode)
    {
        List<String> selectedNames = adapter.getSelectedExerciseNames();

        if (selectedNames.isEmpty())
        {
            Toast.makeText(this, "No exercise selected", Toast.LENGTH_SHORT).show();
            return;
        }

        int day_position = dataStorage.getDayPosition(date);
        if (day_position < 0)
        {
            mode.finish();
            return;
        }

        WorkoutDay day = dataStorage.getWorkoutDays().get(day_position);
        for (String exerciseName : selectedNames)
        {
            day.removeFromSupersetGroup(exerciseName);
        }
        dataStorage.saveWorkoutData(getApplicationContext());

        mode.finish();
        runOnUiThread(this::initViewPager);
    }

    private void confirmDeleteSelectedExercises(ViewPagerExerciseAdapter adapter, String date, ActionMode mode)
    {
        List<String> selectedNames = adapter.getSelectedExerciseNames();

        if (selectedNames.isEmpty())
        {
            Toast.makeText(this, "No exercise selected", Toast.LENGTH_SHORT).show();
            return;
        }

        // Réutilise delete_set_dialog.xml, même confirmation que DayActivity.
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
            deleteSelectedExercises(selectedNames, date, mode);
        });

        alertDialog.show();
    }

    private void deleteSelectedExercises(List<String> exerciseNames, String date, ActionMode mode)
    {
        autoBackupRequired = true;
        com.example.verifit.SharedPreferences sharedPreferences = new com.example.verifit.SharedPreferences(getApplicationContext());
        sharedPreferences.save("true", "autoBackupRequired");

        int day_position = dataStorage.getDayPosition(date);
        if (day_position < 0)
        {
            mode.finish();
            return;
        }

        WorkoutDay day = dataStorage.getWorkoutDays().get(day_position);
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
            final LoadingDialog loadingDialog = new LoadingDialog(MainActivity.this);
            loadingDialog.loadingAlertDialog();

            WorkoutSetsApi workoutSetsApi = new WorkoutSetsApi(getApplicationContext(), getString(R.string.API_ENDPOINT));
            workoutSetsApi.deleteWorkoutSets(setsToDelete, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    loadingDialog.dismissDialog();
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Can't connect to server", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    loadingDialog.dismissDialog();

                    if (200 == response.code())
                    {
                        deleteExerciseSetsLocally(day_position, setsToDelete);
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, exerciseNames.size() + " exercise(s) deleted", Toast.LENGTH_SHORT).show();
                            mode.finish();
                        });
                    }
                    else
                    {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, response.message(), Toast.LENGTH_SHORT).show());
                    }
                }
            });
        }
    }

    private void deleteExerciseSetsLocally(int day_position, List<WorkoutSet> setsToDelete)
    {
        WorkoutDay day = dataStorage.getWorkoutDays().get(day_position);
        day.removeSets(setsToDelete);

        if (day.getSets().isEmpty())
        {
            dataStorage.getWorkoutDays().remove(day_position);
        }

        dataStorage.saveWorkoutData(getApplicationContext());
        dataStorage.saveKnownExerciseData(getApplicationContext());

        runOnUiThread(this::initViewPager);
    }
}

