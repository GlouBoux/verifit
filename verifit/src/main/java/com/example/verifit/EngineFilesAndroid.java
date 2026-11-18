package com.example.verifit;

import android.content.Context;
import android.util.AtomicFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Fichiers des donnees du moteur dans le dossier PRIVE de l'app (getFilesDir()/moteur), lot
 * M3.1 du 17/11/2026. Ecriture par AtomicFile : le nouveau contenu est ecrit a cote puis
 * remplace l'ancien d'un coup ; un arret pendant l'ecriture laisse l'ancien fichier intact.
 *
 * Dossier prive : hors de Documents (pas visible des autres apps ni de Syncthing), efface a
 * la desinstallation ; la copie de securite passe par le backup complet (lot M3.2).
 */
public final class EngineFilesAndroid implements EngineStorage.Files
{
    public static final String FOLDER = "moteur";

    private final File dir;

    public EngineFilesAndroid(Context context)
    {
        this.dir = new File(context.getApplicationContext().getFilesDir(), FOLDER);
    }

    @Override
    public String read(String name) throws IOException
    {
        AtomicFile file = new AtomicFile(new File(dir, name));
        // AtomicFile.openRead() reprend la copie de secours si une ecriture a ete
        // interrompue ; FileNotFoundException = fichier jamais ecrit.
        FileInputStream in;
        try
        {
            in = file.openRead();
        }
        catch (FileNotFoundException e)
        {
            return null;
        }
        try
        {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int n;
            while ((n = in.read(buffer)) != -1)
            {
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
        finally
        {
            in.close();
        }
    }

    @Override
    public void write(String name, String text) throws IOException
    {
        if (!dir.isDirectory() && !dir.mkdirs())
        {
            throw new IOException("dossier " + dir + " impossible à créer");
        }
        AtomicFile file = new AtomicFile(new File(dir, name));
        FileOutputStream out = file.startWrite();
        try
        {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            file.finishWrite(out); // vide sur le disque (fsync) puis remplace l'ancien
        }
        catch (IOException | RuntimeException e)
        {
            file.failWrite(out);
            throw e;
        }
    }
}
