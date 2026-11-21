package com.example.verifit.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.util.Log;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.verifit.AppNames;
import com.example.verifit.BackupManager;
import com.example.verifit.BuildConfig;
import com.example.verifit.CrashLog;
import com.example.verifit.DataStorage;
import com.example.verifit.EngineContent;
import com.example.verifit.EngineEndOfSession;
import com.example.verifit.EngineEndOfSessionView;
import com.example.verifit.EngineRepository;
import com.example.verifit.EngineStorage;
import com.example.verifit.PythonEngine;

import java.util.Date;

/**
 * Bilan de fin de seance (lot M5b.2, 21/11/2026) : remplace dans l'app la sync du PC
 * (2_Sync_FitEngine.bat). Ouvert a l'arret du chrono de seance (apres l'export Coaching) et a
 * la main (Reglages > Moteur > Bilan de fin de seance). Actif seulement si
 * BuildConfig.ENGINE_END_OF_SESSION (variante de test pendant le developpement).
 *
 * 1. export Coaching construit en memoire (fil principal : il lit le carnet), donnees du moteur
 *    lues et bilan calcule hors du fil principal (PythonEngine.endOfSession) ;
 * 2. affichage du bilan (EngineEndOfSessionView), boutons « Valider » et « Fermer » ;
 * 3. « Fermer » n'ecrit rien. « Valider » : export reconstruit et compare (seance modifiee
 *    entre-temps : bilan recalcule et reaffiche), copie automatique avant_bilan (arret si elle
 *    est impossible), donnees du moteur relues et comparees, nouveau contenu construit et
 *    valide (EngineEndOfSession.apply), ecriture fichier par fichier (trace des PR, journal,
 *    donnees, plans) puis relecture et comparaison (EngineRepository.rewrite).
 */
public final class EndOfSessionFlow
{
    private static final String TAG = "EndOfSessionFlow";

    private EndOfSessionFlow() {}

    public static boolean enabled()
    {
        return BuildConfig.ENGINE_END_OF_SESSION;
    }

    // automatic : ouvert par l'arret du chrono. Sans donnees du moteur, rien n'est montre ; sans
    // rien de nouveau, un simple message court.
    public static void start(final Activity activity, final boolean automatic)
    {
        if (!enabled())
        {
            return;
        }
        final DataStorage ds = MainActivity.dataStorage;
        if (ds == null)
        {
            return;
        }
        if (ds.hasLoadProblem())
        {
            if (!automatic)
            {
                message(activity, "Bilan impossible", "Les données de l'app sont illisibles. Restaure d'abord un backup.");
            }
            return;
        }
        final String timestamp = EngineEndOfSession.timestamp(new Date());
        final String export = ds.buildCoachingExportJson(timestamp);
        final Context appContext = activity.getApplicationContext();
        final AlertDialog progress = progress(activity);

        new Thread(() -> {
            EngineStorage.Loaded loaded = null;
            EngineEndOfSession.Result result = null;
            String error = null;
            boolean silent = false;
            try
            {
                loaded = EngineRepository.get(appContext);
                if (loaded.status == EngineStorage.Status.EMPTY)
                {
                    silent = automatic;
                    error = "Aucune donnée du moteur dans l'app : importe-les d'abord (Réglages > Moteur).";
                }
                else if (loaded.status != EngineStorage.Status.OK)
                {
                    error = "Données du moteur bloquées :\n" + loaded.problem + "\nRefais l'import (Réglages > Moteur). Le carnet reste utilisable.";
                }
                else
                {
                    result = PythonEngine.endOfSession(appContext, loaded.content, export, timestamp);
                }
            }
            catch (Exception e)
            {
                Log.e(TAG, "Bilan de fin de seance impossible", e);
                CrashLog.record("bilan_seance", e);
                error = e.getMessage();
            }
            final EngineStorage.Loaded input = loaded;
            final EngineEndOfSession.Result done = result;
            final String failure = error;
            final boolean quiet = silent;
            activity.runOnUiThread(() -> {
                dismiss(activity, progress);
                if (activity.isFinishing())
                {
                    return;
                }
                if (done == null)
                {
                    if (!quiet)
                    {
                        message(activity, "Bilan impossible", failure + "\n\nRien n'a été modifié.");
                    }
                    return;
                }
                if (automatic && !done.hasChanges())
                {
                    Toast.makeText(activity, "Bilan de fin de séance : rien de nouveau à enregistrer", Toast.LENGTH_LONG).show();
                    return;
                }
                show(activity, input, export, timestamp, done);
            });
        }, AppNames.FILE_PREFIX + "-bilan").start();
    }

