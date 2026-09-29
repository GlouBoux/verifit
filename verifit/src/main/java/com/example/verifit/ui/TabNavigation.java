package com.example.verifit.ui;

import android.app.Activity;
import android.content.Intent;

import com.example.verifit.R;

/**
 * Navigation par la barre du bas (Workout, Exercises, Sessions, Charts, Me), commune aux
 * 4 ecrans a onglets. Remplace les 4 copies identiques de onNavigationItemSelected()
 * (voir revue d'architecture du 28/09/2026, §3.3 duplication).
 *
 * Retour Romain 29/09/2026 : "si je fais retour ca cycle 36 fois entre les onglets que
 * j'ai ouverts". Chaque tap sur un onglet empilait un NOUVEL ecran sans jamais fermer le
 * precedent (Workout, Exercises, Workout, Exercises...). Regle desormais, comme dans une
 * app a onglets classique :
 * - Workout (MainActivity) reste la base de la pile ; y revenir ferme tout ce qui est
 *   au-dessus (FLAG_ACTIVITY_CLEAR_TOP + SINGLE_TOP : l'ecran Workout existant est
 *   reutilise, pas recree) ;
 * - passer d'un onglet secondaire a un autre remplace l'ecran courant (finish()) ;
 * - "Me" (Reglages) s'ouvre par-dessus, Retour ramene a l'onglet d'ou on vient ;
 * - retaper l'onglet deja affiche ne fait rien (MainActivity gere ce cas elle-meme :
 *   retour au jour courant).
 * Retour depuis un onglet secondaire ramene donc toujours a Workout, et Retour depuis
 * Workout quitte l'app.
 */
public final class TabNavigation
{
    private TabNavigation() {}

    public static boolean navigate(Activity from, int itemId, int currentItemId)
    {
        if (itemId == currentItemId)
        {
            return true;
        }

        boolean fromWorkout = from instanceof MainActivity;

        if (itemId == R.id.home)
        {
            from.startActivity(workoutIntent(from));
            from.overridePendingTransition(0, 0);
            if (!fromWorkout)
            {
                from.finish();
            }
            return true;
        }

        if (itemId == R.id.me)
        {
            from.startActivity(new Intent(from, SettingsActivity.class));
            from.overridePendingTransition(0, 0);
            return true;
        }

        Intent target;
        if (itemId == R.id.exercises)
        {
            target = new Intent(from, ExercisesActivity.class);
        }
        else if (itemId == R.id.diary)
        {
            target = new Intent(from, DiaryActivity.class);
            if (fromWorkout)
            {
                // Comportement existant conserve : depuis Workout, Sessions s'ouvre sur
                // le jour affiche.
                target.putExtra("date", MainActivity.dateSelected);
            }
        }
        else if (itemId == R.id.charts)
        {
            target = new Intent(from, ChartsActivity.class);
        }
        else
        {
            return false;
        }

        from.startActivity(target);
        from.overridePendingTransition(0, 0);
        if (!fromWorkout)
        {
            from.finish();
            from.overridePendingTransition(0, 0);
        }
        return true;
    }

    // Retour a l'ecran Workout existant (base de la pile) en fermant tout ce qui est
    // au-dessus, sans le recreer.
    public static Intent workoutIntent(Activity from)
    {
        Intent in = new Intent(from, MainActivity.class);
        in.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return in;
    }
}
