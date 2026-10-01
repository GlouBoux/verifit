package com.example.verifit.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Repos reellement pris entre deux series (lot D, etape D2, 01/10/2026).
 *
 * Le repos d'une serie est l'ecart entre son horodatage de validation
 * (WorkoutSet.timestamp) et celui de la serie PRECEDENTE DU MEME EXERCICE le meme jour.
 * Les series passees en parametre doivent donc etre celles d'un seul exercice pour un
 * seul jour, dans l'ordre d'affichage (c'est ce que contient un WorkoutExercise).
 *
 * Decisions de Romain (01/10/2026) :
 * - la 1re serie d'un exercice n'a pas de repos (l'ecart avec l'exercice precedent
 *   melangerait repos et changement de machine) ;
 * - affichage "repos 2:15", champ optionnel "restSeconds" dans l'export Coaching.
 *
 * Limites assumees : l'horodatage est celui de la VALIDATION de la serie, donc l'ecart
 * inclut la duree d'execution de la serie (de l'ordre de 30 a 60 s). Un repos est
 * inconnu (null) si l'une des deux series n'a pas d'horodatage (import, CSV, anciennes
 * donnees), si l'ordre est incoherent (ecart nul ou negatif) ou si l'ecart depasse
 * MAX_REST_SECONDS (pause longue, fin de seance oubliee : ce n'est plus un repos).
 *
 * Classe pure (aucune dependance Android) : testee par SetRestTest.
 */
public final class SetRest
{
    // Au dela de 30 minutes entre deux series du meme exercice, on considere que ce
    // n'est plus un repos mais une interruption.
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

    // Repos de chaque serie, dans le meme ordre que la liste donnee (null = inconnu ;
    // toujours null pour la 1re serie).
    public static List<Long> restSecondsForExercise(List<WorkoutSet> setsInOrder)
    {
        List<Long> result = new ArrayList<>();
        if (setsInOrder == null)
        {
            return result;
        }

        for (int i = 0; i < setsInOrder.size(); i++)
        {
            result.add(i == 0 ? null : restSeconds(setsInOrder.get(i - 1), setsInOrder.get(i)));
        }
        return result;
    }

    // Texte "repos 2:15" de la serie a la position donnee d'une liste d'UN exercice d'UN
    // jour (ordre d'affichage), ou chaine vide : 1re serie, position hors liste, repos
    // inconnu. Point d'entree des adaptateurs de series (lot D, D3).
    public static String labelAt(List<WorkoutSet> setsInOrder, int position)
    {
        if (setsInOrder == null || position <= 0 || position >= setsInOrder.size())
        {
            return "";
        }
        return label(restSeconds(setsInOrder.get(position - 1), setsInOrder.get(position)));
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
