package com.example.verifit;

import android.content.Context;
import android.net.Uri;
import android.os.Build;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Prototype du moteur dans l'app (lot M2.3, 16/11/2026), lance depuis Reglages > Test >
 * Prototype moteur de la variante "FitEngine Test". Sera supprime au lot M4.
 *
 * Lit les fichiers choisis (donnees_moteur.json, et facultativement memoire_echec.json), calcule
 * les 4 seances (cycles A et B, Force et Volume) a la date demandee, ecrit chaque plan dans
 * Documents/FitEngine_dev/prototype et un fichier de mesures, puis rend un resume a afficher.
 *
 * A appeler HORS du thread principal. Une erreur Python arrive en PyException (RuntimeException).
 */
public final class EnginePrototype
{
    public static final String SUBFOLDER = "prototype";
    private static final String[] CYCLES = { "A", "B" };
    private static final String[] TYPES = { "Force", "Volume" };

    private EnginePrototype() {}

    public static String run(Context context, List<Uri> files, String date) throws IOException
    {
        String donnees = null;
        String memoire = null;
        for (Uri uri : files)
        {
            // Lecture plafonnee (TextFiles, lot S2) : un fichier enorme choisi par erreur faisait
            // deborder la memoire (OutOfMemoryError au premier essai du 17/11/2026).
            String text = TextFiles.readAll(uri, context, TextFiles.MAX_SESSION_CHARS);
            JsonElement json = JsonParser.parseString(text);
            if (json.isJsonObject() && json.getAsJsonObject().has("exercices"))
            {
                donnees = text;
            }
            else
            {
                memoire = text;
            }
        }
        if (donnees == null)
        {
            throw new IOException("aucun fichier de données moteur (donnees_moteur.json) parmi les fichiers choisis");
        }

        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
        String relativePath = BackupManager.backupDir() + "/" + SUBFOLDER;
        boolean canWrite = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;

        StringBuilder summary = new StringBuilder();
        summary.append("Date des séances : ").append(date).append('\n');
        summary.append("Mémoire d'échec : ").append(memoire == null ? "aucune" : "fournie").append('\n');
        summary.append("Démarrage de Python : ").append(PythonEngine.start(context)).append(" ms\n\n");

        for (String cycle : CYCLES)
        {
            for (String type : TYPES)
            {
                long t0 = System.nanoTime();
                String raw = PythonEngine.planSeance(context, donnees, memoire, cycle, type, date);
                long totalMs = (System.nanoTime() - t0) / 1000000L;
                JsonObject result = JsonParser.parseString(raw).getAsJsonObject();
                String fileName = "prototype_" + stamp + "_Cycle" + cycle + "_" + type + ".session.json";
                if (canWrite)
                {
                    BackupManager.writeDocument(context, relativePath, fileName,
                            result.get("plan").getAsString(), "application/json");
                }
                summary.append("Cycle ").append(cycle).append(' ').append(type)
                        .append(" : ").append(totalMs).append(" ms (calcul ")
                        .append(result.get("calcul_ms").getAsDouble()).append(" ms), ")
                        .append(result.getAsJsonArray("avertissements").size()).append(" avertissement(s), ")
                        .append(result.getAsJsonArray("exercices_ignores").size()).append(" exercice(s) ignoré(s)\n");
            }
        }

        if (canWrite)
        {
            summary.append("\nPlans écrits dans Documents/").append(AppNames.EXPORT_FOLDER).append('/')
                    .append(SUBFOLDER).append(" (prototype_").append(stamp).append("_...).");
            BackupManager.writeDocument(context, relativePath, "prototype_" + stamp + "_mesures.txt",
                    summary.toString(), "text/plain");
        }
        else
        {
            summary.append("\nAndroid trop ancien pour écrire dans Documents : plans non écrits.");
        }
        return summary.toString();
    }
}
