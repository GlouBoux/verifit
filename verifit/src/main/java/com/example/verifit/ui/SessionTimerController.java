package com.example.verifit.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;

import com.example.verifit.R;
import com.example.verifit.SessionTimerTicker;
import com.example.verifit.WorkoutReportGenerator;
import com.example.verifit.model.WorkoutDay;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Chrono de la SEANCE (barre "session_timer_bar" + dialogue "Workout Time"), partage par
 * l'ecran de saisie d'un exercice (AddExerciseActivity) et l'ecran d'un jour
 * (DayActivity).
 *
 * Lot C, etape C.3 (01/10/2026) : barre, dialogue, reglages et annulation etaient copies
 * dans les deux ecrans, et la correction du 28/09/2026 (pouvoir demarrer le chrono a la
 * main avant toute serie : etat "Not started" + bouton "Start Timer") n'avait ete faite
 * que dans AddExerciseActivity. Une seule version desormais. Les regles (demarrer,
 * arreter, reprendre, annuler) sont dans WorkoutDay, testees en JUnit.
 *
 * Utilisation : creer a l'initialisation de l'ecran, appeler start() dans onResume(),
 * stop() dans onPause(), refresh() quand les series du jour ont pu changer.
 */
public final class SessionTimerController
{
    // Date du jour affiche, lue a chaque usage : l'ecran de saisie la prend dans
    // MainActivity.dateSelected, l'ecran du jour dans son extra d'Intent.
    public interface DateSource
    {
        String currentDate();
    }

    private final AppCompatActivity activity;
    private final DateSource dateSource;
    private final SessionTimerTicker barTicker;

    // Duree affichee DANS le dialogue : demarre a l'ouverture, arretee a la fermeture.
    private SessionTimerTicker dialogTicker;

    public SessionTimerController(AppCompatActivity activity, TextView barDisplay, View barContainer, DateSource dateSource)
    {
        this.activity = activity;
        this.dateSource = dateSource;
        this.barTicker = new SessionTimerTicker(barDisplay);

        // La barre n'a pas de bouton propre (retour Romain 07/09/2026) : un tap ouvre le
        // dialogue "Workout Time", seul endroit pour Start/Stop/Resume.
        barContainer.setOnClickListener(v -> showWorkoutTimeDialog());
    }

    public void start()
    {
        refresh();
        barTicker.start();
    }

    public void stop()
    {
        barTicker.stop();
    }

    public void refresh()
    {
        barTicker.setWorkoutDay(currentDay());
    }

    // null tant qu'aucune serie n'a ete loggee ce jour-la.
    private WorkoutDay currentDay()
    {
        int position = MainActivity.dataStorage.getDayPosition(dateSource.currentDate());
        return (position >= 0) ? MainActivity.dataStorage.getWorkoutDays().get(position) : null;
    }

    private void save()
    {
        MainActivity.dataStorage.saveWorkoutData(activity.getApplicationContext());
    }

