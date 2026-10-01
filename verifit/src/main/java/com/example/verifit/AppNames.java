package com.example.verifit;

/**
 * Nom de l'app et noms de fichiers/dossiers exportes, en UN seul endroit (renommage
 * "Verifit" -> "FitEngine", 01/10/2026).
 *
 * Ce qui change : le nom affiche, le dossier Documents/FitEngine et le prefixe des
 * fichiers exportes. Ce qui NE change PAS, volontairement : l'applicationId
 * (com.whatever.verifit, le changer ferait une AUTRE app, donc la perte des donnees),
 * le package Java et les noms de classes.
 *
 * Les anciens fichiers verifit_* (dossier Documents/Verifit) ne sont ni renommes ni
 * supprimes : l'app continue seulement de reconnaitre le format des anciens backups
 * complets pour pouvoir les restaurer (voir isBackupFormat()).
 *
 * Classe sans aucune dependance Android : utilisable dans les tests JUnit locaux.
 */
public final class AppNames
{
    private AppNames() {}

    public static final String APP_NAME = "FitEngine";

    // Dossier d'export sous Documents/ (backups, exports CSV/JSON) ; les copies
    // automatiques vont dans le sous-dossier AUTO_SUBFOLDER.
    public static final String EXPORT_FOLDER = "FitEngine";
    public static final String AUTO_SUBFOLDER = "auto";

    // Prefixe des fichiers exportes : fitengine_backup_..., fitengine_coaching_latest.json...
    public static final String FILE_PREFIX = "fitengine";

    // Champ "format" ecrit dans un backup complet. Les backups faits avant le renommage
    // portent l'ancien identifiant : ils restent restaurables.
    public static final String BACKUP_FORMAT = "fitengine-full-backup";
    public static final String LEGACY_BACKUP_FORMAT = "verifit-full-backup";

    public static boolean isBackupFormat(String format)
    {
        return BACKUP_FORMAT.equals(format) || LEGACY_BACKUP_FORMAT.equals(format);
    }

    // Nom du fichier de backup complet manuel (hors extension et date).
    public static final String FULL_BACKUP_FILE_PREFIX = FILE_PREFIX + "_backup_complet_";

    // Nom fixe de l'export Coaching "dernier etat" (lot D, D5).
    public static final String LATEST_COACHING_EXPORT_NAME = FILE_PREFIX + "_coaching_latest.json";
}
