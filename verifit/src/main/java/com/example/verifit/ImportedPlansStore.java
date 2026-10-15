package com.example.verifit;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Persistance du registre des plans importes (ImportedPlans) et ecriture de l'accuse
 * pour le PC (session du 15/10/2026).
 *
 * - Registre : SharedPreferences propres ("imported_plans"), separees des donnees de
 *   seances ("shared preferences") : "Clear Data" ou la restauration d'un backup complet
 *   n'y touchent pas, un plan deja importe reste reconnu.
 * - Accuse : Documents/FitEngine/fitengine_plan_imports.json, nom fixe, reecrit a chaque
 *   import reussi (meme mecanique que fitengine_coaching_latest.json : FixedNameExport +
 *   MediaStoreExportStore, Android 10+). Documents/FitEngine est le SEUL dossier ou l'app
 *   ecrit ; Syncthing le livre dans Coaching\fitengine_sync. Rien n'est jamais ecrit dans
 *   Documents/FitEngine_plans.
 *
 * Non testable en JUnit local (Context, MediaStore) : la logique est dans ImportedPlans.
 */
public final class ImportedPlansStore
{
    private static final String TAG = "ImportedPlansStore";
    private static final String PREFS_NAME = "imported_plans";
    private static final String PREF_KEY = "registry";

    public static final String ACK_FILE_NAME = AppNames.FILE_PREFIX + "_plan_imports.json";

    private ImportedPlansStore() {}

    public static ImportedPlans load(Context context)
    {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return ImportedPlans.fromJson(prefs.getString(PREF_KEY, ""));
    }

    // Enregistre une importation reussie puis reecrit l'accuse. Ne leve jamais : l'import
    // est deja fait, un echec ici ne doit pas le faire passer pour rate. Renvoie un
    // message a montrer a l'utilisateur si l'accuse n'a pas pu etre ecrit, sinon null.
    public static String recordImport(Context context, ImportedPlans.Entry entry)
    {
        String now = now();
        ImportedPlans plans = load(context);
        plans.add(entry);
        String json = plans.toJson(now);

        context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(PREF_KEY, json).apply();

        return writeAck(context, json);
    }

    // Accuse dans Documents/FitEngine. Message d'erreur a afficher, ou null si c'est fait
    // (ou impossible avant Android 10, comme les autres exports).
    private static String writeAck(Context context, String json)
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
        {
            return null;
        }
        try
        {
            MediaStoreExportStore store = new MediaStoreExportStore(
                    context, Environment.DIRECTORY_DOCUMENTS + "/" + AppNames.EXPORT_FOLDER, "application/json");
            FixedNameExport.Result result = FixedNameExport.write(store, ACK_FILE_NAME, json.getBytes("UTF-8"));
            if (result == FixedNameExport.Result.BLOCKED_BY_FOREIGN_FILE)
            {
                return ACK_FILE_NAME + " existe déjà (installation précédente) et ne peut pas être modifié : "
                        + "supprime-le dans Fichiers > Documents > " + AppNames.EXPORT_FOLDER
                        + ". Il sera recréé au prochain import.";
            }
            return null;
        }
        catch (Exception e)
        {
            Log.e(TAG, "Accuse d'import non ecrit", e);
            return "Accusé d'import non écrit : " + e;
        }
    }

    // Horodatage de l'import, heure locale du telephone.
    public static String now()
    {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date());
    }
}
