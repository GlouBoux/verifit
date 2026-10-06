package com.example.verifit.ui;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.verifit.ExerciseGraphData;
import com.example.verifit.ExerciseGraphData.Point;
import com.example.verifit.ExerciseGraphData.ProfileRow;
import com.example.verifit.R;
import com.example.verifit.RepRangePREvent;
import com.example.verifit.model.WorkoutDay;
import com.example.verifit.model.WorkoutSet;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

// Onglet Graph d'un exercice (retour Romain 06/10/2026 : "l'onglet graph est inutilisable
// actuellement [...] reprend ce que fait FitNotes (sans la totalite). Max weight, volume,
// rep, workout volume, workout reps ne m'interessent pas. Estimated 1RM, PR, max weight for
// rep oui [...] l'idee est que je puisse suivre sur l'app que je progresse bien dans le temps
// sur un exercice et idealement identifier ou ca peche"). Remplace l'ancien dialogue
// "Progress" (Volume / Weight / Reps, axe Y masque, aucune tendance).
//
// Quatre vues (tous les calculs sont dans ExerciseGraphData, teste en JUnit) :
//  - Estimated 1RM : meilleur 1RM estime de chaque seance ;
//  - Max Weight for Reps : poids max par seance pour N reps (ou plus), peut baisser ;
//  - Personal Records : escalier des records pour N reps, ne baisse jamais, prolonge
//    jusqu'a aujourd'hui pour voir depuis combien de temps il tient ;
//  - Profil par reps : pour chaque nombre de reps, record absolu contre meilleur sur la
//    periode choisie, pour reperer ou ca stagne.
// Periode 1m / 3m / 6m / 1y / all, ligne de tendance, "Y-Axis From 0" et "Graph Points" dans le
// menu "...", detail du point touche (navigable) et analyse en clair sous le graphique.
public class ExerciseGraphActivity extends AppCompatActivity implements OnChartValueSelectedListener
{
    public static final String EXTRA_EXERCISE_NAME = "exercise_name";

    private static final String PREFS = "exercise_graph_prefs";
    private static final String PREF_TREND = "trend_line";
    private static final String PREF_POINTS = "graph_points";
    private static final String PREF_FROM_ZERO = "y_axis_from_zero";

    private static final int TYPE_E1RM = 0;
    private static final int TYPE_MAX_WEIGHT_REPS = 1;
    private static final int TYPE_RECORDS = 2;
    private static final int TYPE_PROFILE = 3;
    private static final String[] TYPE_LABELS =
            {"Estimated 1RM", "Max Weight for Reps", "Personal Records", "Profil par reps"};

    // Orange lisible sur fond clair comme sur fond sombre (courbe "meilleur recent").
    private static final int COLOR_RECENT = Color.rgb(255, 152, 0);

    private String exerciseName = "";
    private ArrayList<ExerciseGraphData.Session> sessions = new ArrayList<>();
    private TreeMap<Integer, ArrayList<RepRangePREvent>> history = new TreeMap<>();
    private ArrayList<Integer> repOptions = new ArrayList<>();
    private long today;
    private long baseDay;

    private int type = TYPE_E1RM;
    private int periodIndex = 2;
    private int reps = -1;
    private boolean showTrend = true;
    private boolean showPoints = true;
    private boolean yFromZero = false;

    // Donnees de la vue courante
    private ArrayList<Point> allPoints = new ArrayList<>();
    private ArrayList<Point> periodPoints = new ArrayList<>();
    private ArrayList<ProfileRow> profileRows = new ArrayList<>();
    private final ArrayList<Entry> selectable = new ArrayList<>();
    private int selectedIdx = -1;

    private LineChart chart;
    private Spinner typeSpinner;
    private Spinner repsSpinner;
    private LinearLayout periodBar;
    private View detailCard;
    private TextView tvDetailTitle;
    private TextView tvDetailMain;
    private TextView tvDetailSub;
    private TextView tvAnalysis;
    private ImageButton btnPrev;
    private ImageButton btnNext;
    private final ArrayList<TextView> periodButtons = new ArrayList<>();

