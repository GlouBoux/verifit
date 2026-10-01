package com.example.verifit;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Implementation Android (10+) de FixedNameExport.Store : un dossier de Documents, via
 * MediaStore. Non testable en JUnit local (ContentResolver) : la logique est dans
 * FixedNameExport, testee avec un faux Store.
 */
public class MediaStoreExportStore implements FixedNameExport.Store<Uri>
{
    private final ContentResolver resolver;
    private final String mimeType;
    // Dossier relatif tel qu'ecrit a l'insertion, ex. "Documents/Verifit" ; MediaStore le
    // stocke avec un "/" final, d'ou la requete sur relativePath + "/".
    private final String relativePath;

    public MediaStoreExportStore(Context context, String relativePath, String mimeType)
    {
        this.resolver = context.getContentResolver();
        this.relativePath = relativePath;
        this.mimeType = mimeType;
    }

    private Uri collection()
    {
        return MediaStore.Files.getContentUri("external");
    }

    @Override
    public Uri find(String name)
    {
        Cursor cursor = resolver.query(
                collection(),
                new String[]{MediaStore.MediaColumns._ID},
                MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                new String[]{name, relativePath + "/"},
                null);
        if (cursor == null)
        {
            return null;
        }

        try
        {
            if (cursor.moveToFirst())
            {
                return ContentUris.withAppendedId(collection(), cursor.getLong(0));
            }
            return null;
        }
        finally
        {
            cursor.close();
        }
    }

    @Override
    public Uri create(String name) throws IOException
    {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);
        Uri uri = resolver.insert(collection(), values);
        if (uri == null)
        {
            throw new IOException("MediaStore a refuse la creation de " + name);
        }
        return uri;
    }

    @Override
    public String nameOf(Uri file)
    {
        Cursor cursor = resolver.query(file, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null);
        if (cursor == null)
        {
            return null;
        }

        try
        {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
        finally
        {
            cursor.close();
        }
    }

    @Override
    public OutputStream openTruncate(Uri file) throws IOException
    {
        // "wt" : ecriture avec troncature (sans ca, un contenu plus court laisserait la fin
        // de l'ancien JSON derriere le nouveau).
        OutputStream out = resolver.openOutputStream(file, "wt");
        if (out == null)
        {
            throw new IOException("Ouverture impossible : " + file);
        }
        return out;
    }

    @Override
    public void delete(Uri file)
    {
        try
        {
            resolver.delete(file, null, null);
        }
        catch (RuntimeException ignored)
        {
            // Meilleur effort.
        }
    }
}
