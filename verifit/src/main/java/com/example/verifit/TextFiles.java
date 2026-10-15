package com.example.verifit;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

    public static String readAll(Uri uri, Context context) throws IOException
    {
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        if (inputStream == null)
        {
            throw new IOException("Unable to open selected file");
        }
        return readAll(inputStream);
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
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
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
}
