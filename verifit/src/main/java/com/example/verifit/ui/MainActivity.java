package com.example.verifit.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.example.verifit.AppNames;
import com.example.verifit.DataStorage;
import com.example.verifit.R;
import com.example.verifit.SessionImporter;
import com.example.verifit.adapters.ViewPagerExerciseAdapter;
import com.example.verifit.adapters.ViewPagerWorkoutDayAdapter;
import com.example.verifit.model.WorkoutDay;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.DateFormat;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener {

    public static DataStorage dataStorage = new DataStorage(); // Holds all app data and handles file I/O
    public static String dateSelected; // Used for other activities to get the selected date, by default it's set to today
    public static ViewPager2 viewPager2; // View Pager that is used in main activity
    public static final int READ_REQUEST_CODE = 42;
    // Request code for the "Import Session" file picker (see fileSearchImportSession()),
    // meme mecanique que DayActivity.IMPORT_SESSION_REQUEST_CODE mais sa propre valeur -
    // les deux ecrans sont des Activity separees, pas de conflit possible, mais autant
    // eviter la confusion en cas de lecture croisee du code.
    public static final int IMPORT_SESSION_REQUEST_CODE = 78;
    public static String EXPORT_FILENAME = AppNames.FILE_PREFIX + "_backup";

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

        // Hacky way to have the same code run in onRestart() as well
        onCreateStuff();
    }

    // General Initialization Stuff
    public void onCreateStuff()
    {
        initActivity();

        // Bottom Navigation Bar Intents
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation_view);
        // Retour Romain 29/09/2026 (import de seance sans reaction) : setSelectedItemId()
        // declenche le listener meme quand l'onglet est DEJA selectionne (BottomNavigationView
        // sans OnNavigationItemReselectedListener). Appele a chaque onRestart() avec le
        // listener deja pose, il relancait donc une NOUVELLE instance de cet ecran par-dessus
        // (invisible, sans animation) : dialogue affiche sur l'ancienne instance masque, et
        // une instance de plus dans la pile a chaque retour sur l'ecran. On retire le
        // listener le temps de synchroniser l'onglet affiche.
        bottomNavigationView.setOnNavigationItemSelectedListener(null);
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

        // Retour Romain 05/09/2026 : l'ancien nom de l'app ne voulait rien dire pour lui -
        // titre d'écran renommé "Workout" (l'app s'appelle FitEngine par ailleurs).
        getSupportActionBar().setTitle("Workout");

        // From Settings Activity when importing CSV
        Intent in = getIntent();

        String whatToDo = in.getStringExtra("doit");

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
            // Export JSON dedie au pipeline Coaching (retour Romain 24/09/2026, voir
            // claude/verifit-migration-plan.md story 2.2) - meme mecanique que
            // "exportcsv" ci-dessus, juste une autre methode DataStorage.
            else if(whatToDo.equals("exportjson"))
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                {
                    dataStorage.writeJsonExport(getApplicationContext());
                }

                initViewPager();
            }
        }
        // No intent
        else
        {
            dataStorage.loadWorkoutData(getApplicationContext());
            dataStorage.loadKnownExercisesData(getApplicationContext());
            dataStorage.loadGoalsData(getApplicationContext()); // "Goals" (Vague 4, item 14)
            initViewPager();
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
        // Retour Romain 29/09/2026 (import de seance sans reaction) : setSelectedItemId()
        // declenche le listener meme quand l'onglet est DEJA selectionne (BottomNavigationView
        // sans OnNavigationItemReselectedListener). Appele a chaque onRestart() avec le
        // listener deja pose, il relancait donc une NOUVELLE instance de cet ecran par-dessus
        // (invisible, sans animation) : dialogue affiche sur l'ancienne instance masque, et
        // une instance de plus dans la pile a chaque retour sur l'ecran. On retire le
        // listener le temps de synchroniser l'onglet affiche.
        bottomNavigationView.setOnNavigationItemSelectedListener(null);
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
        EXPORT_FILENAME = AppNames.FILE_PREFIX;
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

    // Opens the system file picker so the user can pick a JSON file describing a
    // session (see docs/session-import-format.md), same mecanique que
    // DayActivity.fileSearchImportSession() mais depuis l'onglet Workout (accueil).
    // "*/*" plutot que "application/json" - certains file providers ne rapportent pas
    // .json sous ce mime type exact et seraient filtres du picker.
    public void fileSearchImportSession()
    {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, IMPORT_SESSION_REQUEST_CODE);
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
                else if (requestCode == IMPORT_SESSION_REQUEST_CODE)
                {
                    onImportSessionResult(uri);
                }
            }
        }
    }

    // Traite le fichier JSON choisi par fileSearchImportSession() - meme logique que
    // DayActivity.onActivityResult() pour IMPORT_SESSION_REQUEST_CODE, mais utilise
    // dateSelected (le jour actuellement affiche dans le carrousel) au lieu de
    // date_clicked, et rafraichit via initViewPager() (qui preserve la position
    // courante, voir son commentaire) plutot que initActivity().
    private void onImportSessionResult(Uri uri)
    {
        if (uri == null)
        {
            return;
        }

        // Point 1.5 de la revue du 28/09/2026 : confirmation si la seance a deja ete
        // importee ce jour-la (voir SessionImporter.importWithDuplicateCheck()).
        SessionImporter.importWithDuplicateCheck(this, uri, dataStorage, dateSelected, this::showImportSessionResult);
    }

    private void showImportSessionResult(SessionImporter.Result result)
    {
        if (result.success)
        {
            DataStorage.ImportSummary summary = result.summary;
            String message = summary.setsImported + " set(s) imported into " + summary.date;
            if (summary.exercisesCreated > 0)
            {
                message += " (" + summary.exercisesCreated + " new exercise(s) created)";
            }
            if (summary.setsSkipped > 0)
            {
                message += " - " + summary.setsSkipped + " incomplete set(s) skipped";
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();

            // Unlike DayActivity (a single fixed day), the carousel's current page is
            // tracked by position, not by date - dateSelected is only kept in sync via
            // the onPageScrolled callback in initViewPager(). The JSON file is allowed
            // to carry its own "date" (session-import-format.md), so if it differs from
            // the day currently shown, the toast above says which date actually
            // received the import; jumping the carousel there automatically is left for
            // later if this turns out to matter in practice. initViewPager() here only
            // refreshes the data - it already preserves whatever page is showing.
            initViewPager();
        }
        else
        {
            Toast.makeText(this, "Import failed: " + result.errorMessage, Toast.LENGTH_LONG).show();
        }
    }


    // Navigates to given activity based on the selected menu item
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item)
    {
        // Retaper "Workout" deja affiche : retour au jour courant. Jusqu'ici ce tap
        // ouvrait une NOUVELLE instance de cet ecran (positionnee sur aujourd'hui) par-dessus
        // l'ancienne, ce qui empilait les ecrans (voir TabNavigation).
        if (item.getItemId() == R.id.home)
        {
            scrollToToday();
            return true;
        }
        return TabNavigation.navigate(this, item.getItemId(), R.id.home);
    }

    private void scrollToToday()
    {
        if (viewPager2 == null)
        {
            return;
        }
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        ArrayList<WorkoutDay> days = dataStorage.getInfiniteWorkoutDays();
        for (int i = 0; i < days.size(); i++)
        {
            if (today.equals(days.get(i).getDate()))
            {
                viewPager2.setCurrentItem(i, false);
                return;
            }
        }
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
        else if(item.getItemId() == R.id.import_session)
        {
            fileSearchImportSession();
        }
        else if(item.getItemId() == R.id.select_exercises)
        {
            startExerciseSelectionMode();
        }
        // Retour Romain 24/09/2026 : "je n'ai pas acces a share workout ailleurs que
        // depuis le calendrier. Ce n'est pas user friendly" - ajoute sur l'onglet
        // Workout (accueil), l'ecran ouvert par defaut au lancement de l'app, en plus
        // de DayActivity (voir day_activity_menu.xml/DayActivity.shareWorkout()).
        else if(item.getItemId() == R.id.share_workout)
        {
            DayActions.shareWorkout(this, dateSelected);
        }
        else if(item.getItemId() == R.id.comment_workout)
        {
            DayActions.showCommentWorkoutDialog(this, dateSelected, this::initViewPager);
        }
        else if(item.getItemId() == R.id.copy_workout)
        {
            new CalendarPickerDialog(this, dataStorage, dateSelected,
                    dateKey -> DayActions.promptCopyOrMoveExercises(this, dateKey, dateSelected, false, this::initViewPager)).show();
        }
        else if(item.getItemId() == R.id.copy_previous_workout)
        {
            DayActions.copyPreviousWorkout(this, dateSelected, this::initViewPager);
        }
        else if(item.getItemId() == R.id.move_workout)
        {
            new CalendarPickerDialog(this, dataStorage, dateSelected,
                    dateKey -> DayActions.promptCopyOrMoveExercises(this, dateKey, dateSelected, true, this::initViewPager)).show();
        }
        return super.onOptionsItemSelected(item);
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
                    DayActions.confirmDeleteSelectedExercises(MainActivity.this, date, adapter.getSelectedExerciseNames(), mode, MainActivity.this::initViewPager);
                    return true;
                }
                else if (item.getItemId() == R.id.group_selected_exercises)
                {
                    DayActions.groupSelectedExercises(MainActivity.this, date, adapter.getSelectedExerciseNames(), mode, MainActivity.this::initViewPager);
                    return true;
                }
                else if (item.getItemId() == R.id.ungroup_selected_exercises)
                {
                    DayActions.ungroupSelectedExercises(MainActivity.this, date, adapter.getSelectedExerciseNames(), mode, MainActivity.this::initViewPager);
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
}

