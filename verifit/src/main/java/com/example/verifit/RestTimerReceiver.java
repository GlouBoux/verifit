package com.example.verifit;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.verifit.ui.MainActivity;

// Retour Romain 06/09/2026 : "d'une facon generale quand je set un timer, je veux que
// meme telephone verrouille, il sonne pour me dire que je peux reprendre ma serie. Et
// je dois pouvoir lui faire confiance sur le fait de sonner."
//
// AddExerciseActivity utilisait jusqu'ici un simple CountDownTimer pour le minuteur de
// repos - correct pour l'affichage, mais son onFinish() ne declenchait ni son ni
// vibration, et de toute facon un CountDownTimer classique (Handler attache au cycle de
// vie de l'Activity) n'est pas fiable telephone verrouille ou app en arriere-plan
// (throttling/Doze, voire kill du process).
//
// Ce receiver est declenche par une alarme systeme independante (voir
// AddExerciseActivity.scheduleTimerAlarm(), AlarmManager.setAlarmClock() - exempte des
// restrictions Doze/App Standby) : elle se declenche que l'app soit ouverte, en
// arriere-plan, ou l'ecran verrouille, sans dependre du CountDownTimer ni du cycle de
// vie de l'Activity.
//
// Retour Romain 06/09/2026 (apres premier test reussi) : "je voudrai que la sonnerie
// ne perturbe pas. Sur fitnotes ca fait un Tuuut et c'est tout." Le premier jet
// utilisait le son/l'attribut audio de type ALARME (TYPE_ALARM/USAGE_ALARM), pense
// pour rester audible meme en mode silencieux/Ne pas deranger - mais qui se traduit
// sur la plupart des telephones par une sonnerie d'alarme longue et forte, pas un
// simple bip discret. Bascule sur le son/l'attribut de notification standard
// (TYPE_NOTIFICATION), plus proche du "Tuuut" de FitNotes - au prix de ne plus
// forcement passer en mode silencieux/Ne pas deranger (compromis assume : Romain a
// confirme prefer un signal discret a un signal qui "perturbe").
public class RestTimerReceiver extends BroadcastReceiver
{
    // Nouvel identifiant de canal ("rest_timer_channel" auparavant) : sur les
    // telephones ayant deja recu la premiere version, ce canal restait cree avec le
    // son/vibration d'alarme d'origine - un canal de notification est immuable une
    // fois cree sur Android 8+ (cf. createNotificationChannel() ci-dessous). Changer
    // l'id force sa recreation avec les nouveaux reglages plus discrets.
    private static final String CHANNEL_ID = "rest_timer_channel_v2";
    private static final int NOTIFICATION_ID = 4242;

    @Override
    public void onReceive(Context context, Intent intent)
    {
        createNotificationChannel(context);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(
                context, NOTIFICATION_ID, new Intent(context, MainActivity.class), flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Repos termine")
                .setContentText("Vous pouvez reprendre votre serie")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(contentIntent);

        // Sur Android < 8 (pas de canaux de notification), le son et la vibration se
        // fixent directement sur la notification. A partir d'Android 8, c'est le canal
        // (cree une seule fois ci-dessous) qui les porte - non modifiables ensuite.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
        {
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            builder.setVibrate(new long[]{0, 200});
        }

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null)
        {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private void createNotificationChannel(Context context)
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
        {
            return;
        }

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager == null || notificationManager.getNotificationChannel(CHANNEL_ID) != null)
        {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Minuteur de repos", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Notification sonore discrete a la fin du temps de repos entre les series");
        channel.enableVibration(true);
        // Une seule vibration courte (200ms) plutot que le double-buzz plus long du
        // premier jet - coherent avec le "Tuuut" bref demande par Romain.
        channel.setVibrationPattern(new long[]{0, 200});

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        channel.setSound(soundUri, audioAttributes);

        notificationManager.createNotificationChannel(channel);
    }
}
