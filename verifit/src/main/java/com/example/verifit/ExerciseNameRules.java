package com.example.verifit;

import java.util.Collection;

// Regles de nommage d'un exercice (retour Romain 14/10/2026), partagees par Edit et
// Duplicate (voir ui/ExerciseActions). Java pur, sans dependance Android, pour etre
// testable en JUnit (voir ExerciseNameRulesTest).
//
// Pourquoi : tout l'app retrouve un exercice par son NOM exact (series, ordre du jour,
// supersets, objectifs, et cote Coaching le fichier correspondance_fitnotes_excel.csv).
// Deux trous existaient dans le dialogue Edit :
//  - aucun controle de doublon : renommer "A" en "B" alors que "B" existe fusionnait
//    silencieusement les deux historiques (Duplicate, lui, controlait deja) ;
//  - aucun nettoyage : un espace en fin de nom creait un nom "different" de celui
//    attendu par la correspondance Coaching.
public final class ExerciseNameRules
{
    public enum Result { OK, EMPTY, ALREADY_EXISTS }

    public static final class Check
    {
        public final Result result;
        // OK : le nom a enregistrer (nettoye, ou l'ancien nom intact s'il n'a pas change).
        // ALREADY_EXISTS : le nom de l'exercice existant en conflit (pour le message).
        // EMPTY : chaine vide.
        public final String name;

        Check(Result result, String name)
        {
            this.result = result;
            this.name = name;
        }

        public boolean isOk()
        {
            return result == Result.OK;
        }
    }

    private ExerciseNameRules() {}

    // Retire les espaces en debut/fin et ramene toute suite d'espaces interne a un seul.
    public static String normalize(String raw)
    {
        if (raw == null) return "";
        return raw.trim().replaceAll("\\s+", " ");
    }

    // currentName : nom actuel en cas de renommage, null pour une creation/duplication.
    // existingNames : noms de tous les exercices connus (l'exercice renomme compris).
    //
    // Doublon insensible a la casse : "Squat" et "squat" seraient deux exercices
    // distincts pour l'app mais indiscernables a l'oeil (et pour la correspondance
    // Coaching). Changer seulement la casse de SON PROPRE nom reste permis.
    //
    // Nom inchange (aux espaces pres) : on renvoie l'ancien nom tel quel, pour qu'un
    // simple Save sur un exercice dont le nom historique contient un double espace ne
    // declenche pas un renommage implicite.
    public static Check check(String raw, String currentName, Collection<String> existingNames)
    {
        String name = normalize(raw);
        if (name.isEmpty())
        {
            return new Check(Result.EMPTY, "");
        }

        if (currentName != null && name.equals(normalize(currentName)))
        {
            return new Check(Result.OK, currentName);
        }

        if (existingNames != null)
        {
            for (String existing : existingNames)
            {
                if (existing == null) continue;
                if (currentName != null && existing.equals(currentName)) continue;
                if (normalize(existing).equalsIgnoreCase(name))
                {
                    return new Check(Result.ALREADY_EXISTS, existing);
                }
            }
        }

        return new Check(Result.OK, name);
    }
}
