package com.example.verifit.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.widget.Toast;

import com.example.verifit.DataStorage;

import java.lang.ref.WeakReference;

/**
 * Ecran de recuperation (revue securite du 27/10/2026, lot S1, point C3) : affiche quand
 * les donnees enregistrees n'ont pas pu etre chargees (voir StorageCheck et
 * DataStorage.hasLoadProblem()). Tant que le probleme n'est pas regle, DataStorage
 * n'enregistre rien, pour ne pas ecraser ce qui reste.
 *
 * Montre par VerifitApplication.onActivityResumed() sur TOUS les ecrans sauf les
 * Reglages (ou se fait la restauration) : l'app peut etre rouverte directement sur un
 * autre ecran que l'onglet Workout. Non annulable : deux choix seulement, restaurer un
 * backup (ouvre directement le selecteur des Reglages) ou repartir de zero apres une
 * seconde confirmation.
 */
public final class RecoveryDialog
{
    private static WeakReference<AlertDialog> current = new WeakReference<AlertDialog>(null);

    private RecoveryDialog() {}

    public static void showIfNeeded(final Activity activity, final DataStorage dataStorage)
    {
        if (!dataStorage.hasLoadProblem() || activity instanceof SettingsActivity || activity.isFinishing())
        {
            return;
        }

        AlertDialog shown = current.get();
        if (shown != null && shown.isShowing())
        {
            if (shown.getOwnerActivity() == activity)
            {
                return;
            }
            shown.dismiss();
        }

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Données illisibles")
                .setMessage(dataStorage.describeLoadProblem())
                .setCancelable(false)
                .setPositiveButton("Restaurer un backup", (d, which) -> {
                    Intent intent = new Intent(activity, SettingsActivity.class);
                    intent.putExtra(SettingsActivity.EXTRA_OPEN_RESTORE, true);
                    activity.startActivity(intent);
                })
                .setNegativeButton("Repartir de zéro", (d, which) -> confirmReset(activity, dataStorage))
                .create();
        dialog.setOwnerActivity(activity);
        dialog.show();
        current = new WeakReference<AlertDialog>(dialog);
    }

    // Appele quand un ecran est detruit, pour ne pas laisser de fenetre orpheline.
    public static void dismissFor(Activity activity)
    {
        AlertDialog shown = current.get();
        if (shown != null && shown.getOwnerActivity() == activity && shown.isShowing())
        {
            shown.dismiss();
        }
        if (shown != null && shown.getOwnerActivity() == activity)
        {
            current = new WeakReference<AlertDialog>(null);
        }
    }

    private static void confirmReset(final Activity activity, final DataStorage dataStorage)
    {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Repartir de zéro ?")
                .setMessage("Les données illisibles seront remplacées par des données vides, "
                        + "et cette fois enregistrées. À ne faire que si aucun backup ne convient.")
                .setCancelable(false)
                .setPositiveButton("Repartir de zéro", (d, which) -> {
                    dataStorage.resetUnreadableData(activity);
                    Toast.makeText(activity, "Données remises à zéro", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Retour", (d, which) -> {
                    // Ce dialogue est encore affiche pendant ce clic : on l'oublie, sinon
                    // showIfNeeded() croirait l'ecran de recuperation deja visible.
                    current = new WeakReference<AlertDialog>(null);
                    showIfNeeded(activity, dataStorage);
                })
                .create();
        dialog.setOwnerActivity(activity);
        dialog.show();
        current = new WeakReference<AlertDialog>(dialog);
    }
}
