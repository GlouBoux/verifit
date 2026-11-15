package com.example.verifit;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Mise en forme et rotation des journaux de plantage (lot M0.3, 14/11/2026, point F4 de
 * claude/fitengine-revue-securite-2026-10-27.md).
 *
 * Pourquoi : l'app n'a pas d'autre mainteneur que Romain, et une erreur sur le telephone est
 * invisible sans adb. Chaque plantage (et, plus tard, chaque erreur Python rattrapee, voir
 * CrashLog.record) laisse un petit fichier texte lisible dans Documents/FitEngine/crash.
 *
 * Cette classe ne fait QUE du texte et des noms : aucune dependance Android, testee en JUnit
 * (CrashReportTest). L'ecriture des fichiers est dans CrashLog.
 */
public final class CrashReport
{
    private CrashReport() {}

    // Nombre de journaux gardes (les plus recents), dans le dossier prive comme dans Documents.
    public static final int KEEP = 20;

    // Sous-dossier de Documents/FitEngine (ou FitEngine_dev). Il n'est pas au premier niveau :
    // la sync Coaching, qui ne lit que ce premier niveau, ne le voit pas.
    public static final String SUBFOLDER = "crash";

    public static final String FILE_PREFIX = AppNames.FILE_PREFIX + "_crash_";
    public static final String FILE_SUFFIX = ".txt";

    // Une pile peut faire des milliers de lignes (StackOverflowError) : on garde le debut.
    static final int MAX_TRACE_CHARS = 200000;

    private static final int MAX_ORIGIN_CHARS = 24;

    // Origine reduite a des caracteres sans risque dans un nom de fichier.
    static String cleanOrigin(String origin)
    {
        if (origin == null)
        {
            return "inconnu";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < origin.length() && out.length() < MAX_ORIGIN_CHARS; i++)
        {
            char c = origin.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-';
            out.append(ok ? c : '_');
        }
        return out.length() == 0 ? "inconnu" : out.toString();
    }

    // fitengine_crash_2026-11-14_12-06-51-123_<origine>.txt : la date vient avant l'origine,
    // donc l'ordre alphabetique est l'ordre chronologique.
    public static String fileName(long timeMillis, TimeZone zone, String origin)
    {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.US);
        format.setTimeZone(zone);
        return FILE_PREFIX + format.format(new Date(timeMillis)) + "_" + cleanOrigin(origin) + FILE_SUFFIX;
    }

    public static String format(long timeMillis, TimeZone zone, String origin, String threadName,
                                String versionName, long buildTimestamp,
                                String deviceModel, String androidRelease, int sdkInt,
                                Throwable throwable)
    {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US);
        dateFormat.setTimeZone(zone);
        SimpleDateFormat buildFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        buildFormat.setTimeZone(zone);

        StringBuilder text = new StringBuilder();
        text.append(AppNames.APP_NAME).append(" : journal de plantage\n");
        text.append("Date : ").append(dateFormat.format(new Date(timeMillis))).append('\n');
        text.append("Origine : ").append(origin == null ? "inconnu" : origin).append('\n');
        text.append("Fil d'exécution : ").append(threadName == null ? "inconnu" : threadName).append('\n');
        text.append("Version : ").append(versionName)
                .append(" (build du ").append(buildFormat.format(new Date(buildTimestamp)))
                .append(", BUILD_TIMESTAMP ").append(buildTimestamp).append(")\n");
        text.append("Appareil : ").append(deviceModel).append('\n');
        text.append("Android : ").append(androidRelease).append(" (API ").append(sdkInt).append(")\n");
        text.append("\nTrace :\n").append(trace(throwable)).append('\n');
        return text.toString();
    }

    // Pile complete, causes comprises ("Caused by: ..."), tronquee si elle est enorme.
    static String trace(Throwable throwable)
    {
        if (throwable == null)
        {
            return "(aucune exception fournie)";
        }
        StringWriter writer = new StringWriter();
        PrintWriter printer = new PrintWriter(writer);
        throwable.printStackTrace(printer);
        printer.flush();
        String trace = writer.toString();
        if (trace.length() > MAX_TRACE_CHARS)
        {
            return trace.substring(0, MAX_TRACE_CHARS) + "\n... (trace tronquée à " + MAX_TRACE_CHARS + " caractères)";
        }
        return trace;
    }

    // Noms a supprimer pour ne garder que les `keep` journaux les plus recents. Les noms qui
    // ne sont pas des journaux (fichier temporaire, autre fichier) ne sont jamais proposes.
    public static List<String> namesToDelete(List<String> names, int keep)
    {
        List<String> logs = new ArrayList<String>();
        for (String name : names)
        {
            if (name != null && name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX))
            {
                logs.add(name);
            }
        }
        Collections.sort(logs, Collections.reverseOrder());
        if (keep < 0)
        {
            keep = 0;
        }
        if (logs.size() <= keep)
        {
            return new ArrayList<String>();
        }
        return new ArrayList<String>(logs.subList(keep, logs.size()));
    }
}
