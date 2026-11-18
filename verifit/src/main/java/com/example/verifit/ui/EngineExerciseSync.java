package com.example.verifit.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.example.verifit.BackupManager;
import com.example.verifit.EngineContent;
import com.example.verifit.EngineRename;
import com.example.verifit.EngineRepository;
import com.example.verifit.EngineStorage;

import java.io.IOException;

/**
 * Renommer ou supprimer un exercice dans l'app fait suivre les donnees du moteur (lot M3.5,
 * 17/11/2026, logique dans EngineRename). Les donnees du moteur sont modifiees AVANT
 * l'exercice de l'app : si leur ecriture echoue, rien n'est change du tout. Copie
 * automatique (backup complet) avant chaque modification.
 *
 * Donnees du moteur illisibles : renommage et suppression refuses (ils ne pourraient pas
 * suivre), avec la marche a suivre.
 */
public final class EngineExerciseSync
{
    private static final String TAG = "EngineExerciseSync";

    private EngineExerciseSync() {}

    // A appeler avant DataStorage.editExercise() quand le nom change. Faux = renommage refuse
    // (message deja affiche), rien n'a ete modifie.
    public static boolean renameBefore(Context ct, String oldName, String newName)
    {
        EngineStorage.Loaded loaded = EngineRepository.get(ct);
        if (loaded.status == EngineStorage.Status.EMPTY)
        {
            return true;
        }
        if (loaded.status == EngineStorage.Status.BLOCKED)
        {
            alert(ct, "Renommage refusé : les données du moteur sont illisibles et ne pourraient pas suivre. "
                    + "Réimporte-les depuis le PC (Réglages > Importer les données du moteur), puis recommence.");
            return false;
        }
        EngineContent renamed;
        try
        {
            renamed = EngineRename.rename(loaded.content, oldName, newName);
        }
        catch (IllegalArgumentException e)
        {
            alert(ct, "Renommage refusé : " + e.getMessage() + ".");
            return false;
        }
        if (renamed == loaded.content)
        {
            return true;
        }
        BackupManager.snapshot(ct, MainActivity.dataStorage, "avant_renommage");
        try
        {
            EngineRepository.rewrite(ct, renamed, loaded.state);
            return true;
        }
        catch (IOException | RuntimeException e)
        {
            Log.e(TAG, "Renommage dans les donnees du moteur impossible", e);
            alert(ct, "Renommage annulé : les données du moteur n'ont pas pu être écrites (" + e.getMessage() + ").");
            return false;
        }
    }

    // Suppression d'un exercice : si le moteur en a des donnees, une confirmation de plus dit
    // ce qui part ; `deleteInApp` n'est lance qu'une fois le moteur a jour.
    public static void confirmDelete(final Context ct, final String name, final Runnable deleteInApp)
    {
        final EngineStorage.Loaded loaded = EngineRepository.get(ct);
        if (loaded.status == EngineStorage.Status.EMPTY)
        {
            deleteInApp.run();
            return;
        }
        if (loaded.status == EngineStorage.Status.BLOCKED)
        {
            alert(ct, "Suppression refusée : les données du moteur sont illisibles et ne pourraient pas suivre. "
                    + "Réimporte-les depuis le PC (Réglages > Importer les données du moteur), puis recommence.");
            return;
        }
        String what = EngineRename.describeDeletion(loaded.content, name);
        if (what == null)
        {
            deleteInApp.run();
            return;
        }
        new AlertDialog.Builder(ct)
                .setTitle("Données du moteur")
                .setMessage("« " + name + " » a des données du moteur : " + what + ". Elles seront supprimées aussi "
                        + "(l'historique des séries du moteur garde ses lignes). Une copie automatique est faite avant.")
                .setPositiveButton("Supprimer", (d, which) -> {
                    BackupManager.snapshot(ct, MainActivity.dataStorage, "avant_suppression_exercice");
                    try
                    {
                        EngineRepository.rewrite(ct, EngineRename.delete(loaded.content, name), loaded.state);
                    }
                    catch (IOException | RuntimeException e)
                    {
                        Log.e(TAG, "Suppression dans les donnees du moteur impossible", e);
                        alert(ct, "Suppression annulée : les données du moteur n'ont pas pu être écrites (" + e.getMessage() + ").");
                        return;
                    }
                    deleteInApp.run();
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private static void alert(Context ct, String text)
    {
        try
        {
            new AlertDialog.Builder(ct).setMessage(text).setPositiveButton("OK", null).show();
        }
        catch (RuntimeException e)
        {
            Toast.makeText(ct, text, Toast.LENGTH_LONG).show();
        }
    }
}
