package com.example.verifit.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.verifit.DataStorage;
import com.example.verifit.R;
import com.example.verifit.WorkoutReportGenerator;
import com.example.verifit.model.CalendarFilter;
import com.example.verifit.model.CategoryColours;
import com.example.verifit.model.WorkoutDay;
import com.example.verifit.model.WorkoutExercise;
import com.example.verifit.model.WorkoutSet;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Custom month calendar shown from the toolbar's calendar icon, replacing the plain
// Android DatePickerDialog. Unlike the platform DatePicker, this can mark which days
// already have a logged workout - a small dot under the day number, the same idea as
// FitNotes' own calendar navigation. Picking a day hands back a "yyyy-MM-dd" string,
// the same date format used everywhere else in the app (MainActivity.dateSelected,
// WorkoutDay.getDate(), ...).
//
// Reused for TWO different purposes (Vague 3 du plan de migration, retour 08/09/2026) :
// - "Browse" (enableBrowsingFeatures(), appele uniquement par
//   MainActivity.showDatePickerForNavigation(), l'icone calendrier de l'accueil) :
//   Category Dots multicolores + Filter (CalendarFilter/CalendarFilterDialog) + List
//   View, comme le vrai Calendrier de FitNotes.
// - "Pick a day" (tous les autres appels - Copy/Move/Copy Previous Workout) : juste un
//   selecteur de date, sans Filter ni List View (n'a pas de sens quand on choisit un
//   jour SOURCE precis a copier/deplacer) - mais garde quand meme les points multicolores,
//   ameliration purement visuelle qui ne genene jamais ce cas d'usage.
//
// Retour UAT Romain 21/09/2026 (US 1.8), mode Browse uniquement :
// - un tap sur un jour SELECTIONNE ce jour sans fermer le calendrier et affiche sous la
//   grille un "panneau Workout" (resume de la seance : commentaire, duree, exercices et
//   series avec leurs icones PR / ecart Prevu-Realise / commentaire) - voir updatePanel() ;
// - l'en-tete du panneau (libelle "Workout") ouvre le jour selectionne dans l'app ; les
//   fleches de l'en-tete sautent a la seance precedente/suivante (jumpToMatch()) ;
// - bouton Today (goToToday()) et anneau permanent autour du jour courant ;
// - la fenetre a une hauteur fixe en mode Browse (le panneau occupe la place restante).
// Le mode "Pick a day" garde exactement son comportement d'origine : un tap ferme le
// dialogue et rend la date.
public class CalendarPickerDialog extends Dialog
{
    public interface OnDaySelectedListener
    {
        void onDaySelected(String dateKey);
    }

    private static final SimpleDateFormat KEY_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    // Filtre Categorie/Exercice (Vague 3, item 11) - instance UNIQUE partagee par tous
    // les CalendarPickerDialog de la session app (vit en memoire seulement, voir
    // CalendarFilter) : reste actif d'une ouverture du calendrier a l'autre tant que
    // Romain ne fait pas "Reset", mais n'a d'effet que quand browsingFeaturesEnabled
    // est vrai (voir shouldShowDots()/CalendarListAdapter.rebuild()) - jamais applique
    // en mode "Pick a day".
    private static final CalendarFilter filter = new CalendarFilter();

    private final DataStorage dataStorage;
    private final OnDaySelectedListener listener;
    private String selectedDateKey; // the day to highlight - mutable, voir jumpToMatch()

    // Jours ("yyyy-MM-dd") qui ont deja une seance loggee, indexes par date - construit
    // une fois depuis dataStorage.getWorkoutDays() (celle reellement peuplee au
    // demarrage de l'app, DataStorage.loadWorkoutData() - DataStorage.getDays() aurait
    // semble la source evidente mais reste un reliquat de l'import CSV/session,
    // toujours vide au lancement normal de l'app).
    private final Map<String, WorkoutDay> workoutDaysByDate;

    // Couleurs de categorie distinctes par jour (jusqu'a 4, Vague 3 "Category Dots"
    // multicolores) - derive de workoutDaysByDate une seule fois, voir
    // buildDayCategoryColours().
    private final Map<String, List<Integer>> dayCategoryColours;

