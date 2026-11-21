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
 *
 * Lot M5b (21/11/2026) : endOfSession() calcule le bilan de fin de seance (fin_de_seance.py du
 * meme dossier moteur/, par moteur_app.fin_de_seance).
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

    // Lot M5b.1 : bilan de fin de seance avec moteur_app.fin_de_seance (regles de la sync du PC),
    // a partir du contenu du moteur deja charge par l'appelant (garde pour verifier, au
    // « Valider », que rien n'a change entre-temps), de l'export Coaching construit en memoire
    // (DataStorage.buildCoachingExportJson) et de l'horodatage des lignes de journal. N'ecrit
    // rien. IOException (message affichable) si les donnees n'ont pas rm_max ; PyException si
    // le calcul echoue ; IllegalArgumentException si le texte rendu est illisible.
    public static EngineEndOfSession.Result endOfSession(Context context, EngineContent content,
                                                         String exportJson, String timestamp) throws IOException
    {
        if (!content.hasRmMax())
        {
            throw new IOException("données du moteur importées avant le lot M5b (sans rm_max). Refais l'import : "
                    + "8_Export_moteur_app.bat sur le PC, puis Réglages > Moteur > Importer les données du moteur.");
        }
        PyObject app = python(context).getModule("moteur_app");
        String raw = app.callAttr("fin_de_seance", content.text(EngineContent.DATA), exportJson,
                content.text(EngineContent.PLANS), content.text(EngineContent.HISTORY), timestamp).toString();
        return EngineEndOfSession.parse(raw);
    }

    // Lot M5b.3 : annulation des PR d'un exercice ecrits par un bilan (moteur_app.annuler_pr :
    // valeurs remises avec le plancher de la cascade, journal corrige, plans remis). N'ecrit
    // rien. PyException avec un message lisible si l'annulation est refusee (deja annulee, RM
    // change depuis) ; IllegalArgumentException si le texte rendu est illisible.
    public static EnginePrHistory.Cancellation cancelPr(Context context, EngineContent content, String exportJson,
                                                        String bilanId, String exercise, String timestamp)
    {
        PyObject app = python(context).getModule("moteur_app");
        String raw = app.callAttr("annuler_pr", content.text(EngineContent.DATA), content.text(EngineContent.PR_HISTORY),
                content.text(EngineContent.HISTORY), content.text(EngineContent.PLANS), exportJson,
                bilanId, exercise, timestamp).toString();
        return EnginePrHistory.parseCancellation(raw);
    }
}
