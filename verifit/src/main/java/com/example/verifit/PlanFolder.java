package com.example.verifit;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Acces en LECTURE SEULE au dossier des plans Documents/FitEngine_plans (session du
 * 15/10/2026, bouton "Importer la seance en attente").
 *
 * Pourquoi pas MediaStore comme pour Documents/FitEngine (MediaStoreExportStore) : depuis
 * Android 11 (stockage cloisonne), une app ne voit dans MediaStore que les fichiers
 * qu'elle a crees elle-meme, plus les vrais medias (images, sons, videos). Les plans sont
 * ecrits par Syncthing : invisibles par ce chemin, et READ_EXTERNAL_STORAGE n'y change
 * rien pour des .json. Solution retenue : l'utilisateur choisit UNE fois le dossier dans
 * le selecteur Android (ACTION_OPEN_DOCUMENT_TREE) et l'app garde l'autorisation de
 * LECTURE seulement (takePersistableUriPermission avec FLAG_GRANT_READ_URI_PERMISSION,
 * jamais l'ecriture) : aucune permission speciale, et l'app ne peut pas modifier,
 * deplacer ni supprimer un fichier de ce dossier, meme par erreur.
 *
 * Android 5 (API 21) minimum pour les dossiers choisis. Non testable en JUnit local
 * (ContentResolver) : le choix des plans est dans PendingPlans.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
public final class PlanFolder
{
    public static final String EXPECTED_PATH = "Documents/" + AppNames.PLANS_FOLDER;

    private static final String PREFS_NAME = "plan_folder";
    private static final String PREF_TREE_URI = "tree_uri";
    private static final String EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents";

    private PlanFolder() {}

    // Dossier deja choisi ET dont l'autorisation de lecture est toujours valide, sinon null.
    public static Uri grantedTree(Context context)
    {
        String saved = prefs(context).getString(PREF_TREE_URI, "");
        if (saved.isEmpty())
        {
            return null;
        }
        Uri tree = Uri.parse(saved);
        for (UriPermission permission : context.getContentResolver().getPersistedUriPermissions())
        {
            if (permission.isReadPermission() && tree.equals(permission.getUri()))
            {
                return tree;
            }
        }
        return null;
    }

    // Selecteur de dossier, ouvert si possible directement sur Documents/FitEngine_plans
    // (Android 8+ ; avant, l'utilisateur y navigue lui-meme).
    public static Intent pickIntent()
    {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                    DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, "primary:" + EXPECTED_PATH));
        }
        return intent;
    }

    // Dossier choisi dans le selecteur : garde l'autorisation de LECTURE (seulement) et
    // libere celle d'un dossier choisi auparavant. Leve SecurityException si le systeme
    // refuse de garder l'autorisation.
    public static void remember(Context context, Uri tree)
    {
        Uri previous = savedTree(context);
        context.getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        prefs(context).edit().putString(PREF_TREE_URI, tree.toString()).apply();
        if (previous != null && !previous.equals(tree))
        {
            release(context, previous);
        }
    }

    // Oublie le dossier (autorisation perdue, dossier supprime...) : il sera redemande.
    public static void forget(Context context)
    {
        Uri previous = savedTree(context);
        prefs(context).edit().remove(PREF_TREE_URI).apply();
        if (previous != null)
        {
            release(context, previous);
        }
    }

    // "Documents/FitEngine_plans" pour un dossier du stockage interne, sinon l'identifiant
    // brut du fournisseur (carte SD, autre appli...).
    public static String describe(Uri tree)
    {
        String id = DocumentsContract.getTreeDocumentId(tree);
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    // Contenu du premier niveau du dossier (pas de sous-dossiers parcourus). Leve
    // SecurityException ou IllegalArgumentException si l'autorisation ou le dossier n'est
    // plus valide, IOException si le fournisseur ne repond pas.
    public static List<PendingPlans.FileEntry<Uri>> list(Context context, Uri tree) throws IOException
    {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        Cursor cursor = context.getContentResolver().query(children, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
        if (cursor == null)
        {
            throw new IOException("dossier illisible : " + describe(tree));
        }

        ArrayList<PendingPlans.FileEntry<Uri>> entries = new ArrayList<PendingPlans.FileEntry<Uri>>();
        try
        {
            while (cursor.moveToNext())
            {
                String documentId = cursor.getString(0);
                String name = cursor.getString(1);
                long lastModified = cursor.isNull(2) ? 0L : cursor.getLong(2);
                boolean isDirectory = DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(3));
                Uri document = DocumentsContract.buildDocumentUriUsingTree(tree, documentId);
                entries.add(new PendingPlans.FileEntry<Uri>(document, name, lastModified, isDirectory));
            }
        }
        finally
        {
            cursor.close();
        }
        return entries;
    }

    private static Uri savedTree(Context context)
    {
        String saved = prefs(context).getString(PREF_TREE_URI, "");
        return saved.isEmpty() ? null : Uri.parse(saved);
    }

    private static void release(Context context, Uri tree)
    {
        try
        {
            context.getContentResolver().releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        catch (RuntimeException ignored)
        {
            // Deja perdue : rien a liberer.
        }
    }

    private static SharedPreferences prefs(Context context)
    {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