    private boolean browsingFeaturesEnabled = false;
    private boolean listViewActive = false;

    // Only the year/month matter here - the day-of-month field is meaningless for the
    // grid itself, it's just whatever Calendar.getInstance()/setTime() leaves it as.
    private final Calendar displayedMonth = Calendar.getInstance();

    private TextView monthLabel;
    private GridView gridView;
    private RecyclerView listRecyclerView;
    private LinearLayout browseToolbar;
    private TextView filterResultsText;
    private ImageButton prevMatchButton;
    private ImageButton nextMatchButton;
    private LinearLayout panelContainer;
    private TextView panelDate;
    private LinearLayout panelContent;
    private DayGridAdapter gridAdapter;
    private CalendarListAdapter listAdapter;

    public CalendarPickerDialog(Context context, DataStorage dataStorage, String initialDateKey, OnDaySelectedListener listener)
    {
        super(context);
        this.dataStorage = dataStorage;
        this.listener = listener;
        this.selectedDateKey = initialDateKey;
        this.workoutDaysByDate = buildWorkoutDaysByDate(dataStorage);
        this.dayCategoryColours = buildDayCategoryColours(workoutDaysByDate);

        if(initialDateKey != null)
        {
            try
            {
                displayedMonth.setTime(KEY_FORMAT.parse(initialDateKey));
            }
            catch (ParseException e)
            {
                // Unparsable initialDateKey (shouldn't happen) - fall back to today,
                // already the Calendar.getInstance() default above.
            }
        }
    }

    // A appeler avant show(), uniquement depuis
    // MainActivity.showDatePickerForNavigation() (l'icone calendrier de l'accueil) -
    // voir le commentaire de classe ci-dessus pour la distinction Browse/Pick a day.
    public void enableBrowsingFeatures()
    {
        browsingFeaturesEnabled = true;
    }

    private static Map<String, WorkoutDay> buildWorkoutDaysByDate(DataStorage dataStorage)
    {
        Map<String, WorkoutDay> result = new HashMap<>();
        for(WorkoutDay day : dataStorage.getWorkoutDays())
        {
            // A day with no sets (shouldn't normally be in this list, but just in
            // case) has its date reset to "0000-00-00" by WorkoutDay.UpdateData() -
            // skip it explicitly rather than rely on that placeholder never matching.
            if(day.getSets() != null && !day.getSets().isEmpty())
            {
                result.put(day.getDate(), day);
            }
        }
        return result;
    }

    // Jusqu'a 4 couleurs distinctes par jour, dans l'ordre de premiere apparition
    // parmi les series du jour (pas d'ordre alphabetique - reflete plutot l'ordre dans
    // lequel les categories ont ete travaillees). Un jour avec plus de 4 categories
    // (tres rare) ne montre que les 4 premieres plutot qu'un indicateur "+N" - pas
    // justifie pour une petite cellule de calendrier.
    private static Map<String, List<Integer>> buildDayCategoryColours(Map<String, WorkoutDay> workoutDaysByDate)
    {
        Map<String, List<Integer>> result = new HashMap<>();
        for(Map.Entry<String, WorkoutDay> entry : workoutDaysByDate.entrySet())
        {
            LinkedHashSet<String> seenCategories = new LinkedHashSet<>();
            List<Integer> colours = new ArrayList<>();
            for(WorkoutSet set : entry.getValue().getSets())
            {
                String category = set.getCategory();
                if(category == null || !seenCategories.add(category))
                {
                    continue;
                }
                colours.add(CategoryColours.getColour(category));
                if(colours.size() >= 4)
                {
                    break;
                }
            }
            result.put(entry.getKey(), colours);
        }
        return result;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.calendar_navigation_dialog);

