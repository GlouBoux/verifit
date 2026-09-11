package com.example.verifit.ui;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.verifit.ExercisePersonalStats;
import com.example.verifit.R;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

// "Statistics par periode" (Vague 4 du plan de migration, item 13, retour Romain
// 08/09/2026 : "passe a la vague 4") - contrairement a l'hypothese initiale du plan
// (qui pensait ce selecteur manquant au-dessus d'un ecran de stats deja existant),
// aucun ecran d'agregat par exercice n'existait reellement : exercise_history_stats_dialog
// (ExerciseHistoryExerciseAdapter) est une mini-fiche PAR SEANCE (tap sur un jour de
// l'historique "Training History"), et PersonalRecordsActivity n'affiche que des
// records "depuis toujours" pour TOUS les exercices - ni l'un ni l'autre n'agrege sur
// une periode choisie pour UN exercice. Ce dialogue est donc entierement nouveau,
// ouvert depuis le nouveau menu "Statistics" de PersonalRecordsActivity (menu
// contextuel long-press, meme point d'acces que "Historique par nombre de reps" -
// voir ExerciseStatsAdapter.showPopupMenu()).
//
// Reduction de perimetre assumee (comme pour Calendar Filter, item 11) : le
// referentiel FitNotes propose aussi un choix "Workout" (parcourir seance par
// seance) en plus de Week/Month/Year/All/Custom - omis ici car deja couvert par
// l'ecran "Training History" existant (tap sur un jour -> mini-stats de cette
// seance precise). Pas de tap-through depuis une statistique vers la seance
// correspondante (propose par FitNotes) - laisse de cote pour cette premiere
// version.
public class ExerciseStatsPeriodDialog extends Dialog
{
    // Reprend Week/Month/Year/All/Custom du referentiel FitNotes (Progress Tracking,
    // onglet Stats). L'enum sert aussi d'adapter Spinner (toString() = libelle
    // affiche), meme motif que CalendarFilter.Comparison dans CalendarFilterDialog -
    // garantit que l'ordre du Spinner et l'ordre de l'enum restent toujours
    // synchronises (pas de tableau XML a maintenir en parallele).
    private enum Period
    {
        WEEK("Week"), MONTH("Month"), YEAR("Year"), ALL("All"), CUSTOM("Custom");

        private final String label;

        Period(String label)
        {
            this.label = label;
        }

        @Override
        public String toString()
        {
            return label;
        }
    }

    private static final SimpleDateFormat ISO_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    private final String exerciseName;

    private Spinner periodSpinner;
    private LinearLayout customRangeContainer;
    private Button startDateButton;
    private Button endDateButton;

    private TextView totalWorkoutsValue;
    private TextView totalSetsValue;
    private TextView totalRepsValue;
    private TextView totalVolumeValue;
    private TextView maxWeightValue;
    private TextView maxRepsValue;
    private TextView maxSetVolumeValue;
    private TextView oneRepMaxLabel;
    private TextView oneRepMaxValue;

    // Bornes "Custom", format ISO "yyyy-MM-dd" (initialisees au jour meme par defaut,
    // ajustees a chaque selection dans le DatePickerDialog).
    private String customStartDate;
    private String customEndDate;

    public ExerciseStatsPeriodDialog(Context context, String exerciseName)
    {
        super(context);
        this.exerciseName = exerciseName;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.exercise_stats_period_dialog);

        // Meme correctif que GoalDialog (retour Romain, screenshot) : sans ceci la
        // fenetre d'un Dialog personnalise peut se retrouver minuscule sur certains
        // telephones/ROMs (constate sur MIUI), meme si le layout interne est en
        // match_parent.
        if (getWindow() != null)
        {
            int dialogWidth = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.9);
            getWindow().setLayout(dialogWidth, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView title = findViewById(R.id.stats_period_exercise_name);
        title.setText(exerciseName);

        periodSpinner = findViewById(R.id.stats_period_spinner);
        customRangeContainer = findViewById(R.id.stats_period_custom_range);
        startDateButton = findViewById(R.id.stats_period_start_date);
        endDateButton = findViewById(R.id.stats_period_end_date);

        totalWorkoutsValue = findViewById(R.id.stats_period_total_workouts);
        totalSetsValue = findViewById(R.id.stats_period_total_sets);
        totalRepsValue = findViewById(R.id.stats_period_total_reps);
        totalVolumeValue = findViewById(R.id.stats_period_total_volume);
        maxWeightValue = findViewById(R.id.stats_period_max_weight);
        maxRepsValue = findViewById(R.id.stats_period_max_reps);
        maxSetVolumeValue = findViewById(R.id.stats_period_max_set_volume);
        oneRepMaxLabel = findViewById(R.id.stats_period_1rm_label);
        oneRepMaxValue = findViewById(R.id.stats_period_1rm_value);

        Button closeButton = findViewById(R.id.stats_period_close);
        closeButton.setOnClickListener(v -> dismiss());

        String today = ISO_FORMAT.format(new Date());
        customStartDate = today;
        customEndDate = today;
        updateDateButtonLabels();

        ArrayAdapter<Period> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, Period.values());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        periodSpinner.setAdapter(adapter);

        periodSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
        {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
            {
                customRangeContainer.setVisibility(position == Period.CUSTOM.ordinal() ? View.VISIBLE : View.GONE);
                refreshStats();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent)
            {
                // Rien a faire - un Spinner rempli n'a jamais de position "nulle" ici.
            }
        });

        startDateButton.setOnClickListener(v -> pickDate(true));
        endDateButton.setOnClickListener(v -> pickDate(false));

        refreshStats();
    }

    private void pickDate(boolean isStart)
    {
        Calendar calendar = Calendar.getInstance();
        try
        {
            calendar.setTime(ISO_FORMAT.parse(isStart ? customStartDate : customEndDate));
        }
        catch (ParseException e)
        {
            // Repli defensif improbable - garde la date du jour deja posee par defaut.
        }

        new DatePickerDialog(getContext(), (view, year, month, dayOfMonth) ->
        {
            Calendar picked = Calendar.getInstance();
            picked.set(year, month, dayOfMonth);
            String iso = ISO_FORMAT.format(picked.getTime());
            if (isStart)
            {
                customStartDate = iso;
            }
            else
            {
                customEndDate = iso;
            }
            updateDateButtonLabels();
            refreshStats();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDateButtonLabels()
    {
        startDateButton.setText(customStartDate);
        endDateButton.setText(customEndDate);
    }

    private void refreshStats()
    {
        Period period = Period.values()[periodSpinner.getSelectedItemPosition()];

        String startDate = null;
        String endDate = null;

        switch (period)
        {
            case WEEK:
                startDate = ISO_FORMAT.format(addDays(new Date(), -6));
                break;
            case MONTH:
                startDate = ISO_FORMAT.format(addDays(new Date(), -29));
                break;
            case YEAR:
                startDate = ISO_FORMAT.format(addDays(new Date(), -364));
                break;
            case CUSTOM:
                startDate = customStartDate;
                endDate = customEndDate;
                break;
            case ALL:
            default:
                // startDate/endDate restent null : pas de borne (agregat "depuis toujours").
                break;
        }

        ExercisePersonalStats stats = MainActivity.dataStorage.calculateExerciseStatsForPeriod(exerciseName, startDate, endDate);

        totalWorkoutsValue.setText(String.valueOf(stats.getTotalWorkouts().intValue()));
        totalSetsValue.setText(String.valueOf(stats.getTotalSets().intValue()));
        totalRepsValue.setText(String.valueOf(stats.getTotalReps().intValue()));
        totalVolumeValue.setText(stats.getTotalVolume().toString());
        maxWeightValue.setText(stats.getMaxWeight().toString());
        maxRepsValue.setText(String.valueOf(stats.getMaxReps().intValue()));
        maxSetVolumeValue.setText(stats.getMaxSetVolume().toString());

        // Meme convention que PersonalRecordsActivity/ExerciseStatsAdapter : le vrai
        // 1RM (serie a 1 rep reellement performee sur la periode) prime sur
        // l'estimation quand il existe.
        if (stats.getActual1RM() != null && stats.getActual1RM() > 0.0)
        {
            oneRepMaxLabel.setText("1RM");
            oneRepMaxValue.setText(stats.getActual1RM().toString());
        }
        else
        {
            oneRepMaxLabel.setText("Estimated 1RM");
            oneRepMaxValue.setText(String.valueOf(Math.floor(stats.getEstimated1RM())));
        }
    }

    private Date addDays(Date date, int days)
    {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return calendar.getTime();
    }
}
