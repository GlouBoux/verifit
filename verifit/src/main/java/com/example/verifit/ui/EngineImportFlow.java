package com.example.verifit.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.verifit.AppNames;
import com.example.verifit.BackupManager;
import com.example.verifit.CrashLog;
import com.example.verifit.DataStorage;
import com.example.verifit.EngineContent;
import com.example.verifit.EngineEndOfSession;
import com.example.verifit.EngineImport;
import com.example.verifit.EnginePrHistory;
import com.example.verifit.EngineRepository;
import com.example.verifit.EngineStorage;
import com.example.verifit.ImportedPlansStore;
import com.example.verifit.TextFiles;
import com.example.verifit.model.Exercise;

import java.util.Date;
import java.util.List;

/**
 * Reglages > « Importer les donnees du moteur » (lot M3.3, 17/11/2026).
 *
 * 1. choix du fichier (dossier Documents/FitEngine_plans, ou le PC le depose par Syncthing) ;
 * 2. lecture et controle hors du thread principal (EngineImport.parse : format, lecture
 *    stricte, totaux du PC) ;
 * 3. apercu et confirmation ; un exercice d'un cycle inconnu de l'app bloque l'import ;
 * 4. copie automatique de l'etat actuel (backup complet, moteur compris) ;
 * 5. remplacement complet des donnees du moteur, relecture du disque et comparaison aux
 *    totaux du PC (EngineImport.verify) ;
 * 6. assistance remplie dans l'app pour les exercices dont le reglage est vide.
 */
public final class EngineImportFlow
{
    private static final String TAG = "EngineImportFlow";
    public static final int REQUEST_CODE = 93;

    private EngineImportFlow() {}

