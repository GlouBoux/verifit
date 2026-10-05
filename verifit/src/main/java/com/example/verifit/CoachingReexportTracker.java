package com.example.verifit;

import com.example.verifit.model.WorkoutDay;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decide quand l'export Coaching a nom fixe (fitengine_coaching_latest.json) doit etre
 * REFAIT apres l'arret du chrono de seance (lot D, etape D9, 05/10/2026).
 *
 * Probleme resolu : l'export automatique se declenche a l'arret du chrono (D6), mais
 * Romain relit ensuite sa seance et corrige des reps / poids. Sans ce suivi, le fichier
 * envoye au PC (Syncthing) garde les valeurs d'AVANT les corrections.
 *
 * Principe : on garde, pour chaque jour, l'empreinte (JSON Coaching du jour) telle
 * qu'elle a ete exportee pour la derniere fois. Apres une sauvegarde, un re-export est
 * necessaire si au moins un jour dont le chrono est ARRETE (SessionEndTimestamp non
 * null) a une empreinte differente de celle exportee, ou n'a jamais ete exporte.
 *
 * Volontairement ignores :
 * - un jour dont le chrono tourne ou n'a jamais demarre (seance en cours : l'export se
 *   fera a l'arret, pas a chaque serie) ;
 * - un jour qui disparait de la liste (suppression de toutes ses series) : le fichier
 *   n'est pas refait pour ce seul motif, il le sera a la prochaine modification d'un
 *   jour arrete.
 *
 * Logique pure (aucune dependance Android), testee en JUnit. Doit etre appelee depuis le
 * meme fil que les modifications de workoutDays (fil principal dans l'app).
 */
public final class CoachingReexportTracker
{
    private final Gson gson = new Gson();

    // date du jour -> empreinte exportee (le JSON du jour dans l'export Coaching).
    private final Map<String, String> exported = new HashMap<String, String>();

    // Memorise l'etat actuel comme "deja exporte" : a appeler apres chaque ecriture
    // reussie du fichier, apres le chargement des donnees et apres une restauration.
    public void remember(List<WorkoutDay> days)
    {
        exported.clear();
        for (WorkoutDay day : days)
        {
            exported.put(day.getDate(), fingerprint(day));
        }
    }

    // Vrai si un jour au chrono arrete differe de ce qui a ete exporte.
    public boolean stoppedDayChanged(List<WorkoutDay> days)
    {
        for (WorkoutDay day : days)
        {
            if (day.getSessionEndTimestamp() == null)
            {
                continue;
            }
            String previous = exported.get(day.getDate());
            if (previous == null || !previous.equals(fingerprint(day)))
            {
                return true;
            }
        }
        return false;
    }

    // Meme contenu que celui ecrit par DataStorage.buildCoachingExportJson() pour ce
    // jour (sans l'horodatage d'export, qui changerait a chaque fois).
    private String fingerprint(WorkoutDay day)
    {
        return gson.toJson(CoachingExportDay.fromWorkoutSets(day.getDate(), day.getSets()));
    }
}
