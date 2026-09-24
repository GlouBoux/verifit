package com.example.verifit;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import java.util.Locale;

// Barre persistante affichant le temps restant du minuteur de REPOS, visible sur
// l'ecran de saisie (AddExerciseActivity) meme quand la boite de dialogue "Timer" est
// fermee - retour Romain 24/09/2026 : "je veux voir le temps restant avant de repartir
// (principalement pour savoir visuellement qu'il me reste du temps. Si je n'ai pas
// entendu le timer MAIS que je vois le temps restant c'est ok. Si je n'ai rien entendu
// MAIS que je ne vois plus le temps restant alors je peux y retourner.)"
//
// Contrairement au CountDownTimer local de AddExerciseActivity.startTimer()/
// TimeLeftInMillis (attaches au cycle de vie de cette Activity, donc perdus en
// changeant d'exercice via le volet de navigation - architecture "relance d'ecran",
// finish()+startActivity() - voir claude/fitnotes-feature-navigation-panel.md), cette
// classe relit a chaque tick l'instant de fin persiste par
// RestTimerReceiver.persistEndTimestamp() : elle retrouve donc le bon temps restant
// meme apres un changement d'exercice ou un redemarrage de l'app, tant que le minuteur
// est toujours en cours - meme principe de fiabilite que l'alarme systeme qui
// declenche le bip (RestTimerReceiver), juste applique a l'affichage plutot qu'au son.
//
// Ne fait jamais de mutation d'etat (demarrage/pause/reset du minuteur) - purement de
// l'affichage, meme repartition des responsabilites que SessionTimerTicker pour le
// chrono de seance.
public class RestTimerBarTicker
{
    private final Context context;
    private final View barContainer;
    private final TextView display;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable tick = new Runnable()
    {
        @Override
        public void run()
        {
            refresh();
            handler.postDelayed(this, 1000);
        }
    };

    public RestTimerBarTicker(Context context, View barContainer, TextView display)
    {
        this.context = context;
        this.barContainer = barContainer;
        this.display = display;
    }

    // A appeler depuis onResume() de l'Activity - fait defiler l'affichage chaque
    // seconde tant qu'un minuteur de repos est en cours (voir refresh()).
    public void start()
    {
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    // A appeler depuis onPause() de l'Activity, pour ne pas continuer a rafraichir un
    // affichage qui n'est plus visible.
    public void stop()
    {
        handler.removeCallbacks(tick);
    }

    // Affiche/cache la barre et met a jour le decompte immediatement - a appeler aussi
    // en dehors du ticker (Start/Pause/Reset), pour ne pas attendre le prochain tick
    // avant que le changement soit visible.
    public void refresh()
    {
        if (barContainer == null || display == null)
        {
            return;
        }

        long endTimestamp = RestTimerReceiver.getPersistedEndTimestamp(context);
        long remainingMillis = endTimestamp - System.currentTimeMillis();

        if (endTimestamp <= 0 || remainingMillis <= 0)
        {
            barContainer.setVisibility(View.GONE);
            return;
        }

        long remainingSeconds = (remainingMillis + 999) / 1000; // arrondi au-dessus
        long minutes = remainingSeconds / 60;
        long seconds = remainingSeconds % 60;

        display.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
        barContainer.setVisibility(View.VISIBLE);
    }
}
