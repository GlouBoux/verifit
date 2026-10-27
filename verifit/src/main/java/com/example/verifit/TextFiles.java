package com.example.verifit;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.BufferedReader;
import java.io.FilterReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Lecture d'un fichier texte (UTF-8) choisi par l'utilisateur : seance JSON
 * (SessionImporter), notes d'exercices (ExerciseNotesImporter), backup complet
 * (BackupManager).
 *
 * Lot C, etape C.3 (01/10/2026) : cette lecture etait copiee dans les deux importeurs.
 */
public final class TextFiles
{
    private TextFiles() {}

    // Taille maximale lue, en caracteres (lot S2, revue securite du 27/10/2026) : sans
    // limite, un fichier enorme recu d'une autre appli ("Ouvrir avec FitEngine") etait
    // charge entierement en memoire et faisait planter l'app. Un plan de seance fait
    // quelques Ko ; un backup complet environ 1 Mo en octobre 2026.
    public static final long MAX_SESSION_CHARS = 1000000L;
    public static final long MAX_FILE_CHARS = 50000000L;

    public static String readAll(Uri uri, Context context) throws IOException
    {
        return readAll(uri, context, MAX_FILE_CHARS);
    }

    public static String readAll(Uri uri, Context context, long maxChars) throws IOException
    {
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        if (inputStream == null)
        {
            throw new IOException("Unable to open selected file");
        }
        return readAll(inputStream, maxChars);
    }

    // Nom du fichier tel que l'affiche l'appli qui le fournit (Fichiers, Drive, dossier
    // des plans...), ou "" si inconnu. Meilleur effort, ne leve jamais (import idempotent,
    // 15/10/2026 : nom garde dans le registre des plans importes et l'accuse pour le PC).
    public static String displayName(Uri uri, Context context)
    {
        if (uri == null)
        {
            return "";
        }
        Cursor cursor = null;
        try
        {
            cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0))
            {
                return cursor.getString(0);
            }
        }
        catch (RuntimeException ignored)
        {
            // Fournisseur qui ne connait pas cette colonne, ou uri "file://" : repli ci-dessous.
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
        }
        String last = uri.getLastPathSegment();
        return last == null ? "" : last;
    }

    // Lit tout le flux, ligne par ligne (chaque ligne suivie de '\n'), puis le ferme.
    static String readAll(InputStream inputStream) throws IOException
    {
        return readAll(inputStream, MAX_FILE_CHARS);
    }

    // Meme lecture, mais leve IOException des que plus de maxChars caracteres ont ete
    // lus : la limite est verifiee pendant la lecture (LimitedReader), pas apres, sinon
    // une seule ligne geante serait deja entierement en memoire.
    static String readAll(InputStream inputStream, long maxChars) throws IOException
    {
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader(new LimitedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8), maxChars));
            String line;
            while ((line = reader.readLine()) != null)
            {
                builder.append(line).append('\n');
            }
        }
        finally
        {
            if (reader != null)
            {
                reader.close();
            }
            else
            {
                inputStream.close();
            }
        }
        return builder.toString();
    }

    // Lecteur qui compte les caracteres lus et s'arrete au-dela de la limite.
    private static final class LimitedReader extends FilterReader
    {
        private final long maxChars;
        private long count;

        LimitedReader(Reader in, long maxChars)
        {
            super(in);
            this.maxChars = maxChars;
        }

        @Override
        public int read() throws IOException
        {
            int c = super.read();
            if (c >= 0)
            {
                add(1);
            }
            return c;
        }

        @Override
        public int read(char[] buffer, int offset, int length) throws IOException
        {
            int n = super.read(buffer, offset, length);
            if (n > 0)
            {
                add(n);
            }
            return n;
        }

        private void add(long n) throws IOException
        {
            count += n;
            if (count > maxChars)
            {
                throw new IOException("fichier trop gros (limite : " + maxChars + " caracteres)");
            }
        }
    }
}
