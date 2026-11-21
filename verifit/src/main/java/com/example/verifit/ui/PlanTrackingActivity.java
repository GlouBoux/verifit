package com.example.verifit.ui;

import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.example.verifit.AppNames;
import com.example.verifit.CrashLog;
import com.example.verifit.DataStorage;
import com.example.verifit.EngineEndOfSession;
import com.example.verifit.EnginePlanTracking;
import com.example.verifit.EngineRepository;
import com.example.verifit.EngineStorage;
import com.example.verifit.PythonEngine;

import java.util.Date;

/**
 * Ecran « Suivi des plans » (lot M5b.4, 21/11/2026) : l'equivalent de l'onglet Suivi_PR du PC,
 * par plan en attente, prevu contre realise, avec les PR enregistres de l'app. Calcul par le
 * moteur (PythonEngine.planTracking) hors du fil principal, texte EnginePlanTracking. N'ecrit
 * rien. Ouvert depuis Reglages > Moteur, seulement si EndOfSessionFlow.enabled().
 */
public class PlanTrackingActivity extends AppCompatActivity
{
    private static final String TAG = "PlanTrackingActivity";

    private TextView body;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setTitle("Suivi des plans");
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
        {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        list.setPadding(pad, pad, pad, pad);
        body = new TextView(this);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        body.setTextIsSelectable(true);
        list.addView(body);
        scroll.addView(list);
        setContentView(scroll);
        compute();
    }

    private void compute()
    {
        EngineStorage.Loaded loaded = EngineRepository.get(this);
        if (loaded.status != EngineStorage.Status.OK)
        {
            body.setText(loaded.status == EngineStorage.Status.EMPTY
                    ? "Aucune donnée du moteur dans l'app."
                    : "Données du moteur bloquées :\n" + loaded.problem);
            return;
        }
        DataStorage ds = MainActivity.dataStorage;
        if (ds == null || ds.hasLoadProblem())
        {
            body.setText("Les données de l'app sont illisibles : suivi impossible.");
            return;
        }
        body.setText("Calcul du suivi…");
        final String export = ds.buildCoachingExportJson(EngineEndOfSession.timestamp(new Date()));
        new Thread(() -> {
            String text;
            try
            {
                text = EnginePlanTracking.text(PythonEngine.planTracking(getApplicationContext(), loaded.content, export));
            }
            catch (Exception e)
            {
                Log.e(TAG, "Suivi impossible", e);
                CrashLog.record("suivi_plans", e);
                text = "Suivi impossible : " + e.getMessage();
            }
            final String shown = text;
            runOnUiThread(() -> {
                if (!isFinishing())
                {
                    body.setText(shown);
                }
            });
        }, AppNames.FILE_PREFIX + "-suivi-plans").start();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == android.R.id.home)
        {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
