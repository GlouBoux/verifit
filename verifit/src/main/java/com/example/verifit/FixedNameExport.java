package com.example.verifit;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Ecriture d'un fichier sous un nom FIXE, mis a jour sur place (lot D, etape D5,
 * 01/10/2026) : sert a l'export Coaching automatique (fitengine_coaching_latest.json), pour
 * que le fichier synchronise vers le PC garde toujours le meme nom au lieu d'accumuler
 * "fitengine_coaching_latest (1).json", "(2)"... comme le fait MediaStore a chaque insert.
 *
 * Logique pure, separee de MediaStore (interface Store, implementee par
 * MediaStoreExportStore sur Android) pour etre testee en JUnit.
 *
 * Contrainte Android (10+, stockage cloisonne) : une app ne peut retrouver et modifier
 * que les fichiers qu'ELLE a crees. Apres une reinstallation (ou "Effacer les donnees"),
 * l'ancien fichier appartient a l'ancienne installation : il est invisible et non
 * modifiable, et un nouvel insert du meme nom serait renomme "(1)". Ce cas est detecte
 * (BLOCKED_BY_FOREIGN_FILE) et remonte a l'utilisateur au lieu de creer un doublon
 * silencieux : le doublon cree par l'insert est supprime, l'utilisateur doit supprimer
 * l'ancien fichier a la main (appli Fichiers) une seule fois.
 */
public final class FixedNameExport
{
    public enum Result
    {
        CREATED,
        UPDATED,
        BLOCKED_BY_FOREIGN_FILE
    }

    // Acces au stockage. H = identifiant d'un fichier (un Uri sur Android).
    public interface Store<H>
    {
        // Fichier de ce nom deja cree par cette installation, ou null.
        H find(String name);

        // Nouveau fichier (le stockage peut le renommer en cas de collision). Jamais null
        // en fonctionnement normal ; leve IOException en cas d'echec.
        H create(String name) throws IOException;

        // Nom reellement donne au fichier (peut differer de celui demande).
        String nameOf(H file);

        // Ouvre en ecriture en vidant le contenu existant. Peut lever
        // SecurityException (fichier d'une autre installation) ou FileNotFoundException
        // (entree orpheline, fichier disparu du disque).
        OutputStream openTruncate(H file) throws IOException;

        // Supprime (meilleur effort, sans exception).
        void delete(H file);
    }

    private FixedNameExport() {}

    public static <H> Result write(Store<H> store, String name, byte[] content) throws IOException
    {
        H existing = store.find(name);
        if (existing != null)
        {
            OutputStream out = null;
            try
            {
                out = store.openTruncate(existing);
            }
            catch (FileNotFoundException e)
            {
                // Entree orpheline : on la supprime et on recree proprement ci-dessous.
                store.delete(existing);
            }
            catch (SecurityException e)
            {
                // Pas a nous : on tentera de creer, ce qui sera detecte comme bloque.
            }

            if (out != null)
            {
                writeAndClose(out, content);
                return Result.UPDATED;
            }
        }

        H created = store.create(name);
        if (created == null)
        {
            throw new IOException("Creation du fichier impossible : " + name);
        }

        if (!name.equals(store.nameOf(created)))
        {
            // Le stockage a renomme (collision avec un fichier invisible pour nous) : on
            // ne laisse pas de doublon.
            store.delete(created);
            return Result.BLOCKED_BY_FOREIGN_FILE;
        }

        writeAndClose(store.openTruncate(created), content);
        return Result.CREATED;
    }

    private static void writeAndClose(OutputStream out, byte[] content) throws IOException
    {
        try
        {
            out.write(content);
            out.flush();
        }
        finally
        {
            out.close();
        }
    }
}
