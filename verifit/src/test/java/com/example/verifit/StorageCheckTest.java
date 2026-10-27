package com.example.verifit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.verifit.model.WorkoutDay;
import com.example.verifit.model.WorkoutSet;

import org.junit.Test;

import java.util.ArrayList;

// Lot S1 de la revue securite du 27/10/2026 (point C3) : regle de decision du chargement
// des seances, et ordre des ecritures de la sauvegarde. Voir StorageCheck.
public class StorageCheckTest
{
    private static WorkoutDay day(String date, int sets)
    {
        WorkoutDay day = new WorkoutDay();
        day.setDate(date);
        for (int i = 0; i < sets; i++)
        {
            day.addSet(new WorkoutSet(date, "Squat", "Legs", 5.0, 100.0));
        }
        return day;
    }

    // ------------------------------------------------------------ workouts()

    @Test
    public void jsonIllisible_bloque_meme_sans_compteur()
    {
        assertEquals(StorageCheck.Status.UNREADABLE, StorageCheck.workouts(false, 0, StorageCheck.UNKNOWN_COUNT));
        assertEquals(StorageCheck.Status.UNREADABLE, StorageCheck.workouts(false, 0, 120));
    }

    @Test
    public void historiqueVide_alors_que_la_derniere_sauvegarde_avait_des_series_bloque()
    {
        // Cas du fichier de preferences illisible, lu VIDE par Android.
        assertEquals(StorageCheck.Status.MISSING, StorageCheck.workouts(true, 0, 3800));
        assertEquals(StorageCheck.Status.MISSING, StorageCheck.workouts(true, 0, 1));
    }

    @Test
    public void premiere_ouverture_apres_la_mise_a_jour_est_acceptee()
    {
        // Compteur jamais ecrit : pas de comparaison possible, aucune fausse alerte.
        assertEquals(StorageCheck.Status.OK, StorageCheck.workouts(true, 3800, StorageCheck.UNKNOWN_COUNT));
        assertEquals(StorageCheck.Status.OK, StorageCheck.workouts(true, 0, StorageCheck.UNKNOWN_COUNT));
    }

    @Test
    public void historiqueVide_voulu_est_accepte()
    {
        // "Clear Data" ecrit le compteur a 0.
        assertEquals(StorageCheck.Status.OK, StorageCheck.workouts(true, 0, 0));
    }

    @Test
    public void compteur_en_retard_ne_bloque_pas_tant_que_des_series_sont_lues()
    {
        // Arret brutal entre l'ecriture des seances et celle du compteur : seul le cas
        // "zero serie lue" est suspect.
        assertEquals(StorageCheck.Status.OK, StorageCheck.workouts(true, 3790, 3800));
        assertEquals(StorageCheck.Status.OK, StorageCheck.workouts(true, 3810, 3800));
        assertEquals(StorageCheck.Status.OK, StorageCheck.workouts(true, 5, 0));
    }

    // ------------------------------------------------------------ countSets()

    @Test
    public void countSets_additionne_les_series_de_tous_les_jours()
    {
        ArrayList<WorkoutDay> days = new ArrayList<WorkoutDay>();
        days.add(day("2026-10-20", 3));
        days.add(day("2026-10-22", 0));
        days.add(day("2026-10-23", 7));
        assertEquals(10, StorageCheck.countSets(days));
    }

    @Test
    public void countSets_ignore_les_jours_et_listes_absents()
    {
        // Gson laisse null un element "null" ou un champ Sets manquant.
        ArrayList<WorkoutDay> days = new ArrayList<WorkoutDay>();
        days.add(null);
        WorkoutDay sansSeries = day("2026-10-21", 0);
        sansSeries.setSets(null);
        days.add(sansSeries);
        days.add(day("2026-10-23", 2));
        assertEquals(2, StorageCheck.countSets(days));
        assertEquals(0, StorageCheck.countSets(null));
        assertEquals(0, StorageCheck.countSets(new ArrayList<WorkoutDay>()));
    }

    // ------------------------------------------------------------ writeCountBeforeData()

    @Test
    public void compteur_ecrit_en_premier_seulement_quand_il_tombe_a_zero()
    {
        assertTrue(StorageCheck.writeCountBeforeData(0));
        assertFalse(StorageCheck.writeCountBeforeData(1));
        assertFalse(StorageCheck.writeCountBeforeData(3800));
    }

    @Test
    public void un_arret_entre_les_deux_ecritures_ne_cree_jamais_de_fausse_alerte()
    {
        // On simule chaque sauvegarde interrompue apres sa PREMIERE ecriture, puis le
        // chargement suivant, pour toutes les transitions de compteur.
        int[] counts = {0, 1, 50, 3800};
        for (int before : counts)
        {
            for (int after : counts)
            {
                int storedSets;
                int storedCount;
                if (StorageCheck.writeCountBeforeData(after))
                {
                    storedCount = after;   // compteur ecrit
                    storedSets = before;   // seances pas encore ecrites
                }
                else
                {
                    storedSets = after;    // seances ecrites
                    storedCount = before;  // compteur pas encore ecrit
                }
                assertEquals("avant=" + before + " apres=" + after,
                        StorageCheck.Status.OK, StorageCheck.workouts(true, storedSets, storedCount));
            }
        }
    }
}
