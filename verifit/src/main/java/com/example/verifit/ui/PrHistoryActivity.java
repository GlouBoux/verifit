package com.example.verifit.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.example.verifit.AppNames;
import com.example.verifit.BackupManager;
import com.example.verifit.CrashLog;
import com.example.verifit.DataStorage;
import com.example.verifit.EngineContent;
import com.example.verifit.EngineEndOfSession;
import com.example.verifit.EnginePrHistory;
import com.example.verifit.EngineRepository;
import com.example.verifit.EngineStorage;
import com.example.verifit.PythonEngine;

import java.util.Date;
import java.util.List;

/**
 * Ecran « Historique des PR » (lot M5b.3, 21/11/2026) : chaque modification d'un PR faite dans
 * l'app (bilan de fin de seance, annulation, import du PC), la plus recente en haut. Remplace
 * claude/sync_fitengine_changelog.md du PC. Les PR d'un exercice ecrits par un bilan
 * s'annulent d'un coup (decision de Romain du 21/11/2026, « annulation en deux temps ») :
 * calcul par le moteur (PythonEngine.cancelPr), confirmation, copie automatique
 * avant_annulation_pr, donnees relues et comparees, ecriture puis relecture.
 * Ouvert depuis Reglages > Moteur, seulement si EndOfSessionFlow.enabled().
 */
public class PrHistoryActivity extends AppCompatActivity
{
    private static final String TAG = "PrHistoryActivity";

    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setTitle("Historique des PR");
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
        {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        list.setPadding(pad, pad, pad, pad);
        scroll.addView(list);
        setContentView(scroll);
        reload();
    }

    private void reload()
    {
        list.removeAllViews();
        EngineStorage.Loaded loaded = EngineRepository.get(this);
        if (loaded.status != EngineStorage.Status.OK)
        {
            list.addView(text(loaded.status == EngineStorage.Status.EMPTY
                    ? "Aucune donnée du moteur dans l'app."
                    : "Données du moteur bloquées :\n" + loaded.problem));
            return;
        }
        List<EnginePrHistory.Group> groups;
        try
        {
            groups = EnginePrHistory.groups(loaded.content.text(EngineContent.PR_HISTORY));
        }
        catch (RuntimeException e)
        {
            CrashLog.record("historique_pr_lecture", e);
            list.addView(text("Historique illisible : " + e.getMessage()));
            return;
        }
        if (groups.isEmpty())
        {
            list.addView(text("Aucune modification de PR dans l'app pour l'instant (elles arrivent avec le bilan de fin de séance)."));
            return;
        }
        for (final EnginePrHistory.Group g : groups)
        {
            TextView t = text(EnginePrHistory.text(g));
            t.setTextIsSelectable(true);
            t.setPadding(0, dp(12), 0, dp(4));
            list.addView(t);
            if (g.cancellable())
            {
                Button b = new Button(this);
                b.setText("Annuler ces PR");
                b.setOnClickListener(v -> prepareCancel(loaded, g));
                list.addView(b);
            }
        }
    }

    private void prepareCancel(final EngineStorage.Loaded input, final EnginePrHistory.Group g)
    {
        final DataStorage ds = MainActivity.dataStorage;
        if (ds == null || ds.hasLoadProblem())
        {
            message("Annulation impossible", "Les données de l'app sont illisibles.");
            return;
        }
        final String timestamp = EngineEndOfSession.timestamp(new Date());
        final String export = ds.buildCoachingExportJson(timestamp);
        new Thread(() -> {
            EnginePrHistory.Cancellation result = null;
            String error = null;
            try
            {
                result = PythonEngine.cancelPr(getApplicationContext(), input.content, export, g.id, g.exercise, timestamp);
            }
            catch (Exception e)
            {
                Log.e(TAG, "Annulation impossible", e);
                CrashLog.record("annulation_pr", e);
                error = e.getMessage();
            }
            final EnginePrHistory.Cancellation done = result;
            final String failure = error;
            runOnUiThread(() -> {
                if (isFinishing())
                {
                    return;
                }
                if (done == null)
                {
                    message("Annulation impossible", failure + "\n\nRien n'a été modifié.");
                    return;
                }
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setTitle("Annuler ces PR ?")
                        .setMessage(EnginePrHistory.cancellationText(done))
                        .setPositiveButton("Annuler les PR", (d, w) -> confirmCancel(input, done))
                        .setNegativeButton("Garder", null)
                        .show();
                Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                if (ok != null)
                {
                    ok.setFilterTouchesWhenObscured(true);
                }
            });
        }, AppNames.FILE_PREFIX + "-annulation-pr").start();
    }

    private void confirmCancel(final EngineStorage.Loaded input, final EnginePrHistory.Cancellation c)
    {
        if (!BackupManager.snapshot(this, MainActivity.dataStorage, "avant_annulation_pr"))
        {
            message("Annulation non faite", "La copie automatique avant écriture est impossible. Rien n'a été écrit.");
            return;
        }
        new Thread(() -> {
            String title;
            String text;
            try
            {
                EngineStorage.Loaded current = EngineRepository.get(getApplicationContext());
                if (current.status != EngineStorage.Status.OK || !current.content.sameTexts(input.content))
                {
                    title = "Annulation non faite";
                    text = "Les données du moteur ont changé entre-temps. Rien n'a été écrit : relance l'annulation.";
                }
                else
                {
                    EngineContent next = EnginePrHistory.apply(current.content, c);
                    EngineRepository.rewrite(getApplicationContext(), next, current.state);
                    title = "PR annulés";
                    text = "Écrit et relu sur le téléphone : " + c.changes.size() + " RM remis, "
                            + c.correctedLines + " ligne(s) du journal marquée(s), " + c.restoredPlans.size()
                            + " plan(s) remis en attente. Copie automatique faite avant (avant_annulation_pr).\n\n"
                            + "Pense à corriger la série dans le carnet avant le prochain bilan.";
                }
            }
            catch (Exception e)
            {
                Log.e(TAG, "Ecriture de l'annulation impossible", e);
                CrashLog.record("annulation_pr_ecriture", e);
                title = "Annulation non faite";
                text = "Écriture impossible : " + e.getMessage() + "\n\nSi une partie a été écrite, la copie automatique "
                        + "« avant_annulation_pr » permet de revenir en arrière.";
            }
            final String shownTitle = title;
            final String shownText = text;
            runOnUiThread(() -> {
                if (isFinishing())
                {
                    return;
                }
                message(shownTitle, shownText);
                reload();
            });
        }, AppNames.FILE_PREFIX + "-annulation-pr-ecriture").start();
    }

    private TextView text(String value)
    {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        return t;
    }

    private void message(String title, String text)
    {
        new AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("OK", null).show();
    }

    private int dp(int value)
    {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
