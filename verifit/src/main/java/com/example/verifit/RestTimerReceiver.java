package com.example.verifit;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

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
// Historique des ajustements du son (06/09/2026) :
// 1er jet : son/attribut de type ALARME (TYPE_ALARM/USAGE_ALARM) porte par le canal de
// notification - fiable meme en silencieux, mais rendu comme une sonnerie d'alarme
// longue et forte sur la plupart des telephones ("je voudrai que la sonnerie ne
// perturbe pas").
// 2e jet : bascule sur le son de notification standard du telephone (TYPE_NOTIFICATION)
// - plus discret, mais Romain a alors signale un nouveau besoin : un son SPECIFIQUE et
// reconnaissable, audible a quelques metres par-dessus la musique de la salle de sport,
// avec un volume reglable depuis l'app (pas juste le volume "notifications" du systeme).
// 3e jet : bip synthetise dedie (fichier WAV fixe, 350ms) joue via MediaPlayer sur le
// flux ALARME, volume reglable (sb_volume/VOLUME_PREF_KEY). Notification System rendue
// silencieuse (le bip est joue separement) pour eviter un double signal sonore.
// 4e jet (celui-ci) : "le bip est un peu court [...] possible que tu le rendes 2 fois
// plus long ? ou m'offrir la possibilite de l'editer dans l'app ?" Plutot qu'un fichier
// WAV fixe, le bip est desormais SYNTHETISE A LA VOLEE (AudioTrack, meme calcul que
// l'ancien script Python ayant produit le WAV) pour une duree choisie dans l'app
// (sb_beep_duration/DURATION_PREF_KEY, meme boite de dialogue que le volume) plutot que
// figee dans un fichier - defaut releve a 700ms (2x les 350ms d'origine).
public class RestTimerReceiver extends BroadcastReceiver
{
    // 3e identifiant de canal : le son du canal passe a "aucun son" (le bip est
    // desormais joue separement par playRestTimerBeep()) - un canal de notification
    // etant immuable une fois cree sur Android 8+, il faut un nouvel id pour que ce
    // changement s'applique aussi sur les telephones ayant deja recu une version
    // precedente.
    private static final String CHANNEL_ID = "rest_timer_channel_v3";
    private static final int NOTIFICATION_ID = 4242;

    // Cle/valeur par defaut du volume du bip (0-100), partagees avec
    // AddExerciseActivity.setupTimer() (sb_volume) via les memes SharedPreferences que
    // la duree du minuteur ("seconds"). public : AddExerciseActivity vit dans le
    // package com.example.verifit.ui, different du package de cette classe.
    public static final String VOLUME_PREF_KEY = "rest_timer_volume";
    public static final int DEFAULT_VOLUME_PERCENT = 100;

    // Duree du bip (ms), reglable dans l'app (sb_beep_duration) - retour Romain
    // 06/09/2026 : "un peu court [...] possible de le rendre 2 fois plus long ? ou
    // m'offrir la possibilite de l'editer ?". Defaut double par rapport au premier jet
    // (350ms). Bornes de securite pour eviter un bip inaudible (trop court) ou
    // excessif (trop long) meme si la valeur stockee est corrompue/hors plage.
    public static final String DURATION_PREF_KEY = "rest_timer_beep_duration_ms";
    public static final int DEFAULT_DURATION_MS = 700;
    public static final int MIN_DURATION_MS = 150;
    public static final int MAX_DURATION_MS = 2000;

    private static final int SAMPLE_RATE = 44100;
    private static final double BEEP_FREQUENCY_HZ = 880.0; // La5 - clair et reconnaissable
    private static final int FADE_MS = 20; // fondu entree/sortie, evite tout "clic"

    // Filet de securite : si ni le marqueur de fin de lecture de l'AudioTrack ni la
    // gestion d'erreur ne se declenchent (comportement recalcitrant d'un OEM, etc.), on
    // libere quand meme le goAsync() plutot que de risquer un ANR.
    private static final long SAFETY_TIMEOUT_MS = 4000;

    @Override
    public void onReceive(Context context, Intent intent)
    {
        createNotificationChannel(context);
        postNotification(context);

        // goAsync() : le bip est joue de facon asynchrone (play() ne bloque pas) - sans
        // cela, rien ne garantit que le process reste actif assez longtemps pour que le
        // son soit entendu en entier si l'app etait deja tuee en arriere-plan au moment
        // ou l'alarme se declenche.
        playRestTimerBeep(context.getApplicationContext(), goAsync());
    }

