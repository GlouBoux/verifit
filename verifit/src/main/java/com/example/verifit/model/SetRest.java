package com.example.verifit.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Repos reellement pris avant une serie (lot D, etape D2, 01/10/2026 ; regle revue le
 * 14/10/2026).
 *
 * Le repos d'une serie est l'ecart entre son horodatage de validation
 * (WorkoutSet.timestamp) et celui de la DERNIERE SERIE VALIDEE DU JOUR, TOUS EXERCICES
 * CONFONDUS (la serie du jour qui a l'horodatage le plus recent strictement anterieur).
 *
 * Retour Romain 14/10/2026 (seance du 12/10, Assisted Pelican alterne avec Assisted
 * HSPU FP) : l'ancienne regle mesurait l'ecart avec la serie precedente du MEME
 * exercice, ce qui, des que deux exercices sont alternes, compte la serie de l'autre
 * exercice dans le "repos" (12:17 affiche pour un vrai repos de 6:21). Decision : le
 * repos = temps depuis la derniere serie cochee, quel que soit l'exercice.
 *
 * Le precedent est cherche par HORODATAGE, pas par ordre de liste : les series d'un
 * WorkoutDay sont rangees par exercice, pas chronologiquement (le 12/10, les HSPU
 * 18:12 sont listees apres les Pelican 18:15). Recherche par valeur, jamais par identite
 * d'objet (WorkoutDay.Sets et WorkoutDay.Exercises[].Sets sont des instances
 * differentes apres un rechargement, voir DataStorage.getRepRangePRKeys()).
 *
 * Decisions de Romain conservees :
 * - la 1re serie d'un exercice n'a pas de repos (01/10/2026, reconfirme le 14/10/2026,
 *   option A : l'ecart avec l'exercice precedent melangerait repos et changement de
 *   machine, meme quand les exercices sont alternes) ;
 * - affichage "repos 2:15", champ optionnel "restSeconds" dans l'export Coaching.
 *
 * Limites assumees : l'horodatage est celui de la VALIDATION de la serie, donc l'ecart
 * inclut la duree d'execution de la serie (de l'ordre de 30 a 60 s). Un repos est
 * inconnu (null) si la serie n'a pas d'horodatage (import, CSV, anciennes donnees, serie
 * pas encore cochee), s'il n'existe aucune serie validee avant elle ce jour-la, ou si
 * l'ecart depasse MAX_REST_SECONDS (pause longue : ce n'est plus un repos).
 *
 * Classe pure (aucune dependance Android) : testee par SetRestTest.
 */
public final class SetRest
{
    // Au dela de 30 minutes depuis la derniere serie, on considere que ce n'est plus un
    // repos mais une interruption.
    public static final long MAX_REST_SECONDS = 30 * 60;

    private SetRest() {}

    // Repos (en secondes) de "current" par rapport a "previous", ou null si inconnu.
    public static Long restSeconds(WorkoutSet previous, WorkoutSet current)
    {
        if (previous == null || current == null
                || !previous.hasTimestamp() || !current.hasTimestamp())
        {
            return null;
        }

        long seconds = (current.getTimestamp() - previous.getTimestamp()) / 1000L;
        if (seconds <= 0 || seconds > MAX_REST_SECONDS)
        {
            return null;
        }
        return seconds;
    }

    // Derniere serie validee du jour avant "current" (horodatage le plus recent
    // strictement anterieur au sien), tous exercices confondus, ou null.
    public static WorkoutSet previousValidatedInDay(List<WorkoutSet> daySets, WorkoutSet current)
    {
        if (daySets == null || current == null || !current.hasTimestamp())
        {
            return null;
        }

        long currentTs = current.getTimestamp();
        WorkoutSet best = null;
        for (WorkoutSet candidate : daySets)
        {
            if (candidate == null || !candidate.hasTimestamp())
            {
                continue;
            }
            long ts = candidate.getTimestamp();
            if (ts < currentTs && (best == null || ts > best.getTimestamp()))
            {
                best = candidate;
            }
        }
        return best;
    }

    // Repos de "current" depuis la derniere serie validee du jour (null si inconnu).
    // Ne traite PAS la regle "1re serie de l'exercice" : c'est a l'appelant de la
    // connaitre (position affichee ou 1re occurrence dans le jour).
    public static Long restSecondsInDay(List<WorkoutSet> daySets, WorkoutSet current)
    {
        return restSeconds(previousValidatedInDay(daySets, current), current);
    }

    // Repos de chaque serie d'un JOUR entier, aligne sur la liste donnee. La 1re serie
    // de chaque exercice (1re occurrence de son nom dans la liste) vaut null. Sert a
    // l'export Coaching (lot D, D4).
    public static List<Long> restSecondsForDay(List<WorkoutSet> daySets)
    {
        List<Long> result = new ArrayList<>();
        if (daySets == null)
        {
            return result;
        }

        Set<String> seenExercises = new HashSet<>();
        for (WorkoutSet set : daySets)
        {
            String key = set.getExerciseName() == null ? "" : set.getExerciseName();
            boolean firstOfExercise = seenExercises.add(key);
            result.add(firstOfExercise ? null : restSecondsInDay(daySets, set));
        }
        return result;
    }

    // Texte "repos 2:15" de la serie a la position donnee de la liste d'UN exercice d'UN
    // jour (ordre d'affichage), le repos etant mesure contre toutes les series du jour
    // (daySets). Chaine vide : 1re serie de l'exercice, position hors liste, repos
    // inconnu. Point d'entree des adaptateurs de series (lot D, D3).
    public static String labelAt(List<WorkoutSet> exerciseSets, int position, List<WorkoutSet> daySets)
    {
        if (exerciseSets == null || position <= 0 || position >= exerciseSets.size())
        {
            return "";
        }
        return label(restSecondsInDay(daySets, exerciseSets.get(position)));
    }

    // "2:15" (minutes:secondes). Jamais appele avec une valeur negative.
    public static String formatRest(long seconds)
    {
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }

    // Texte affiche a cote de la serie ("repos 2:15"), ou chaine vide si inconnu.
    public static String label(Long seconds)
    {
        return seconds == null ? "" : "repos " + formatRest(seconds);
    }
}
