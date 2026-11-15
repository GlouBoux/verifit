package com.example.verifit;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.TimeZone;

/**
 * Journal de plantage (lot M0.3, 14/11/2026, point F4 de la revue securite du 27/10/2026).
 *
 * Fonctionnement en deux temps, parce qu'ecrire dans MediaStore depuis un process qui est en
 * train de mourir est trop fragile :
 * 1. au moment du plantage, on ecrit seulement un petit fichier texte dans le dossier PRIVE de
 *    l'app (getFilesDir()/crash), operation simple et synchrone, puis on rend la main au
 *    gestionnaire d'origine (le plantage reste visible, comme avant) ;
 * 2. au lancement suivant (VerifitApplication.onCreate), flushPendingAsync() copie les fichiers
 *    en attente vers Documents/FitEngine/crash (mecanisme MediaStore de BackupManager), puis
 *    les supprime du dossier prive. On garde les CrashReport.KEEP plus recents.
 *
 * record(origine, erreur) sert aux erreurs RATTRAPEES (par exemple une exception Python, lot
 * M2) : meme fichier, mais le process continue, donc la copie vers Documents se fait tout de
 * suite en arriere-plan.
 *
 * Avant Android 10, MediaStore ne permet pas d'ecrire dans Documents : les journaux restent
 * alors dans le dossier prive (rotation comprise), lisibles seulement avec adb.
 */
public final class CrashLog
{
    private static final String TAG = "CrashLog";
    private static final Object LOCK = new Object();

    private static Context appContext;
    private static boolean installed;

    private CrashLog() {}

    // A appeler le plus tot possible (VerifitApplication.onCreate). Sans effet au deuxieme appel.
    public static void install(Application application)
    {
        synchronized (LOCK)
        {
            if (installed)
            {
                return;
            }
            installed = true;
            appContext = application.getApplicationContext();
        }

        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler()
        {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable)
            {
                try
                {
                    writePending("crash", thread, throwable);
                }
                catch (Throwable ignored)
                {
                    // Rien a faire de plus : le plantage d'origine doit suivre son cours.
                }
                if (previous != null)
                {
                    previous.uncaughtException(thread, throwable);
                }
            }
        });
    }

    // Erreur rattrapee : origine = ce qui l'a produite (ex. "python"). Ne leve jamais.
    public static void record(String origin, Throwable throwable)
    {
        try
        {
            if (!writePending(origin, Thread.currentThread(), throwable))
            {
                Log.w(TAG, "Erreur non journalisee (CrashLog non installe) : " + origin, throwable);
                return;
            }
            flushPendingAsync(appContext);
        }
        catch (Throwable e)
        {
            Log.e(TAG, "Journalisation impossible (" + origin + ")", e);
        }
    }

    // Copie les journaux en attente vers Documents, sans bloquer le demarrage de l'app.
    public static void flushPendingAsync(final Context context)
    {
        if (context == null)
        {
            return;
        }
        final Context applicationContext = context.getApplicationContext();
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    flushPending(applicationContext);
                }
                catch (Throwable e)
                {
                    Log.e(TAG, "Copie des journaux de plantage impossible", e);
                }
            }
        }, AppNames.FILE_PREFIX + "-crash-flush").start();
    }

    // ---------------------------------------------------------------- dossier prive

    private static File pendingDir(Context context)
    {
        return new File(context.getFilesDir(), CrashReport.SUBFOLDER);
    }

    // Ecrit le journal dans le dossier prive (fichier temporaire puis renommage : jamais de
    // fichier a moitie ecrit). Faux si CrashLog n'est pas installe.
    private static boolean writePending(String origin, Thread thread, Throwable throwable) throws IOException
    {
        Context context = appContext;
        if (context == null)
        {
            return false;
        }
        long now = System.currentTimeMillis();
        TimeZone zone = TimeZone.getDefault();
        String text = CrashReport.format(now, zone, origin,
                thread == null ? null : thread.getName(),
                BuildConfig.VERSION_NAME, BuildConfig.BUILD_TIMESTAMP,
                Build.MANUFACTURER + " " + Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT,
                throwable);

        File dir = pendingDir(context);
        dir.mkdirs();
        String name = CrashReport.fileName(now, zone, origin);
        File temp = new File(dir, name + ".tmp");
        FileOutputStream out = new FileOutputStream(temp);
        try
        {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        finally
        {
            out.close();
        }
        if (!temp.renameTo(new File(dir, name)))
        {
            throw new IOException("renommage impossible : " + name);
        }
        prunePending(dir);
        return true;
    }

    private static void prunePending(File dir)
    {
        String[] names = dir.list();
        if (names == null)
        {
            return;
        }
        for (String name : CrashReport.namesToDelete(Arrays.asList(names), CrashReport.KEEP))
        {
            new File(dir, name).delete();
        }
    }

    // ---------------------------------------------------------------- copie vers Documents

    private static void flushPending(Context context)
    {
        synchronized (LOCK)
        {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            {
                return;
            }
            File dir = pendingDir(context);
            String[] names = dir.list();
            if (names == null)
            {
                return;
            }
            Arrays.sort(names);

            String relativePath = BackupManager.backupDir() + "/" + CrashReport.SUBFOLDER;
            boolean copied = false;
            for (String name : names)
            {
                // Les fichiers .tmp (ecriture interrompue) ne sont jamais copies.
                if (!name.startsWith(CrashReport.FILE_PREFIX) || !name.endsWith(CrashReport.FILE_SUFFIX))
                {
                    continue;
                }
                File file = new File(dir, name);
                try
                {
                    BackupManager.writeDocument(context, relativePath, name, readText(file), "text/plain");
                    copied = true;
                    if (!file.delete())
                    {
                        Log.w(TAG, "Journal copie mais non supprime du dossier prive : " + name);
                    }
                }
                catch (IOException | RuntimeException e)
                {
                    // Le fichier reste dans le dossier prive : nouvel essai au prochain lancement.
                    Log.e(TAG, "Copie du journal impossible (" + name + ")", e);
                }
            }
            if (copied)
            {
                BackupManager.pruneOldFiles(context, relativePath, CrashReport.FILE_PREFIX, CrashReport.KEEP);
            }
        }
    }

    private static String readText(File file) throws IOException
    {
        InputStream in = new FileInputStream(file);
        try
        {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) != -1)
            {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
        finally
        {
            in.close();
        }
    }
}
