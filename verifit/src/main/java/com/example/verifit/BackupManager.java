package com.example.verifit;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import com.example.verifit.model.Exercise;
import com.example.verifit.model.Goal;
import com.example.verifit.model.WorkoutDay;
import com.example.verifit.model.WorkoutSet;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * Backup JSON COMPLET de verifit et copies automatiques (revue d'architecture du
 * 28/09/2026, point 1.4).
 *
 * Pourquoi : le backup CSV, seule sauvegarde restaurable jusqu'ici, perd une partie des
 * donnees (valeurs prevues, horodatages, chrono de seance, commentaire de seance, ordre
 * des exercices, supersets, objectifs, notes et favoris d'exercice). Ce backup contient
 * TOUT ce que l'app sauvegarde elle-meme : la liste des jours (WorkoutDay, dont Sets),
 * les exercices connus et les objectifs, avec le meme Gson que la sauvegarde interne.
 *
 * Trois usages :
 * - export manuel (Reglages) dans Documents/Verifit/ ;
 * - restauration (Reglages), apres confirmation, qui REMPLACE toutes les donnees ;
 * - copies automatiques dans Documents/Verifit/auto/ : une par jour a l'ouverture de
 *   l'app, et une juste avant chaque operation qui remplace ou efface des donnees
 *   (import CSV, restauration, effacement complet, import de seance). Les
 *   KEEP_AUTO_SNAPSHOTS plus recentes sont gardees, les plus anciennes supprimees.
 *
 * Les fichiers passent par MediaStore (Android 10+), comme les exports existants : ils
 * sont donc visibles dans le selecteur de fichiers et depuis un PC, et restent
 * restaurables meme apres une desinstallation de l'app.
 */
public class BackupManager
{
    private static final String TAG = "BackupManager";

    public static final String FORMAT = "verifit-full-backup";
    public static final int SCHEMA_VERSION = 1;

    private static final String BACKUP_DIR = Environment.DIRECTORY_DOCUMENTS + "/Verifit";
    private static final String AUTO_DIR = Environment.DIRECTORY_DOCUMENTS + "/Verifit/auto";
    private static final String AUTO_PREFIX = "verifit_auto_";
    private static final int KEEP_AUTO_SNAPSHOTS = 15;

    private static final String PREFS_NAME = "shared preferences";
    private static final String PREF_LAST_DAILY_SNAPSHOT = "last_daily_auto_backup_day";

    // Contenu du fichier. Les noms de champs SONT le format du fichier : ne pas les
    // renommer sans augmenter SCHEMA_VERSION.
    public static class FullBackup
    {
        String format;
        int schemaVersion;
        String exportedAt;
        ArrayList<WorkoutDay> workoutDays;
        ArrayList<Exercise> knownExercises;
        ArrayList<Goal> goals;

        public String getExportedAt() { return exportedAt; }
        public ArrayList<WorkoutDay> getWorkoutDays() { return workoutDays; }
        public ArrayList<Exercise> getKnownExercises() { return knownExercises; }
        public ArrayList<Goal> getGoals() { return goals; }

        public int countSets()
        {
            int n = 0;
            for (WorkoutDay day : workoutDays)
            {
                n += day.getSets().size();
            }
            return n;
        }
    }

    // ---------------------------------------------------------------- (de)serialisation

    public static String toJson(DataStorage dataStorage)
    {
        FullBackup backup = new FullBackup();
        backup.format = FORMAT;
        backup.schemaVersion = SCHEMA_VERSION;
        backup.exportedAt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date());
        backup.workoutDays = dataStorage.getWorkoutDays();
        backup.knownExercises = dataStorage.getKnownExercises();
        backup.goals = dataStorage.getGoals();
        return new Gson().toJson(backup);
    }

    // Lit et VALIDE un backup complet, sans toucher aux donnees de l'app. Leve
    // IllegalArgumentException avec un message lisible si le fichier n'est pas un backup
    // complet verifit (ex. l'export JSON Coaching, un CSV) ou s'il est incomplet.
    public static FullBackup parse(String json)
    {
        // Retour Romain 29/09/2026 : un export CSV choisi par erreur (noms tres proches)
        // donnait "fichier JSON invalide", peu parlant. Un backup commence toujours par
        // "{" : on le dit clairement avant meme d'appeler Gson.
        String trimmed = json == null ? "" : json.trim();
        if (trimmed.startsWith("\uFEFF"))
        {
            trimmed = trimmed.substring(1);
        }
        if (!trimmed.startsWith("{"))
        {
            throw new IllegalArgumentException("ce fichier n'est pas un backup complet verifit (ce n'est pas du JSON, probablement un export CSV). Le bon fichier s'appelle verifit_backup_complet_<date>.json");
        }

        FullBackup backup;
        try
        {
            backup = new Gson().fromJson(json, FullBackup.class);
        }
        catch (JsonParseException | IllegalStateException | NumberFormatException e)
        {
            throw new IllegalArgumentException("fichier JSON invalide (" + e.getMessage() + ")");
        }

        if (backup == null || !FORMAT.equals(backup.format))
        {
            throw new IllegalArgumentException("ce fichier n'est pas un backup complet verifit (le bon fichier s'appelle verifit_backup_complet_<date>.json)");
        }
        if (backup.schemaVersion > SCHEMA_VERSION)
        {
            throw new IllegalArgumentException("backup cree par une version plus recente de verifit (format " + backup.schemaVersion + ")");
        }
        if (backup.workoutDays == null || backup.knownExercises == null)
        {
            throw new IllegalArgumentException("backup incomplet (seances ou exercices absents)");
        }
        if (backup.goals == null)
        {
            backup.goals = new ArrayList<Goal>();
        }

        for (int d = 0; d < backup.workoutDays.size(); d++)
        {
            WorkoutDay day = backup.workoutDays.get(d);
            if (day == null || day.getSets() == null)
            {
                throw new IllegalArgumentException("jour n°" + (d + 1) + " illisible");
            }
            for (WorkoutSet set : day.getSets())
            {
                if (set == null || set.getDate() == null || set.getExerciseName() == null
                        || set.getReps() == null || set.getWeight() == null)
                {
                    throw new IllegalArgumentException("série incomplète le " + day.getDate());
                }
            }
        }
        return backup;
    }

    // ---------------------------------------------------------------- export manuel

    public static boolean exportToDocuments(Context context, DataStorage dataStorage)
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
        {
            Toast.makeText(context, "Backup complet : Android 10 minimum", Toast.LENGTH_LONG).show();
            return false;
        }

        String fileName = "verifit_backup_complet_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm").format(new Date()) + ".json";
        try
        {
            writeDocument(context, BACKUP_DIR, fileName, toJson(dataStorage));
            Toast.makeText(context, "Backup complet enregistré dans " + BACKUP_DIR + "/" + fileName, Toast.LENGTH_LONG).show();
            return true;
        }
        catch (IOException | RuntimeException e)
        {
            Log.e(TAG, "Export du backup complet impossible", e);
            Toast.makeText(context, "Backup complet impossible : " + e.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    // ---------------------------------------------------------------- restauration

    // Dossier Documents/Verifit, pour ouvrir le selecteur de fichiers directement dedans
    // (Intent EXTRA_INITIAL_URI, Android 8+). Retour Romain 29/09/2026 : la vue
    // "Documents" du selecteur (categorie, pas le vrai dossier) n'affichait que les
    // exports CSV et masquait les fichiers .json.
    public static Uri documentsFolderUri()
    {
        return android.provider.DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents", "primary:" + BACKUP_DIR);
    }

    public static FullBackup readFromUri(Context context, Uri uri) throws IOException
    {
        return parse(SessionImporter.readAll(uri, context));
    }

    // Remplace toutes les donnees par le backup (deja valide par parse()). Une copie
    // automatique de l'etat actuel est faite juste avant.
    public static void restore(Context context, DataStorage dataStorage, FullBackup backup)
    {
        snapshot(context, dataStorage, "avant_restauration");
        dataStorage.restoreFullBackup(backup, context);
    }

    // ---------------------------------------------------------------- copies automatiques

    // Une copie par jour, a la premiere ouverture d'un ecran dans la journee.
    public static void dailySnapshotIfNeeded(Context context, DataStorage dataStorage)
    {
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        android.content.SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (today.equals(prefs.getString(PREF_LAST_DAILY_SNAPSHOT, "")))
        {
            return;
        }
        if (snapshot(context, dataStorage, "quotidien"))
        {
            prefs.edit().putString(PREF_LAST_DAILY_SNAPSHOT, today).apply();
        }
    }

    // Copie automatique de l'etat ACTUEL. Ne fait rien (et renvoie false) si les donnees
    // ne sont pas chargees ou sont vides : une copie vide chasserait des copies utiles de
    // la rotation. La mise en JSON se fait tout de suite (etat exact au moment de
    // l'appel, avant l'operation qui va suivre), l'ecriture du fichier en arriere-plan.
    public static boolean snapshot(Context context, DataStorage dataStorage, String reason)
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
        {
            Log.w(TAG, "Copie automatique ignoree : Android 10 minimum");
            return false;
        }
        if (!dataStorage.isWorkoutDataLoaded() || dataStorage.getWorkoutDays().isEmpty())
        {
            Log.w(TAG, "Copie automatique ignoree (" + reason + ") : aucune donnee chargee");
            return false;
        }

        final Context appContext = context.getApplicationContext();
        final String json = toJson(dataStorage);
        final String fileName = AUTO_PREFIX + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()) + "_" + reason + ".json";

        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    writeDocument(appContext, AUTO_DIR, fileName, json);
                    pruneAutoSnapshots(appContext);
                }
                catch (IOException | RuntimeException e)
                {
                    Log.e(TAG, "Copie automatique impossible (" + fileName + ")", e);
                }
            }
        }, "verifit-auto-backup").start();
        return true;
    }

    // Garde les KEEP_AUTO_SNAPSHOTS copies les plus recentes. Le nom commence par la date
    // et l'heure : l'ordre alphabetique inverse est l'ordre chronologique inverse. Seules
    // les copies creees par cette installation de l'app sont visibles et supprimables
    // (regle MediaStore) : celles d'une installation precedente restent en place.
    private static void pruneAutoSnapshots(Context context)
    {
        Uri collection = MediaStore.Files.getContentUri("external");
        String[] projection = { MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME };
        String selection = MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?";
        String[] args = { AUTO_DIR + "/", AUTO_PREFIX + "%" };

        Cursor cursor = context.getContentResolver().query(collection, projection, selection, args, MediaStore.MediaColumns.DISPLAY_NAME + " DESC");
        if (cursor == null)
        {
            return;
        }
        try
        {
            int index = 0;
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            while (cursor.moveToNext())
            {
                index++;
                if (index > KEEP_AUTO_SNAPSHOTS)
                {
                    context.getContentResolver().delete(ContentUris.withAppendedId(collection, cursor.getLong(idColumn)), null, null);
                }
            }
        }
        finally
        {
            cursor.close();
        }
    }

    // ---------------------------------------------------------------- ecriture MediaStore

    private static void writeDocument(Context context, String relativePath, String fileName, String content) throws IOException
    {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);

        Uri fileUri = context.getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
        if (fileUri == null)
        {
            throw new IOException("création du fichier refusée par le système");
        }

        OutputStream outputStream = context.getContentResolver().openOutputStream(fileUri);
        if (outputStream == null)
        {
            throw new IOException("écriture du fichier refusée par le système");
        }
        try
        {
            outputStream.write(content.getBytes(StandardCharsets.UTF_8));
        }
        finally
        {
            outputStream.close();
        }
    }
}
