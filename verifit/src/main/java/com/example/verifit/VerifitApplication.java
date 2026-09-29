package com.example.verifit;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.PreferenceManager;

import com.example.verifit.ui.MainActivity;

/**
 * Vague IHM-Dark (16/09/2026, retour Romain : "le bouton Light/Dark/Système dans les
 * Réglages, comme FitNotes").
 *
 * Seule raison d'etre de cette classe : appliquer le theme Light/Dark/Système choisi
 * par l'utilisateur (voir ThemeHelper) le plus tot possible dans le cycle de vie du
 * process - avant que la moindre Activity ne soit creee - pour que l'app s'ouvre
 * directement dans le bon theme, sans flash visuel ni recreation d'ecran. Declaree via
 * android:name=".VerifitApplication" dans AndroidManifest.xml.
 */
public class VerifitApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeHelper.applyStoredTheme(this);
        removeLegacyPreferences();

        // Revue d'architecture du 28/09/2026 (point 1.2) : charger les donnees avant le
        // code de CHAQUE ecran, pas seulement de MainActivity. onActivityCreated() est
        // appele pendant le super.onCreate() de l'Activity, donc avant que l'ecran ne lise
        // ou n'ecrive dataStorage - y compris quand Android recree directement un ecran
        // apres avoir tue l'app en arriere-plan. Pas de chargement dans onCreate() de
        // l'Application elle-meme : elle demarre aussi pour le seul minuteur de repos
        // (RestTimerReceiver), qui n'a pas besoin de l'historique.
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                MainActivity.dataStorage.ensureLoaded(activity);
                // Copie automatique quotidienne (point 1.4, voir BackupManager).
                BackupManager.dailySnapshotIfNeeded(activity, MainActivity.dataStorage);
            }
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityResumed(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }

    // Lot B du nettoyage (29/09/2026) : cles laissees sur le telephone par le backend
    // verifit_rs et WebDAV, supprimes du code (dont le mot de passe WebDAV, stocke en
    // clair). Suppression cle par cle, jamais de clear() : le fichier "shared
    // preferences" contient aussi l'historique. Sans effet une fois les cles parties.
    private static final String[] LEGACY_KEYS = {
            "mode", "refresh_required", "verifit_rs_token", "verifit_rs_username", "verifit_rs_password",
            "webdav_url", "webdav_username", "webdav_password", "togglewebdav", "autowebdavbackup",
            "autobackup", "autoBackupRequired", "inAddExerciseActivity",
            "webdavurl", "webdavusername", "webdavpassword"
    };

    private void removeLegacyPreferences()
    {
        removeKeys(getSharedPreferences("shared preferences", MODE_PRIVATE));
        removeKeys(PreferenceManager.getDefaultSharedPreferences(this));
    }

    private static void removeKeys(SharedPreferences prefs)
    {
        SharedPreferences.Editor editor = null;
        for (String key : LEGACY_KEYS)
        {
            if (prefs.contains(key))
            {
                if (editor == null) editor = prefs.edit();
                editor.remove(key);
            }
        }
        if (editor != null) editor.apply();
    }
}
