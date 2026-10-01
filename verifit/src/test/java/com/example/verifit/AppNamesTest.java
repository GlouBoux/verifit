package com.example.verifit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Renommage Verifit -> FitEngine (01/10/2026) : noms exportes centralises dans AppNames,
 * et anciens backups complets toujours restaurables.
 */
public class AppNamesTest
{
    @Test
    public void exportedNamesUseTheNewName()
    {
        assertEquals("FitEngine", AppNames.EXPORT_FOLDER);
        assertEquals("fitengine_coaching_latest.json", AppNames.LATEST_COACHING_EXPORT_NAME);
        assertEquals("fitengine_backup_complet_", AppNames.FULL_BACKUP_FILE_PREFIX);
        assertEquals("fitengine-full-backup", AppNames.BACKUP_FORMAT);
    }

    @Test
    public void newConstantsNoLongerMentionTheOldName()
    {
        String[] all = {
                AppNames.APP_NAME, AppNames.EXPORT_FOLDER, AppNames.AUTO_SUBFOLDER, AppNames.FILE_PREFIX,
                AppNames.BACKUP_FORMAT, AppNames.FULL_BACKUP_FILE_PREFIX, AppNames.LATEST_COACHING_EXPORT_NAME };
        for (String s : all)
        {
            assertFalse(s, s.toLowerCase().contains("verifit"));
        }
    }

    @Test
    public void dataStorageAndBackupManagerUseTheCentralNames()
    {
        assertEquals(AppNames.LATEST_COACHING_EXPORT_NAME, DataStorage.LATEST_COACHING_EXPORT_NAME);
        assertEquals(AppNames.BACKUP_FORMAT, BackupManager.FORMAT);
    }

    @Test
    public void bothBackupFormatsAreAccepted()
    {
        assertTrue(AppNames.isBackupFormat("fitengine-full-backup"));
        assertTrue(AppNames.isBackupFormat("verifit-full-backup"));
    }

    @Test
    public void otherFormatsAreRejected()
    {
        assertFalse(AppNames.isBackupFormat(null));
        assertFalse(AppNames.isBackupFormat(""));
        assertFalse(AppNames.isBackupFormat("fitnotes-full-backup"));
        assertFalse(AppNames.isBackupFormat("FITENGINE-FULL-BACKUP"));
    }

    private static String emptyBackup(String format)
    {
        return "{\"format\":\"" + format + "\",\"schemaVersion\":1,\"exportedAt\":\"2026-10-01T10:00:00\","
                + "\"workoutDays\":[],\"knownExercises\":[],\"goals\":[]}";
    }

    @Test
    public void parseAcceptsAnOldBackupMadeBeforeTheRename()
    {
        BackupManager.FullBackup backup = BackupManager.parse(emptyBackup("verifit-full-backup"));
        assertEquals("2026-10-01T10:00:00", backup.getExportedAt());
        assertEquals(0, backup.countSets());
    }

    @Test
    public void parseAcceptsANewBackup()
    {
        BackupManager.FullBackup backup = BackupManager.parse(emptyBackup("fitengine-full-backup"));
        assertEquals(0, backup.getWorkoutDays().size());
    }

    @Test
    public void parseRejectsAnotherFormatAndNamesTheExpectedFile()
    {
        try
        {
            BackupManager.parse(emptyBackup("something-else"));
            fail("un format inconnu doit etre refuse");
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage(), e.getMessage().contains("fitengine_backup_complet_"));
        }
    }
}
