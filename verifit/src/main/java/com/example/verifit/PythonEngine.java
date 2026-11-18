package com.example.verifit;

import android.content.Context;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.io.IOException;
import java.util.Map;

/**
 * Seul point de contact de l'app avec Python (Chaquopy), lot M2 de l'integration du moteur
 * Coaching (16/11/2026). Tout appel a Chaquopy passe par ici.
 *
 * Le code Python embarque est celui du dossier moteur/ du depot Coaching (voir le bloc chaquopy
 * de build.gradle) : workout_engine.py, engine_data.py et moteur_app.py (point d'entree de l'app :
 * textes JSON en entree et en sortie, aucun objet Python ne sort d'ici).
 *
 * A appeler HORS du thread principal : le premier appel demarre Python (plusieurs centaines de
 * millisecondes, voire plus) et un calcul de seance prend du temps. Les exceptions Python
 * arrivent en com.chaquo.python.PyException (RuntimeException) : a l'appelant de les rattraper
 * et de les envoyer a CrashLog.record.
 *
 * Lot M4 (18/11/2026) : generate() calcule la seance a partir des donnees du moteur gardees par
 * l'app (EngineRepository, lot M3). Le prototype du lot M2.3 (hello, planSeance) est retire.
 */
public final class PythonEngine
{
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

    // Lot M4.1 : seance du cycle ("A", "B"), du type ("Force", "Volume") et de la date
    // ("AAAA-MM-JJ") demandes, avec moteur_app.generer_seance : donnees du moteur de l'app
    // (assistance de l'app remise, voir EngineSession.withAssistance) et memoire d'echec tiree du
    // journal historique_series.jsonl de l'app. Le mode filet (« jour faible ») est le defaut du
    // moteur. IOException (message affichable) si les donnees du moteur manquent ou sont
    // bloquees ; PyException si le calcul echoue.
    public static EngineSession.Result generate(Context context, Map<String, EngineSession.Assist> assistance,
                                                String cycle, String type, String date) throws IOException
    {
        EngineStorage.Loaded loaded = EngineRepository.get(context);
        if (loaded.status == EngineStorage.Status.EMPTY)
        {
            throw new IOException("aucune donnée du moteur dans l'app. Importe-les d'abord : "
                    + "Réglages > Moteur > Importer les données du moteur.");
        }
        if (loaded.status != EngineStorage.Status.OK)
        {
            throw new IOException("données du moteur illisibles, fonctions du moteur bloquées :\n"
                    + loaded.problem + "\nRefais l'import (Réglages > Moteur).");
        }
        String data = EngineSession.withAssistance(loaded.content.text(EngineContent.DATA), assistance);
        String journal = loaded.content.text(EngineContent.HISTORY);
        PyObject app = python(context).getModule("moteur_app");
        String raw = app.callAttr("generer_seance", data, journal, cycle, type, date).toString();
        return EngineSession.parseResult(raw);
    }
}