        // Meme correctif que GoalDialog/CalendarFilterDialog/ExerciseStatsPeriodDialog
        // (retour Romain, screenshot sur le dialogue Goals) : sans ceci la fenetre
        // d'un Dialog personnalise peut se retrouver minuscule sur certains
        // telephones/ROMs (constate sur MIUI), meme si le layout interne est en
        // match_parent - meme cause latente, corrigee ici par coherence meme si
        // Romain n'a pas signale ce dialogue precis (le Calendrier n'a pas encore
        // ete retesté depuis le rebuild).
        if (getWindow() != null)
        {
            DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
            int dialogWidth = (int) (metrics.widthPixels * 0.9);
            // Mode Browse : hauteur fixe pour laisser au panneau Workout (ou a la List View)
            // toute la place restante sous la grille ; sinon on garde WRAP_CONTENT (Pick a day).
            int dialogHeight = browsingFeaturesEnabled
                    ? (int) (metrics.heightPixels * 0.9)
                    : android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
            getWindow().setLayout(dialogWidth, dialogHeight);
            // Fond de fenetre explicite (meme couleur que la racine du layout, qui suit le
            // theme clair/sombre) : evite tout cadre/liseré blanc autour en mode sombre.
            getWindow().setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(getContext(), R.color.core_white)));
        }

        monthLabel = findViewById(R.id.calendar_month_label);
        gridView = findViewById(R.id.calendar_day_grid);
        listRecyclerView = findViewById(R.id.calendar_day_list);
        ImageButton prevButton = findViewById(R.id.calendar_prev_month);
        ImageButton nextButton = findViewById(R.id.calendar_next_month);
        browseToolbar = findViewById(R.id.calendar_browse_toolbar);
        ImageButton filterButton = findViewById(R.id.calendar_filter_button);
        filterResultsText = findViewById(R.id.calendar_filter_results);
        prevMatchButton = findViewById(R.id.calendar_prev_match);
        nextMatchButton = findViewById(R.id.calendar_next_match);
        ImageButton toggleListViewButton = findViewById(R.id.calendar_toggle_list_view);
        ImageButton todayButton = findViewById(R.id.calendar_today_button);
        panelContainer = findViewById(R.id.calendar_panel);
        panelDate = findViewById(R.id.calendar_panel_date);
        panelContent = findViewById(R.id.calendar_panel_content);

        gridAdapter = new DayGridAdapter();
        gridView.setAdapter(gridAdapter);

        listAdapter = new CalendarListAdapter();
        listRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        listRecyclerView.setAdapter(listAdapter);

        gridView.setOnItemClickListener((parent, view, position, id) -> {
            String dateKey = gridAdapter.dateKeyAt(position);
            if(dateKey == null)
            {
                return;
            }

            if(browsingFeaturesEnabled)
            {
                // Mode Browse : on selectionne seulement, le panneau Workout se met a jour ;
                // c'est son en-tete qui ouvre le jour.
                selectedDateKey = dateKey;
                refresh();
                return;
            }

            dismiss();
            listener.onDaySelected(dateKey);
        });

        prevButton.setOnClickListener(v -> {
            displayedMonth.add(Calendar.MONTH, -1);
            refresh();
        });

        nextButton.setOnClickListener(v -> {
            displayedMonth.add(Calendar.MONTH, 1);
            refresh();
        });

        if(browsingFeaturesEnabled)
        {
            browseToolbar.setVisibility(View.VISIBLE);

            filterButton.setOnClickListener(v -> {
                new CalendarFilterDialog(getContext(), dataStorage, filter, newFilter -> {
                    filter.setCategories(newFilter.getCategories());
                    filter.setMatchAll(newFilter.isMatchAll());
                    filter.setExerciseName(newFilter.getExerciseName());
                    filter.setWeightComparison(newFilter.getWeightComparison());
                    filter.setWeightThreshold(newFilter.getWeightThreshold());
                    filter.setRepsComparison(newFilter.getRepsComparison());
                    filter.setRepsThreshold(newFilter.getRepsThreshold());
                    refresh();
                }).show();
            });

            toggleListViewButton.setOnClickListener(v -> {
                listViewActive = !listViewActive;
                refresh();
            });

            prevMatchButton.setOnClickListener(v -> jumpToMatch(false));
            nextMatchButton.setOnClickListener(v -> jumpToMatch(true));

            todayButton.setOnClickListener(v -> goToToday());

            findViewById(R.id.calendar_panel_prev).setOnClickListener(v -> jumpToMatch(false));
            findViewById(R.id.calendar_panel_next).setOnClickListener(v -> jumpToMatch(true));
            findViewById(R.id.calendar_panel_header).setOnClickListener(v -> {
                if(selectedDateKey != null)
                {
                    String dateKey = selectedDateKey;
                    dismiss();
                    listener.onDaySelected(dateKey);
                }
            });
        }

        refresh();
    }

    private void refresh()
    {
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        monthLabel.setText(monthFormat.format(displayedMonth.getTime()));

        if(browsingFeaturesEnabled)
        {
            updateFilterResults();
        }

        if(listViewActive)
        {
            gridView.setVisibility(View.GONE);
            listRecyclerView.setVisibility(View.VISIBLE);
            listAdapter.rebuild();
        }
        else
        {
            gridView.setVisibility(View.VISIBLE);
            listRecyclerView.setVisibility(View.GONE);
            gridAdapter.notifyDataSetChanged();
        }

        updatePanel();
    }

    // Bouton Today (retour UAT 21/09/2026) : revient au mois courant, selectionne
    // aujourd'hui et met a jour le panneau. Ne ferme pas le calendrier - c'est l'en-tete du
    // panneau qui ouvre le jour. En List View, fait defiler jusqu'a la seance d'aujourd'hui
    // ou, a defaut, a la plus recente qui la precede.
    private void goToToday()
    {
        Calendar today = Calendar.getInstance();
        selectedDateKey = KEY_FORMAT.format(today.getTime());
        displayedMonth.setTime(today.getTime());
        refresh();

        if(listViewActive)
        {
            listRecyclerView.post(() -> {
                int position = listAdapter.positionOnOrBefore(selectedDateKey);
                ((LinearLayoutManager) listRecyclerView.getLayoutManager()).scrollToPositionWithOffset(position, 0);
            });
        }
    }

    private static String todayKey()
    {
        return KEY_FORMAT.format(Calendar.getInstance().getTime());
    }

    // Panneau "Workout" (mode Browse, grille visible) : resume de la seance du jour
    // selectionne, ou "No workout on this day". L'en-tete (libelle + date) est cliquable
    // dans onCreate() et ouvre ce jour dans l'app.
    private void updatePanel()
    {
        if(!browsingFeaturesEnabled || panelContainer == null)
        {
            return;
        }

        boolean showPanel = !listViewActive && selectedDateKey != null;
        panelContainer.setVisibility(showPanel ? View.VISIBLE : View.GONE);
        if(!showPanel)
        {
            return;
        }

        panelDate.setText(WorkoutReportGenerator.formatDateHeader(selectedDateKey));
        panelContent.removeAllViews();

        WorkoutDay day = workoutDaysByDate.get(selectedDateKey);
        if(day == null || day.getExercises() == null || day.getExercises().isEmpty())
        {
            panelContent.addView(buildPanelText("No workout on this day", 13, false));
            return;
        }

        String comment = day.getComment();
        if(comment != null && !comment.trim().isEmpty())
        {
            panelContent.addView(buildPanelText(comment.trim(), 13, true));
        }

        String duration = formatSessionDuration(day);
        if(duration != null)
        {
            panelContent.addView(buildPanelText("Duration: " + duration, 12, false));
        }

        Map<String, HashSet<String>> prKeysByExercise = new HashMap<>();
        for(WorkoutExercise exercise : day.getExercises())
        {
            String name = exercise.getExercise();
            HashSet<String> prKeys = prKeysByExercise.get(name);
            if(prKeys == null)
            {
                prKeys = dataStorage.getRepRangePRKeys(name);
                prKeysByExercise.put(name, prKeys);
            }
            panelContent.addView(buildPanelExercise(exercise, prKeys));
        }
    }

    // Duree du chrono de seance (Workout Time), seulement s'il a ete demarre ET arrete -
    // un chrono jamais arrete sur un jour passe donnerait une duree absurde.
    private static String formatSessionDuration(WorkoutDay day)
    {
        Long start = day.getSessionStartTimestamp();
        Long end = day.getSessionEndTimestamp();
        if(start == null || end == null || end <= start)
        {
            return null;
        }
        long minutes = (end - start) / 60000L;
        long hours = minutes / 60;
        return hours > 0 ? (hours + "h " + (minutes % 60) + "m") : (minutes + "m");
    }

    private TextView buildPanelText(String text, int sizeSp, boolean italic)
    {
        float density = getContext().getResources().getDisplayMetrics().density;
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(ContextCompat.getColor(getContext(), R.color.core_black));
        view.setTypeface(null, italic ? Typeface.ITALIC : Typeface.NORMAL);
        view.setPadding(0, 0, 0, (int) (4 * density));
        return view;
    }

    // Un exercice du panneau : point de couleur de categorie + nom, puis toutes ses
    // series sur une ligne compacte ("80kg x8, 80kg x8...") avec, apres chaque serie
    // concernee, les memes icones que l'ecran du jour (PR, ecart Prevu/Realise,
    // commentaire) - memes regles que WorkoutSetAdapter.
    private View buildPanelExercise(WorkoutExercise exercise, HashSet<String> prKeys)
    {
        float density = getContext().getResources().getDisplayMetrics().density;

        LinearLayout block = new LinearLayout(getContext());
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, (int) (6 * density), 0, (int) (6 * density));

        LinearLayout titleRow = new LinearLayout(getContext());
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(getContext());
        int dotSizePx = (int) (8 * density);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dotSizePx, dotSizePx);
        dotParams.setMarginEnd((int) (6 * density));
        dot.setLayoutParams(dotParams);
        dot.setBackgroundResource(R.drawable.calendar_day_dot_shape);
        String category = exercise.getSets().isEmpty() ? null : exercise.getSets().get(0).getCategory();
        if(category != null)
        {
            ViewCompat.setBackgroundTintList(dot, ColorStateList.valueOf(CategoryColours.getColour(category)));
        }
        titleRow.addView(dot);

        TextView name = new TextView(getContext());
        name.setText(exercise.getExercise());
        name.setTextSize(14);
        name.setTypeface(null, Typeface.BOLD);
        name.setTextColor(ContextCompat.getColor(getContext(), R.color.core_black));
        titleRow.addView(name);

        block.addView(titleRow);

        TextView sets = new TextView(getContext());
        sets.setTextSize(13);
        sets.setTextColor(ContextCompat.getColor(getContext(), R.color.core_black));
        sets.setText(buildSetsText(exercise, prKeys));
        LinearLayout.LayoutParams setsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        setsParams.setMarginStart(dotSizePx + (int) (6 * density));
        setsParams.topMargin = (int) (2 * density);
        sets.setLayoutParams(setsParams);
        block.addView(sets);

        return block;
    }

    private CharSequence buildSetsText(WorkoutExercise exercise, HashSet<String> prKeys)
    {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        for(WorkoutSet set : exercise.getSets())
        {
            if(builder.length() > 0)
            {
                builder.append(",  ");
            }
            builder.append(formatNumber(set.getWeight())).append("kg x").append(formatNumber(set.getReps()));

            if(isPersonalRecord(set, prKeys))
            {
                appendIcon(builder, R.drawable.ic_emoji_events_24px, R.color.colorPrimary);
            }
            if(set.hasDiscrepancy())
            {
                appendIcon(builder, R.drawable.ic_error_outline_24px, R.color.red);
            }
            if(set.getComment() != null && !set.getComment().trim().isEmpty())
            {
                appendIcon(builder, R.drawable.ic_comment_24px, R.color.colorPrimary);
            }
        }
        return builder;
    }

    // Meme regle que WorkoutSetAdapter.isPersonalRecord() : cle date#reps#poids, stable
    // apres un cycle sauvegarde/chargement (voir DataStorage.repRangePRKey()).
    private static boolean isPersonalRecord(WorkoutSet set, HashSet<String> prKeys)
    {
        if(set.getDate() == null || set.getReps() == null || set.getWeight() == null)
        {
            return false;
        }
        return prKeys.contains(DataStorage.repRangePRKey(set.getDate(), (int) Math.round(set.getReps()), set.getWeight()));
    }

    private void appendIcon(SpannableStringBuilder builder, int drawableRes, int colorRes)
    {
        Drawable icon = ContextCompat.getDrawable(getContext(), drawableRes);
        if(icon == null)
        {
            return;
        }
        float density = getContext().getResources().getDisplayMetrics().density;
        icon = DrawableCompat.wrap(icon.mutate());
        DrawableCompat.setTint(icon, ContextCompat.getColor(getContext(), colorRes));
        int sizePx = (int) (14 * density);
        icon.setBounds(0, 0, sizePx, sizePx);

        builder.append(" ");
        int start = builder.length();
        builder.append("#"); // remplace par l'image
        builder.setSpan(new ImageSpan(icon, ImageSpan.ALIGN_BASELINE), start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void updateFilterResults()
    {
        if(!filter.isActive())
        {
            filterResultsText.setText("");
            prevMatchButton.setVisibility(View.GONE);
            nextMatchButton.setVisibility(View.GONE);
            return;
        }

        int matchCount = 0;
        for(WorkoutDay day : workoutDaysByDate.values())
        {
            if(filter.matches(day))
            {
                matchCount++;
            }
        }

        filterResultsText.setText(matchCount == 1 ? "1 workout found" : (matchCount + " workouts found"));
        prevMatchButton.setVisibility(View.VISIBLE);
        nextMatchButton.setVisibility(View.VISIBLE);
    }

    // "Navigation Bar" simplifiee (FitNotes propose des boutons precedent/suivant pour
    // sauter de seance en seance parmi les resultats filtres) - saute au mois de la
    // seance correspondante la plus proche avant/apres le jour actuellement mis en
    // surbrillance (ou le mois affiche si aucun jour n'est encore selectionne), et
    // boucle sur l'autre bout de la liste une fois arrive au bout.
    private void jumpToMatch(boolean forward)
    {
        List<String> matchingDates = new ArrayList<>();
        for(Map.Entry<String, WorkoutDay> entry : workoutDaysByDate.entrySet())
        {
            if(filter.matches(entry.getValue()))
            {
                matchingDates.add(entry.getKey());
            }
        }
        if(matchingDates.isEmpty())
        {
            return;
        }
        Collections.sort(matchingDates); // format ISO -> tri chronologique par simple tri lexicographique

        String anchor = selectedDateKey != null ? selectedDateKey : KEY_FORMAT.format(displayedMonth.getTime());

        String target = null;
        if(forward)
        {
            for(String date : matchingDates)
            {
                if(date.compareTo(anchor) > 0)
                {
                    target = date;
                    break;
                }
            }
            if(target == null)
            {
                target = matchingDates.get(0); // boucle sur la plus ancienne
            }
        }
        else
        {
            for(int i = matchingDates.size() - 1; i >= 0; i--)
            {
                if(matchingDates.get(i).compareTo(anchor) < 0)
                {
                    target = matchingDates.get(i);
                    break;
                }
            }
            if(target == null)
            {
                target = matchingDates.get(matchingDates.size() - 1); // boucle sur la plus recente
            }
        }

        selectedDateKey = target;
        try
        {
            displayedMonth.setTime(KEY_FORMAT.parse(target));
        }
        catch (ParseException e)
        {
            // Ne peut pas arriver - target vient d'une date deja formattee par KEY_FORMAT.
        }
        refresh();
    }

    // Vrai si ce jour doit afficher ses points colores dans la grille du mois. Hors
    // mode Browse, ou si aucun filtre n'est actif, un point marque simplement "il y a
    // une seance ce jour-la" (comportement d'origine). En mode Browse avec un filtre
    // actif, seuls les jours qui CORRESPONDENT au filtre gardent leurs points - les
    // autres jours avec seance restent volontairement sans point, comme un calendrier
    // "grise" autour des resultats de recherche.
    private boolean shouldShowDots(String dateKey)
    {
        if(!browsingFeaturesEnabled || !filter.isActive())
        {
            return workoutDaysByDate.containsKey(dateKey);
        }
        WorkoutDay day = workoutDaysByDate.get(dateKey);
        return day != null && filter.matches(day);
    }

    private View buildExerciseLine(WorkoutExercise exercise)
    {
        float density = getContext().getResources().getDisplayMetrics().density;

        LinearLayout line = new LinearLayout(getContext());
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        int verticalPaddingPx = (int) (2 * density);
        line.setPadding(0, verticalPaddingPx, 0, verticalPaddingPx);

        View dot = new View(getContext());
        int dotSizePx = (int) (8 * density);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dotSizePx, dotSizePx);
        dotParams.setMarginEnd((int) (6 * density));
        dot.setLayoutParams(dotParams);
        dot.setBackgroundResource(R.drawable.calendar_day_dot_shape);
        String category = exercise.getSets().isEmpty() ? null : exercise.getSets().get(0).getCategory();
        if(category != null)
        {
            ViewCompat.setBackgroundTintList(dot, ColorStateList.valueOf(CategoryColours.getColour(category)));
        }
        line.addView(dot);

        TextView text = new TextView(getContext());
        text.setText(exercise.getExercise() + " - " + summarizeSets(exercise));
        text.setTextSize(12);
        text.setTextColor(ContextCompat.getColor(getContext(), R.color.core_grey_55));
        line.addView(text);

        return line;
    }

    private String summarizeSets(WorkoutExercise exercise)
    {
        StringBuilder builder = new StringBuilder();
        for(WorkoutSet set : exercise.getSets())
        {
            if(builder.length() > 0)
            {
                builder.append(", ");
            }
            builder.append(formatNumber(set.getWeight())).append("kg x").append(formatNumber(set.getReps()));
        }
        return builder.toString();
    }

    private String formatNumber(Double value)
    {
        if(value == null)
        {
            return "-";
        }
        if(value == Math.floor(value))
        {
            return String.valueOf(value.intValue());
        }
        return String.valueOf(value);
    }

    // One cell per day, Sunday-first, always 42 cells (6 full weeks) so the grid never
    // reflows height between months - including the leading/trailing days of the
    // neighboring months as blank, disabled filler cells.
    private class DayGridAdapter extends BaseAdapter
    {
        private static final int CELL_COUNT = 42;

        @Override
        public int getCount()
        {
            return CELL_COUNT;
        }

        @Override
        public Object getItem(int position)
        {
            return null;
        }

        @Override
        public long getItemId(int position)
        {
            return position;
        }

        // Returns the Calendar day shown at this grid position, or null if that cell
        // falls outside the displayed month (a filler cell).
        private Calendar dayAt(int position)
        {
            Calendar firstOfMonth = (Calendar) displayedMonth.clone();
            firstOfMonth.set(Calendar.DAY_OF_MONTH, 1);

            // Calendar.DAY_OF_WEEK: Sunday = 1 ... Saturday = 7, matching the grid's
            // Sunday-first column order.
            int leadingBlanks = firstOfMonth.get(Calendar.DAY_OF_WEEK) - 1;
            int dayOffset = position - leadingBlanks;

            int daysInMonth = firstOfMonth.getActualMaximum(Calendar.DAY_OF_MONTH);
            if(dayOffset < 0 || dayOffset >= daysInMonth)
            {
                return null;
            }

            Calendar day = (Calendar) firstOfMonth.clone();
            day.set(Calendar.DAY_OF_MONTH, dayOffset + 1);
            return day;
        }

        String dateKeyAt(int position)
        {
            Calendar day = dayAt(position);
            if(day == null)
            {
                return null;
            }
            return KEY_FORMAT.format(day.getTime());
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            View view = convertView;
            if(view == null)
            {
                view = LayoutInflater.from(getContext()).inflate(R.layout.calendar_day_cell, parent, false);
            }

            TextView dayNumber = view.findViewById(R.id.calendar_day_number);
            View[] dots = {
                    view.findViewById(R.id.calendar_day_dot_1),
                    view.findViewById(R.id.calendar_day_dot_2),
                    view.findViewById(R.id.calendar_day_dot_3),
                    view.findViewById(R.id.calendar_day_dot_4)
            };

            Calendar day = dayAt(position);

            if(day == null)
            {
                // Blank filler cell (previous/next month) - not clickable, nothing shown.
                dayNumber.setText("");
                dayNumber.setBackgroundResource(R.drawable.calendar_day_number_background);
                dayNumber.setSelected(false);
                for(View dot : dots)
                {
                    dot.setVisibility(View.INVISIBLE);
                }
                view.setEnabled(false);
                return view;
            }

            view.setEnabled(true);

            String dateKey = KEY_FORMAT.format(day.getTime());
            dayNumber.setText(String.valueOf(day.get(Calendar.DAY_OF_MONTH)));

            boolean isSelected = dateKey.equals(selectedDateKey);
            dayNumber.setSelected(isSelected); // drives the circle background + text color

            // Jour courant : anneau permanent tant qu'il n'est pas le jour selectionne (le
            // cercle plein de la selection prend alors le dessus) - fonction Today, 21/09/2026.
            boolean isToday = dateKey.equals(todayKey());
            dayNumber.setBackgroundResource(isToday && !isSelected
                    ? R.drawable.calendar_day_today_ring
                    : R.drawable.calendar_day_number_background);

            List<Integer> colours = shouldShowDots(dateKey) ? dayCategoryColours.get(dateKey) : null;
            for(int i = 0; i < dots.length; i++)
            {
                if(colours != null && i < colours.size())
                {
                    dots[i].setVisibility(View.VISIBLE);
                    ViewCompat.setBackgroundTintList(dots[i], ColorStateList.valueOf(colours.get(i)));
                }
                else
                {
                    dots[i].setVisibility(View.INVISIBLE);
                }
            }

            return view;
        }
    }

    // List View (Vague 3, item 10, retour 08/09/2026) - liste chronologique
    // (plus recent en premier) des seances, respectant le filtre actif s'il y en a
    // un (memes regles que shouldShowDots() : jamais applique hors mode Browse).
    private class CalendarListAdapter extends RecyclerView.Adapter<CalendarListAdapter.RowHolder>
    {
        private final List<String> dateKeys = new ArrayList<>();

        void rebuild()
        {
            dateKeys.clear();
            for(Map.Entry<String, WorkoutDay> entry : workoutDaysByDate.entrySet())
            {
                if(!browsingFeaturesEnabled || !filter.isActive() || filter.matches(entry.getValue()))
                {
                    dateKeys.add(entry.getKey());
                }
            }
            Collections.sort(dateKeys, Collections.reverseOrder());
            notifyDataSetChanged();
        }

        // Position de la premiere seance datee au plus tard a dateKey (liste triee du plus
        // recent au plus ancien), ou de la derniere si toutes sont plus recentes.
        int positionOnOrBefore(String dateKey)
        {
            for(int i = 0; i < dateKeys.size(); i++)
            {
                if(dateKeys.get(i).compareTo(dateKey) <= 0)
                {
                    return i;
                }
            }
            return Math.max(0, dateKeys.size() - 1);
        }

        @NonNull
        @Override
        public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
        {
            View view = LayoutInflater.from(getContext()).inflate(R.layout.calendar_day_list_row, parent, false);
            return new RowHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RowHolder holder, int position)
        {
            String dateKey = dateKeys.get(position);
            WorkoutDay day = workoutDaysByDate.get(dateKey);

            holder.dateLabel.setText(WorkoutReportGenerator.formatDateHeader(dateKey));

            holder.exercisesContainer.removeAllViews();
            for(WorkoutExercise exercise : day.getExercises())
            {
                holder.exercisesContainer.addView(buildExerciseLine(exercise));
            }

            holder.itemView.setOnClickListener(v -> {
                dismiss();
                listener.onDaySelected(dateKey);
            });
        }

        @Override
        public int getItemCount()
        {
            return dateKeys.size();
        }

        class RowHolder extends RecyclerView.ViewHolder
        {
            TextView dateLabel;
            LinearLayout exercisesContainer;

            RowHolder(@NonNull View itemView)
            {
                super(itemView);
                dateLabel = itemView.findViewById(R.id.calendar_list_row_date);
                exercisesContainer = itemView.findViewById(R.id.calendar_list_row_exercises);
            }
        }
    }
}
