package com.example.verifit;

import com.example.verifit.model.WorkoutDay;
import com.example.verifit.model.WorkoutSet;

import java.util.List;

/**
 * Garde-fou au chargement des donnees (revue securite du 27/10/2026, lot S1, point C3 de
 * claude/fitengine-revue-securite-2026-10-27.md).
 *
 * Deux scenarios faisaient perdre l'historique sans prevenir :
 * - JSON des seances illisible : l'exception de Gson n'etait pas rattrapee et l'app
 *   plantait a chaque ouverture, sans acces aux Reglages pour restaurer ;
 * - fichier des preferences illisible : Android le lit comme VIDE, l'app demarrait sans
 *   historique, et la premiere serie loguee ecrivait cet historique vide par-dessus.
 *
 * Parade : a chaque sauvegarde, le nombre de series est aussi ecrit dans un petit fichier
 * de preferences SEPARE (voir DataStorage). Au chargement, des seances illisibles, ou
 * zero serie alors que ce compteur en annonce, bloquent toute sauvegarde et ouvrent
 * l'ecran de recuperation (ui/RecoveryDialog).
 *
 * Logique pure, sans Android : testee en JUnit (StorageCheckTest).
 */
public final class StorageCheck
{
    public enum Status
    {
        OK,
        // Le JSON enregistre ne se lit pas.
        UNREADABLE,
        // Lu sans erreur mais vide, alors que la derniere sauvegarde contenait des series.
        MISSING
    }

    // Compteur jamais ecrit (premiere ouverture apres la mise a jour, ou donnees de l'app
    // effacees par Android) : aucune comparaison possible, le chargement est accepte.
    public static final int UNKNOWN_COUNT = -1;

    private StorageCheck() {}

    // readable : le JSON a ete lu sans exception (absent compte comme lisible).
    // loadedSets : nombre de series lues. lastSavedSets : compteur de la derniere
    // sauvegarde, ou UNKNOWN_COUNT.
    public static Status workouts(boolean readable, int loadedSets, int lastSavedSets)
    {
        if (!readable)
        {
            return Status.UNREADABLE;
        }
        if (loadedSets == 0 && lastSavedSets > 0)
        {
            return Status.MISSING;
        }
        return Status.OK;
    }

    // Nombre de series, en ignorant les jours ou listes absents (donnees deserialisees
    // par Gson, qui laisse null ce qui manque).
    public static int countSets(List<WorkoutDay> days)
    {
        if (days == null)
        {
            return 0;
        }
        int count = 0;
        for (WorkoutDay day : days)
        {
            if (day == null)
            {
                continue;
            }
            List<WorkoutSet> sets = day.getSets();
            if (sets != null)
            {
                count += sets.size();
            }
        }
        return count;
    }

    // Ordre des deux ecritures d'une sauvegarde (seances, puis compteur), pour qu'un arret
    // brutal ENTRE les deux ne declenche jamais une fausse alerte. Le seul ordre
    // dangereux est "seances vides ecrites, compteur encore positif" : quand le nouveau
    // compteur vaut 0 (tout efface volontairement), on ecrit donc le compteur EN PREMIER.
    // Dans les autres cas, seances d'abord : un compteur en retard ne gene pas tant que
    // les seances ne sont pas vides, et il est recale au chargement suivant.
    public static boolean writeCountBeforeData(int newCount)
    {
        return newCount == 0;
    }
}
