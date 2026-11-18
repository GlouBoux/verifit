package com.example.verifit.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.example.verifit.AppNames;
import com.example.verifit.CrashLog;
import com.example.verifit.DataStorage;
import com.example.verifit.EngineContent;
import com.example.verifit.EngineRepository;
import com.example.verifit.EngineSession;
import com.example.verifit.EngineStorage;
import com.example.verifit.PythonEngine;
import com.example.verifit.SessionPreview;
import com.example.verifit.model.Exercise;
import com.example.verifit.model.WorkoutDay;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Onglet Workout > « Générer une séance » (lot M4.2, 18/11/2026) : remplace sur le téléphone
 * l'étape 1 de la boucle PC (generate_workout.py).
 *
 * 1. lecture des données du moteur (stockage M3) hors du thread principal ;
 * 2. dialogue : cycle, type (proposés d'après la dernière séance du carnet, voir
 *    EngineSession.defaultChoice), date (aujourd'hui par défaut) ;
 * 3. calcul hors du thread principal (PythonEngine.generate), erreurs vers le journal de
 *    plantage et message clair ;
 * 4. résultat envoyé dans l'aperçu EXISTANT (ImportSessionActivity) : mêmes contrôles, même
 *    bouton « Importer », même copie automatique avant import. Le plan est gardé dans
 *    plans_en_attente.json au moment de l'import (voir ImportSessionActivity).
 */
final class GenerateSessionFlow
{
    private static final String TAG = "GenerateSessionFlow";

    private GenerateSessionFlow() {}

    static void start(final Activity activity)
    {
        final DataStorage ds = MainActivity.dataStorage;
        if (ds == null)
        {
            return;
        }
        if (ds.hasLoadProblem())
        {
            message(activity, "Génération impossible", "Les données de l'app sont illisibles. Restaure d'abord un backup.");
            return;
        }
        final Context appContext = activity.getApplicationContext();
        new Thread(() -> {
            EngineStorage.Loaded loaded;
            try
            {
                loaded = EngineRepository.get(appContext);
            }
            catch (RuntimeException e)
            {
                CrashLog.record("generer_seance_lecture", e);
                activity.runOnUiThread(() -> message(activity, "Génération impossible", "Lecture des données du moteur : " + e.getMessage()));
                return;
            }
            final EngineStorage.Loaded data = loaded;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing())
                {
                    return;
                }
                if (data.status == EngineStorage.Status.EMPTY)
                {
                    message(activity, "Aucune donnée du moteur",
                            "Importe d'abord les données du moteur : Réglages > Moteur > Importer les données du moteur.");
                    return;
                }
                if (data.status != EngineStorage.Status.OK)
                {
                    message(activity, "Données du moteur bloquées",
                            data.problem + "\n\nRefais l'import (Réglages > Moteur). Le carnet reste utilisable.");
                    return;
                }
                List<String> cycles;
                try
                {
                    cycles = EngineSession.cycles(data.content.text(EngineContent.DATA));
                }
                catch (RuntimeException e)
                {
                    CrashLog.record("generer_seance_cycles", e);
                    message(activity, "Génération impossible", "Cycles illisibles : " + e.getMessage());
                    return;
                }
                if (cycles.isEmpty())
                {
                    message(activity, "Génération impossible", "Aucun cycle dans les données du moteur.");
                    return;
                }
                askChoice(activity, cycles, EngineSession.defaultChoice(commentsByDate(ds), cycles));
            });
        }, AppNames.FILE_PREFIX + "-generer-lecture").start();
    }

    private static void askChoice(final Activity activity, final List<String> cycles, final EngineSession.Choice choice)
    {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 20);
        layout.setPadding(pad, dp(activity, 8), pad, 0);

        layout.addView(label(activity, "Cycle"));
        final RadioGroup cycleGroup = radioGroup(activity);
        final List<RadioButton> cycleButtons = new ArrayList<RadioButton>();
        for (String c : cycles)
        {
            RadioButton b = radio(activity, c);
            cycleGroup.addView(b);
            cycleButtons.add(b);
            b.setChecked(c.equals(choice.cycle));
        }
        layout.addView(cycleGroup);

        layout.addView(label(activity, "Type"));
        final RadioGroup typeGroup = radioGroup(activity);
        final RadioButton force = radio(activity, EngineSession.FORCE);
        final RadioButton volume = radio(activity, EngineSession.VOLUME);
        typeGroup.addView(force);
        typeGroup.addView(volume);
        force.setChecked(EngineSession.FORCE.equals(choice.type));
        volume.setChecked(EngineSession.VOLUME.equals(choice.type));
        layout.addView(typeGroup);

        layout.addView(label(activity, "Date de la séance (AAAA-MM-JJ)"));
        final EditText dateInput = new EditText(activity);
        dateInput.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE);
        dateInput.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()));
        layout.addView(dateInput);

        TextView hint = new TextView(activity);
        hint.setText(choice.basedOn == null
                ? "Aucune séance précédente reconnue : proposition par défaut."
                : "Proposé d'après la dernière séance : " + choice.basedOn + ".");
        hint.setPadding(0, dp(activity, 8), 0, 0);
        layout.addView(hint);

        new AlertDialog.Builder(activity)
                .setTitle("Générer une séance")
                .setView(layout)
                .setPositiveButton("Générer", (d, w) -> {
                    String cycle = null;
                    for (int i = 0; i < cycleButtons.size(); i++)
                    {
                        if (cycleButtons.get(i).isChecked())
                        {
                            cycle = cycles.get(i);
                        }
                    }
                    String type = volume.isChecked() ? EngineSession.VOLUME : EngineSession.FORCE;
                    String date = dateInput.getText().toString().trim();
                    String dateProblem = SessionPreview.dateProblem(date);
                    if (cycle == null)
                    {
                        message(activity, "Génération impossible", "Choisis un cycle.");
                        return;
                    }
                    if (dateProblem != null)
                    {
                        message(activity, "Date invalide", dateProblem);
                        return;
                    }
                    generate(activity, cycle, type, date);
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private static void generate(final Activity activity, final String cycle, final String type, final String date)
    {
        final Context appContext = activity.getApplicationContext();
        final Map<String, EngineSession.Assist> assistance = assistance(MainActivity.dataStorage);
        final String fileName = EngineSession.fileName(type, cycle, new Date());

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.HORIZONTAL);
        int pad = dp(activity, 20);
        box.setPadding(pad, pad, pad, pad);
        box.addView(new ProgressBar(activity));
        TextView text = new TextView(activity);
        text.setText("Calcul de la séance Cycle " + cycle + " - " + type + "…");
        text.setPadding(pad, 0, 0, 0);
        box.addView(text);
        final AlertDialog progress = new AlertDialog.Builder(activity).setView(box).setCancelable(false).show();

        new Thread(() -> {
            EngineSession.Result result = null;
            String error = null;
            try
            {
                result = PythonEngine.generate(appContext, assistance, cycle, type, date);
            }
            catch (Exception e)
            {
                Log.e(TAG, "Calcul de la seance impossible", e);
                CrashLog.record("generer_seance", e);
                error = e.getMessage();
            }
            final EngineSession.Result done = result;
            final String failure = error;
            activity.runOnUiThread(() -> {
                if (progress.isShowing() && !activity.isDestroyed())
                {
                    progress.dismiss();
                }
                if (activity.isFinishing())
                {
                    return;
                }
                if (done == null)
                {
                    message(activity, "Calcul impossible", failure
                            + "\n\nRien n'a été modifié. Le détail est dans le journal de plantage.");
                    return;
                }
                GeneratedPlan plan = GeneratedPlan.put(fileName, done);
                Intent intent = new Intent(activity, ImportSessionActivity.class);
                intent.putExtra(GeneratedPlan.EXTRA_TOKEN, plan.token);
                intent.putExtra(ImportSessionActivity.EXTRA_FALLBACK_DATE, date);
                activity.startActivity(intent);
            });
        }, AppNames.FILE_PREFIX + "-generer-seance").start();
    }

    // Assistance de chaque exercice de l'app qui en a une (decision 5 du lot M3).
    private static Map<String, EngineSession.Assist> assistance(DataStorage ds)
    {
        HashMap<String, EngineSession.Assist> map = new HashMap<String, EngineSession.Assist>();
        for (Exercise e : ds.getKnownExercises())
        {
            if (e.getAssistRefKg() != null)
            {
                map.put(e.getName(), new EngineSession.Assist(e.getAssistRefKg(), e.isAssistPerSide()));
            }
        }
        return map;
    }

    private static Map<String, String> commentsByDate(DataStorage ds)
    {
        HashMap<String, String> map = new HashMap<String, String>();
        for (WorkoutDay day : ds.getWorkoutDays())
        {
            if (day.getDate() != null && day.getComment() != null)
            {
                map.put(day.getDate(), day.getComment());
            }
        }
        return map;
    }

    private static TextView label(Activity activity, String text)
    {
        TextView v = new TextView(activity);
        v.setText(text);
        v.setPadding(0, dp(activity, 12), 0, 0);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        return v;
    }

    private static RadioGroup radioGroup(Activity activity)
    {
        RadioGroup g = new RadioGroup(activity);
        g.setOrientation(RadioGroup.HORIZONTAL);
        return g;
    }

    private static RadioButton radio(Activity activity, String text)
    {
        RadioButton b = new RadioButton(activity);
        b.setId(View.generateViewId());
        b.setText(text);
        b.setPadding(0, 0, dp(activity, 24), 0);
        return b;
    }

    private static int dp(Context context, int value)
    {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
    }

    private static void message(Activity activity, String title, String text)
    {
        new AlertDialog.Builder(activity).setTitle(title).setMessage(text).setPositiveButton("OK", null).show();
    }
}