    public static void openPicker(Fragment fragment)
    {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,
                    android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents",
                            "primary:Documents/" + AppNames.PLANS_FOLDER));
        }
        fragment.startActivityForResult(intent, REQUEST_CODE);
    }

    public static void onFileChosen(final Fragment fragment, final Uri uri)
    {
        final Activity activity = fragment.getActivity();
        final DataStorage ds = MainActivity.dataStorage;
        if (activity == null || ds == null)
        {
            return;
        }
        if (ds.hasLoadProblem())
        {
            Toast.makeText(activity, "Import impossible : les données de l'app sont illisibles. Restaure d'abord un backup.", Toast.LENGTH_LONG).show();
            return;
        }
        final Context appContext = activity.getApplicationContext();
        Toast.makeText(activity, "Lecture du fichier…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            EngineImport parsed = null;
            String error = null;
            String sourceName = TextFiles.displayName(uri, appContext);
            try
            {
                parsed = EngineImport.parse(TextFiles.readAll(uri, appContext));
            }
            catch (Exception e)
            {
                error = e.getMessage();
                Log.e(TAG, "Fichier d'import illisible", e);
            }
            final EngineImport imp = parsed;
            final String failure = error;
            activity.runOnUiThread(() -> {
                if (!fragment.isAdded())
                {
                    return;
                }
                if (imp == null)
                {
                    message(activity, "Import annulé", failure + "\n\nLes données de l'app n'ont pas été modifiées.");
                    return;
                }
                confirm(fragment, activity, imp, sourceName);
            });
        }, AppNames.FILE_PREFIX + "-import-moteur").start();
    }

    private static void confirm(final Fragment fragment, final Activity activity, final EngineImport imp, final String sourceName)
    {
        final EngineImport.Preview preview = imp.preview(MainActivity.dataStorage.getKnownExercises());
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle("Importer les données du moteur ?")
                .setMessage(imp.summary(preview));
        if (preview.blocking())
        {
            builder.setPositiveButton("OK", null).show();
            return;
        }
        AlertDialog dialog = builder
                .setPositiveButton("Importer", (d, which) -> apply(fragment, activity, imp, preview, sourceName))
                .setNegativeButton("Annuler", null)
                .show();
        Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (ok != null)
        {
            ok.setFilterTouchesWhenObscured(true); // comme la restauration (lot S2)
        }
    }

    private static void apply(final Fragment fragment, final Activity activity, final EngineImport imp,
                              final EngineImport.Preview preview, final String sourceName)
    {
        final Context appContext = activity.getApplicationContext();
        // Copie de l'etat actuel (seances, exercices et donnees du moteur) avant de remplacer.
        BackupManager.snapshot(activity, MainActivity.dataStorage, "avant_import_moteur");
        new Thread(() -> {
            String result;
            boolean ok = false;
            try
            {
                // Lot M5b.2 : la trace des PR de l'app (historique_pr.jsonl) n'est pas dans
                // l'export du PC : elle est gardee telle quelle (vide si les donnees actuelles
                // sont absentes ou illisibles). Lot M5b.3 : chaque valeur de PR changee par
                // l'import y est ajoutee (origine « import »).
                EngineStorage.Loaded current = EngineRepository.get(appContext);
                String prHistory = "";
                int traced = 0;
                if (current.status == EngineStorage.Status.OK)
                {
                    List<String> lines = EnginePrHistory.importLines(current.content.data(), imp.content.data(),
                            EngineEndOfSession.timestamp(new Date()), sourceName);
                    StringBuilder s = new StringBuilder(current.content.text(EngineContent.PR_HISTORY));
                    for (String line : lines)
                    {
                        s.append(line);
                    }
                    prHistory = s.toString();
                    traced = lines.size();
                }
                EngineStorage.Loaded loaded = EngineRepository.replaceAll(appContext, imp.contentKeeping(prHistory),
                        ImportedPlansStore.now(), sourceName, imp.exportedAt);
                String gap = imp.verify(loaded.content);
                if (gap != null)
                {
                    result = "Import écrit, mais la relecture ne correspond pas aux totaux du PC :\n" + gap
                            + "\nRefais l'import.";
                }
                else
                {
                    ok = true;
                    result = "Import fait et vérifié (relu sur le téléphone, totaux identiques à ceux du PC) :\n"
                            + loaded.content.count(EngineContent.DATA) + " exercices, " + loaded.content.prRows() + " lignes de PR, "
                            + loaded.content.count(EngineContent.HISTORY) + " lignes d'historique, "
                            + loaded.content.count(EngineContent.PLANS) + " plan(s) en attente.\n"
                            + traced + " valeur(s) de PR changée(s) par l'import, tracée(s) dans l'historique des PR.";
                }
            }
            catch (Exception e)
            {
                CrashLog.record("import_moteur", e);
                result = "Import impossible : " + e.getMessage();
            }
            final String shown = result;
            final boolean fillAssistance = ok;
            activity.runOnUiThread(() -> {
                String text = shown;
                if (fillAssistance && !preview.toFill.isEmpty())
                {
                    text += "\n\n" + fillAssistance(appContext, preview);
                }
                if (fragment.isAdded())
                {
                    message(activity, "Données du moteur", text);
                }
            });
        }, AppNames.FILE_PREFIX + "-import-moteur").start();
    }

    // Reglage d'assistance de l'app rempli quand il est vide (jamais ecrase : decision 5).
    private static String fillAssistance(Context context, EngineImport.Preview preview)
    {
        DataStorage ds = MainActivity.dataStorage;
        StringBuilder names = new StringBuilder();
        for (EngineImport.Assistance a : preview.toFill)
        {
            for (Exercise e : ds.getKnownExercises())
            {
                if (a.name.equals(e.getName()) && e.getAssistRefKg() == null)
                {
                    e.setAssistRefKg(a.refKg);
                    e.setAssistPerSide(a.perSide);
                    names.append(names.length() == 0 ? "" : ", ").append(a.name);
                }
            }
        }
        ds.saveKnownExerciseData(context);
        return "Assistance remplie dans l'app : " + names + ".";
    }

    private static void message(Activity activity, String title, String text)
    {
        new AlertDialog.Builder(activity).setTitle(title).setMessage(text).setPositiveButton("OK", null).show();
    }
}