    private void postNotification(Context context)
    {
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

        // Le son est joue separement par playRestTimerBeep() (volume/duree reglables
        // depuis l'app) - la notification elle-meme reste silencieuse pour eviter un
        // double signal sonore. Vibration courte conservee comme rappel secondaire.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
        {
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
        channel.setDescription("Notification (silencieuse) a la fin du repos entre les series - "
                + "le bip sonore est joue separement, volume/duree reglables dans l'app");
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 200});
        // Pas de son sur le canal : playRestTimerBeep() joue le bip separement, avec un
        // volume controle par l'app plutot que par le volume "notifications" du systeme.
        channel.setSound(null, null);

        notificationManager.createNotificationChannel(channel);
    }

    // Synthetise et joue le bip (sinusoide avec fondu entree/sortie, memes parametres
    // que l'ancien script ayant genere le fichier WAV du 3e jet) sur le flux ALARME, a
    // la duree et au volume choisis par Romain dans la boite de dialogue du minuteur.
    private void playRestTimerBeep(Context context, PendingResult pendingResult)
    {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        final boolean[] finished = {false};
        Runnable finishOnce = () ->
        {
            if (!finished[0])
            {
                finished[0] = true;
                pendingResult.finish();
            }
        };
        mainHandler.postDelayed(finishOnce, SAFETY_TIMEOUT_MS);

        try
        {
            SharedPreferences sharedPreferences = context.getSharedPreferences("shared preferences", Context.MODE_PRIVATE);
            int volumePercent = sharedPreferences.getInt(VOLUME_PREF_KEY, DEFAULT_VOLUME_PERCENT);
            float gain = Math.max(0, Math.min(100, volumePercent)) / 100f;

            int durationMs = sharedPreferences.getInt(DURATION_PREF_KEY, DEFAULT_DURATION_MS);
            durationMs = Math.max(MIN_DURATION_MS, Math.min(MAX_DURATION_MS, durationMs));

            short[] samples = generateBeepSamples(durationMs);
            int bufferSizeBytes = samples.length * 2; // 16 bits = 2 octets/echantillon

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            AudioTrack audioTrack;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            {
                AudioFormat audioFormat = new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build();
                audioTrack = new AudioTrack(audioAttributes, audioFormat, bufferSizeBytes,
                        AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE);
                audioTrack.setVolume(gain);
            }
            else
            {
                // Constructeur historique (avant AudioAttributes/AudioFormat, API < 21) -
                // improbable en pratique (minSdkVersion 16 herite du depot d'origine, mais
                // aucun telephone reel de Romain n'est aussi ancien), garde par prudence.
                //noinspection deprecation
                audioTrack = new AudioTrack(AudioManager.STREAM_ALARM, SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                        bufferSizeBytes, AudioTrack.MODE_STATIC);
                //noinspection deprecation
                audioTrack.setStereoVolume(gain, gain);
            }

            audioTrack.write(samples, 0, samples.length);

            AudioTrack finalAudioTrack = audioTrack;
            audioTrack.setNotificationMarkerPosition(samples.length);
            audioTrack.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener()
            {
                @Override
                public void onMarkerReached(AudioTrack track)
                {
                    finalAudioTrack.release();
                    finishOnce.run();
                }

                @Override
                public void onPeriodicNotification(AudioTrack track) {}
            }, mainHandler);

            audioTrack.play();
        }
        catch (Exception e)
        {
            // Ne doit jamais faire planter l'app (lecon du crash SCHEDULE_EXACT_ALARM) -
            // au pire, pas de bip cette fois-ci, mais la notification (silencieuse +
            // vibration) reste affichee.
            e.printStackTrace();
            finishOnce.run();
        }
    }

    // Genere une sinusoide (16 bits, mono) avec fondu lineaire entree/sortie - memes
    // parametres que le script Python ayant produit le WAV du 3e jet (880Hz, fondu de
    // 20ms), mais duree parametrable au lieu d'un fichier fige.
    private static short[] generateBeepSamples(int durationMs)
    {
        int totalSamples = SAMPLE_RATE * durationMs / 1000;
        int fadeSamples = Math.min(SAMPLE_RATE * FADE_MS / 1000, totalSamples / 2);
        short[] samples = new short[totalSamples];

        for (int i = 0; i < totalSamples; i++)
        {
            double t = i / (double) SAMPLE_RATE;
            double value = 0.9 * Math.sin(2 * Math.PI * BEEP_FREQUENCY_HZ * t);

            if (i < fadeSamples)
            {
                value *= i / (double) fadeSamples;
            }
            else if (i > totalSamples - fadeSamples)
            {
                value *= (totalSamples - i) / (double) fadeSamples;
            }

            samples[i] = (short) (value * Short.MAX_VALUE);
        }

        return samples;
    }
}