    private static void show(final Activity activity, final EngineStorage.Loaded input, final String export,
                             final String timestamp, final EngineEndOfSession.Result result)
    {
        ScrollView scroll = new ScrollView(activity);
        TextView text = new TextView(activity);
        int pad = dp(activity, 20);
        text.setPadding(pad, dp(activity, 8), pad, 0);
        text.setTextIsSelectable(true);
        text.setText(EngineEndOfSessionView.text(result, timestamp));
        scroll.addView(text);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(EngineEndOfSessionView.title(result))
                .setView(scroll)
                .setCancelable(false);
        if (!result.hasChanges())
        {
            builder.setPositiveButton("OK", null).show();
            return;
        }
        AlertDialog dialog = builder
                .setPositiveButton("Valider", (d, w) -> validate(activity, input, export, timestamp, result))
                .setNegativeButton("Fermer", null)
                .show();
        Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (ok != null)
        {
            ok.setFilterTouchesWhenObscured(true); // comme la restauration (lot S2)
        }
    }

    private static void validate(final Activity activity, final EngineStorage.Loaded input, final String export,
                                 final String timestamp, final EngineEndOfSession.Result result)
    {
        final DataStorage ds = MainActivity.dataStorage;
        // Seance modifiee depuis le calcul (serie ajoutee, corrigee) : on recalcule, rien n'est ecrit.
        if (!export.equals(ds.buildCoachingExportJson(timestamp)))
        {
            Toast.makeText(activity, "La séance a changé depuis le calcul : bilan recalculé, rien n'a été écrit.", Toast.LENGTH_LONG).show();
            start(activity, false);
            return;
        }
        // Copie de l'etat actuel (seances, exercices et donnees du moteur) avant d'ecrire.
        if (!BackupManager.snapshot(activity, ds, "avant_bilan"))
        {
            message(activity, "Bilan non validé", "La copie automatique avant écriture est impossible (Android 10 minimum, "
                    + "carnet chargé et non vide). Rien n'a été écrit.");
            return;
        }
        final Context appContext = activity.getApplicationContext();
        final AlertDialog progress = progress(activity);
        new Thread(() -> {
            String title;
            String text;
            boolean recompute = false;
            try
            {
                EngineStorage.Loaded current = EngineRepository.get(appContext);
                if (current.status != EngineStorage.Status.OK || !current.content.sameTexts(input.content))
                {
                    title = "Bilan non validé";
                    text = "Les données du moteur ont changé depuis le calcul : bilan recalculé, rien n'a été écrit.";
                    recompute = current.status == EngineStorage.Status.OK;
                }
                else
                {
                    EngineContent next = EngineEndOfSession.apply(current.content, result);
                    EngineStorage.Loaded written = EngineRepository.rewrite(appContext, next, current.state);
                    title = "Bilan validé";
                    text = summary(result, written.content);
                }
            }
            catch (Exception e)
            {
                Log.e(TAG, "Validation du bilan impossible", e);
                CrashLog.record("bilan_seance_valider", e);
                title = "Bilan non validé";
                text = "Écriture impossible : " + e.getMessage() + "\n\nSi une partie a été écrite, la copie automatique "
                        + "« avant_bilan » (Documents/" + AppNames.EXPORT_FOLDER + "/" + AppNames.AUTO_SUBFOLDER
                        + ", à restaurer par Réglages) permet de revenir en arrière.";
            }
            final String shownTitle = title;
            final String shownText = text;
            final boolean again = recompute;
            activity.runOnUiThread(() -> {
                dismiss(activity, progress);
                if (activity.isFinishing())
                {
                    return;
                }
                if (again)
                {
                    Toast.makeText(activity, shownText, Toast.LENGTH_LONG).show();
                    start(activity, false);
                    return;
                }
                message(activity, shownTitle, shownText);
            });
        }, AppNames.FILE_PREFIX + "-bilan-valider").start();
    }

    private static String summary(EngineEndOfSession.Result r, EngineContent written)
    {
        StringBuilder s = new StringBuilder("Écrit et relu sur le téléphone :\n");
        s.append("- ").append(r.prCount()).append(" PR (").append(r.prs.size()).append(" exercice(s)), tracés dans l'historique des PR\n");
        s.append("- ").append(r.journal.size()).append(" ligne(s) ajoutée(s) au journal des séries\n");
        if (!r.plansToRemove.isEmpty())
        {
            s.append("- ").append(r.plansToRemove.size()).append(" plan(s) sorti(s) de l'attente\n");
        }
        s.append("Plans encore en attente : ").append(written.count(EngineContent.PLANS))
                .append(". Copie automatique faite avant (avant_bilan).");
        return s.toString();
    }

    private static AlertDialog progress(Activity activity)
    {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.HORIZONTAL);
        int pad = dp(activity, 20);
        box.setPadding(pad, pad, pad, pad);
        box.addView(new ProgressBar(activity));
        TextView text = new TextView(activity);
        text.setText("Bilan de fin de séance…");
        text.setPadding(pad, 0, 0, 0);
        box.addView(text);
        return new AlertDialog.Builder(activity).setView(box).setCancelable(false).show();
    }

    private static void dismiss(Activity activity, AlertDialog dialog)
    {
        if (dialog.isShowing() && !activity.isDestroyed())
        {
            dialog.dismiss();
        }
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
