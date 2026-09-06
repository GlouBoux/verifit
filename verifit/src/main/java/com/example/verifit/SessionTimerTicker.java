package com.example.verifit;

import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import com.example.verifit.model.WorkoutDay;

import java.util.Locale;

// Retour Romain 06/09/2026 : chrono de la SEANCE entiere (different du minuteur de
// repos entre les series, deja gere par AddExerciseActivity.startTimer()/pauseTimer()
// etc.) - "je ne sais ni ne peux controler ce timer [...] il faut que je puisse y
// acceder". Ce chrono est affiche a la fois sur AddExerciseActivity et DayActivity ;
// cette classe factorise l'unique partie strictement identique entre les deux ecrans :
// faire defiler l'affichage "HH:mm:ss" chaque seconde tant que la seance tourne.
//
// Le demarrage/la reprise/l'arret du chrono lui-meme (WorkoutDay.SessionStartTimestamp/
// SessionEndTimestamp) restent geres par l'Activity appelante (voir
// AddExerciseActivity.startOrResumeSessionTimer()/toggleSessionTimer() et
// l'equivalent sur DayActivity), car ils impliquent une sauvegarde des donnees
// (DataStorage.saveWorkoutData()) propre a chaque ecran - cette classe ne fait QUE de
// l'affichage, jamais de mutation du modele.
public class SessionTimerTicker
{
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TextView display;
    private WorkoutDay workoutDay;

    private final Runnable tick = new Runnable()
    {
        @Override
        public void run()
        {
            refresh();
            handler.postDelayed(this, 1000);
        }
    };

    public SessionTimerTicker(TextView display)
    {
        this.display = display;
    }

    // A appeler a chaque fois que le WorkoutDay affiche peut avoir change (nouvelle
    // serie loggee, jour different, Stop/Resume manuel...) - rafraichit l'affichage
    // immediatement, sans attendre le prochain tick.
    public void setWorkoutDay(WorkoutDay workoutDay)
    {
        this.workoutDay = workoutDay;
        refresh();
    }

    // A appeler depuis onResume() de l'Activity - fait defiler l'affichage chaque
    // seconde tant que la seance tourne (voir refresh()).
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

    public void refresh()
    {
        if (display == null)
        {
            return;
        }

        if (workoutDay == null || workoutDay.getSessionStartTimestamp() == null)
        {
            display.setText("--:--:--");
            return;
        }

        long start = workoutDay.getSessionStartTimestamp();
        long end = (workoutDay.getSessionEndTimestamp() != null)
                ? workoutDay.getSessionEndTimestamp()
                : System.currentTimeMillis();

        long elapsedSeconds = Math.max(0, (end - start) / 1000);
        long hours = elapsedSeconds / 3600;
        long minutes = (elapsedSeconds % 3600) / 60;
        long seconds = elapsedSeconds % 60;

        display.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
    }
}