    // Dialogue "Workout Time" (retour Romain 07/09/2026, captures FitNotes) : date,
    // heure de debut, de fin, duree, bouton Start/Stop/Resume et menu "..." (Reglages /
    // Annuler le chrono).
    public void showWorkoutTimeDialog()
    {
        WorkoutDay day = currentDay();
        if (day == null)
        {
            // Rien a montrer tant qu'aucune serie n'existe ce jour-la (cas different de
            // "chrono pas encore demarre", qui ouvre le dialogue ci-dessous).
            return;
        }

        View dialogView = LayoutInflater.from(activity).inflate(R.layout.workout_time_dialog, null);

        TextView tvDate = dialogView.findViewById(R.id.tv_workout_time_date);
        TextView tvStart = dialogView.findViewById(R.id.tv_workout_time_start);
        TextView tvEnd = dialogView.findViewById(R.id.tv_workout_time_end);
        TextView tvDuration = dialogView.findViewById(R.id.tv_workout_time_duration);
        ImageButton btOverflow = dialogView.findViewById(R.id.bt_workout_time_overflow);
        MaterialButton btToggle = dialogView.findViewById(R.id.bt_workout_time_toggle);
        MaterialButton btClose = dialogView.findViewById(R.id.bt_workout_time_close);

        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        tvDate.setText(WorkoutReportGenerator.formatDateHeader(day.getDate()));

        // Retour Romain 28/09/2026 ("je dois pouvoir le lancer manuellement") : chrono
        // jamais demarre = etat "Not started" avec un bouton "Start Timer".
        if (day.getSessionStartTimestamp() == null)
        {
            tvStart.setText("Not started");
            tvEnd.setText("-");
            btToggle.setText("Start Timer");
        }
        else
        {
            tvStart.setText(timeFormat.format(new Date(day.getSessionStartTimestamp())));
            tvEnd.setText(day.getSessionEndTimestamp() != null
                    ? timeFormat.format(new Date(day.getSessionEndTimestamp()))
                    : "In progress");
            btToggle.setText(day.isSessionTimerRunning() ? "Stop Timer" : "Resume Timer");
        }

        dialogTicker = new SessionTimerTicker(tvDuration);
        dialogTicker.setWorkoutDay(day);
        dialogTicker.start();

        AlertDialog dialog = new AlertDialog.Builder(activity).setView(dialogView).create();

        btToggle.setOnClickListener(v ->
        {
            day.toggleSession(System.currentTimeMillis());
            save();
            barTicker.setWorkoutDay(day);
            dialog.dismiss();
        });

        btClose.setOnClickListener(v -> dialog.dismiss());

        btOverflow.setOnClickListener(v ->
        {
            PopupMenu popupMenu = new PopupMenu(activity, btOverflow);
            popupMenu.inflate(R.menu.workout_time_dialog_menu);
            popupMenu.setOnMenuItemClickListener(item ->
            {
                int itemId = item.getItemId();
                if (itemId == R.id.workout_time_settings)
                {
                    showSettingsDialog();
                    return true;
                }
                else if (itemId == R.id.workout_time_cancel_timer)
                {
                    confirmCancel(day, dialog);
                    return true;
                }
                return false;
            });
            popupMenu.show();
        });

        dialog.setOnDismissListener(d ->
        {
            if (dialogTicker != null)
            {
                dialogTicker.stop();
                dialogTicker = null;
            }
        });

        dialog.show();
    }

    // "Workout Time Settings" : seul reglage reproduit, "Auto Start" (voir
    // workout_time_settings_dialog.xml pour l'absence voulue d'un "Auto Stop").
    private void showSettingsDialog()
    {
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.workout_time_settings_dialog, null);

        CheckBox cbAutoStart = dialogView.findViewById(R.id.cb_workout_time_auto_start);
        MaterialButton btClose = dialogView.findViewById(R.id.bt_workout_time_settings_close);

        cbAutoStart.setChecked(SessionTimerTicker.isAutoStartEnabled(activity));

        AlertDialog dialog = new AlertDialog.Builder(activity).setView(dialogView).create();

        cbAutoStart.setOnCheckedChangeListener((buttonView, isChecked) -> SessionTimerTicker.setAutoStartEnabled(activity, isChecked));
        btClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    // "Cancel Timer" (irreversible) : efface le chrono, les series ne sont pas touchees.
    // Textes en anglais, comme le reste du dialogue.
    private void confirmCancel(WorkoutDay day, AlertDialog workoutTimeDialog)
    {
        new AlertDialog.Builder(activity)
                .setTitle("Cancel Timer")
                .setMessage("Cancel the timer for this workout? Sets already logged will not be deleted.")
                .setPositiveButton("Cancel Timer", (dlg, which) ->
                {
                    day.cancelSession();
                    save();
                    barTicker.setWorkoutDay(day);
                    workoutTimeDialog.dismiss();
                })
                .setNegativeButton("Back", null)
                .show();
    }
}
