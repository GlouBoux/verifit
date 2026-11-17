package com.example.verifit;

import android.content.Context;
import android.util.Log;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.util.Locale;

/**
 * Seul point de contact de l'app avec Python (Chaquopy), lot M2 de l'integration du moteur
 * Coaching (16/11/2026). Tout appel a Chaquopy passe par ici : un seul endroit a modifier
 * ensuite (lots M4 et suivants).
 *
 * Le code Python embarque est celui du dossier moteur/ du depot Coaching (voir le bloc chaquopy
 * de build.gradle) : workout_engine.py, engine_data.py et moteur_app.py (point d'entree de l'app :
 * textes JSON en entree et en sortie, aucun objet Python ne sort d'ici).
 *
 * A appeler HORS du thread principal : le premier appel demarre Python (plusieurs centaines de
 * millisecondes, voire plus) et un calcul de seance prend du temps. Les exceptions Python
 * arrivent en com.chaquo.python.PyException (RuntimeException) : a l'appelant de les rattraper
 * et de les envoyer a CrashLog.record.
 */
public final class PythonEngine
{
    private static final String TAG = "PythonEngine";

    private PythonEngine() {}

    // Demarre Python au premier appel (sans effet ensuite).
    private static synchronized Python python(Context context)
    {
        if (!Python.isStarted())
        {
            Python.start(new AndroidPlatform(context.getApplicationContext()));
        }
        return Python.getInstance();
    }

    // Demarre Python s'il ne l'est pas encore. Rend la duree du demarrage en ms (0 s'il tournait deja).
    public static long start(Context context)
    {
        long t0 = System.nanoTime();
        python(context);
        return millis(t0, System.nanoTime());
    }

    // Lot M2.2 : calcule une seance avec moteur_app.plan_seance (dossier moteur/ du depot Coaching).
    // cycle "A" ou "B", type "Force" ou "Volume", date "AAAA-MM-JJ", memoireJson peut etre null
    // (pas de memoire d'echec). Rend le texte JSON de moteur_app : {"plan", "exercices_ignores",
    // "avertissements", "calcul_ms"}.
    public static String planSeance(Context context, String donneesJson, String memoireJson,
                                    String cycle, String type, String date)
    {
        PyObject app = python(context).getModule("moteur_app");
        return app.callAttr("plan_seance", donneesJson, cycle, type, date, memoireJson).toString();
    }

    // Lot M2.1 : verifie que Python tourne et que le moteur s'importe, avec les durees.
    public static String hello(Context context)
    {
        long t0 = System.nanoTime();
        Python py = python(context);
        long t1 = System.nanoTime();
        String version = py.getModule("sys").get("version").toString();
        PyObject engineData = py.getModule("engine_data");
        int dataVersion = engineData.get("ENGINE_DATA_VERSION").toInt();
        long t2 = System.nanoTime();
        py.getModule("workout_engine");
        long t3 = System.nanoTime();
        String text = String.format(Locale.FRANCE,
                "Python %s\nDonnées moteur : version %d\n\nDémarrage de Python : %d ms\n"
                        + "Import de engine_data : %d ms\nImport de workout_engine : %d ms",
                version, dataVersion, millis(t0, t1), millis(t1, t2), millis(t2, t3));
        Log.i(TAG, text.replace('\n', ' '));
        return text;
    }

    private static long millis(long startNanos, long endNanos)
    {
        return (endNanos - startNanos) / 1000000L;
    }
}
