package com.example.verifit;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;

/**
 * Point d'acces unique de l'app aux donnees du moteur (lot M3, 17/11/2026) : charge une seule
 * fois par process (a la demande, jamais au demarrage de l'app), garde le resultat, et
 * relit apres chaque ecriture pour verifier ce qui est sur le disque.
 *
 * Un probleme de chargement (BLOCKED) ne bloque QUE les fonctions du moteur : le carnet
 * d'entrainement reste utilisable. Une copie brute des fichiers illisibles part dans
 * Documents/FitEngine/illisible (une fois par process), comme pour les seances (lot S1).
 */
public final class EngineRepository
{
    private static final String TAG = "EngineRepository";

    private static EngineStorage.Loaded cached;
    private static final HashSet<String> copiedUnreadable = new HashSet<String>();

    private EngineRepository() {}

    private static EngineStorage storage(Context context)
    {
        return new EngineStorage(new EngineFilesAndroid(context));
    }

    public static synchronized EngineStorage.Loaded get(Context context)
    {
        if (cached == null)
        {
            cached = storage(context).load();
            if (cached.status == EngineStorage.Status.BLOCKED)
            {
                Log.e(TAG, "Donnees du moteur bloquees :\n" + cached.problem);
                for (Map.Entry<String, String> e : cached.unreadable.entrySet())
                {
                    if (copiedUnreadable.add(e.getKey()))
                    {
                        BackupManager.saveUnreadableCopy(context, "moteur_" + e.getKey().replace('.', '_'), e.getValue());
                    }
                }
            }
        }
        return cached;
    }

    // Remplace tout (import, restauration), puis relit le disque. Leve IOException si
    // l'ecriture echoue ou si la relecture ne rend pas exactement ce qui a ete ecrit.
    public static synchronized EngineStorage.Loaded replaceAll(Context context, EngineContent content,
                                                                String importedAt, String source, String exportedAt) throws IOException
    {
        cached = null;
        storage(context).replaceAll(content, importedAt, source, exportedAt);
        return reloadAndCheck(context, content);
    }

    // Reecrit le contenu en gardant l'origine du dernier import (renommage, suppression).
    public static synchronized EngineStorage.Loaded rewrite(Context context, EngineContent content, EngineState previous) throws IOException
    {
        cached = null;
        storage(context).rewrite(content, previous);
        return reloadAndCheck(context, content);
    }

    private static EngineStorage.Loaded reloadAndCheck(Context context, EngineContent written) throws IOException
    {
        EngineStorage.Loaded loaded = get(context);
        if (loaded.status != EngineStorage.Status.OK || !written.sameTexts(loaded.content))
        {
            throw new IOException("relecture des données du moteur différente de ce qui a été écrit"
                    + (loaded.problem == null ? "" : " :\n" + loaded.problem));
        }
        return loaded;
    }

    public static synchronized void invalidate()
    {
        cached = null;
    }
}