    private int textColor;
    private int mutedColor;
    private int gridColor;
    private int primaryColor;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exercise_graph);

        String name = getIntent().getStringExtra(EXTRA_EXERCISE_NAME);
        exerciseName = name == null ? "" : name;

        if (getSupportActionBar() != null)
        {
            getSupportActionBar().setTitle(exerciseName);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (MainActivity.dataStorage == null)
        {
            // Process recree sans que l'ecran Workout ait recharge les donnees.
            finish();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        showTrend = prefs.getBoolean(PREF_TREND, true);
        showPoints = prefs.getBoolean(PREF_POINTS, true);
        yFromZero = prefs.getBoolean(PREF_FROM_ZERO, false);

        textColor = ContextCompat.getColor(this, R.color.core_black);
        mutedColor = ContextCompat.getColor(this, R.color.core_grey_55);
        gridColor = ContextCompat.getColor(this, R.color.core_grey_20);
        primaryColor = ContextCompat.getColor(this, R.color.colorPrimary);

        loadData();
        bindViews();
        setupChartOnce();
        setupSpinners();
        setupPeriodBar();

        // Periode par defaut 6m ; si l'exercice n'a rien de recent, on elargit a "all"
        // plutot que d'ouvrir sur un graphique vide.
        compute();
        if (type != TYPE_PROFILE && type != TYPE_RECORDS && periodIndex != 4
                && ExerciseGraphData.realPoints(periodPoints).isEmpty()
                && !ExerciseGraphData.realPoints(allPoints).isEmpty())
        {
            periodIndex = 4;
            updatePeriodButtons();
        }
        render();
    }

    // ------------------------------------------------------------------ donnees

    private void loadData()
    {
        ArrayList<WorkoutSet> sets = new ArrayList<>();
        for (WorkoutDay day : MainActivity.dataStorage.getWorkoutDays())
        {
            for (WorkoutSet s : day.getSets())
            {
                if (exerciseName.equals(s.getExerciseName()))
                {
                    sets.add(s);
                }
            }
        }
        sessions = ExerciseGraphData.buildSessions(sets);
        history = MainActivity.dataStorage.calculateRepRangeHistory(exerciseName);
        today = ExerciseGraphData.todayEpochDay();
        baseDay = sessions.isEmpty() ? today : Math.min(today, sessions.get(0).day);
        repOptions = ExerciseGraphData.performedRepCounts(sessions);
        reps = ExerciseGraphData.defaultReps(sessions);
    }

    private long periodFrom()
    {
        int days = ExerciseGraphData.PERIOD_DAYS[periodIndex];
        return days == 0 ? Long.MIN_VALUE : today - days;
    }

    private void compute()
    {
        long from = periodFrom();
        allPoints = new ArrayList<>();
        periodPoints = new ArrayList<>();
        profileRows = new ArrayList<>();

        if (type == TYPE_E1RM)
        {
            allPoints = ExerciseGraphData.e1rmSeries(sessions);
            periodPoints = ExerciseGraphData.inPeriod(allPoints, from);
        }
        else if (type == TYPE_MAX_WEIGHT_REPS)
        {
            allPoints = ExerciseGraphData.maxWeightForReps(sessions, reps);
            periodPoints = ExerciseGraphData.inPeriod(allPoints, from);
        }
        else if (type == TYPE_RECORDS)
        {
            allPoints = ExerciseGraphData.recordSteps(history, reps, today);
            periodPoints = ExerciseGraphData.stepsInPeriod(allPoints, from);
        }
        else
        {
            profileRows = ExerciseGraphData.repProfile(sessions, history, from, today);
        }
    }

    // ------------------------------------------------------------------ vues

    private void bindViews()
    {
        chart = findViewById(R.id.exercise_line_chart);
        typeSpinner = findViewById(R.id.graph_type_spinner);
        repsSpinner = findViewById(R.id.graph_reps_spinner);
        periodBar = findViewById(R.id.period_bar);
        detailCard = findViewById(R.id.detail_card);
        tvDetailTitle = findViewById(R.id.tv_detail_title);
        tvDetailMain = findViewById(R.id.tv_detail_main);
        tvDetailSub = findViewById(R.id.tv_detail_sub);
        tvAnalysis = findViewById(R.id.tv_analysis);
        btnPrev = findViewById(R.id.btn_prev_point);
        btnNext = findViewById(R.id.btn_next_point);

        btnPrev.setOnClickListener(v -> selectIndex(selectedIdx - 1));
        btnNext.setOnClickListener(v -> selectIndex(selectedIdx + 1));
    }

    private void setupSpinners()
    {
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, TYPE_LABELS);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(typeAdapter);
        typeSpinner.setSelection(type, false);
        typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
        {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
            {
                if (position == type)
                {
                    return;
                }
                type = position;
                updateRepsVisibility();
                refresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        ArrayList<String> repLabels = new ArrayList<>();
        for (Integer r : repOptions)
        {
            repLabels.add(r + (r == 1 ? " rep" : " reps"));
        }
        ArrayAdapter<String> repsAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, repLabels);
        repsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        repsSpinner.setAdapter(repsAdapter);
        int repsPos = repOptions.indexOf(reps);
        repsSpinner.setSelection(Math.max(0, repsPos), false);
        repsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
        {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
            {
                if (position < 0 || position >= repOptions.size() || repOptions.get(position) == reps)
                {
                    return;
                }
                reps = repOptions.get(position);
                refresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        updateRepsVisibility();
    }

    private void updateRepsVisibility()
    {
        boolean needsReps = (type == TYPE_MAX_WEIGHT_REPS || type == TYPE_RECORDS) && !repOptions.isEmpty();
        repsSpinner.setVisibility(needsReps ? View.VISIBLE : View.GONE);
    }

    private int dp(float v)
    {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void setupPeriodBar()
    {
        for (int i = 0; i < ExerciseGraphData.PERIOD_LABELS.length; i++)
        {
            final int index = i;
            TextView tv = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1f);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            tv.setLayoutParams(lp);
            tv.setGravity(Gravity.CENTER);
            tv.setText(ExerciseGraphData.PERIOD_LABELS[i]);
            tv.setTextSize(14f);
            tv.setOnClickListener(v ->
            {
                if (periodIndex != index)
                {
                    periodIndex = index;
                    updatePeriodButtons();
                    refresh();
                }
            });
            periodBar.addView(tv);
            periodButtons.add(tv);
        }
        updatePeriodButtons();
    }

    private void updatePeriodButtons()
    {
        int selectedText = ContextCompat.getColor(this, R.color.calendar_day_selected_text);
        for (int i = 0; i < periodButtons.size(); i++)
        {
            TextView tv = periodButtons.get(i);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(8));
            if (i == periodIndex)
            {
                bg.setColor(primaryColor);
                tv.setTextColor(selectedText);
            }
            else
            {
                bg.setColor(Color.TRANSPARENT);
                bg.setStroke(dp(1), gridColor);
                tv.setTextColor(textColor);
            }
            tv.setBackground(bg);
        }
    }

    private void setupChartOnce()
    {
        chart.getDescription().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setNoDataTextColor(mutedColor);
        chart.getAxisRight().setEnabled(false);
        // Pas de zoom ni de glisser : le graphique est dans un ScrollView (un glisser
        // vertical doit faire defiler la page) et la periode se choisit avec 1m/3m/6m/1y/all.
        chart.setDragEnabled(false);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.setHighlightPerTapEnabled(true);
        chart.setMaxHighlightDistance(48f);
        chart.setExtraOffsets(0f, 8f, 8f, 8f);
        chart.setOnChartValueSelectedListener(this);

        XAxis x = chart.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setDrawGridLines(false);
        x.setTextColor(textColor);
        x.setTextSize(11f);
        x.setAxisLineColor(gridColor);
        x.setGranularityEnabled(true);
        x.setGranularity(1f);
        x.setAvoidFirstLastClipping(true);

        YAxis y = chart.getAxisLeft();
        y.setTextColor(textColor);
        y.setTextSize(11f);
        y.setDrawGridLines(true);
        y.setGridColor(gridColor);
        y.setGridLineWidth(1f);
        y.setDrawAxisLine(false);
        y.setSpaceTop(15f);
        y.setSpaceBottom(15f);
        y.setValueFormatter(new ValueFormatter()
        {
            @Override
            public String getFormattedValue(float value)
            {
                return ExerciseGraphData.formatKg(value);
            }
        });
    }

    // ------------------------------------------------------------------ rendu

    private void refresh()
    {
        compute();
        render();
    }

    private boolean hasData()
    {
        if (type == TYPE_PROFILE)
        {
            return !profileRows.isEmpty();
        }
        if (type == TYPE_RECORDS)
        {
            // Escalier : meme sans nouveau record sur la periode, la ligne plate (niveau
            // atteint) montre la stagnation.
            return !ExerciseGraphData.realPoints(allPoints).isEmpty();
        }
        return !ExerciseGraphData.realPoints(periodPoints).isEmpty();
    }

    private String analysisText()
    {
        Locale locale = Locale.getDefault();
        long from = periodFrom();
        if (type == TYPE_PROFILE)
        {
            return ExerciseGraphData.analyseProfile(profileRows, ExerciseGraphData.PERIOD_LABELS[periodIndex]);
        }
        if (type == TYPE_RECORDS)
        {
            return ExerciseGraphData.analyseRecords(allPoints, reps, from, today, locale);
        }
        return ExerciseGraphData.analyseSeries(allPoints, periodPoints, today,
                type == TYPE_E1RM ? "1RM estimé" : "poids", locale);
    }

    private void render()
    {
        selectable.clear();
        selectedIdx = -1;
        tvAnalysis.setText(sessions.isEmpty()
                ? "Aucune série avec une charge enregistrée pour cet exercice."
                : analysisText());

        if (!hasData())
        {
            chart.clear();
            chart.setNoDataText(sessions.isEmpty()
                    ? "Aucune donnée pour cet exercice"
                    : "Aucune séance sur cette période");
            chart.invalidate();
            detailCard.setVisibility(View.GONE);
            return;
        }

        LineData data = new LineData();
        long minDay;
        long maxDay;

        if (type == TYPE_PROFILE)
        {
            buildProfile(data);
            minDay = profileRows.get(0).reps - 1;
            maxDay = profileRows.get(profileRows.size() - 1).reps + 1;
        }
        else
        {
            long[] range = buildSeries(data);
            minDay = range[0];
            maxDay = range[1];
        }

        configureAxes(minDay, maxDay);
        chart.setData(data);
        chart.highlightValues(null);
        chart.invalidate();

        detailCard.setVisibility(View.VISIBLE);
        if (!selectable.isEmpty())
        {
            selectIndex(selectable.size() - 1);
        }
        else
        {
            showOutsidePeriodRecord();
        }
    }

    // Construit la courbe par seance / l'escalier de records. Retourne {jour min, jour max}
    // de l'axe X (en jours depuis 1970).
    private long[] buildSeries(LineData data)
    {
        ArrayList<Entry> entries = new ArrayList<>();
        ArrayList<Integer> circleColors = new ArrayList<>();
        int realCount = 0;
        for (Point p : periodPoints)
        {
            Entry e = new Entry(p.day - baseDay, (float) p.value, p);
            entries.add(e);
            if (p.extension)
            {
                circleColors.add(Color.TRANSPARENT);
            }
            else
            {
                realCount++;
                selectable.add(e);
                if (p.record)
                {
                    circleColors.add(ContextCompat.getColor(this, R.color.core_yellow));
                }
                else if (p.deduced)
                {
                    circleColors.add(mutedColor);
                }
                else
                {
                    circleColors.add(primaryColor);
                }
            }
        }

        LineDataSet ds = new LineDataSet(entries, TYPE_LABELS[type]);
        ds.setColor(primaryColor);
        ds.setLineWidth(2.5f);
        ds.setDrawValues(false);
        // Un seul point : sans cercle on ne verrait rien.
        ds.setDrawCircles(showPoints || realCount == 1);
        ds.setCircleColors(circleColors);
        ds.setCircleRadius(4.5f);
        ds.setDrawCircleHole(false);
        ds.setHighLightColor(mutedColor);
        ds.setHighlightLineWidth(1.5f);
        ds.setDrawHorizontalHighlightIndicator(false);
        if (type == TYPE_RECORDS)
        {
            ds.setMode(LineDataSet.Mode.STEPPED);
        }
        data.addDataSet(ds);

        // Ligne de tendance (pas pour l'escalier de records, qui monte par construction).
        if (showTrend && type != TYPE_RECORDS)
        {
            double[] fit = ExerciseGraphData.linearFit(periodPoints);
            ArrayList<Point> real = ExerciseGraphData.realPoints(periodPoints);
            if (fit != null && real.size() >= 2)
            {
                long d0 = real.get(0).day;
                long d1 = real.get(real.size() - 1).day;
                ArrayList<Entry> trend = new ArrayList<>();
                trend.add(new Entry(d0 - baseDay, (float) ExerciseGraphData.fitValueAt(fit, d0)));
                trend.add(new Entry(d1 - baseDay, (float) ExerciseGraphData.fitValueAt(fit, d1)));
                LineDataSet td = new LineDataSet(trend, "Trend");
                td.setColor(ContextCompat.getColor(this, R.color.red));
                td.setLineWidth(1.8f);
                td.enableDashedLine(14f, 9f, 0f);
                td.setDrawCircles(false);
                td.setDrawValues(false);
                td.setHighlightEnabled(false);
                data.addDataSet(td);
            }
        }

        long start;
        if (periodFrom() == Long.MIN_VALUE)
        {
            start = periodPoints.isEmpty() ? today : periodPoints.get(0).day;
        }
        else
        {
            start = periodFrom();
        }
        long span = Math.max(1, today - start);
        long pad = Math.max(1, Math.round(span * 0.03));
        return new long[]{start - pad, today + pad};
    }

    private void buildProfile(LineData data)
    {
        ArrayList<Entry> record = new ArrayList<>();
        ArrayList<Entry> recent = new ArrayList<>();
        for (ProfileRow row : profileRows)
        {
            Entry e = new Entry(row.reps, (float) row.recordWeight, row);
            record.add(e);
            selectable.add(e);
            if (row.worked())
            {
                recent.add(new Entry(row.reps, (float) row.recentWeight, row));
            }
        }

        LineDataSet recordSet = new LineDataSet(record, "Record absolu");
        recordSet.setColor(primaryColor);
        recordSet.setLineWidth(2.5f);
        recordSet.setDrawValues(false);
        recordSet.setDrawCircles(showPoints);
        recordSet.setCircleColor(primaryColor);
        recordSet.setCircleRadius(3.5f);
        recordSet.setDrawCircleHole(false);
        recordSet.setHighLightColor(mutedColor);
        recordSet.setDrawHorizontalHighlightIndicator(false);
        data.addDataSet(recordSet);

        if (!recent.isEmpty())
        {
            LineDataSet recentSet = new LineDataSet(recent,
                    "Meilleur (" + ExerciseGraphData.PERIOD_LABELS[periodIndex] + ")");
            recentSet.setColor(COLOR_RECENT);
            recentSet.setLineWidth(2.5f);
            recentSet.setDrawValues(false);
            recentSet.setDrawCircles(showPoints);
            recentSet.setCircleColor(COLOR_RECENT);
            recentSet.setCircleRadius(3.5f);
            recentSet.setDrawCircleHole(false);
            recentSet.setHighLightColor(mutedColor);
            recentSet.setDrawHorizontalHighlightIndicator(false);
            data.addDataSet(recentSet);
        }
    }

    private void configureAxes(final long minDay, final long maxDay)
    {
        XAxis x = chart.getXAxis();
        YAxis y = chart.getAxisLeft();

        if (type == TYPE_PROFILE)
        {
            x.setAxisMinimum(minDay);
            x.setAxisMaximum(maxDay);
            x.setLabelCount(6, false);
            x.setValueFormatter(new ValueFormatter()
            {
                @Override
                public String getFormattedValue(float value)
                {
                    return String.valueOf(Math.round(value));
                }
            });
        }
        else
        {
            x.setAxisMinimum(minDay - baseDay);
            x.setAxisMaximum(maxDay - baseDay);
            x.setLabelCount(5, false);
            final boolean longSpan = (maxDay - minDay) > 330;
            x.setValueFormatter(new ValueFormatter()
            {
                @Override
                public String getFormattedValue(float value)
                {
                    return ExerciseGraphData.formatAxisDay(baseDay + Math.round(value), longSpan,
                            Locale.getDefault());
                }
            });
        }

        if (yFromZero)
        {
            y.setAxisMinimum(0f);
        }
        else
        {
            y.resetAxisMinimum();
        }
        y.setLabelCount(6, false);

        Legend legend = chart.getLegend();
        legend.setEnabled(type == TYPE_PROFILE);
        legend.setTextColor(textColor);
        legend.setForm(Legend.LegendForm.LINE);
        legend.setWordWrapEnabled(true);
    }

    // ------------------------------------------------------------------ selection

    private void selectIndex(int idx)
    {
        if (selectable.isEmpty())
        {
            return;
        }
        int i = Math.max(0, Math.min(selectable.size() - 1, idx));
        selectedIdx = i;
        Entry e = selectable.get(i);
        chart.highlightValue(e.getX(), 0, false);
        showDetail(e.getData(), false);
        btnPrev.setAlpha(i > 0 ? 1f : 0.3f);
        btnNext.setAlpha(i < selectable.size() - 1 ? 1f : 0.3f);
    }

    @Override
    public void onValueSelected(Entry e, Highlight h)
    {
        Object d = e.getData();
        for (int i = 0; i < selectable.size(); i++)
        {
            if (selectable.get(i).getData() == d)
            {
                selectIndex(i);
                return;
            }
        }
        // Point technique (prolongement de l'escalier) : on garde la selection courante.
        if (selectedIdx >= 0)
        {
            selectIndex(selectedIdx);
        }
    }

    @Override
    public void onNothingSelected()
    {
        // Un tap dans le vide efface le surlignage : on le remet sur la selection courante.
        if (selectedIdx >= 0 && selectedIdx < selectable.size())
        {
            chart.highlightValue(selectable.get(selectedIdx).getX(), 0, false);
        }
    }

    // Escalier de records sans aucun record dans la periode : pas de point a toucher, on
    // affiche le dernier record connu.
    private void showOutsidePeriodRecord()
    {
        ArrayList<Point> real = ExerciseGraphData.realPoints(allPoints);
        btnPrev.setAlpha(0.3f);
        btnNext.setAlpha(0.3f);
        if (!real.isEmpty())
        {
            showDetail(real.get(real.size() - 1), true);
        }
    }

    private void showDetail(Object data, boolean outsidePeriod)
    {
        Locale locale = Locale.getDefault();
        if (data instanceof ProfileRow)
        {
            ProfileRow r = (ProfileRow) data;
            tvDetailTitle.setText(r.reps + (r.reps == 1 ? " rep" : " reps"));
            tvDetailMain.setText("Record " + ExerciseGraphData.formatKg(r.recordWeight) + " kg");
            StringBuilder sb = new StringBuilder();
            sb.append("Établi le ")
                    .append(ExerciseGraphData.formatDay(ExerciseGraphData.epochDay(r.recordDate), true, locale));
            if (r.recordDeduced)
            {
                sb.append(" (déduit d'une série de ").append(r.recordSourceReps).append(" reps)");
            }
            sb.append("\n");
            if (r.worked())
            {
                sb.append("Meilleur sur la période : ").append(ExerciseGraphData.formatKg(r.recentWeight))
                        .append(" kg");
                if (!Double.isNaN(r.gapPct))
                {
                    sb.append(String.format(Locale.US, " (%+.1f %%)", r.gapPct));
                }
            }
            else
            {
                sb.append("Pas travaillé sur la période");
            }
            tvDetailSub.setText(sb.toString());
            return;
        }

        Point p = (Point) data;
        String dateText = p.date.isEmpty() ? "" : ExerciseGraphData.formatDay(
                ExerciseGraphData.epochDay(p.date), true, locale);
        tvDetailTitle.setText(outsidePeriod ? "Dernier record, hors période (" + dateText + ")" : dateText);

        if (type == TYPE_E1RM)
        {
            tvDetailMain.setText("1RM estimé : " + ExerciseGraphData.formatKg(p.value) + " kg");
            StringBuilder sb = new StringBuilder("Meilleure série : ")
                    .append(ExerciseGraphData.formatKg(p.weight)).append(" kg × ").append(p.reps).append(" reps");
            if (p.setCount > 1)
            {
                sb.append(" (").append(p.setCount).append(" séries ce jour)");
            }
            if (p.record)
            {
                sb.append("\nNouveau record de 1RM estimé");
            }
            tvDetailSub.setText(sb.toString());
        }
        else if (type == TYPE_MAX_WEIGHT_REPS)
        {
            tvDetailMain.setText(ExerciseGraphData.formatKg(p.value) + " kg");
            StringBuilder sb = new StringBuilder();
            if (p.deduced)
            {
                sb.append("Série de ").append(p.reps).append(" reps (compte pour ").append(reps).append(" reps)");
            }
            else
            {
                sb.append("Série de ").append(p.reps).append(" reps");
            }
            if (p.record)
            {
                sb.append("\nNouveau meilleur poids à ").append(reps).append(" reps");
            }
            tvDetailSub.setText(sb.toString());
        }
        else
        {
            tvDetailMain.setText("Record " + ExerciseGraphData.formatKg(p.value) + " kg");
            tvDetailSub.setText(p.deduced
                    ? "À " + reps + " reps, déduit d'une série de " + p.reps + " reps"
                    : "À " + reps + " reps, série réelle");
        }
    }

    // ------------------------------------------------------------------ menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.exercise_graph_menu, menu);
        menu.findItem(R.id.graph_opt_trend).setChecked(showTrend);
        menu.findItem(R.id.graph_opt_points).setChecked(showPoints);
        menu.findItem(R.id.graph_opt_from_zero).setChecked(yFromZero);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item)
    {
        int id = item.getItemId();
        if (id == android.R.id.home)
        {
            finish();
            return true;
        }

        String key = null;
        boolean value = !item.isChecked();
        if (id == R.id.graph_opt_trend)
        {
            showTrend = value;
            key = PREF_TREND;
        }
        else if (id == R.id.graph_opt_points)
        {
            showPoints = value;
            key = PREF_POINTS;
        }
        else if (id == R.id.graph_opt_from_zero)
        {
            yFromZero = value;
            key = PREF_FROM_ZERO;
        }

        if (key != null)
        {
            item.setChecked(value);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(key, value).apply();
            refresh();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
